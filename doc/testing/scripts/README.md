# 独立虚拟机混播回归

`run_virtual_mixed_media.py` 覆盖本地 H.264 视频、PNG、带动态计数器的 HTML、断网混播、系统重启后的恢复以及有界持续播放。

## 前置条件

- Python 3 + Pillow、adb；已安装本工作区 Debug APK。
- 使用专用 AVD `localsignage_ui_20261021`。脚本同时检查 emulator 序列号和 AVD 名称，拒绝物理设备。
- 应用没有已有素材；停留在欢迎或连接页。脚本不主动清空应用数据。真实计费交易不在范围内。
- 输入一段本地 H.264 测试视频，例如用 FFmpeg 生成12秒测试图：

```powershell
ffmpeg -y -f lavfi -i testsrc2=size=640x360:rate=24 -t 12 -c:v libx264 -pix_fmt yuv420p motion.mp4
python -X utf8 doc/testing/scripts/run_virtual_mixed_media.py --device emulator-5564 --video motion.mp4 --seconds 300
```

`--seconds` 只表示重启恢复后的持续混播观察时长，不包含前面的上传、断网与重启。默认300秒。当前 Web Token 有8小时有效期，脚本不实现凭据续期；不能直接把参数设为86400并当作完整24小时方案。

## 断言与证据

- 视频位置推进，间隔截图实际像素变化；HTML正文出现在控件树且计数器截图变化。
- 关闭虚拟机 Wi-Fi/移动数据后，真实 HTTP 状态仍覆盖三种素材并无播放错误。
- 执行真实 `adb reboot`。Android36要求记录开机延迟恢复日志；显式打开应用后列表恢复。
- 持续观察播放状态、错误字段、素材切换次数及进程 PID；保存前后 dumpsys meminfo。两点内存快照不能证明没有泄漏。
- 结果、截图、控件树、内存与环境事件保存在 `build/functional-test/mixed-media/`；Token仅存进程内存，不输出。分享时只选不含连接凭据的证据。
- 清理仅本次新增内容，恢复原设置/列表并启用测试 AVD 网络。服务不可用导致清理失败会返回失败，应检查报告后处理专用 AVD。
- 如虚拟机弹出英文 System UI ANR，只关闭明确识别的系统弹窗，并记录 environment-events.log；不自动关闭应用自身异常。

这是虚拟机回归，不代表实体电视硬解、商业发布或24小时稳定性验收。
