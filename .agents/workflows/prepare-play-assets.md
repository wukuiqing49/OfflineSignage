# 准备 Google Play 商店素材

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/play-assets.md`

提示模板（按素材类型只读取对应文件）：

- `doc/google-play-icon-prompt.md`
- `doc/google-play-screenshot-prompt.md`
- `doc/google-play-feature-graphic-prompt.md`
- `doc/google-play-video-prompt.md`

Skills：

- 实际生成或编辑位图时加载 `$imagegen`

## 流程

1. 检查应用定位、目标用户、品牌信息和 `.ai-work/play-assets/input/` 真实素材。
2. 区分官方图标、设计参考、真实 UI、录屏和缺失输入。
3. 按图标、截图、Feature Graphic 或视频脚本选择一份 `doc/` 提示模板。
4. 在保真和商店合规规则下生成或编辑素材。
5. 对生成位图运行 `.agents/scripts/validate_play_assets.py`，再检查文字、形变、遮挡和营销声明。
6. 将结果写入 `.ai-work/play-assets/output/`，记录输入、变量和未验证项。
