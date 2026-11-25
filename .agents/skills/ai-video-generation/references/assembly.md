# 后期拼接与验收

## 环境与路径

Python 3.10+、FFmpeg（支持 libx264/AAC）和 ffprobe。可用 `FFMPEG_EXE` / `FFPROBE_EXE` 指定本机程序；FFmpeg 也支持 imageio-ffmpeg 回退。环境缺失明确报错，不自动安装或假成功。

下列 `<skill目录>` 指已安装 Skill 的目录，视频项目路径按本轮工作目录解析；清单内路径一律相对清单所在目录。例如 `outputs/manifest.json` 的 `file_path` 应为 `shot-01.mp4`，而非 `outputs/shot-01.mp4`。

```text
python <skill目录>/scripts/extract-frame.py -i outputs/shot-01.mp4 -o outputs/shot-01-last.png -p last
python <skill目录>/scripts/assemble-shots.py -m outputs/manifest.json -o outputs/final.mp4
python <skill目录>/scripts/assemble-shots.py -i outputs/a.mp4 outputs/b.mp4 -o outputs/final.mp4 --resolution 1080x1920 --fps 30 --bgm assets/music.mp3 --audio-mode mix --bgm-volume 0.25
```

提尾帧顺序解码到末尾，对长片成本较高，但避免短片/低帧率下末尾固定时间窗口取不到帧。指定时间须使用 `-p timestamp -t 秒数`，缺少或越界会失败；失败不覆盖旧图片。

## 输入与合成契约

- 清单数组顺序即播放顺序，所有分镜必须有唯一 ID、文件路径、`status=approved` 和 `render_output.review_status=approved`。审核是执行者真实回读后的决定，脚本只检查状态，不代做视觉审核。
- `target_resolution`、`fps` 和 `total_target_seconds` 为交付约束。`--resolution` / `--fps` 可显式覆盖尺寸/帧率，改画幅前同步用户要求；源片实际总时长与目标误差大于 0.2 秒时失败，不静默剪掉内容。
- 始终逐片段规范化，再 concat；不直接将混合格式输入交给 concat demuxer。兼容旧 `--reencode` 参数，现已无须该开关。输出 H.264、yuv420p、方形像素、指定 CFR、AAC 48kHz 立体声，缺音轨片段补静音。
- 默认保持原声。提供 BGM 时默认混音；`--audio-mode replace` 明确替换原声。短音乐循环、长音乐裁至视频长度，并淡入淡出。混音后需要听音检查削波和对白可辨识度。
- 不承诺自动 HDR 转 SDR、统一 BT.709、压字幕、转场或自动响度归一；这些需要额外按素材实现并验证。本脚本使用硬切，尾帧匹配也不能保证无缝或动作自然。
- 使用独立临时目录，不修改源片；校验候选尺寸、时长及完整解码后原子替换最终文件。输出路径不能等于输入路径。

## 最终验收

脚本时长误差要求 ≤0.2 秒，过多片段的帧量化/编码延迟超限时应检查时间线与目标帧率，不能放宽阈值掩盖问题。人工回读首尾、各剪辑接点与完整成片，核对黑帧/绿帧、文字和产品真实性、音量、字幕与音画同步。结构成功不等于视觉或版权验收通过。
