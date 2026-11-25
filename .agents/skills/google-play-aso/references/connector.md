# 项目配置与独立运行

离线脚本支持 Python 3.10+；在线环境建议 Python 3.11+（本机 3.10 的 Google SDK 已发出支持期警告）。所有业务代码位于本 Skill 的 scripts/googleplay_aso。来源快照记录在 engine-origin.json，原工程不是运行依赖。不要查找同级工具目录、使用 GOOGLE_PLAY_ASO_ROOT 或外部虚拟环境。

## 安装与检查

以下示例针对 `.agents`；项目使用 `.agent` 时替换路径。可使用自己的 Python 环境，在线依赖由 Skill 声明，不复制其他项目的虚拟环境。

```powershell
python -m pip install -r .agents/skills/google-play-aso/requirements.txt
python -X utf8 -B .agents/skills/google-play-aso/scripts/run_connector.py inspect
python -X utf8 -B .agents/skills/google-play-aso/scripts/run_connector.py check
```

`inspect`、`validate-copy` 只需标准库；`check` 实际导入 Google SDK、不联网。返回的 onlineDependenciesReady 只说明依赖可以导入，remoteAuthorization 始终为 not_checked。缺在线依赖不妨碍源码分析。

## 所属项目与非秘密配置

默认所属项目由脚本实际路径决定，支持 `.agents/skills/…` 与 `.agent/skills/…`。可用 `--project` 明确覆盖。Skill 安装在个人全局目录时必须传目标项目；不从随机 cwd 猜测。

可选配置文件名为 `google-play-aso.json`，放在所属项目 Agent 目录下的 `config/` 中；未创建时使用 --app。这是可选的、经源码核实的映射，不是账号 App 清单：

```json
{
  "apps": {
    "产品发行版": {"packageName": "com.example.product", "defaultLocale": "en-US"}
  },
  "reportsBucket": "pubsite_prod_rev_123456789"
}
```

示例必须替换为目标项目的真实值；没配置报表 bucket 就省略该字段，不填示例值。没有配置时 `--app` 传入已核实的包名即可。配置中的多个 App 必须都属于这个项目；显式 --app 优先，不读取账号中其他 App。不要把 com.wkq.dev 当作其他项目默认值。

凭据只通过运行环境的 `GOOGLE_APPLICATION_CREDENTIALS` 指向本机已有授权文件；不在 Skill、配置或报告内保存密钥及路径，不借用原工具目录的 credentials。SDK 内部读取认证文件，agent 不读取其内容。权限不足不自动扩大权限。

## 运行与输出

```powershell
python -X utf8 -B .agents/skills/google-play-aso/scripts/run_connector.py analyze --app com.example.product --locale en-US --days 30 --dry-run
python -X utf8 -B .agents/skills/google-play-aso/scripts/run_connector.py analyze --app com.example.product --locale en-US --days 30
```

有项目配置时可省略 --app；重复 --app 选择同项目多个发行 App。--limit 默认 500；--timeout 默认每个 App 300 秒；--reports-bucket 可覆盖该项目配置。dry-run 展示实际包名、语言、周期及样本上限，不启动取证。

每轮输出到所属项目 `.google-play-aso/runs/<时间-随机标识>/`：
- project.json：本轮源码索引、Git 提交及 dirty 状态。
- manifest.json：范围、逐 App 成功/部分失败/超时状态、实际证据及数据基础稿路径。
- `<包名>/<locale>/request.json`、evidence.json、数据分析.md：独立请求和证据。

每个 App 完成后立即更新 manifest；失败或超时不自动重试，不影响其他 App。证据会验证 packageName、locale、analysisDays、runId 及时间字段。最终 ASO优化方案.md 由 agent 结合源码撰写，不能把数据引擎通用建议当作完整交付。数据基础稿也需补充项目事实、源码依据并审查脱敏。

输出根目录自动写入 `.gitignore`，不会默认提交原始评价。既有被强制跟踪文件不能靠忽略规则取消跟踪；不要把认证文件放入输出目录。

## 文案与本地验证

文案 JSON 必须完整包含 title、shortDescription、fullDescription 三个非空字符串，多行和特殊字符原样保留，不经 shell 拼接。

```powershell
python -X utf8 -B .agents/skills/google-play-aso/scripts/run_connector.py validate-copy --copy-json <本次文案JSON路径>
python -X utf8 -B -m unittest discover -s .agents/skills/google-play-aso/scripts -p "test_*.py" -v
```

文案校验 0 为通过，1 为超长，2 为输入错误；在线分析 1 为部分失败。错误说明不得包含原始 Google 响应或密钥。测试使用临时目录和假数据，不验证真实账号授权。
