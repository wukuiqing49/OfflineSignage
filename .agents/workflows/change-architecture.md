# 调整 Android 项目架构

## 加载

规则：

- `.agents/rules/execution.md`
- `.agents/rules/android.md`
- `.agents/rules/architecture.md`
- `.agents/rules/build.md`

Skills：

- `$android-project-architecture-workflow`
- `$android-build-workflow`

## 流程

1. 读取项目档案、settings、版本目录、相关模块构建文件和相邻代码。
2. 判断目标能力是否已有实现或可由 WKQ/现有共享层复用。
3. 明确功能专属逻辑、共享逻辑和平台配置的归属。
4. 设计最小模块或依赖变更，不改变无关外部接口。
5. 实施后运行架构门禁、依赖解析和受影响模块编译。
6. 汇报模块归属、依赖方向、复用选择和验证结果。
