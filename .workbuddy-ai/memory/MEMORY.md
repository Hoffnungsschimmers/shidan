# 项目长期记忆：味笺 / 饭记

## 项目定位

本地优先的 Android 个人餐厅收藏与用餐记录应用。包名 `com.fanji.mealnote`，
应用名「味笺」。目录 `C:\Users\2540\Desktop\mealnote`。

产品收敛为两种入口（由悬浮按钮的底部面板二选一）：
- **想吃，还没去**：仅店名（必填）、可选地址、一张封面图；
- **已经吃过了**：先选店（可新建）→ 推荐 / 尚可 / 不推荐 + 餐品名字 + **花费** + 照片 + 可选附录。

**用户明确不要的字段**：星级、菜系、标签、推荐链接、强制感想。
这些字段的数据库列必须保留（否则 schema 校验失败），但不得在新 UI 中出现。
（「人均消费」原属此类，v0.3.0 以 `priceText` 自由文本的形式重新引入，见下。）

## 技术栈（已锁定版本，不要随意升级）

Kotlin 2.3.21 / AGP 8.13.2 / KSP 2.3.11 / Hilt 2.57.2 / Room 2.8.4 /
Compose BOM 2025.01.00 / Coil 2.7.0 / coroutines 1.10.2 / exifinterface 1.3.7。
compileSdk = targetSdk = 36，minSdk = 26，Java 17。
版本目录：`gradle/libs.versions.toml`。

升级依赖必须在独立分支先跑通构建、Lint 和测试。

## 构建命令（必须遵守）

> 项目目录已于 2026-09-16 从中文名 `饭` 迁移到英文名 `mealnote`。
> 现在**真实路径本身就是英文**，不再依赖联接绕开中文路径限制。

- 直接在新路径执行：`C:\Users\2540\Desktop\mealnote`。
- `C:\Users\2540\mealnote-workspace` 是指向它的目录联接，**现已冗余**，
  保留只是为了不改动旧命令；随时可删（`rmdir`）。
- JDK 17 在 `D:\env\jdk-17.0.16+8`，必须显式传
  `-Porg.gradle.java.installations.paths="D:/env/jdk-17.0.16+8"`。
- 缓存权限异常时加 `--no-build-cache`，必要时删除 `C:\Users\2540\.gradle\caches\build-cache-1`。
- **R8 报 `classes.dex 另一个程序正在使用此文件`**：残留守护进程锁定了
  `app/build/intermediates/dex/release/`。处理：`./gradlew.bat --stop` → 删除该目录 → 重建。
- **`lintDebug` 与 `assembleDebug` 同时跑时偶发 R8 报错**（`com.android.tools.r8.internal.df3.error`），
  单独跑则通过。属偶发，重跑即可，不是代码问题。
- **`testDebugUnitTest` 在本机会全部报 `ClassNotFoundException at initializationError`**
  （环境问题，与代码无关）：AGP 的 `bundleDebugClassesToRuntimeJar` 产物没有进入测试类路径。
  改用 JDK 直接运行 `org.junit.runner.JUnitCore` 验证，详见 `README.md`「已知环境问题」。
- **改了测试源码但任务报 UP-TO-DATE**：Gradle 会复用旧的测试编译产物，跑出旧用例数。
  处理：`rm -rf app/build/tmp/kotlin-classes/debugUnitTest app/build/intermediates/classes/debugUnitTest`
  后重编，并重跑 `:app:transformDebugUnitTestClassesAsm`。

## 架构与约定

```
Compose Screen → ViewModel → MealRepository → (MealDao | PhotoStore | BackupStore)
```

界面层横切模块：`ui/theme/`（调色板、形状、阴影令牌）、
`ui/components/`（Miuix.kt 通用组件 / Motion.kt 动效 / Glass.kt 玻璃 / MealComponents.kt 业务语义组件）。

- 可预期的业务失败用 `MealResult` / `MealError` 返回，**禁止**用异常或返回空值表达“假成功”。
- 数据操作**先提交事务、再删除磁盘文件**，保证失败时数据一致。
- 照片目录：`files/photos/`，应用私有。删除必须经 `PhotoStore` 的 canonical path 校验。
- 图片导入会降采样到最长边 2048px 并校正 EXIF 方向。
- 数据库：`meal_note.db`，**当前 version = 3**，schema 已导出到 `app/schemas/`。
  `identityHash = 9a477c043b56c97eb725b6ad2011a9ed`。
  版本历史：v1→v2 加 `restaurants.recommendationPhotoPath`；
  v2→v3 加 `dining_records.priceText`。
- **禁止**加回 `fallbackToDestructiveMigration()`；任何 schema 变更都必须写 Migration 与升级测试。
- 新增列若在迁移里带 `DEFAULT`，实体上**必须**写 `@ColumnInfo(defaultValue = "…")` 且逐字一致，
  否则「迁移后的表」与「全新安装的表」不等价，Room 校验会在升级时报
  `Migration didn't properly handle`。
- 实体上标 `@Deprecated` 的字段是历史兼容列，新增代码不得读写。
- 备份包（`data/backup/`）使用**独立 JSON 格式、不复用数据库实体**，历史遗留列不导出。
  JSON 用平台 `org.json` 而非 kotlinx-serialization（项目锁定依赖版本，不值得为此加编译器插件）。
  新增字段用 `put` 写、`optString` 读，**不需要**提升 `FORMAT_VERSION`。
- 导入 = 全量替换 + 单事务；清空顺序必须是 照片→记录→餐厅（与外键方向相反）。
- 编辑表单必须区分「已落库照片」与「新导入照片」，只回收后者，否则会破坏已有数据。
- 自由文本入库统一走 `MealRepository.normalizeText(limit)`：trim + 截断（不报错）。

### 导航约定

- `Routes.MAIN` 是唯一的壳路由，底部三个标签**不拆成独立路由**
  （否则返回键会在标签间跳而不是退出应用）。
- 所有进入用餐表单的路径统一 `popUpTo(MAIN)` 压平返回栈，返回键行为一致。

## 设计系统

见 `DESIGN_SYSTEM.md`（**以该文件为准**）。要点：

- MIUIx / HyperOS 方向：中性冷灰底 `#F3F4F6`、纯白浮层、大圆角、柔和投影、弹簧动效。
- 主色青翠绿 `#0EA56B`；语义色分工固定：绿=推荐/已用餐、橙=待探访/花费、
  石板灰=尚可、红=不推荐/危险。
- 圆角 10/14/18/24/30dp；页面边距 20dp；主按钮高 54dp。
- **取消描边分层，改用柔和投影**；投影色带冷色偏移而非纯黑。
- 所有动效用弹簧曲线，不用线性/缓动。
- 玻璃材质 `GlassSurface`：API 31+ 真实背景模糊，低版本降级为拟态玻璃。
- 术语：待探访 / 已用餐；推荐 / 尚可 / 不推荐；添加一家店 / 记录这一餐。

## 当前版本与交付

- **v0.3.9**（versionCode 12）。功能已齐（花费、MIUIx 视觉、信息架构、玻璃、照片查看器、
  统计、封面复用、深色模式、分享卡片、无障碍细化、照上次再来一份）。
- 交付物在 `apk/`：`味笺-v0.3.9-release.apk`（已签名，2,062,107 B，
  sha256 `c66daaf8ed5910b819451c0134926788daaa7e59367a5993fe7b451c7d79d5df`）、
  `味笺-v0.3.9-debug.apk`（18,900,209 B）。
- 签名密钥库 `mealnote-release.jks`（项目根目录），凭据在 `local.properties`。
  `.gitignore` 已排除 `*.jks` / `local.properties` / `*.apk`。
  **密钥库是单点故障**：丢失后已安装用户无法覆盖升级。
- Release 签名配置：`signingConfigs` **必须声明在 `buildTypes` 之前**，
  否则报 `SigningConfig with name 'release' not found`。
  另外 `android {}` 块内 `java` 被 AGP 遮蔽，`Properties` 需在文件顶部 import。
- 判定 APK 是否签名**必须用 `apksigner verify`**：本项目只用 v2 方案，
  签名块在 ZIP 中央目录之后，解包看不到 `META-INF/*.RSA` 属正常现象。

## 版本控制

- 已是 Git 仓库（`main` 分支），2026-09-13 初始化。
- **提交前必须确认 `local.properties` 与 `mealnote-release.jks` 未被纳入**
  （前者含签名口令），用 `git ls-files --error-unmatch <file>` 逐个验证。

## 文件生命周期（数据安全的核心不变式）

**磁盘文件只在没有任何数据库行引用它时才允许删除。**
仓库内所有删除都走 `MealRepository.deleteUnreferencedFiles()`。
一个文件可能同时被 `restaurants.recommendationPhotoPath`（封面）与
`photos.filePath`（用餐照片）引用 —— 用户可以把某张用餐照片设成封面。

**编辑表单回收图片时，只回收「本次导入、且当前未被选中」的那些**，统一走
`reclaimableFormPhotos(formPhotoPaths, originalCoverPath, recordPhotoPaths, keep)`。
表单首次加载时 `photoPaths` 就是数据库当前的封面，无条件删「上一张」会在导入新图的
瞬间删掉仍在使用的文件（v0.3.3 修复的正是这个自 v0.2.0 起就存在的 bug）。
已有 9 个单元测试覆盖（`ReclaimableFormPhotosTest`）。

**`restoredFromSavedState` 必须在构造时求值，且声明在 `init` 之前。**
四个表单 ViewModel 都用它决定 `onCleared` 是否回收图片。写成函数留到 `onCleared`
再算会**恒为 true** —— 因为 `init` 里的持久化收集器（`Dispatchers.Main.immediate`
+ StateFlow 首帧同步发射）在**构造期间**就把那些 key 写回了 `savedStateHandle`，
导致清理逻辑静默失效（v0.3.6 修复）。

## 审计「删除」相关代码的例行要求

删除路径**不会自己暴露问题**：没有报错、没有用户可见异常，只是逻辑静默失效。
v0.3.3 的换封面误删、v0.3.6 的回收失效，都是靠主动重读删除代码发现的。
每次大改之后，把 `onCleared` / `deleteXxx` / `updateXxx` 里所有涉及文件删除的
分支重读一遍。

## 审计字符串处理（同样例行）

v0.3.8 发现 `normalizeText` 用 `take(limit)` 截断，而 `take` 数的是
**UTF-16 码元**，会把 emoji 的代理对切成半个，产生非法字符串
（入库显示 `�`、JSON 备份可能无法解析）。**已改为按 code point 截断**
（`offsetByCodePoints`），统一入口见 `data/MealText.kt`。

**禁止再用 `String.take(n)` / `substring(0, n)` 做「长度限制」** ——
只要输入可能含 emoji，就必须按 code point 或字素簇。

长度上限的语义统一为**码点**（一个 emoji 算 1 个字符）。
已知近似：ZWJ 复合 emoji（`👨‍👩‍👧`）可能被拆开，视觉不理想但不损坏数据 ——
要修需引入 `java.text.BreakIterator` 与 locale 依赖，不划算，已在代码注释与
CHANGELOG 中写明。

## 无障碍硬约定（改 UI 时必须遵守）

「视觉上表达了、但语义树里没有」等于对屏幕阅读器不存在。

- 分段控件/标签页：容器 `selectableGroup()` + 选项 `selectable(selected)`，**必须配对**。
- `clearAndSetSemantics` 会**清掉 `clickable` 的语义**，必须显式补回 `role` 与
  `onClick`（或 `disabled`），否则控件对无障碍服务直接消失。
- 按钮加载中：标签被替换成转圈会让语义变空，必须保留 `contentDescription` +
  `stateDescription = "处理中"`。
- 图表整体给 `contentDescription`；纯装饰元素（指示块、比例条）用
  `clearAndSetSemantics {}` 排除。
- 带滚动动画的数字要锁定最终值播报。
- 紧凑记法（`3 / 9`）会被读成「三 斜杠 九」，要写成完整句子。
- 大字体：固定高度会裁字。按**文字行高反推最小高度**，并保证常规字号下
  算出来的值仍小于原高度 —— **默认外观必须完全不变**。
- **卡片内含可交互子元素时不能 `mergeDescendants`**，否则子元素对 TalkBack 失效。
  `MiuixCard` 是通用组件无法自行判断，因此不加。

## 测试策略

**本机跑不了这个应用，单元测试是唯一的自动验证手段**，因此测试覆盖优先级很高。

- 运行方式：Gradle 的 `testDebugUnitTest` 在本机全部报 `ClassNotFoundException`
  （环境问题，见 `README.md`），必须用 JDK 直接跑 `org.junit.runner.JUnitCore`。
  当前 8 个测试类、74 个用例。
- **纯逻辑必须抽成不读系统时钟/时区的函数**，把 `today` / `zone` 作为参数传入。
  否则测试只能断言「跑得通」。范例：`ui/home/FootprintAggregation.kt`。
- 把**设计决策锁进测试**，而不只测计算结果。例如
  「花费全识别不出时返回 `null` 而非 `0`」「排行榜次数相同时按 id 升序」——
  以后有人想「简化」掉这些行为，测试会拦下来。
- 测试类清单（新增测试必须同时更新 `README.md` 里的 JDK 运行命令）：
  `MealResultTest`、`MealTextTest`、`ConvertersTest`、`FormattersTest`、`HomeFilterTest`、
  `FootprintAggregationTest`、`PendingPhotoCleanupConcurrencyTest`、
  `BackupEntryNameSafetyTest`、`ReclaimableFormPhotosTest`、`VisitPrefillTest`。
- **把设计决策锁进测试**，而不只测计算结果。范例：
  「花费全识别不出时返回 `null` 而非 `0`」、
  「排行榜次数相同时按 id 升序」、
  「`VisitPrefill` 的字段集合用反射断言，防止被加字段」。
- **零覆盖的缺口**：`MealRepository` 与全部 ViewModel。要补需要
  Room 测试环境与协程测试调度器，且 `MealRepository` 是具体类不是接口，
  得先重构出接口或把纯逻辑继续往下抽。

## 明确评估后放弃的功能（不要贸然加回）

（暂无。原先列为「放弃」的「从用餐照片设置封面」已在 v0.3.3 完成 ——
前置的引用守卫重构已做完，见上节。）

评估过但暂缓的：

- **底部导航栏跟随大字体长高**：需要连带把 `MainContentBottomPadding`（104dp 常量）
  动态化，牵动三个标签页的 padding。fontScale 2.0 时现状勉强够用，收益小改动面大。
- **分享卡片接入足迹时间线**：卡片上加按钮会让每张卡高 48dp，影响浏览密度。

## 其他

- 文档：`README.md`（构建与环境）、`DESIGN_SYSTEM.md`（设计规范）、
  `CHANGELOG.md`（变更）、`PROJECT_PLAN.md`（路线图）、`HANDOFF.md`（交接）。
- 新增设置项优先用平台自带的 `SharedPreferences`（见 `data/settings/ThemePreference.kt`）：
  项目锁定依赖版本，为单个设置项引入 DataStore 不划算。写入用 core-ktx 的
  `preferences.edit { }`（默认 apply），否则 Lint 会报 `UseKtx`。
- 分享卡片用 `GraphicsLayer` 录制 + `toImageBitmap()`（挂起函数）导出位图，
  图片写在 `cacheDir/shared/`（**不是** `files/photos/`），
  `res/xml/file_paths.xml` 同时映射 `files-path` 与 `cache-path`。
  必须让卡片可见才能录到内容 —— 所以设计成「先预览再分享」。
- 未做（V0.3 剩余候选）：统计的年度回顾视图、无障碍细化、
  分享卡片接入足迹时间线（目前只在详情页）；`AppDatabaseMigrationTest`
  仪器化测试从未运行（需真机/模拟器；本机改用内存 SQLite 做了迁移等价性验证）。
