# 食单 项目总览（PROJECT OVERVIEW）

> 整理日期：2026-09-20 ｜ 最近校准：**2026-10-06**（第二、四、六、十、十二、十四、十五节按工作区实况重写）
> 本文件是对整个项目的单一入口式概览：定位、现状、结构、架构、数据、约定、已知问题与文档索引。
> 各主题的权威文档见文末「文档索引」，本文与它们冲突时以对应专题文档为准。

---

## 一、项目是什么

**食单**（曾用名「饭记」「味笺」，包名 `com.fanji.mealnote`）是一款**本地优先**的 Android
餐厅收藏与用餐记录应用。产品口号：**记住每一家想去的店，也记住每一顿值得回味的饭。**

- 两种记录入口（悬浮按钮面板二选一）：
  - **想吃，还没去** → 新建店铺：店名必填，地址与封面图可选；
  - **已经吃过了** → 选店 → 用餐表单：三级评价（**推荐 / 尚可 / 不推荐**）+ 餐品名 + **花费（自由文本）** + 照片（≤9 张）+ 可选附录。
- 明确**不做**的字段：星级、菜系、标签、推荐链接、强制感想（对应数据库列保留但不进 UI）。
- 产品原则：一次记录尽量 20 秒完成；数据只存本机、可控可迁移。

### 信息架构（底部三栏）

| 标签 | 回答的问题 | 主要文件 |
|---|---|---|
| **清单** | 还有什么想吃的 / 哪些店去过了（搜索、分段筛选、随机选一家） | `ui/home/WantListScreen.kt` |
| **足迹** | 我吃过什么、评价如何、花了多少（时间线 ⇄ 统计两个分段） | `ui/home/FootprintScreen.kt` |
| **我的** | 备份恢复、WebDAV 同步、运行日志、存储管理、外观、关于 | `ui/settings/SettingsScreen.kt` |

---

## 二、当前状态（2026-10-06 校准）

| 项 | 值 |
|---|---|
| 已提交 | **v0.6.1（versionCode 17）**，应用名「食单」（曾用名 饭记 → 味笺） |
| 工作区 | 本轮整理后**全部入库**（含此前压着的 37 个文件 `+3429 / −1001` 与 ≈30 个未跟踪文件） |
| 数据库 | Room version **4**，identityHash `b93fd210509db5642fb4c1aed52f6a14`（v0.6.0 **无 schema 变更**） |
| 测试 | **28 个 JVM 套件 / 252 例**，2026-10-06 实测全过 + 1 个从未运行过的迁移仪器测试 |
| 验证门槛 | 单测 / `lintDebug` / `assembleDebug` / `assembleRelease` 全过，`apksigner verify` 退出码 0；已交付 `apk/食单-v0.6.1-{debug,release}.apk`（release 证书与 v0.5.1 / v0.6.0 一致，可覆盖安装） |
| 未解锁 | **设备验证**与 WebDAV **真实服务器验证**（AGENTS §5-3）——见第十四节 |

v0.6.0 由三块互相交叠的工作合并而成（ROADMAP 原本拆在 v0.5.2 / v0.6.0 / v0.6.x 三个版本）：

1. **暖食欲 · 图为主视觉重做**（全域，不止清单域）：主色 `#0EA56B`→`#0A8558`（白字对比 3.18→4.66:1）、阴影由冷蓝灰转暖棕、新增 `*Vivid` 与陶土红**纯装饰档**、`ic_launcher` 同步转色；`Type.kt` 补 `headlineSmall`/`titleSmall`（此前 ShareCard 店名静默回落 Roboto）。底色当时改为暖米白，**v0.6.1 又按真机反馈改回纯白 + 暖白卡片**（见第八节）。
2. **功能批次**：账本 CSV 导出（SAF）、每月预算、清单排序、备份新鲜度、WebDAV 连通性检查、重复店名提醒、年度回顾卡片、单店累计入账。逐项核过接线：都有纯函数 + UI 入口 + 持久化，无孤儿代码；测试从 14 类 / 181 例增至 **28 类 / 252 例**（+71 例，含删除 1 个自证测试）。
3. **`OPTIMIZATION_PLAN.md` 阶段 0 + 个别 2/3 条目**：WebDAV 凭据排除出系统备份与设备迁移、删除只断言 JDK 自身语义的假测试、测试清单与文档漂移校正、转场弹簧归位、`Type.kt` 补档。该规划的**阶段 1、4、5 仍未开始**。

> ⚠️ v0.6.0 已装到真机跑过首轮（发现上述两个 bug）；**v0.6.1 的修复本身仍未经设备确认**，
> 纯白底层次感与 6 个二级页的手感需要你再复看一轮。风险清单见第十四节。

### 代码规模

- 主源码约 **17,200 行** Kotlin（最大文件：`FootprintScreen.kt` 1171 行、`Miuix.kt` 1003 行、`RestaurantDetailScreen.kt` 738 行）。
- 测试 **28 个类 / 252 个用例**（约 3,200 行），另有 1 个未运行过的迁移仪器测试。

---

## 三、技术栈（版本已锁定，勿随意升级）

| 依赖 | 版本 |
|---|---|
| Kotlin / AGP / KSP | 2.3.21 / 8.13.2 / 2.3.11 |
| Android Gradle Plugin 目标 | compileSdk = targetSdk = **36**，minSdk = **26**（Android 8.0） |
| Compose BOM / Material 3 / Navigation Compose | 2025.01.00 / — / 2.8.5 |
| Hilt / Room | 2.57.2 / 2.8.4 |
| Coil / coroutines / exifinterface | 2.7.0 / 1.10.2 / 1.3.7 |
| Java 工具链 | 17（本机位于 `D:\env\jdk-17.0.16+8`） |
| 版本目录 | `gradle/libs.versions.toml`（唯一依赖声明处） |

约定：新依赖必须与 AGP 8.13.2 / SDK 36 兼容；升级依赖必须在独立分支先跑通构建、Lint 与测试；不为单个设置项引入 DataStore（用 `SharedPreferences`），备份 JSON 用平台 `org.json`（不引入 kotlinx-serialization）。

---

## 四、项目结构

```text
mealnote/
├─ app/
│  ├─ build.gradle.kts            模块构建配置（版本号、签名、lint 策略）
│  ├─ proguard-rules.pro          R8 规则
│  ├─ schemas/…AppDatabase/       Room schema 导出（1/2/3/4.json，迁移等价性验证依据）
│  └─ src/
│     ├─ main/java/com/fanji/mealnote/
│     │  ├─ MainActivity.kt / MealNoteApplication.kt
│     │  ├─ data/
│     │  │  ├─ MealRepository.kt      业务一致性、事务边界、文件生命周期守卫
│     │  │  ├─ MealError.kt           MealResult/MealError 统一错误模型
│     │  │  ├─ MealText.kt            自由文本统一处理（按 code point 截断）
│     │  │  ├─ AmountText.kt          花费文本 → 入账金额（分）解析
│     │  │  ├─ PhotoStore.kt          私有目录图片读写、降采样、安全删除
│     │  │  ├─ ShareImageStore.kt     分享卡片图片落盘（cacheDir/shared/）
│     │  │  ├─ local/                 AppDatabase(v4)、MealDao、Models（实体）、Converters
│     │  │  ├─ backup/                BackupStore（ZIP 导出导入）、BackupModels
│     │  │  ├─ export/ ★              LedgerCsv（纯转义/BOM）+ LedgerCsvStore（SAF 写盘）
│     │  │  ├─ log/AppLog.kt          运行日志（环形缓冲 + 落盘滚动，脱敏）
│     │  │  ├─ settings/              Theme / Motion / Random / ListSort ★ / Budget ★ /
│     │  │  │                         BackupState ★ Preference（全部 SharedPreferences）
│     │  │  └─ webdav/                WebDavStore（HttpURLConnection + 连通性检查 ★）、WebDavPreference
│     │  ├─ di/DatabaseModule.kt      Hilt 模块 + Migration1To2 / 2To3 / 3To4
│     │  └─ ui/
│     │     ├─ MealNoteApp.kt         NavHost、转场动效、路由表
│     │     ├─ Formatters.kt          日期/金额/存储量格式化
│     │     ├─ theme/                 Color（暖食欲语义色）、Theme（令牌）、Type
│     │     ├─ components/            Miuix（通用组件）、Motion、Glass、FluidMotion、
│     │     │                         GalleryPicker、MealComponents（业务组件）、
│     │     │                         CommonStates、FormSections、VerdictSelector、
│     │     │                         PhotoViewer、ShareCard、AmountLedgerSection ★、
│     │     │                         YearReviewCard ★
│     │     ├─ home/                  MainScaffold（壳）、WantList（清单）、Footprint（足迹）、
│     │     │                         ListSearch、FootprintAggregation、LedgerAggregation（纯函数）
│     │     ├─ add/  visit/           新建店铺（含重复店名提醒 ★）、选店、用餐表单
│     │     ├─ detail/ edit/          详情（含单店累计入账 ★）、编辑店铺、编辑用餐
│     │     └─ settings/              设置页、WebDavSection
│     ├─ test/                        28 个 JVM 单元测试类 / 252 例
│     └─ androidTest/                 AppDatabaseMigrationTest（仪器测试，未运行过）
├─ apk/                              历代交付 APK（gitignore，不入库）
├─ scripts/run-unit-tests.ps1 ★      全量 JVM 测试（从源码自动发现测试类，清单不会漂移）
├─ docs/ ★                           代码级模块参考（架构/数据层/UI/功能/构建测试/安全隐私）
├─ build.gradle.kts / settings.gradle.kts / gradle.properties
├─ gradle/libs.versions.toml         版本目录（依赖唯一声明处）
├─ mealnote-release.jks              发布签名密钥库（gitignore；凭据在 local.properties）
├─ local.properties                  SDK 路径 + 签名凭据（gitignore）
└─ 文档：AGENTS / README / CHANGELOG / DESIGN_SYSTEM / ROADMAP / OPTIMIZATION_PLAN /
         PROJECT_OVERVIEW / PROJECT_PLAN / HANDOFF(-0.4.0)
```

★ = 尚未提交的内容（截至 2026-10-06 为未跟踪文件）。

---

## 五、架构分层与数据流

```text
Compose Screen          界面展示与用户事件
  └─ ViewModel          页面状态、防抖、savedStateHandle 持久化、错误文案
       └─ MealRepository  输入校验（normalizeText）、事务边界、照片文件生命周期
            ├─ MealDao       纯 SQL 与对象映射（Room）
            ├─ PhotoStore    files/photos/ 图片读写、EXIF 校正、降采样（最长边 2048px）、安全删除
            ├─ BackupStore   版本化 ZIP 备份包导出与导入
            └─ WebDavStore   WebDAV 上传/下载（复用 BackupStore.restore 校验）
```

界面横切模块：`ui/theme/`（调色板/形状/阴影令牌）、`ui/components/`（通用组件 + 动效 + 玻璃材质 + 业务组件）。

**导航约定**：`Routes.MAIN` 是唯一壳路由，底部三标签不拆成独立路由（否则返回键在标签间跳）；所有进入用餐表单的路径统一 `popUpTo(MAIN)` 压平返回栈。

---

## 六、数据模型（Room，version 4）

```text
restaurants (1) ──< dining_records (1) ──< photos     外键均 CASCADE
                    └─ restaurants.recommendationPhotoPath 可指向与 photos 相同的文件
```

| 实体 | 业务字段（非遗留） | 说明 |
|---|---|---|
| `RestaurantEntity` | id、name（唯一必填）、address、recommendationPhotoPath（封面）、status（WANT_TO_EAT/EATEN）、createdAt/updatedAt | 6 个 `@Deprecated` 遗留列（city/cuisine/tags/priceHint/sourceUrl/sourceNote）仅为维持 schema，禁止读写（已定：继续保留不清理） |
| `DiningRecordEntity` | id、restaurantId、eatenAt、verdict（GOOD/MEH/BAD）、dishes、priceText（v3，自由文本花费）、amountMinorUnits + personCount（v4，入账金额/人数）、note、createdAt | 2 个遗留列（star、perPersonCost）；`priceText` 的 `@ColumnInfo(defaultValue="''")` 必须与 Migration SQL 逐字一致；`amountMinorUnits` 是**可空且无 DEFAULT** 的列，实体上因此不写 `defaultValue`，`personCount` 是 `NOT NULL DEFAULT 1`，实体必须写 `@ColumnInfo(defaultValue = "1")` |
| `PhotoEntity` | id、diningRecordId、filePath（绝对路径）、sortOrder | 磁盘文件删除由 Repository 显式负责，级联只删行 |

**关键规则**：

- 枚举常量名即持久化编码（`Converters` 按名写入，容错解码未知值），**禁止重命名常量**；
- 禁止 `fallbackToDestructiveMigration()`，任何 schema 变更必须配 Migration + 升级测试，schema 导出到 `app/schemas/`；
- 迁移历史：1→2 加 `restaurants.recommendationPhotoPath`；2→3 加 `dining_records.priceText`（`ALTER TABLE ... ADD COLUMN priceText TEXT NOT NULL DEFAULT ''`）；3→4 加 `amountMinorUnits`（入账金额，可空无 DEFAULT）与 `personCount`（人数，`NOT NULL DEFAULT 1`）；
- 迁移等价性可在本机用内存 SQLite 验证（用相邻两版 json 的 `createSql` 各建一张表、执行迁移 SQL、比对 `PRAGMA table_info` 的 name / type / notNull / pk / defaultValue，实测差异为空）。仪器测试 `AppDatabaseMigrationTest` 从未运行，**老用户升级路径仍是风险最高、验证最薄的一环**。

**备份包格式**（独立 JSON，不复用数据库实体；新增字段用 `put` 写 / `optLong`·`optString` 读，不必升格式版本——`dining_records.json` 的 `amountMinor`/`personCount` 即如此，旧备份导入后金额为空）：

```text
mealnote-backup-YYYYMMDD-HHmm.zip
├─ manifest.json / restaurants.json / dining_records.json / photos.json
└─ photos/<name>.jpg （v0.4.0 起包含店铺封面）
```

导入 = 全量替换 + 单事务（清空顺序：照片→记录→餐厅，与外键方向相反）；导入校验：格式版本、512MB 总量预检、单条目/单图限额、路径穿越阻断、ZIP 炸弹防护。

---

## 七、数据安全的四条核心不变式

1. **磁盘文件只在没有任何数据库行引用它时才允许删除**。所有删除统一走
   `MealRepository.deleteUnreferencedFiles()` 守卫（一个文件可能同时是封面和用餐照片）。
2. **先提交事务、再删除磁盘文件**，失败时数据一致。
3. **编辑表单只回收「本次导入、且当前未被选中」的图片**（`reclaimableFormPhotos()` 纯函数，9 个测试覆盖）；原封面与已落库照片无条件保留。
4. **`restoredFromSavedState` 必须在构造时求值且声明在 `init` 之前**（init 里的持久化协程会在构造期写回 savedStateHandle，否则清理逻辑静默失效——v0.3.3/v0.3.6 两个数据丢失 bug 的共同根因）。

其他数据相关约定：

- 可预期业务失败走 `MealResult`/`MealError`，禁止异常或「假成功」；
- `CancellationException` 必须重抛；
- 自由文本入库统一走 `MealText.normalizeText(limit)`：**按 code point 截断**，禁止 `take(n)`/`substring(0,n)`（会把 emoji 代理对切成半个，产生非法字符串并可能损坏备份文件）；
- 删除/回收相关代码改动后必须人工重读全部分支（这类 bug 无报错、静默失效）。

---

## 八、设计系统要点（详见 DESIGN_SYSTEM.md，UI 改动以其为准）

- 方向：**纯白底 · 暖白浮层 · 图为主**（继承 MIUIx / HyperOS 的浮层与弹簧语言）。Canvas `#FFFFFF` + 卡片 `#FBF8F4`（暖白）+ 1px 描边 `#EFE9E1` + 柔和**暖棕**投影 + **弹簧动效**（禁线性/缓动，循环型动画除外）。分层不再靠「底与卡片的色差」——真机反馈偏好纯白底，白底上卡片改由暖白色调、描边与阴影三件叠加撑起来。
- 语义色分工全应用一致：**绿** = 主操作/已用餐/推荐（主色 `#0A8558`，收深到能压住白色按钮文字 4.66:1）、**琥珀** = 待探访/花费、**暖石灰** = 尚可、**红** = 不推荐/危险；状态不允许只靠颜色，必须带文字。`*Vivid` 与陶土红是**纯装饰**档，禁止进入状态语义。
- 圆角阶梯 10/16/20/26/32dp；页面边距 20dp；主按钮最小高 54dp（`heightIn(min=…)` 防大字体裁字）；可点卡片按下时**投影收缩到 35%**。
- 玻璃材质 `GlassSurface`：API 31+ 真实背景模糊（GraphicsLayer + RenderEffect），低版本降级拟态玻璃；**流畅模式**关闭时全部退化（v0.4.0）。
- 无障碍硬约定：分段控件 `selectableGroup()+selectable()` 配对；`clearAndSetSemantics` 必须补回 `role`/`onClick`；图表整体描述；动画数字锁定终值播报；`3/9` 要写成「第 3 张，共 9 张」。
- 术语固定：待探访 / 已用餐；推荐 / 尚可 / 不推荐；添加一家店 / 记录这一餐。不用感叹号与语气助词。

---

## 九、开发环境与构建

### 常规构建

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。
Release 用根目录 `mealnote-release.jks` 自签名（凭据在 `local.properties`，未配置时产出未签名包不报错）。R8 + 资源裁剪已开启。判定签名用 `apksigner verify`（本项目 v2 方案，解包看不到 META-INF/*.RSA 属正常）。

### 本机已知环境问题（与代码无关）

| 问题 | 处理 |
|---|---|
| `testDebugUnitTest` 曾报所有测试类 `ClassNotFoundException at initializationError` | **2026-10-06 复测未复现**（`--rerun` 强制执行 28 套件 / 252 例全过）。历史记录的原因：AGP 的 `bundleDebugClassesToRuntimeJar` 产物未进测试类路径。首选跑法是 `scripts/run-unit-tests.ps1`（自动发现测试类），README 的 JDK 直跑命令留作复发时的备用路径 |
| 找不到 JDK 17 工具链 | 显式传 `-Porg.gradle.java.installations.paths="D:/env/jdk-17.0.16+8"` |
| 中文路径乱码 | 项目目录已是英文 `Desktop\mealnote`；`C:\Users\2540\mealnote-workspace` 联接**仍在用**（AGENTS §3 的构建命令以它为入口，2026-10-06 实测可用），别当冗余删掉 |
| build-cache 写入失败（拒绝访问） | 加 `--no-build-cache`，必要时删 `~/.gradle/caches/build-cache-1` |
| R8 报 `classes.dex 另一个程序正在使用此文件` | `./gradlew.bat --stop` → 删 `app/build/intermediates/dex/release/` → 重建 |
| `lintDebug` 与 `assembleDebug` 同跑偶发 R8 内部错误 | 单独跑、重跑即可 |
| 改了测试源码但任务 UP-TO-DATE | 手动删 `app/build/tmp/kotlin-classes/debugUnitTest` 等目录后重编 |

---

## 十、测试

**本机跑不了应用，单元测试是唯一自动验证手段**，覆盖优先级高。

- **28 个测试套件 / 252 例**（以 `grep -rc "@Test" app/src/test` 为准）。逐类覆盖范围只在
  `docs/BUILD_AND_TEST.md` §6 维护一份，本文不再复制清单——这份清单在 README/文档间各自漂移过至少三次。
- 跑法首选 `scripts/run-unit-tests.ps1`：**从源码自动发现测试类**，新增类不需要维护清单，
  不可能漂移。README 里的 JDK 直跑命令是备用路径，用它就必须手工同步类清单。
- 策略：纯逻辑抽成**不读时钟/时区的函数**（`today`/`zone` 作参数，范例 `FootprintAggregation.kt`）；**把设计决策锁进测试**（如「花费全识别不出返回 null 而非 0」「排行榜同次数按 id 升序」）；分支逻辑一律抽成 `internal` 顶层纯函数供测试直调。
- 零覆盖缺口：`MealRepository` 与全部 ViewModel（需 Room 测试环境与协程调度器，Repository 是具体类需先抽接口）。
- `AppDatabaseMigrationTest` 仪器测试从未运行（需真机/模拟器）。
- 反面教材（已清理）：`PendingPhotoCleanupConcurrencyTest` 在测试里自建 `ConcurrentHashMap`
  副本、断言的是 JDK 自身语义，从未触达生产代码，随优化轮阶段 0 删除。真实的删除守卫
  `MealRepository.deleteUnreferencedFiles()` 需 DAO + Context，本机 JVM 覆盖不了——
  「测试自己的副本」比没有测试更危险，`HomeFilterTest` 曾犯同样的错（已抽到 `ListSearch.kt`）。

**发布门槛**：`testDebugUnitTest` + `lintDebug` + `assembleDebug` + `assembleRelease` 全过，另做真机核心流程冒烟（权威清单见 `AGENTS.md` §5）。

---

## 十一、安全与隐私

- 数据库、照片、备份全部存应用私有目录；**唯一权限是 `INTERNET`**（v0.4.0 起，仅 WebDAV 同步需要；不配置同步不发起任何网络连接）。导出导入走 SAF，无存储权限。
- WebDAV：只允许 https（内网段放行 http）、Basic 认证、密码明文存私有目录（已在界面提示）、下载复用备份导入同一套校验。
- `AppLog` 只记事件与脱敏来源，**禁止记录店名、备注、花费原文、完整路径、URI 明文、备份内容**。
- `PhotoStore` 删除强制 canonical path 校验，仅允许删私有照片目录内文件。
- 备份 JSON 为独立格式，历史遗留列不导出。
- 密钥库 `mealnote-release.jks` 是**单点故障**（丢失后无法为已发布应用推送更新），已被 gitignore；提交前须确认 `local.properties` 与 `*.jks` 未入库。

---

## 十二、版本历史

| 版本 | 主题 | 数据库 |
|---|---|---|
| 0.2.0 | 编辑/删除闭环、ZIP 备份恢复、统一错误模型 | v2 |
| 0.3.0 | 花费字段（自由文本）、底部三栏信息架构、MIUIx 视觉重做、玻璃材质、动效体系 | v3 |
| 0.3.1 | 全屏照片查看器、git init | v3 |
| 0.3.2 | 足迹统计（概览/花费估算/12 个月柱状图/评价分布/Top 5） | v3 |
| 0.3.3 | 修复换封面误删图片（引用守卫）、从用餐照片设封面、深色模式 | v3 |
| 0.3.4 | 分享卡片（先预览再分享） | v3 |
| 0.3.5 | 无障碍细化（语义树、大字体） | v3 |
| 0.3.6 | 修复表单图片回收从未执行（构造时序） | v3 |
| 0.3.7 | 统计聚合抽成纯函数并补 21 用例 | v3 |
| 0.3.8 | 修复 emoji 截断数据损坏（按 code point） | v3 |
| 0.3.9 | 「照上次再来一份」（预填评价/餐品/花费） | v3 |
| **0.4.0** | 编辑空白/旧机相册导入/备份丢封面三修复；运行日志、流畅模式、WebDAV 同步 | v3（未变） |
| **0.5.0** | 记账：结构化入账金额 `amountMinorUnits`（分）+ `personCount`、表单金额确认条、足迹页账本统计 | **v4** |
| **0.5.1**（已提交，versionCode 15） | 随机选店按评价范围（三档 + 待探访开关 + 排除最近 N 天）、清单搜索覆盖餐品名、WebDAV 失败提示可诊断 | v4（未变） |
| **0.6.0**（versionCode 16，2026-10-06 提交） | 应用改名**食单**；暖食欲视觉全域重做；账本 CSV 导出、每月预算、清单排序、备份新鲜度、WebDAV 连通性检查、重复店名提醒、年度回顾卡片、单店累计入账；优化轮阶段 0 | v4（未变） |
| **0.6.1**（versionCode 17，2026-10-07） | 真机首轮反馈：修吸顶条滚动发灰（`Color.Transparent` 插值经透明黑）与「统计」页回不去；底色改**纯白 + 暖白卡片 + 1px 描边**；「我的」拆 6 个二级分区页（新路由 `settings/{section}`） | v4（未变） |

更早（0.1.x 原型 → 产品重构）见 CHANGELOG「未发布」节：产品由「餐厅资料管理」聚焦为「想吃清单 + 极简评价」，应用更名味笺，重构为两入口模型。

---

## 十三、开发约定（红线清单）

1. **版本控制**：用户逐轮确认后才 commit（格式 `feat/fix/docs: …（vX.Y.Z）`，正文写根因与验证）；未经确认不改版本号、不动 `apk/`、不升级依赖。
2. **构建**：从项目英文路径执行，显式传 JDK 17 路径。
3. **数据层**：`MealResult`/`MealError`；先事务后删文件；删除走引用守卫；重抛 `CancellationException`；schema 变更必配 Migration（当前 v4）。
4. **文本**：长度限制一律按 code point，禁 `take(n)`。
5. **视觉**：一切 UI 改动符合 `DESIGN_SYSTEM.md`；流畅模式关闭时的降级外观也要测。
6. **无障碍**：语义树硬约定（见第八节）。
7. **日志**：过 AppLog 隐私红线。
8. **提交前**：至少跑单元测试、Lint、Debug 构建；确认敏感文件未入库。
9. 新依赖与 AGP/SDK 36 兼容；整体升级 Compose/AndroidX 须独立分支验证。

---

## 十四、已知问题与未做事项

**验证债（最高优先）**

- 本机无真机/模拟器：暖食欲视觉重做、八个新功能与阶段 0 的全部 UI 改动**都没在设备上跑过**。
  排版、深色模式、大字体裁字、TalkBack 播报、流畅模式关闭时的降级外观一律未经确认。
- WebDAV **手动同步从未在真实服务器上验证过**（v0.4.0 发布即撞上 404 的旧债）。本批新增的
  连通性检查正是为它准备的工具，但同样未经真实服务器验证——AGENTS §5-3 把它列为发布门槛。
- `MealRepository` 与全部 ViewModel 零测试覆盖；`AppDatabaseMigrationTest` 从未运行。
- `OPTIMIZATION_PLAN.md` 的阶段 1–5 **基本未动**：`MealError→文案` 仍在 6 个 ViewModel 里各写
  一份（已出现文案漂移）、`Dimens.kt`/`MealSprings` 未建（硬编码尺寸与散装弹簧参数仍在）、
  `liveRegion` 全项目 0 处、`FootprintScreen.kt` 仍 1171 行未拆、Migrations 仍在 `di/`。
  仅少量条目被顺手做掉（`Type.kt` 已补 `headlineSmall`/`titleSmall`，`MealNoteApp` 转场改取
  `MealMotion`）——**该文档的状态列已落后于工作区，别照它排期**。

**仓库与文档状态（2026-10-06 整理后）**

- `AGENTS.md`、`OPTIMIZATION_PLAN.md`、`docs/`（7 个文件）、`scripts/run-unit-tests.ps1` 已入库；
  此前它们只存在于这块磁盘上，与密钥库同属单点故障。
- `.workbuddy-ai/memory/`（1523 行、停在 v0.3.9 的旧 Agent 记忆）已从索引摘除并 gitignore
  （磁盘文件保留）。**注意**：它们仍留在历史提交里，要彻底切断需要重写历史（未做）。
- 本仓库**没有 Compose UI 测试**，也没有任何一次真机走查；未提交批次合并成一个版本号提交，
  因为 `SettingsScreen.kt` 等文件同时承载多个功能，按版本拆提交拆不干净。

**长期限制（非本批引入）**

- WebDAV 未做自动同步、版本管理、失败重试；密码明文存于私有目录（界面已提示；已从系统备份
  与设备迁移排除）。
- 旧备份包无封面文件，导入新包到旧版应用时封面条目被忽略（前向兼容未真机验证）。
- `MAX_PICKED_FILE_BYTES`（512MB）、`MAX_RAW_COPY_BYTES`（32MB）为经验值，真机反馈后可调。
- 无 Compose UI 测试；3→4 迁移等价性靠内存 SQLite 手工验证而非仪器测试。
- 实现与规划的偏差已确认一处：ROADMAP 要求 WebDAV 连通性检查为「PROPFIND/HEAD 四态」，
  实际实现是「两次 HEAD + 六态归类」（`WebDavStore.testConnection`）。
- 暂缓项（评估后不做，勿贸然加回）：底部导航栏跟随大字体长高；分享卡片接入足迹时间线。

---

## 十五、文档索引

| 文件 | 内容 |
|---|---|
| `AGENTS.md` | **代理协作规约**（目标、架构、命令、硬约定、验收门槛、安全边界）——与本文同为接手必读 |
| `README.md` | 产品现状、技术栈、构建环境（含已知环境问题与 JDK 直跑命令）、构建状态、迁移说明、代码分层、发布签名、开发约定 |
| `CHANGELOG.md` | 全部版本的权威变更记录（每个版本含验证结果） |
| `DESIGN_SYSTEM.md` | 视觉与交互规范（**UI 改动以其为准**）：信息架构、术语、色彩、形状、字体、组件、动效、玻璃、图像、无障碍、语义树 |
| `ROADMAP.md` | 发展规划：记账、评价范围随机筛选、竞品调研、新功能优先级（**§7.3 的节奏已被 `OPTIMIZATION_PLAN.md` 取代**） |
| `OPTIMIZATION_PLAN.md` | 当前执行清单与验收门（修复打磨轮，不加新功能）；阶段 0 已做完，1–5 未开始，状态列落后于工作区 |
| `docs/` | **代码级模块参考**：`ARCHITECTURE` / `DATA_LAYER` / `UI_LAYER` / `FEATURES` / `BUILD_AND_TEST` / `SECURITY_PRIVACY` |
| `scripts/run-unit-tests.ps1` | 全量 JVM 测试入口（从源码自动发现测试类，清单不会漂移） |
| `PROJECT_PLAN.md` | 历史股东项目书（背景、早期路线图、里程碑、风险）；§21 为 2026-09-10 产品方向调整 |
| `HANDOFF-0.4.0.md` / `HANDOFF.md` | 历史交接文档（v0.4.0 与 0.3.x 时期），仅供追溯 |
| `PROJECT_OVERVIEW.md` | 本文 |

> ⚠️ `.workbuddy-ai/memory/` 里 1523 行旧 Agent 记忆**已提交进仓库但停在 v0.3.9**（数据库版本、
> 测试清单、视觉方向都已过时），不要作为事实依据；权威信息以上表为准。
