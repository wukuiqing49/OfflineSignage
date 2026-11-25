"""用合成媒体验证画幅、音轨、完整性和失败保护；不调用生成服务。"""
import importlib.util
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest


def load(name, filename):
    """加载同目录带连字符脚本。"""
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


media = load('media', 'assemble-shots.py')
frames = load('frames', 'extract-frame.py')


class ManifestTests(unittest.TestCase):
    """验证未审核或缺失镜头不能被静默交付。"""

    def test_manifest_completeness(self):
        """有效相对路径可解析，缺路径、重复及审核未通过都拒绝。"""
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'manifest.json'
            shot = {'shot_id': 'one', 'status': 'approved', 'render_output': {'review_status': 'approved', 'file_path': 'one.mp4'}}
            path.write_text(json.dumps({'shots': [shot]}))
            self.assertEqual(media.read_manifest(path)[1], [Path(folder).resolve() / 'one.mp4'])
            for bad in [[], [shot, shot], [dict(shot, status='planned')],
                        [dict(shot, render_output={'review_status': 'approved'})],
                        [dict(shot, render_output={'review_status': 'pending', 'file_path': 'one.mp4'})]]:
                path.write_text(json.dumps({'shots': bad}))
                with self.assertRaises(ValueError):
                    media.read_manifest(path)


class MediaTests(unittest.TestCase):
    """真实调用本机 FFmpeg/ffprobe 检验输出媒体行为。"""

    @classmethod
    def setUpClass(cls):
        """在临时目录准备含单引号路径、混合尺寸帧率与短音乐的素材。"""
        try:
            cls.ffmpeg = media.executable('ffmpeg')
            media.executable('ffprobe')
        except ValueError as error:
            raise unittest.SkipTest(str(error))
        cls.temp = tempfile.TemporaryDirectory(prefix="video-test-'路径-")
        cls.root = Path(cls.temp.name)
        cls.a, cls.b, cls.music = [cls.root / name for name in ['a.mp4', 'b.mp4', 'music.wav']]
        cls.prefix = [cls.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y']
        media.run(cls.prefix + ['-f', 'lavfi', '-i', 'color=red:s=160x90:r=24:d=1', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest', str(cls.a)])
        media.run(cls.prefix + ['-f', 'lavfi', '-i', 'color=blue:s=90x160:r=15:d=1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', str(cls.b)])
        media.run(cls.prefix + ['-f', 'lavfi', '-i', 'sine=frequency=880:duration=0.25', str(cls.music)])

    @classmethod
    def tearDownClass(cls):
        """清理测试媒体。"""
        cls.temp.cleanup()

    def output(self, name):
        """生成当前测试专用输出路径。"""
        return self.root / (name + '.mp4')

    def tone(self, path, frequency):
        """测量指定频率的相关能量，确认混音是否保留原声。"""
        raw = subprocess.run(self.prefix + ['-i', str(path), '-ss', '0.3', '-t', '0.4', '-vn', '-ac', '1', '-ar', '8000', '-f', 's16le', '-'], capture_output=True, check=True).stdout
        samples = struct.unpack('<' + 'h' * (len(raw)//2), raw)
        real = sum(v * math.cos(2*math.pi*frequency*i/8000) for i,v in enumerate(samples))
        imag = sum(v * math.sin(2*math.pi*frequency*i/8000) for i,v in enumerate(samples))
        return math.hypot(real, imag)/len(samples)

    def test_portrait_mixed_inputs(self):
        """混合帧率、尺寸和有无音轨仍输出完整竖屏与统一媒体格式。"""
        result = media.assemble([self.a, self.b], self.output('portrait'), '90x160', 30, expected=2)
        stream, duration, audio = media.video_info(result)
        self.assertEqual((stream['width'], stream['height']), (90,160))
        self.assertEqual(stream['pix_fmt'], 'yuv420p')
        self.assertEqual(stream['r_frame_rate'], '30/1')
        self.assertAlmostEqual(duration, 2, delta=0.2)
        self.assertTrue(audio)
        self.assertGreater(self.tone(result, 440), 100)

    def test_short_bgm_mix_and_replace(self):
        """短音乐不会截断成片；混音保留原声，替换模式移除原声。"""
        mixed = media.assemble([self.a,self.b], self.output('mixed'), '160x90', 30, self.music)
        replaced = media.assemble([self.a,self.b], self.output('replaced'), '160x90', 30, self.music, 'replace')
        for result in (mixed,replaced):
            self.assertAlmostEqual(media.video_info(result)[1], 2, delta=0.2)
            self.assertGreater(self.tone(result,880), 10)
        self.assertGreater(self.tone(mixed,440), 100)
        self.assertLess(self.tone(replaced,440), self.tone(mixed,440)/10)

    def test_failure_preserves_existing_output(self):
        """缺音乐、坏源片和目标时长错误不覆盖已有文件。"""
        output = self.output('protected')
        output.write_bytes(b'original')
        bad = self.output('broken')
        bad.write_bytes(b'broken')
        for sources, music, target in [([self.a],self.root/'missing.wav',None),([bad],None,None),([self.a],None,9)]:
            with self.assertRaises((ValueError,subprocess.CalledProcessError)):
                media.assemble(sources,output,'160x90',30,music,expected=target)
            self.assertEqual(output.read_bytes(),b'original')

    def test_invalid_parameters(self):
        """非法尺寸、帧率和覆盖输入在执行前拒绝。"""
        for resolution, fps in [('91x160',30),('90x160',0),('90x160',float('nan'))]:
            with self.assertRaises(ValueError):
                media.assemble([self.a],self.output('invalid'),resolution,fps)
        with self.assertRaises(ValueError):
            media.assemble([self.a],self.a,'160x90',30)

    def test_frame_positions_and_stale_file(self):
        """低帧率尾帧准确，参数错误或无帧不能把旧图片当成功。"""
        source = self.output('lowfps')
        media.run(self.prefix + ['-f','lavfi','-i','color=red:s=32x32:r=1:d=1','-f','lavfi','-i','color=blue:s=32x32:r=1:d=1','-filter_complex','[0:v][1:v]concat=n=2:v=1:a=0[v]','-map','[v]','-r','1','-c:v','libx264',str(source)])
        first,last = self.root/'first.png',self.root/'last.png'
        frames.extract_frame(source,first,'first')
        frames.extract_frame(source,last,'last')
        for path, channel in [(first,0),(last,2)]:
            raw = subprocess.run(self.prefix+['-i',str(path),'-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-'],capture_output=True,check=True).stdout
            self.assertGreater(raw[channel],200)
        original=last.read_bytes()
        for timestamp in (None,-1,1.9,9,float('nan')):
            with self.assertRaises(ValueError):
                frames.extract_frame(source,last,'timestamp',timestamp)
            self.assertEqual(last.read_bytes(),original)
        frames.extract_frame(self.a,self.root/'time.png','timestamp',0.5)


if __name__ == '__main__':
    unittest.main()
