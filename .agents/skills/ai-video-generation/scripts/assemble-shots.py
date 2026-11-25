#!/usr/bin/env python3
"""校验分镜并逐段规范化，原子交付带原声或背景音乐的 MP4。"""
import argparse
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def executable(name):
    """优先使用环境覆盖及系统工具，FFmpeg 可回退到 imageio。"""
    value = os.environ.get(name.upper() + '_EXE') or shutil.which(name)
    if value:
        return value
    if name == 'ffmpeg':
        try:
            import imageio_ffmpeg
            return imageio_ffmpeg.get_ffmpeg_exe()
        except ImportError:
            pass
    raise ValueError(f'缺少 {name}，请安装或设置 {name.upper()}_EXE')


def run(args):
    """执行媒体工具并保留可诊断错误。"""
    return subprocess.run(args, check=True, capture_output=True, text=True, encoding='utf-8', errors='replace')


def probe(path):
    """读取机器可解析的流信息，不以文件存在替代有效媒体。"""
    return json.loads(run([executable('ffprobe'), '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)]).stdout)


def video_info(path):
    """校验视频流及正数时长。"""
    data = probe(path)
    stream = next((s for s in data['streams'] if s['codec_type'] == 'video'), None)
    duration = float((stream or {}).get('duration') or data.get('format', {}).get('duration', 0))
    if not stream or not math.isfinite(duration) or duration <= 0:
        raise ValueError(f'无有效视频流或时长: {path}')
    return stream, duration, any(s['codec_type'] == 'audio' for s in data['streams'])


def read_manifest(path):
    """拒绝缺失、未审核、重复分镜，路径相对清单所在目录解析。"""
    path = Path(path).resolve()
    data = json.loads(path.read_text(encoding='utf-8-sig'))
    if not isinstance(data, dict) or not isinstance(data.get('shots'), list):
        raise ValueError('清单必须包含 shots 数组')
    shots, ids = [], set()
    for shot in data.get('shots', []):
        if not isinstance(shot, dict) or not isinstance(shot.get('render_output'), dict):
            raise ValueError('分镜及 render_output 必须为对象')
        identity = shot.get('shot_id')
        output = shot.get('render_output', {})
        if not isinstance(identity, str) or not identity or identity in ids:
            raise ValueError('shot_id 缺失或重复')
        ids.add(identity)
        if shot.get('status') != 'approved' or output.get('review_status') != 'approved':
            raise ValueError(f'{identity} 尚未审核通过')
        if not output.get('file_path'):
            raise ValueError(f'{identity} 缺少文件路径')
        shots.append((path.parent / output['file_path']).resolve())
    if not shots:
        raise ValueError('清单无分镜')
    return data, shots


def assemble(shots, output, resolution, fps, bgm=None, audio_mode='mix', bgm_volume=0.25, expected=None):
    """统一音视频参数后拼接，校验成功才原子替换输出文件。"""
    if audio_mode not in ('mix', 'replace'):
        raise ValueError('未知音频模式')
    width, height = map(int, resolution.lower().split('x'))
    if min(width, height) <= 0 or width % 2 or height % 2 or not math.isfinite(fps) or fps <= 0:
        raise ValueError('尺寸必须为正偶数，帧率必须为正数')
    if not math.isfinite(bgm_volume) or bgm_volume < 0:
        raise ValueError('BGM 音量必须为非负有限数')
    shots = [Path(p).resolve() for p in shots]
    output = Path(output).resolve()
    if output in shots or (bgm and output == Path(bgm).resolve()):
        raise ValueError('输出不能覆盖输入')
    if output.suffix.lower() != '.mp4':
        raise ValueError('输出须为 .mp4')
    info = [video_info(p) for p in shots]
    if not info:
        raise ValueError('无输入视频')
    if bgm and not any(s['codec_type'] == 'audio' for s in probe(bgm)['streams']):
        raise ValueError('BGM 无音轨')
    total = sum(item[1] for item in info)
    if expected is not None and (not math.isfinite(float(expected)) or abs(total - float(expected)) > 0.2):
        raise ValueError(f'分镜实际总时长 {total:.3f}s 与目标 {expected}s 不符')
    ffmpeg = executable('ffmpeg')
    prefix = [ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y']
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='video-', dir=output.parent) as folder:
        temp = Path(folder)
        for index, (source, (_, duration, has_audio)) in enumerate(zip(shots, info)):
            command = prefix + ['-i', str(source)]
            if not has_audio:
                command += ['-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo']
            command += ['-map', '0:v:0', '-map', '0:a:0' if has_audio else '1:a:0',
                        '-vf', f'scale={width}:{height}:force_original_aspect_ratio=decrease,pad={width}:{height}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps={fps},format=yuv420p',
                        '-af', 'aresample=48000,apad', '-t', str(duration),
                        '-c:v', 'libx264', '-preset', 'fast', '-crf', '19',
                        '-c:a', 'aac', '-ar', '48000', '-ac', '2', '-b:a', '192k',
                        '-video_track_timescale', '90000', str(temp / f'{index}.mp4')]
            run(command)
        # 相对数字文件名避免用户路径中的单引号破坏 concat 语法。
        listing = temp / 'concat.txt'
        listing.write_text(''.join(f"file '{i}.mp4'\n" for i in range(len(shots))), encoding='utf-8')
        joined = temp / 'joined.mp4'
        run(prefix + ['-f', 'concat', '-safe', '1', '-i', str(listing), '-c', 'copy', '-movflags', '+faststart', str(joined)])
        candidate = joined
        if bgm:
            candidate = temp / 'final.mp4'
            fade = min(1.5, total / 2)
            music = f'[1:a]volume={bgm_volume},atrim=duration={total},afade=t=in:d={min(0.5,total/2)},afade=t=out:st={total-fade}:d={fade}[music]'
            mixing = ';[0:a][music]amix=inputs=2:duration=first:normalize=0[a]' if audio_mode == 'mix' else ';[music]anull[a]'
            run(prefix + ['-i', str(joined), '-stream_loop', '-1', '-i', str(bgm),
                          '-filter_complex', music + mixing, '-map', '0:v:0', '-map', '[a]',
                          '-t', str(total), '-c:v', 'copy', '-c:a', 'aac', '-ar', '48000', '-ac', '2',
                          '-movflags', '+faststart', str(candidate)])
        stream, actual, _ = video_info(candidate)
        if (stream['width'], stream['height']) != (width, height) or abs(actual - total) > 0.2:
            raise ValueError(f'输出尺寸或时长校验失败: {actual:.3f}s / {total:.3f}s')
        run(prefix + ['-xerror', '-i', str(candidate), '-f', 'null', '-'])
        os.replace(candidate, output)
    return output


def main():
    """解析命令行；清单模式使用目标配置，直接输入模式需显式指定尺寸。"""
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('-m', '--manifest')
    source.add_argument('-i', '--inputs', nargs='+')
    parser.add_argument('-o', '--output', required=True)
    parser.add_argument('--resolution', help='如 1080x1920；覆盖清单尺寸')
    parser.add_argument('--fps', type=float)
    parser.add_argument('--reencode', action='store_true', help='兼容旧调用；现在始终逐段规范化')
    parser.add_argument('--bgm')
    parser.add_argument('--audio-mode', choices=['mix', 'replace'], default='mix')
    parser.add_argument('--bgm-volume', type=float, default=0.25)
    args = parser.parse_args()
    try:
        data, shots = read_manifest(args.manifest) if args.manifest else ({}, args.inputs)
        resolution = args.resolution or data.get('target_resolution')
        if not resolution:
            raise ValueError('请指定 --resolution 或清单 target_resolution')
        result = assemble(shots, args.output, resolution, args.fps if args.fps is not None else data.get('fps', 30),
                          args.bgm, args.audio_mode, args.bgm_volume, data.get('total_target_seconds'))
        print(f'已校验并交付: {result}')
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'失败: {error}\n{getattr(error, "stderr", "") or ""}\n')


if __name__ == '__main__':
    main()
