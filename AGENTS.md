# AGENTS.md — 食单（曾用名 味笺 / 饭记）代理协作规约

> 本文件是给 AI 代理与协作者的**稳定规则清单**,只写不常变的目标、架构、命令、修改约束、验收与安全边界。
> 产品与视觉细节以专题文档为准(见文末索引),本文与它们冲突时以专题文档为准。
> 代码级模块参考见 `docs/`。

---

## 1. 项目目标(不变)

- **本地优先**的 Android 餐厅收藏与用餐记录应用,包名 `com.fanji.mealnote`,应用名「食单」。
  v0.6.0 起改名「食单」;**包名、`applicationId`、数据库文件名与 SharedPreferences 名一律不变**
  ——它们决定已安装应用能否覆盖更新、旧数据是否成为孤儿,与产品名解耦。
- 核心价值:记住想去的店 + 记录每一餐(三级评价 推荐/尚可/不推荐 + 餐品 + 花费 + 照片 + 附录),并逐步承担「吃饭账本」角色。
- 产品红线:**一次记录 ≤ 20 秒**;数据默认只存本机;底部三栏信息架构不变(清单 / 足迹 / 我的)。

## 2. 架构与技术栈(锁定)

分层数据流:
```
Compose Screen → ViewModel → MealRepository → { MealDao, PhotoStore, BackupStore, WebDavStore }
```
- 单模块 `:app`,Kotlin 2.3.21 / Java 17;Jetpack Compose(BOM 2025.01.00)+ Material 3 + Navigation Compose 2.8.5。
- Room 2.8.4(当前 DB version 4)、Hilt 2.57.2 + KSP、Coil 2.7.0、ExifInterface 1.3.7。
- AGP 8.13.2,compileSdk/targetSdk 36,minSdk 26(Android 8.0)。
- 备份、WebDAV、CSV 导出与备份新鲜度同属「旁路」:由 ViewModel 直接注入 Store
  (`BackupStore`/`WebDavStore`/`LedgerCsvStore`/`BackupStatePreference`),不经 `MealRepository`。
- **依赖唯一声明处**:`gradle/libs.versions.toml`。

## 3. 命令(构建 / 验证)

从**英文路径**执行,显式传 JDK 17:
```powershell
.\gradlew.bat -p C:\Users\2540\mealnote-workspace `
  testDebugUnitTest lintDebug assembleDebug assembleRelease `
  -Porg.gradle.java.installations.paths="D:\env\jdk-17.0.16+8"
```
- **`testDebugUnitTest` 在本机可用**(2026-10-06 实测:`--rerun` 强制执行 28 个套件 / 252 例全过,
  早先记录的「全体 `ClassNotFoundException`」类路径 bug 未再复现)。跑测试首选
  **`scripts/run-unit-tests.ps1`**——它从源码自动发现测试类,不存在清单,也就不会漂移。
- README「已知环境问题」里的 JDK 直跑 `org.junit.runner.JUnitCore` 降为**备用路径**
  (换机或该类路径问题复发时启用)。走这条备用路径才需要手工同步测试类清单;
  **不要**再把「新增测试类必须同步 README 清单」当成默认约定。
- 缓存写入失败追加 `--no-build-cache`;签名判定用 `apksigner verify`(v2 方案)。
- `lintDebug` 与 `assembleDebug` 同跑偶发 R8 内部错误:分开跑、重跑即可。
- 详细环境坑见 `docs/BUILD_AND_TEST.md`。

## 4. 修改约束(硬约定)

**数据与文件生命周期**
- 可预期业务失败一律走 `MealResult` / `MealError`,禁止异常或「假成功」;必须重抛 `CancellationException`。
- **先提交事务,再删除磁盘文件**;磁盘文件**只在没有任何数据库行引用时**才允许删除,统一走 `MealRepository.deleteUnreferencedFiles()`(封面与用餐照片可能共享同一文件)。
- 编辑表单只回收「本次导入且当前未选中」的图片(`reclaimableFormPhotos()`);原封面与已落库照片无条件保留。
- 删除 / 回收相关代码改动后**必须人工重读全部分支**(这类 bug 静默无报错)。

**Schema 与文本**
- 枚举常量名即持久化编码(`Converters` 按名读写),**禁止重命名 / 删除常量**。
- **禁止** `fallbackToDestructiveMigration()`;任何 schema 变更必配 Migration + 升级测试,schema 导出到 `app/schemas/`。
- 实体 `@ColumnInfo(defaultValue)` 必须与 Migration SQL 的 `DEFAULT` **逐字一致**(可空且无默认列则不写);用内存 SQLite 比对 `PRAGMA table_info` 验证等价性。
- `@Deprecated` 遗留列仅维持 schema,**禁止读写**(已定:继续保留不清理)。
- 自由文本入库统一 `MealText.normalizeText(limit)`:**按 code point 截断**,禁 `take(n)` / `substring(0,n)`(会切碎 emoji 代理对损坏备份)。

**UI / 视觉**
- 一切 UI 改动符合 `DESIGN_SYSTEM.md`:视觉方向为**暖食欲 · 图为主**(暖米白底 + 深翠绿主色);语义色分工(绿=主操作/已用餐/推荐、琥珀=待探访/花费、暖石灰=尚可、红=不推荐/危险)、圆角阶梯 10/16/20/26/32dp、页面边距 20dp、弹簧动效(禁线性/缓动;**唯一例外**是无终点的循环型动画:骨架屏扫光、空态呼吸)。状态不可只靠颜色,必须带文字。
- 动效弹簧一律取自 `MealMotion`(`settle`/`bouncy`/`pop`/`quick`),组件内不得自行写 `spring(...)`。
- 无障碍硬约定:分段控件 `selectableGroup()`+`selectable(selected)` 配对;`clearAndSetSemantics` 补回 `role`/`onClick`;图表整体描述;动画数字锁定终值播报;`3/9` 写作「第 3 张,共 9 张」。
- 主按钮用 `heightIn(min=…)` 防大字体裁字;图片**解码尺寸必须有界**(`AsyncImage` 显式指定尺寸,或 `rememberAsyncImagePainter` + 有界外层 Box),否则 Coil 按原图解码,一张 2048px 照片约占 16MB。
- 玻璃 `GlassSurface`:API 31+ 真实模糊(GraphicsLayer+RenderEffect),低版本降级拟态玻璃;**流畅模式**关闭时降级外观也要验证。

**依赖 / 版本控制**
- 新依赖须与 AGP 8.13.2 / SDK 36 兼容;不为单个设置项引入 DataStore(用 `SharedPreferences`),备份 JSON 用平台 `org.json`(不引入 kotlinx-serialization)。
- **不升级** AGP / Kotlin / Compose;整体升级须独立分支先跑通构建+Lint+测试。
- **未经用户逐轮确认不 commit**、不改 versionCode/versionName、不动 `apk/`。commit 格式 `feat/fix/docs: …(vX.Y.Z)`,正文写根因与验证。

## 5. 验收(发布门槛)

1. `testDebugUnitTest`(或首选 `scripts/run-unit-tests.ps1`)+ `lintDebug` + `assembleDebug` + `assembleRelease` 全过;
2. Schema 变更:独立 Migration + 内存 SQLite 等价性验证 + 升版号(schema 变更单独成版,不与其他功能混跑);
3. 涉及远程服务器的功能,**发布前必须在真实服务器上跑通一次**(WebDAV 单测通过不代表可用);
4. 文档同步纳入门槛(README 的 DB 版本 / 迁移表、`docs/BUILD_AND_TEST.md` 的逐类清单与计数不得漂移);
5. 新增纯逻辑一律「时钟/时区作参数」,把设计决策锁进测试。

## 6. 安全边界

- 唯一权限 `INTERNET`,仅 WebDAV 同步用;不配置同步时不发起任何网络连接。导入导出走 SAF,无存储权限。
- **敏感数据禁止进 `AppLog`**:店名、备注、花费原文、完整路径、URI 明文、备份内容、金额、凭据。回显地址/文件名前必须 `redactUrlCredentials` 洗掉凭据。
- **失败必须可见**:远程/耗时操作失败提示不自动消失,整块错误色 + 显式关闭,带足以自查的信息;成功提示可自动消失。
- `PhotoStore` 删除强制 canonical path 校验,仅允许删私有照片目录内文件。
- 备份导入:格式版本校验 + 总量/单条目/单图限额 + 路径穿越阻断 + ZIP 炸弹防护;导入为全量替换单事务。
- **凭据不离开设备**:`mealnote_webdav`(存 WebDAV 账号密码的 SharedPreferences)必须同时出现在
  `backup_rules.xml` 与 `data_extraction_rules.xml` 的 `cloud-backup` + `device-transfer` 排除列表里;
  清理或重写这两个 XML 前先到 `docs/SECURITY_PRIVACY.md` 核对不变式。
- **密钥库 `mealnote-release.jks` 是单点故障**(丢失即无法为已发布应用推更新),已 gitignore;`local.properties` 含签名凭据,提交前确认两者未入库,且不得回显凭据明文。

## 7. 已知瓶颈(排期时优先考虑)

- 本机**无设备/模拟器**:UI、深色模式、大字体、TalkBack 一律未经真机确认(`android-automation` 技能可用于 `adb install`/`screencap`/`uiautomator dump`)。
- `MealRepository` 与全部 ViewModel **零测试覆盖**;`AppDatabaseMigrationTest` 仪器测试从未运行(升级路径是风险最高、验证最薄的一环)。
- 「验证能力」优先于「新增功能」——继续堆未验证功能只会扩大未验证表面积。

## 8. 文档索引

| 文件 | 内容 |
|---|---|
| `README.md` | 产品现状、技术栈、构建环境、已知环境问题、迁移说明、发布签名 |
| `PROJECT_OVERVIEW.md` | 单一入口式项目总览(定位/结构/架构/数据/约定/已知问题);第二节与第十四节记工作区实况 |
| `DESIGN_SYSTEM.md` | 视觉与交互规范(权威,UI 改动以其为准) |
| `ROADMAP.md` | 发展规划(第七节节奏 + **第九节落地校准**,二者取代前六节版本表) |
| `OPTIMIZATION_PLAN.md` | 当前执行清单与验收门(修复打磨轮,不加新功能);状态列可能落后于工作区 |
| `CHANGELOG.md` | 全部版本权威变更记录 |
| `PROJECT_PLAN.md` / `HANDOFF*.md` | 历史项目书与交接文档(仅供追溯) |
| `scripts/run-unit-tests.ps1` | 全量 JVM 测试入口,**从源码自动发现测试类**(首选跑法,清单不会漂移) |
| `docs/` | **代码级模块参考**(见下) |

`docs/` 目录:
- `docs/ARCHITECTURE.md` — 分层、模块地图、导航路由、依赖注入
- `docs/DATA_LAYER.md` — Repository/Dao/DB/迁移/PhotoStore/BackupStore/WebDav/日志/金额解析
- `docs/UI_LAYER.md` — 首页域、表单/详情域、组件体系、主题与材质
- `docs/FEATURES.md` — 记账、随机选店、备份恢复、WebDAV、分享卡片、运行日志、流畅模式
- `docs/BUILD_AND_TEST.md` — 构建命令、环境坑、测试策略与清单
- `docs/SECURITY_PRIVACY.md` — 权限、签名、数据安全不变式、隐私红线
