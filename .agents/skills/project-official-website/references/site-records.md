# 事实记录与文件归属

审计目录位于部署根外，不在公开清单中。记录版本 schemaVersion 为 1，路径用相对路径；不填样例产品资料。

## project-facts.json

- source：revision（Git commit 或 null）、dirty（布尔或 null）、analyzedAt（ISO 时间）、selectedProduct、variant（不适用为 null）。commit 不代表包含未提交源码，dirty 时记录关键证据文件 SHA-256。
- product：name、type、version（未知为 null）、sourceLocale、targetLocales。
- audience：每项 text 与 basis（verified/inferred）。
- features：每项 id、name、summary、reachability（confirmed/unknown/disabled）、evidence（path、symbol 或行号、sha256）、inputs、outputs、limitations。只宣传 confirmed 项；源码可达不等于运行验收通过。
- assets：每项 sourcePath、publicPath、purpose、sha256、reviewStatus；必须实际查看图片。
- links：每项 purpose、url、evidence；没有确认的链接不生成。
- unknowns、claimsToAvoid：字符串列表。

## generation-manifest.json

记录 schemaVersion、generatedAt、sourceRevision、publicRoot（相对工作区）、files。files 每项为 path（相对公开目录）、sha256（上次生成后文件哈希）、owner（generated/manual）、featureIds。

生成前读取旧清单并比较磁盘哈希；只自动更新由生成器管理且未被人工修改的文件。已修改文件先做定向合并，不能判断时保留并报告冲突。首次遇到未知文件不得直接据为 generated 或覆盖。仅更新本次任务涉及文件。

废弃页面先检查入站链接与人工内容，确认是未修改的旧生成文件后才清理，并修复导航；不能依清单批量删除未知文件。写入清单前完成验证，失败时保留旧记录与具体错误。

业务例外记录在审计报告：规则编号、相对路径、原因、替代验证、复查条件。例外不改变真实检查结果，不能把失败改写为通过。
