# 味笺

味笺是一款本地优先的 Android 餐厅收藏与用餐记录应用。它可以快速保存想去的店，并用「推荐 / 尚可 / 不推荐」、餐品名字、**花费**、照片和可选附录记录每次用餐。

## 当前状态

项目处于 V0.3 阶段。核心记录流程、管理能力、备份恢复与界面重做均已完成。

**信息架构**（底部三栏）

| 标签 | 回答的问题 |
|---|---|
| **清单** | 还有什么想吃的 / 哪些店去过了（含搜索、分段筛选、随机选一家） |
| **足迹** | 我吃过什么、评价如何、花了多少（时间线 ⇄ 统计两个视角） |
| **我的** | 备份导出、恢复、存储占用、清理与清空数据、关于 |

「足迹」内含两个分段：

- **时间线**：按月份分组、组内按时间倒序；
- **统计**：今年次数 / 去过的店 / 累计次数、花费估算、最近 12 个月柱状图、
  评价分布、去得最多的店 Top 5。

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
- 所有数据仅保存在本机，不需要任何存储权限。

**界面**

- MIUIx / HyperOS 风格：中性冷灰底、纯白浮层、大圆角、柔和投影、弹簧动效；
- 底部导航栏、详情页顶栏与操作栏使用 **Liquid Glass** 玻璃材质；
  API 31+ 为真实背景模糊（`GraphicsLayer` + `RenderEffect`），低版本自动降级为拟态玻璃；
- 全屏照片查看器：左右滑动切换、双击缩放、放大后拖动平移；
- 分享卡片：把一次用餐渲染成竖版图片交给系统分享面板（先预览再分享）；
- 深色模式可选「跟随系统 / 浅色 / 深色」。

完整设计规范见 [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)，路线图见 [PROJECT_PLAN.md](PROJECT_PLAN.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

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
C:\Users\2540\mealnote-workspace  ->  C:\Users\2540\Desktop\饭
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

### 已知环境问题：单元测试类路径

本机执行 `testDebugUnitTest` 时，所有测试类都会在装载阶段报
`java.lang.ClassNotFoundException ... at initializationError`（注意是**全部**测试类，
而非某一个断言失败）。

原因：AGP 的 `bundleDebugClassesToRuntimeJar` 产物
（`app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar`）
未被加入测试工作进程的运行时类路径，测试代码因此看不到主源码集中的类。
这与被测代码无关。

在该问题修复前，可改用 JDK 直接运行同一批测试（结果等价）：

```bash
"$JDK17/bin/java.exe" \
  -cp "app/build/intermediates/classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs;\
app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar;\
<junit-4.13.2.jar>;<kotlin-stdlib.jar>;<hamcrest-core-1.3.jar>" \
  org.junit.runner.JUnitCore \
  com.fanji.mealnote.data.MealResultTest \
  com.fanji.mealnote.data.local.ConvertersTest \
  com.fanji.mealnote.ui.FormattersTest \
  com.fanji.mealnote.ui.home.HomeFilterTest \
  com.fanji.mealnote.data.PendingPhotoCleanupConcurrencyTest \
  com.fanji.mealnote.data.backup.BackupEntryNameSafetyTest \
  com.fanji.mealnote.ui.edit.ReclaimableFormPhotosTest \
  com.fanji.mealnote.ui.home.FootprintAggregationTest
```

依赖 jar 位于 `~/.gradle/caches/modules-2/files-2.1/` 下。

## 构建状态

| 检查项 | 状态 |
|---|---|
| `testDebugUnitTest` | 74 个用例全部通过（经 JDK 直接运行验证，见上方环境问题说明） |
| `lintDebug` | 通过（0 错误） |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过（R8 混淆 + 资源裁剪，已签名） |

数据库版本 3，`identityHash` = `9a477c043b56c97eb725b6ad2011a9ed`。

### 数据库迁移

| 版本 | 变更 |
|---|---|
| 1 → 2 | `restaurants` 增加 `recommendationPhotoPath`（封面图） |
| 2 → 3 | `dining_records` 增加 `priceText`（花费，自由文本） |

`Migration2To3` 使用 `ALTER TABLE ... ADD COLUMN priceText TEXT NOT NULL DEFAULT ''`。
实体上的 `@ColumnInfo(defaultValue = "''")` 与迁移中的 `DEFAULT ''` **必须逐字一致**：
迁移后的表结构要与全新安装完全等价，否则 Room 的 schema 校验会在老用户升级时
抛出 `Migration didn't properly handle`。

该等价性可以在本机用内存 SQLite 验证（无需设备）：用 `app/schemas/.../2.json` 的
`createSql` 建表、执行迁移 SQL、再用 `3.json` 的 `createSql` 建一张表，比对两者
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

餐厅、用餐记录和照片默认只保存在应用本地目录，**不会上传到任何服务器**。

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


