# 食单

食单（曾用名「味笺」「饭记」）是一款本地优先的 Android 餐厅收藏与用餐记录应用。它可以快速保存想去的店，并用「推荐 / 尚可 / 不推荐」、餐品名字、**花费**、照片和可选附录记录每次用餐。

## 当前状态

最新版本 **v0.6.1（versionCode 17）**：修掉真机首轮反馈的两处 bug（清单吸顶条滚动发灰、
足迹切到「统计」后回不去），底色按你的偏好改回**纯白 + 暖白卡片**，「我的」拆成 6 个二级分区页。
上一版 v0.6.0（16）改名「食单」，含账本 CSV 导出、每月预算、清单排序、备份新鲜度、
WebDAV 连通性检查、重复店名提醒、年度回顾卡片、单店累计入账与暖食欲视觉全域重做。

> ⚠️ v0.6.1 **同样只过了编译与 JVM 测试**——它修的正是「只有真机才看得见」的问题，
> 而修复本身还得你再看一眼：纯白底与暖白卡片的层次感、6 个二级页的返回手感、吸顶条在滚动中的表现。
> WebDAV 同步与连通性检查仍未在真实服务器上验证通过（AGENTS §5-3 的发布门槛未解锁）。
> 交付包：`apk/食单-v0.6.1-release.apk`（证书与 v0.5.1 / v0.6.0 release 逐字一致，覆盖安装保数据）
> 与 `apk/食单-v0.6.1-debug.apk`（与历次 debug 包同 debug 证书）。**debug 与 release 之间跨类型安装会被签名拒绝。**
> 逐项明细见 `CHANGELOG.md` 0.6.1 节。

**信息架构**（底部三栏）

| 标签 | 回答的问题 |
|---|---|
| **清单** | 还有什么想吃的 / 哪些店去过了（搜索**含餐品名**、分段筛选、随机选一家按评价范围） |
| **足迹** | 我吃过什么、评价如何、花了多少（时间线 ⇄ 统计两个视角，统计内含账本） |
| **我的** | 备份导出与恢复、WebDAV 同步、外观与流畅模式、随机选店范围、运行日志、存储管理、关于 |

「足迹」内含两个分段：

- **时间线**：按月份分组、组内按时间倒序；
- **统计**：今年次数 / 去过的店 / 累计次数、花费估算、**入账金额口径的账本卡**、
  最近 12 个月柱状图、评价分布、去得最多的店 Top 5。

> 花费是自由文本，统计值由 `String.parseEstimatedAmount()` 从文本中取**最大**的数字估算
> （`人均60` → 60、`3个人吃了240` → 240）。界面明确标注为「估算」并显示可识别比例，
> 一条都识别不出来时显示「还没有能识别的金额」而不是 `¥0`。

新增记录统一从悬浮按钮进入，弹出面板二选一：「想吃，还没去」→ 新建店铺；「已经吃过了」→ 选店 → 用餐表单。

**记录与查找**

- 新增、搜索和筛选餐厅；
- 区分“待探访”和“已用餐”；
- 查看餐厅及历史到访；
- 添加、编辑、删除用餐评价、餐品、**花费**和照片；
- 编辑餐厅资料（店名、地址、封面）与删除餐厅。

**数据安全**

- 导出全部数据为版本化 ZIP 备份包，可从备份文件完整恢复；
- 设置页提供照片存储用量统计、无用文件清理与清空数据；
- 所有数据默认只保存在本机，不需要任何存储权限；
- 可选的 WebDAV 服务器同步需 `INTERNET` 权限：仅在用户主动配置并点击上传/下载时
  与用户自己的服务器通信，其余功能不受影响。

**界面**

- **纯白底 · 暖白浮层 · 图为主**视觉（继承 MIUIx / HyperOS 的浮层与弹簧语言）：纯白页面底、暖白卡片 + 1px 描边 + 柔和暖棕投影、大圆角、弹簧动效；封面图承担卡片锚点，缺图退化为「暖光渐变 + 店名首字」；
- 底部导航栏、详情页顶栏与操作栏使用 **Liquid Glass** 玻璃材质；
  API 31+ 为真实背景模糊（`GraphicsLayer` + `RenderEffect`），低版本自动降级为拟态玻璃；
- 全屏照片查看器：左右滑动切换、双击缩放、放大后拖动平移；
- 分享卡片：把一次用餐渲染成竖版图片交给系统分享面板（先预览再分享）；
- 深色模式可选「跟随系统 / 浅色 / 深色」。

完整设计规范见 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)，**下一步规划见 [ROADMAP.md](ROADMAP.md)**（历史项目书见 [PROJECT_PLAN.md](PROJECT_PLAN.md)），版本变更见 [CHANGELOG.md](CHANGELOG.md)。

## 技术栈

- Kotlin / Java 17
- Jetpack Compose + Material 3
- Navigation Compose
- Room
- Hilt
- StateFlow + ViewModel
- Coil

最低支持 Android 8.0（API 26），当前 compileSdk/targetSdk 为 36。

## 开发环境

1. 安装 JDK 17 和 Android SDK 36。
2. 使用 Android Studio 打开项目根目录。
3. 确认 `local.properties` 中的 `sdk.dir` 指向本机 Android SDK。
4. 执行以下命令验证项目：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 中文路径与 JDK 定位

项目目录包含中文时，部分 Gradle 任务会出现乱码或 `ClassNotFoundException`。已建立英文目录联接
（非副本）：

```text
C:\Users\2540\mealnote-workspace  ->  C:\Users\2540\Desktop\mealnote
```

建议所有 Gradle 命令从英文入口执行，并显式传入 JDK 17 路径：

```powershell
.\gradlew.bat -p C:\Users\2540\mealnote-workspace `
  testDebugUnitTest lintDebug assembleDebug `
  -Porg.gradle.java.installations.paths="D:\env\jdk-17.0.16+8"
```

若机器上只安装了 JDK 21，Gradle 会因找不到 17 工具链而报
`Cannot find a Java installation ... languageVersion=17`，上述参数即为解决方式。

若出现 `build-cache-1\*.part (拒绝访问)` 之类的缓存写入失败，可追加 `--no-build-cache` 绕过。

### 已知环境问题：单元测试类路径（2026-10-06 复测未复现）

历史上本机执行 `testDebugUnitTest` 时，所有测试类都会在装载阶段报
`java.lang.ClassNotFoundException ... at initializationError`（注意是**全部**测试类，
而非某一个断言失败）。原因：AGP 的 `bundleDebugClassesToRuntimeJar` 产物
（`app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar`）
未被加入测试工作进程的运行时类路径，测试代码因此看不到主源码集中的类——这与被测代码无关。

**2026-10-06 用 `--rerun` 强制实跑，28 套件 / 252 例全部通过，问题未再出现。**
日常跑测试用 `scripts/run-unit-tests.ps1`（自动发现测试类，无需维护清单）；
下面的 JDK 直跑命令保留为**备用路径**，仅在换机或该问题复发时使用。

在该问题修复前，可改用 JDK 直接运行同一批测试（结果等价）。**首选跑法是
`scripts/run-unit-tests.ps1`**：它从 `app/src/test/java` 自动发现测试类，不需要维护下面的
类清单，也就不可能漂移。下面的手工命令是备用路径，用它才必须同步清单。

```bash
"$JDK17/bin/java.exe" \
  -cp "app/build/intermediates/classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs;\
app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar;\
<junit-4.13.2.jar>;<kotlin-stdlib.jar>;<hamcrest-core-1.3.jar>" \
  org.junit.runner.JUnitCore \
  com.fanji.mealnote.data.AmountParsingTest \
  com.fanji.mealnote.data.MealResultTest \
  com.fanji.mealnote.data.MealTextTest \
  com.fanji.mealnote.data.local.ConvertersTest \
  com.fanji.mealnote.data.backup.BackupEntryNameSafetyTest \
  com.fanji.mealnote.data.export.CsvExportSafetyTest \
  com.fanji.mealnote.data.settings.BackupFreshnessTest \
  com.fanji.mealnote.data.settings.BudgetPreferenceParseTest \
  com.fanji.mealnote.data.webdav.WebDavProbeTest \
  com.fanji.mealnote.data.webdav.WebDavUrlSafetyTest \
  com.fanji.mealnote.ui.FormattersTest \
  com.fanji.mealnote.ui.add.DuplicateNameCheckTest \
  com.fanji.mealnote.ui.detail.DetailStatsTest \
  com.fanji.mealnote.ui.edit.ReclaimableFormPhotosTest \
  com.fanji.mealnote.ui.visit.VisitPrefillTest \
  com.fanji.mealnote.ui.home.HomeFilterTest \
  com.fanji.mealnote.ui.home.FootprintAggregationTest \
  com.fanji.mealnote.ui.home.FootprintSearchAndMonthTest \
  com.fanji.mealnote.ui.home.CountInMonthTest \
  com.fanji.mealnote.ui.home.LedgerAggregationTest \
  com.fanji.mealnote.ui.home.LedgerSumTest \
  com.fanji.mealnote.ui.home.VisitCountsTest \
  com.fanji.mealnote.ui.home.VerdictFilterTest \
  com.fanji.mealnote.ui.home.BudgetProgressTest \
  com.fanji.mealnote.ui.home.YearlySummaryTest \
  com.fanji.mealnote.ui.home.YearReviewTest \
  com.fanji.mealnote.ui.home.RandomScopeFilterTest \
  com.fanji.mealnote.ui.home.RestaurantSortTest
```

依赖 jar 位于 `~/.gradle/caches/modules-2/files-2.1/` 下。

> 上面这份类清单**只服务于备用路径**，日常不需要维护：`scripts/run-unit-tests.ps1` 从源码
> 自动发现测试类。若确实改走了手工命令，才需要同步这里的清单与计数
> （`grep -rc "@Test" app/src/test` 可数；本项目历史上因手工清单漂移失守过三次，故改为脚本）。

## 构建状态

以下为 **2026-10-07（v0.6.1）**对当前代码的实测结果：

| 检查项 | 状态 |
|---|---|
| `testDebugUnitTest`（`--rerun` 强制实跑） | 28 个套件 / 252 个用例全部通过，0 失败 0 错误 |
| `scripts/run-unit-tests.ps1` | 同上（从源码发现 28 个测试类，`OK (252 tests)`） |
| `lintDebug` | 通过，报告 `No issues found` |
| `assembleDebug` | 通过（versionCode 17，versionName 0.6.1，label 食单）；已交付 `C:\Users\2540\Desktop\mealnote\apk\食单-v0.6.1-debug.apk` |
| `assembleRelease` | 通过（R8 混淆 + 资源裁剪），`apksigner verify` 退出码 0；已交付 `C:\Users\2540\Desktop\mealnote\apk\食单-v0.6.1-release.apk`，证书与 v0.5.1/v0.6.0 release 逐字一致，可覆盖安装保数据 |

> 构建能过 ≠ 功能可用：本机无真机/模拟器，**UI 与 WebDAV 远程路径均未经验证**（见 ROADMAP 第七/九节）。

数据库版本 4，`identityHash` = `b93fd210509db5642fb4c1aed52f6a14`。

### 数据库迁移

| 版本 | 变更 |
|---|---|
| 1 → 2 | `restaurants` 增加 `recommendationPhotoPath`（封面图） |
| 2 → 3 | `dining_records` 增加 `priceText`（花费，自由文本） |
| 3 → 4 | `dining_records` 增加 `amountMinorUnits`（入账金额，分）与 `personCount`（人数） |

`Migration2To3` 使用 `ALTER TABLE ... ADD COLUMN priceText TEXT NOT NULL DEFAULT ''`。
实体上的 `@ColumnInfo(defaultValue = "''")` 与迁移中的 `DEFAULT ''` **必须逐字一致**：
迁移后的表结构要与全新安装完全等价，否则 Room 的 schema 校验会在老用户升级时
抛出 `Migration didn't properly handle`。

`Migration3To4` 加两列，两列的**默认值处理不同**，加新列时注意区分：
`amountMinorUnits` 是可空列且**不带 DEFAULT**（历史行即为 NULL = 「未记金额」），
实体上因此**不写** `@ColumnInfo(defaultValue)`；`personCount` 是
`NOT NULL DEFAULT 1`，实体上必须写 `@ColumnInfo(defaultValue = "1")`。

该等价性可以在本机用内存 SQLite 验证（无需设备）：用 `app/schemas/.../3.json` 的
`createSql` 建表、执行迁移 SQL、再用 `4.json` 的 `createSql` 建一张表，比对两者
`PRAGMA table_info` 的 name / type / notNull / pk / defaultValue。实测差异为空。

## 代码分层

```text
Compose Screen          界面展示与用户事件
  └─ ViewModel          页面状态、防抖与错误文案
       └─ MealRepository  输入校验、事务边界、照片文件生命周期
            ├─ MealDao       纯 SQL 与对象映射
            ├─ PhotoStore     私有目录图片读写、降采样与安全删除
            └─ BackupStore    版本化 ZIP 备份包导出与导入
```

界面层另有三个横切模块：

```text
ui/theme/       调色板、形状阶梯、阴影令牌（softShadow）、深色方案
ui/components/  Miuix.kt（通用组件）、Motion.kt（动效）、Glass.kt（玻璃材质）、
                MealComponents.kt（业务语义组件：徽章、餐厅行、照片入口、对话框）
```

约定：

- 可预期的业务失败通过 `MealResult` / `MealError` 返回，不使用异常或“假成功”表达；
- 涉及数据的操作先提交事务、再删除磁盘文件，保证失败时数据一致；
- **磁盘文件只在没有任何数据库行引用它时才允许删除**。仓库内所有删除都经过
  `MealRepository.deleteUnreferencedFiles()`：一个文件可能同时被店铺封面与用餐记录引用
  （用户把某张用餐照片设成了封面），逐处打补丁漏一处就是数据丢失；
- 编辑表单回收图片时，只回收「本次导入、且当前未被选中」的那些，
  原封面与用餐照片无论何时都保留（见 `reclaimableFormPhotos()`）；
- `RestaurantEntity` / `DiningRecordEntity` 中被标记 `@Deprecated` 的字段是维持数据库
  schema 的历史列，新增代码不得读写；
- 备份包使用独立的 JSON 格式，不复用数据库实体，避免历史遗留列进入备份文件；
- **UI 视觉必须遵循 `DESIGN_SYSTEM.md`**：语义色分工、圆角阶梯、动效曲线均已固定，
  不要在页面里就地发明新的色值或圆角。

## 数据与隐私

餐厅、用餐记录和照片默认只保存在应用本地目录。未配置同步时**不会连接任何服务器**；
配置 WebDAV 并主动触发上传/下载后，仅与用户填写的服务器地址通信。

- **备份与恢复**：设置页可将全部数据导出为 ZIP 备份包（含照片），并在换机或重装后完整恢复。
  导入采用全量替换，执行前会二次确认。
- **存储管理**：可查看照片占用空间，并清理保存流程中断遗留的残留文件。
- **权限**：导入导出通过系统文件选择器（SAF）完成，应用未申请任何存储权限。

数据库 schema 导出到 `app/schemas`，任何数据库结构变更都必须同时提供 Room Migration，不允许依赖清库升级。

### 备份包格式

```text
mealnote-backup-YYYYMMDD-HHmm.zip
├─ manifest.json        格式版本、应用版本、导出时间、条目统计
├─ restaurants.json
├─ dining_records.json
├─ photos.json
└─ photos/<name>.jpg
```

导入时会校验格式版本，并限制单条目与总解压体积、规范化条目文件名以阻断路径穿越。

## 发布签名

Release 包使用项目根目录的 `mealnote-release.jks` 自签名，凭据写在 `local.properties`
（该文件已被 `.gitignore` 排除）。若该文件未配置签名信息，`assembleRelease`
仍会成功但产出未签名包，便于 CI 只做编译验证。

> 提示：自签名证书仅适用于个人安装。若后续需要上架应用商店，请改用正式的发布密钥，
> 并妥善备份密钥库 —— 密钥丢失后将无法为已发布应用推送更新。

## 开发约定

- 新依赖必须与 AGP 8.13.2、compileSdk 36 兼容；
- 不要只为追新版本整体升级 Compose/AndroidX；
- 数据模型变更必须执行升级测试；
- 图片删除、取消和保存失败流程必须验证文件清理；
- 提交前至少运行单元测试、Lint 和 Debug 构建。
