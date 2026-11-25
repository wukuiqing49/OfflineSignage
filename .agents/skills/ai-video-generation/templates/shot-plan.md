# 分镜模板

记录产品/主题、目标总时长、尺寸/画幅、帧率和素材依据。镜头数按总时长与后端能力确定；4～8 秒是建议而非硬限制。各镜头时长之和须匹配交付目标。

每个镜头填写：
- shot_id、目标时长、单一主要动作、景别与运镜。
- 类型：AI 场景 / 真实录屏 / 确定性合成；来源、授权与产品事实依据。
- 提示词：主体、动作、运镜、光影和质感；负向词仅用于支持该参数的模型。
- 参考素材：相对 manifest 的路径；角色/产品参考能力以当前后端为准。
- 衔接：连续动作时填写 continuation_from；换场、换景别、CTA 可独立剪辑。
- 后端/模型版本、尺寸与时长能力、费用单位、报价来源、允许尝试次数。
- 验收：文字/功能真实性、动作、连贯性、清晰度，以及实际回读证据。

功能演示用真实录屏；Logo、标语与商店徽章后期合成。不要让视频模型绘制可读 UI 并将其当作功能证据。

manifest 的 attempts 每次追加记录：attempt_no、task_id、candidate_id、submitted_at、provider、model_version、request_fingerprint、quoted_cost、actual_cost、state、file_path、review_result。下载重试保留 task_id；重新生成才增加尝试次数。selected_candidate_id 指向最终通过的候选，render_output 保存所选候选的相对路径、时长和审查证据。
