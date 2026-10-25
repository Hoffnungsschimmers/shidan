# 熟店快记与体验优化状态（2026-10-22）

## 目标与边界
- 用户批准：主功能熟店快记，金额/日期/返回状态/失败反馈/搜索解释优化。
- 不新增必填字段、权限、依赖或schema；不改版本、包名、三栏结构；保留已有未提交修改，不commit/push，不动apk/。
- 不做想去理由新字段、定位/智能推荐、自动同步或删除撤销。

## 设计决策
- 熟店沿用详情→用餐表单入口；复用餐品，而非整条旧记录。上次评价、金额/人数、照片、日期和备注不自动复制为本次事实。
- 最近餐品可点选；历史花费仅显示参考，需要用户明确点选才带入并按正常金额/人数变换。
- 金额识别结果必须可见，不躲在折叠区；照片和备注按需展开，不增确认弹窗。
- 编辑已落库金额（包括NULL）保留原值；“恢复自动”是显式动作。
- DatePicker以UTC自然日表达选择，业务日期按当前ZoneId转换；不整体迁移旧数据。
- 主标签本地UI状态按稳定MainTab key保存，不冻结Room数据；餐品搜索结果给可读命中依据。
- 失败在保存按钮上方常驻错误色+关闭+无障碍播报，部分导入显示成功/失败/容量截断。

## 文件边界与进度
- 主线程：AddVisit表单/VM/预填、EditVisitVM金额、FormErrorBanner/金额摘要、详情成功反馈。
- 独立范围A：MainScaffold/清单搜索及RestaurantRow匹配摘要。
- 独立范围B：MealDates/日期测试/EditVisitScreen/重复币符调用方。
- 代码与回归用例已接入：快记/日期/标签状态/搜索/金额/错误反馈/保存确认，新增用餐图片 CAS 接缝与加载金额接缝。
- 2026-10-23 最终验证：35 类/354 例，原生 testDebugUnitTest 与脚本（最终 Gradle 已重新编译，再 -NoBuild 直跑最新产物）均通过；失败/错误/跳过均为0。相比 F01/F02 基线299例净增55例。
- 末轮人工复核发现 DAO 查询期间表单选择可变化，已补删除前实时保护谓词与 2 例回归；原一参调用保持接口，备份守卫不变。前述阶段352例已由最终354例取代。
- 最终 testDebugUnitTest + assembleDebug + assembleRelease + assembleDebugAndroidTest：BUILD SUCCESSFUL，143任务/36执行；Release Kotlin/R8/打包实际执行。独立 lintDebug：BUILD SUCCESSFUL，报告 No issues found。
- 测试APK中1–4历史schema均唯一且SHA-256与源码一致；Release签名v2通过、1个签名者。未比较历史交付证书，不据此声明覆盖安装实测成功。git diff --check通过，schema/生产Migration/依赖/apk目录无diff，应用版本仍17/0.6.1。
- adb devices -l 当前为空：未运行任何设备流程。人工重读 Add/EditVisit 所有照片导入、相机、移除、回收与 onCleared 分支；原图/提交快照保护已核对，通用 PhotoStore 取消后孤儿回收仍不能当作完整端到端验证。
- 两个独立范围已返回并核对实际接线。搜索代理单独验证 32 例不替代全量集成测试。
- 保存确认通过详情返回栈的 SavedStateHandle 传新记录 id，关闭/编辑时清除，不修改路由编码或数据库。
- 交付APK：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`；未复制到 `apk/`，未发版。证据见 `docs/QUICK_RECORD_EVIDENCE_2026-10-22.json`。
- 工作区main，origin沿用 `https://github.com/Hoffnungsschimmers/shidan.git`；未commit/push。构建保留已存在的Sort图标废弃提示及overridePathCheck实验提示，不当作Lint失败。

## 验收与限制
- 直接测试新增生产纯逻辑、旧金额NULL保留、多人人均换算、迟到预填拒绝覆盖、餐品复用、日期跨时区/DST、搜索命中。
- 主线程统一串行全量JVM、Lint、Debug/Release和androidTest编译；不允许多线程Gradle并发。
- 本机无设备：20秒目标、大字体、TalkBack、键盘/返回/可视结果与真实SAF/WebDAV未实际验证；不能称发布前提全部达标。
- 备份限额与完整往返仍为发布阻塞，不把本轮体验改动冒充F03/F04/F05全部修复。
