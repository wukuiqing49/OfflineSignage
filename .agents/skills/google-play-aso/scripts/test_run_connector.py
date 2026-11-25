"""独立安装、项目隔离与失败恢复的离线回归。"""
from __future__ import annotations
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch
import run_connector as runner
import project_context
import import_reports
import engine_worker


class StandaloneTests(unittest.TestCase):
    def invoke(self, args):
        """捕获入口的结构化输出。"""
        output = io.StringIO()
        with redirect_stdout(output):
            code = runner.main(args)
        return code, json.loads(output.getvalue())

    def test_copied_skill_runs_without_external_project_or_site_packages(self):
        """在两种项目目录中复制 Skill，从无关 cwd 用标准库独立运行。"""
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for dirname in ('.agent', '.agents'):
                project = root / dirname[1:]
                target = project / dirname / 'skills/google-play-aso'
                shutil.copytree(Path(__file__).resolve().parents[1], target)
                app = project / 'app'
                app.mkdir()
                (app / 'build.gradle').write_text('applicationId "com.example.product"', encoding='utf-8')
                result = subprocess.run([sys.executable, '-S', '-X', 'utf8', '-B', str(target / 'scripts/run_connector.py'), 'inspect'],
                                        cwd=root, capture_output=True, text=True, encoding='utf-8')
                self.assertEqual(result.returncode, 0, result.stderr)
                data = json.loads(result.stdout)
                self.assertEqual(Path(data['projectRoot']), project)
                self.assertEqual(data['applicationIdCandidates'][0]['packageName'], 'com.example.product')
                self.assertFalse(data['applicationIdCandidates'][0]['verifiedRelease'])

    def test_dry_run_uses_only_project_config_and_full_scope(self):
        """项目配置决定默认 App，dry-run 不触发在线进程。"""
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            config = root / '.agents/config'
            config.mkdir(parents=True)
            (config / 'google-play-aso.json').write_text(json.dumps({'apps': {'A': {'packageName':'com.example.a','defaultLocale':'fr-FR'}}}))
            with patch.object(runner.subprocess, 'run') as process:
                code, data = self.invoke(['analyze','--project',folder,'--dry-run','--days','7'])
            process.assert_not_called()
            self.assertEqual(code, 0)
            self.assertEqual(data['analysisDays'], 7)
            self.assertEqual(data['apps'][0]['locale'], 'fr-FR')
            self.assertFalse(data['executed'])

    def test_missing_app_does_not_fetch_account_catalog(self):
        """没有确认的发行包时停止联网，不把静态候选当成授权范围。"""
        with tempfile.TemporaryDirectory() as folder, patch.object(runner.subprocess, 'run') as process:
            code, _ = self.invoke(['analyze','--project',folder])
        self.assertEqual(code, 2)
        process.assert_not_called()

    def test_explicit_app_overrides_config(self):
        """明确选择的 App 不扩展到配置的其他包。"""
        with tempfile.TemporaryDirectory() as folder:
            code, data = self.invoke(['analyze','--project',folder,'--app','com.example.b','--app','com.example.b','--dry-run'])
        self.assertEqual(code, 0)
        self.assertEqual(len(data['apps']), 1)

    def test_copy_validation_requires_all_fields_and_handles_special_text(self):
        """缺字段不会误报成功，特殊字符无需 shell 转义。"""
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / 'copy.json'
            file.write_text(json.dumps({'title':'Title'}))
            self.assertEqual(self.invoke(['validate-copy','--copy-json',str(file)])[0], 2)
            file.write_text(json.dumps({'title':'--help $(text)', 'shortDescription':'Line\nTwo','fullDescription':'完整说明'}), encoding='utf-8')
            self.assertEqual(self.invoke(['validate-copy','--copy-json',str(file)])[0], 0)
            file.write_text(json.dumps({'title':'x'*31, 'shortDescription':'short','fullDescription':'full'}))
            self.assertEqual(self.invoke(['validate-copy','--copy-json',str(file)])[0], 1)

    def test_evidence_rejects_wrong_scope(self):
        """包名、语言、周期和运行标识均必须匹配。"""
        request = dict(packageName='com.example.a',locale='en-US',analysisDays=30,runId='new')
        for key in request:
            data = dict(request,status='ok',fetchedAt='2026-10-30T00:00:00Z')
            data[key] = 'wrong'
            with self.assertRaises(ValueError):
                runner.validate_evidence(data, request)
        with self.assertRaises(ValueError):
            runner.validate_evidence([], request)
        with self.assertRaises(ValueError):
            runner.validate_evidence(dict(request, status='ok', fetchedAt='invalid'), request)

    def test_timeout_preserves_previous_app_and_continues(self):
        """第二个 App 超时不覆盖第一个的结果，第三个仍执行。"""
        calls = []
        def process(command, **kwargs):
            request_path = Path(command[-1])
            request = json.loads(request_path.read_text(encoding='utf-8'))
            calls.append(request['packageName'])
            if len(calls) == 2:
                # 第一项已写入 manifest，恢复不依赖进程全部结束。
                manifest = json.loads(request_path.parents[2].joinpath('manifest.json').read_text(encoding='utf-8'))
                self.assertEqual(len(manifest['results']), 1)
                raise subprocess.TimeoutExpired(command, 1)
            data = {**request,'status':'ok','fetchedAt':'2026-10-30T00:00:00Z'}
            request_path.with_name('evidence.json').write_text(json.dumps(data))
            request_path.with_name('数据分析.md').write_text('data')
            return subprocess.CompletedProcess(command,0,stdout='',stderr='private error')
        with tempfile.TemporaryDirectory() as folder, patch.object(runner,'inspect_project',return_value={}), patch.object(runner.subprocess,'run',side_effect=process):
            code, data = self.invoke(['analyze','--project',folder,'--app','com.example.a','--app','com.example.b','--app','com.example.c'])
            self.assertEqual(code, 1)
            self.assertEqual([r['status'] for r in data['results']], ['ok','timeout','ok'])
            self.assertNotIn('private error',json.dumps(data))
            self.assertTrue(Path(data['results'][0]['evidenceFile']).is_file())
        self.assertEqual(len(calls), 3)

    def test_success_without_evidence_is_failure(self):
        """退出码 0 不能替代真实证据。"""
        with tempfile.TemporaryDirectory() as folder, patch.object(runner,'inspect_project',return_value={}), patch.object(runner.subprocess,'run',return_value=subprocess.CompletedProcess([],0,'','')):
            code, data = self.invoke(['analyze','--project',folder,'--app','com.example.a'])
        self.assertEqual(code, 1)
        self.assertEqual(data['results'][0]['status'], 'error')

    def test_check_reports_missing_dependencies_without_network(self):
        """离线检查准确反映依赖缺失，不把 --help 成功当完整环境就绪。"""
        with patch.object(runner.importlib,'import_module',side_effect=ImportError):
            result = runner.check_dependencies()
        self.assertTrue(result['localAnalysisReady'])
        self.assertFalse(result['onlineDependenciesReady'])
        self.assertEqual(result['remoteAuthorization'],'not_checked')

    def test_offline_import_rejects_mixed_apps_and_duplicate_rows(self):
        """导入错误 App 或重复统计时返回错误，合法导入保留来源。"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'export.csv'
            header = 'Date,Package Name,Country,Daily User Installs,Daily User Uninstalls\n'
            args = ['--project',folder,'--app','com.example.a','--kind','installs','--csv',str(path),'--days','1','--end-date','2026-10-30']
            for rows, expected in [('2026-10-30,com.example.b,US,2,1\n',2),('2026-10-30,com.example.a,US,2,1\n'*2,2),('2026-10-30,com.example.a,US,2,1\n',0)]:
                path.write_text(header+rows)
                with redirect_stdout(io.StringIO()) as out:
                    code = import_reports.main(args)
                self.assertEqual(code,expected)
                if code == 0:
                    data = json.loads(Path(json.loads(out.getvalue())['evidenceFile']).read_text(encoding='utf-8'))
                    self.assertEqual(data['mode'],'offline_import')
                    self.assertEqual(data['summary']['totalUserInstalls'],2)

    def test_worker_fetches_and_writes_from_mock_api(self):
        """真实 worker 组装路径以模拟 API 验证，无账号无网络。"""
        with tempfile.TemporaryDirectory() as folder:
            request = dict(packageName='com.example.a',appName='A',locale='en-US',localeSource='requested',analysisDays=30,limit=5,runId='test')
            path = Path(folder) / 'request.json'
            path.write_text(json.dumps(request))
            with patch.object(engine_worker.cli,'make_client') as client:
                client.return_value.list_reviews.return_value = {'reviews':[]}
                client.return_value.get_listing.return_value = {'listing':{'title':'A','shortDescription':'Short','fullDescription':'Full'}}
                code = engine_worker.main(path)
            self.assertEqual(code,0)
            data = json.loads(path.with_name('evidence.json').read_text(encoding='utf-8'))
            runner.validate_evidence(data,request)
            self.assertTrue(path.with_name('数据分析.md').is_file())


if __name__ == '__main__':
    unittest.main()
