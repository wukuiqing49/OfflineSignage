#!/usr/bin/env python3
"""提取首帧、真实尾帧或指定时间帧，成功后原子替换目标。"""
import argparse
import importlib.util
import math
import os
from pathlib import Path
import subprocess
import tempfile


def media_module():
    """复用同目录媒体工具发现和校验实现。"""
    spec = importlib.util.spec_from_file_location('assemble_shots', Path(__file__).with_name('assemble-shots.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def extract_frame(video_path, output_image_path, position='last', timestamp=None):
    """顺序解码尾帧，拒绝非法时间戳与旧文件假成功。"""
    media = media_module()
    source, output = Path(video_path).resolve(), Path(output_image_path).resolve()
    if source == output or output.suffix.lower() not in ('.png', '.jpg', '.jpeg'):
        raise ValueError('输出须为独立 PNG/JPEG 文件')
    _, duration, _ = media.video_info(source)
    if position not in ('first', 'last', 'timestamp'):
        raise ValueError('未知提帧位置')
    if position == 'timestamp':
        if timestamp is None or not math.isfinite(timestamp) or not 0 <= timestamp < duration:
            raise ValueError('timestamp 模式须提供有效范围内的 --timestamp')
    elif timestamp is not None:
        raise ValueError('--timestamp 仅可用于 timestamp 模式')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='frame-', dir=output.parent) as folder:
        candidate = Path(folder) / ('frame' + output.suffix)
        command = [media.executable('ffmpeg'), '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-xerror', '-i', str(source)]
        if position == 'timestamp':
            command += ['-ss', str(timestamp)]
        command += ['-map', '0:v:0', '-an']
        if position == 'last':
            # 逐帧覆盖临时图片，支持低帧率和可变帧率，不依赖末尾 0.1 秒内有帧。
            command += ['-fps_mode', 'passthrough', '-update', '1']
        else:
            command += ['-frames:v', '1']
        media.run(command + [str(candidate)])
        if not candidate.exists() or candidate.stat().st_size == 0:
            raise ValueError('请求位置无可解码帧')
        os.replace(candidate, output)
    return output


def main():
    """解析提帧参数并返回明确失败状态。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('-i', '--input', required=True)
    parser.add_argument('-o', '--output', required=True)
    parser.add_argument('-p', '--position', choices=['first', 'last', 'timestamp'], default='last')
    parser.add_argument('-t', '--timestamp', type=float)
    args = parser.parse_args()
    try:
        print(extract_frame(args.input, args.output, args.position, args.timestamp))
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'失败: {error}\n{getattr(error, "stderr", "") or ""}\n')


if __name__ == '__main__':
    main()
