# 项目现状（PROJECT_STATUS）

> **本文是项目「当前状态」的单一事实来源。** 接手前先读这里，不要重新全仓摸底；
> 历史轨迹看 [`DEVELOPMENT_LOG.md`](DEVELOPMENT_LOG.md)，发版记录看 [`../CHANGELOG.md`](../CHANGELOG.md)。
> 维护规则见 [`../AGENTS.md`](../AGENTS.md) §9。
>
> **最后更新：2026-10-26（v0.6.2 本地打包验证完成）** ｜ 更新者：AI 代理（Claude Code）
> **对应代码：** `cb954b1` + 未提交语法修复、照片保护/迁移测试与 WebDAV 上传错误通道增强（见 §2）
> **证据等级：** `[实测]` 本次实际运行 ｜ `[代码]` 读源码确认 ｜ `[文档]` 仅文档声明 ｜ `[推断]` 未验证

---

## 0. 5 分钟接手清单

1. 读本文 §1–§5，知道版本、分支、验证到哪、当前风险。
2. 用 `git status` / `git log --oneline -5` 核对 §2 是否仍成立；不一致时以 git 为准并回写本文。
3. 跑一次 `scripts/run-unit-tests.ps1`（首选测试入口），确认基线。
4. 改代码前读 `AGENTS.md` 与对应 `docs/` 模块文档。
5. **本次会话结束前**按 §9 追加 `DEVELOPMENT_LOG.md`，并回写本文变化的事实。

---

## 1. 基本信息

| 项 | 值 |
|---|---|
| 应用名 / 包名 | 食单 / `com.fanji.mealnote` |
| 版本 | `versionCode 18` / `versionName 0.6.2`（本地待验收包；无远程 Release） |
| 数据库 | Room v4，schema 1–4 已导出到 `app/schemas/` |
| 技术栈 | Kotlin 2.3.21 / AGP 8.13.2 / Compose BOM 2025.01.00（实际 Compose 1.7.6、Material3 1.3.1）/ Room 2.8.4 / Hilt 2.57.2 / Coil 2.7.0 |
| SDK | compileSdk 36 / targetSdk 36 / minSdk 26 |
| 代码规模 | 主源码约 80 个 `.kt`、1.86 万行；JVM 测试 **38 类 / 399 例**；androidTest 1 类 / 6 例 |
| 权限 | 仅 `INTERNET`（WebDAV 同步用） |
| 交付包 | `apk/` 保留 30 个历史 APK，新增 `食单-v0.6.2-release.apk`（2,144,195B）与 `食单-v0.6.2-debug.apk`（19,195,336B），共 32 个；**未 git 跟踪**。新版与本轮构建 SHA-256 一致，同类型证书与 v0.6.1 一致；指纹/哈希见 `RELEASE_0.6.2.md` |
| 产品红线 | 一次记录 ≤ 20 秒；数据默认只存本机；底部三栏（清单 / 足迹 / 我的）不变 |

## 2. 分支与工作区

| 项 | 事实 | 证据 |
|---|---|---|
| 当前分支 | `polish/quick-record-experience`，**无 upstream、未推送** | `git status -sb` |
| main | `e3c0f3a`（v0.6.1） | `git log` |
| 相对 main | 1 个提交 `cb954b1`，78 文件 `+4472/−999` | `git diff main...HEAD --stat` |
| origin | `https://github.com/Hoffnungsschimmers/shidan.git` | `git remote -v` |
| 工作区 | 已有 `AddVisitViewModel.kt` 语法修复（删除 3 行）保留；新增照片回收保护/日志与 17 例回归，增强六路径迁移测试；WebDAV 本地导出失败透传/独立上传临时包与 13 例执行器回归，状态/构建/数据层文档同步；全部未提交 | `[实测]` `git status --short` |

**HEAD 语法错误（P0，未解决）**：`cb954b1` 的 `AddVisitViewModel.kt` 在 `onCleared()` 之后残留类体级
`super.onCleared()` 与多余 `}`（花括号 68/69），`[代码]` 静态判定无法编译；工作区修改正是删除这 3 行，
把文件恢复到此前可编译的内容。`[实测]` 2026-10-26 用该工作树跑测试通过，但**修复尚未提交**。
在提交前，任何从 HEAD 干净检出、CI 或出包都会失败。

> 该 3 行修改**在本轮摸底之前就已存在于工作区**，不是本轮产生的改动。

> 成因：`cb954b1` 手工把 `MealError.toSaveMessage()` 替换为 `data/MealErrorMessages.kt` 时误留了旧方法尾部两行。

## 3. 功能现状

| 模块 | 状态 | 说明 |
|---|---|---|
| 清单（待探访 / 已用餐 / 全部、搜索、排序、随机、熟店快记、重复名提醒） | ✅ 已实现 | 有 JVM 纯逻辑测试；UI 未真机验证 |
| 足迹（时间线 / 统计 / 账本 / 预算 / 历年汇总 / 年度回顾） | ✅ 已实现 | 聚合逻辑有测试；视觉与分享图未真机验证 |
| 我的（外观 / 随机 / 账本与预算 / 备份与同步 / 存储与日志 / 关于） | ✅ 已实现 | 6 个二级分区页 |
| ZIP 备份 / 恢复、CSV 导出、WebDAV 手动同步 | ⚠️ 可用但未端到端验证 | 纯函数与恢复执行器有测试；真实 SAF / 真实服务器未跑通 |
| 删除撤销 | ⛔ 未实现 | 仅提示「无法撤销」；零 schema 方案未定 |
| WebDAV 自动备份 | ⛔ 未实现 | 门槛未解锁：手动同步未在真实服务器验证 |

## 4. 验证现状

### 4.1 本次已实测（2026-10-26）

| 检查 | 结果 |
|---|---|
| 修改前生产代码基线（英文 junction 路径 / JDK 17） | 脚本 **OK (369 tests)**；`lintDebug`、`assembleDebug`、`assembleRelease` 均 **BUILD SUCCESSFUL** |
| v0.6.2 JVM 回归 | 原生 `testDebugUnitTest --rerun-tasks` **BUILD SUCCESSFUL in 1m 33s**；38 套件 / 399 例，XML 0 跳过/失败/错误；此前脚本也 **OK (399 tests)** |
| v0.6.2 Lint / 构建 | `lintDebug` **BUILD SUCCESSFUL in 1m 12s**，**No issues found.**；`assembleDebug assembleRelease assembleDebugAndroidTest` **BUILD SUCCESSFUL in 3m 1s** |
| 签名 / APK 信息 | 两包 `apksigner verify` **Verifies、v2=true**，同类型证书与 v0.6.1 一致；aapt 确认包名不变、版本 18/0.6.2、SDK 26/36；交付复制哈希一致 |
| 迁移测试 APK | `assembleDebugAndroidTest` **BUILD SUCCESSFUL**；`check-migration-assets.ps1` **PASS** 四份历史 schema 及 SHA-256；仅编译/资产验证，不是设备执行 |
| 源码测试计数 | 38 个 `*Test.kt` / 399 个 `@Test`；androidTest 仍 1 类 / 6 例 |

### 4.2 未运行 / 未验证

- `AppDatabaseMigrationTest` 6 例：已增强迁移至 v4 后真实 Room/DAO/关系/SQL 默认值断言，**只有编译产物，从未在设备运行**。
- 本轮仅交付 v0.6.2 本地待验收包；已验证签名与同类型历史证书一致，**没有设备覆盖升级/数据保留实测**，未创建远程 Release 或推送。
- 真机 UI、深色模式、大字体、TalkBack、流畅模式降级外观、20 秒录入目标：**无设备/模拟器**。
- 真实 SAF 备份往返、真实 WebDAV 上传/下载/恢复：**未跑通**。
- `MealRepository`、Android `PhotoStore`、`BackupStore`、`WebDavStore`、9 个完整 ViewModel：**仍无完整直接集成测试**。本轮 17 例直接验证生产照片回收接缝（真实临时文件/注入解析失败），不能算 Android Room/SAF/Repository 生命周期集成验证；DAO 仪器断言仅编译。

> 判断：**纯逻辑与状态守卫的验证较扎实；持久化、文件生命周期、备份恢复、网络与完整 UI 基本未验证。**
> 「测试全过」只覆盖 JVM 层，不等于可发布。

## 5. 风险登记（按优先级）

| ID | 级别 | 风险 | 证据 | 状态 |
|---|---|---|---|---|
| R-01 | P0 | HEAD 不可编译，修复未提交 | `[代码]` 花括号 68/69 | **未解决** |
| R-02 | P1 | 无设备：迁移、UI、无障碍、大字体、深色全未真机验证 | `[文档]`+`[实测]` adb 空 | 未解决 |
| R-03 | P1 | **相机照片绕过 PhotoStore 处理链**：`createCameraTarget()` 让相机直接写 `files/photos`，四个 VM 只存路径，无 2048px / JPEG88 / EXIF / 32MB 上限；>32MB 照片可导出却会导致整包恢复失败 | `[代码]` `PhotoStore.kt:138-146`、四个 Screen/VM 回调 | 未解决（本次新发现） |
| R-04 | P1 | 备份恢复内存/磁盘压力：4 份 JSON 各 32MB 全量读入，OOM 不被 `catch(Exception)` 捕获；2GB staging + 复制双份磁盘 | `[代码]` `BackupStore.kt:576-600` | 未压测 |
| R-05 | P1 | F03/F04 未实现：导出无对称限额；四份 JSON 后到覆盖；未知条目被忽略 | `[代码]` `BackupStore.kt:209-265,433-436` | 未解决 |
| R-06 | P1 | 高风险模块缺少完整集成测试：Repository / ViewModel / PhotoStore / BackupStore | `[实测]` 照片回收生产接缝 17 例通过；Android 全链仍未验证 | 部分改善，未解决 |
| R-07 | P1 | WebDAV 真实协议未验证；404/401/403 与网络失败映射仍可能误报 | `[实测]` 13 例上传生产执行器通过，但导出/上传依赖为替身，没有真实服务器请求 | 未解决；本地备份失败透传已修（R-18） |
| R-08 | P1 | 签名密钥库单点故障：`mealnote-release.jks` 与 `local.properties` 未入库、无备份；缺 `local.properties` 时 release 仍"成功"但未签名 | `[实测]` git 跟踪检查 | 运维风险 |
| R-09 | P2 | 系统备份排除不完整（疑似）：只排除 `meal_note.db`，未排除 `-wal/-shm/-journal`；含预算的 `mealnote_settings` 未排除 | `[推断]` `backup_rules.xml` | 待真机确认 |
| R-10 | P2 | 流畅模式降级不完整：主清单 `staggeredEnter` 未传 `enabled`、三处 `animateItem` 未降级、FAB/入口/按压动画仍执行 | `[代码]` `WantListScreen.kt:156,228-229`、`MainScaffold.kt:489` | 未解决 |
| R-11 | P2 | 深色/大字体：`themes.xml` 固定浅色窗口 + 默认 `enableEdgeToEdge()`；年度回顾卡主题绿配白字对比度不足；底栏固定 64dp 大字体裁字 | `[代码]`+`[推断]` | 未解决 |
| R-12 | P2 | F05/F08/F10/F11/F12/F13/F14 未修：超大 ID、统计不自动刷新、缺图静默跳过、照片重名、金额窄化、降采样不足、WebDAV 误报 | `[代码]` 审计文档 | 未解决 |
| R-13 | P3 | `file_paths.xml` 把整个 `cacheDir` 映射给 FileProvider | `[代码]` | 加固项 |
| R-14 | P3 | 店名/地址入库无长度上限，与 `MealText.normalizeText` 约定不一致 | `[代码]` `MealRepository.kt:122-125,415-422` | 未解决 |
| R-15 | P3 | 文档计数/状态漂移 | `[实测]` | 本轮部分修正 |
| R-16 | P3 | WebDAV 内网 http 放行用字符串前缀判断，`10.example.com` 之类可绕过「仅内网」表述 | `[代码]` `WebDavStore.kt:243-252` | 未解决 |
| R-17 | P1 | 孤儿清理静默丢弃无法 canonical 解析的引用，定向回收未归一文件身份，可能误删共享照片（未复现实际丢图） | `[代码]` 原 `PhotoStore.deleteOrphanPhotos`/`PhotoReclamation`；`[实测]` 新增 17 例生产接缝回归 | **已解决（2026-10-26：坏引用整次中止、别名共享保护/目录边界/年龄/取消回归通过；Android 集成仍由 R-06 跟踪）** |
| R-18 | P2 | WebDAV 本地导出 Failure 被变成 null，统一误报 PhotoIoFailure；固定上传临时文件可能被重叠操作覆盖/回收 | `[代码]` 原 buildLocalBackup/upload；`[实测]` 13 例生产执行器回归 | **已解决（2026-10-26：原始 Failure/不 PUT、取消和临时包隔离回归通过；完整 SAF/HTTP 链由 R-07 跟踪）** |

## 6. 已裁决事项（不要重复调查）

| ID | 结论 | 依据 |
|---|---|---|
| D-01 | **1→2 迁移的 `DEFAULT ''` 不会导致升级崩溃，保持现状，无需改代码。** Room 2.8.4 仅在实体侧声明了默认值时才比较默认值；期望为 `null` 时跳过。去掉 `DEFAULT ''` 反而会让有数据的老用户 `ADD COLUMN NOT NULL` 失败；给实体补默认值则必须升 DB v5 + 重建表。只需修正文档的绝对化表述。 | Room 2.8.4 字节码 `TableInfoKt.equalsCommon` + 本机 SQLite 3.45.1 实验 |
| D-02 | 相机照片通道是**缺陷**，不是有意的设计；修复应新增 `processCameraFile`，不能接 `importFile`（后者是裸拷贝，不解决任何一项） | `PhotoStore.kt:112-129`、`importSingleInner` 对比 |
| D-03 | 测试计数口径：**2026-10-26 当前源码 38 类 / 399 例，脚本与原生 Gradle 全过**。37/386 为照片工作包快照，36/369 为更早基线；29/266、31/299、35/354 是历史快照。 | `[实测]` 测试输出/XML 与源码计数 |
| D-04 | 删除撤销不实施：`deleteUnreferencedFiles` 会物理删除最后一处引用消失的照片，仅存路径无法恢复字节；需先定延迟回收协议 | `OPTIMIZATION_PLAN.md` |
| D-05 | 相机照片仍受 UI 解码有界约束（所有 `AsyncImage` 调用点都有界），AGENTS §4 的「解码尺寸有界」未被相机通道违反；问题是存储策略与备份对称性 | `[代码]` 全量调用点核对 |
| D-06 | 无法解析非空照片引用时不能靠原字符串猜测受保护文件：孤儿扫描删除前整次中止并显式失败；提交后定向回收保留整批文件、仅写脱敏警告，不将成功保存误报失败。候选无法解析仅保留该候选。 | `[代码]` 生产共享接缝/Repository 错误分支；`[实测]` 17 例回归（非完整 Repository 测试） |

## 7. 文档漂移与待裁决

- **测试计数**：`README.md` 历史段与 `PROJECT_OVERVIEW.md` 仍写 29/266；`docs/BUILD_AND_TEST.md` 当前口径已改为 38/399。历史快照保留，但当前口径以本文 §4 为准。
- **状态文档**：`docs/QUICK_RECORD_STATUS_2026-10-22.md` 与证据 JSON 仍写「branch=main、未 commit」，实际已提交到 polish 分支。属历史快照，不改写；以本文 §2 为准。
- **DESIGN_SYSTEM.md:139** 称「不使用描边分层」，与同文 51–55 行、AGENTS §4、README 的「暖白卡片 + 1px 描边 + 阴影」冲突。**现行实现与 AGENTS/README 为准**，该行为过期条目，待用户裁定后修文。
- **文档索引**：`AGENTS.md` §8、`docs/README.md`、`PROJECT_OVERVIEW.md` 已补入本文与 `DEVELOPMENT_LOG.md`。
- **`docs/README.md`** 当前为 DB v4 / v0.6.2 本地待验收包；历史快照不改写。

## 8. 下一步（按项目既定方针：验证能力优先于新增功能）

1. 提交工作区 3 行修复，使 HEAD 可构建（**需用户逐轮确认 commit**）。
2. 接一台设备执行增强后的 `AppDatabaseMigrationTest` 6 例；再做一次真实 SAF 往返。WebDAV 本地导出失败透传已补；网络/HTTP 分类与真实服务器往返仍需独立验收。
3. 修复相机照片通道（`processCameraFile` + 导入占位/回收语义），并修正 `calculateSampleSize` 对 4000×3000 仍解原尺寸的问题。
4. 实现 F03/F04 的导出对称限额与导入预算。
5. 处理流畅模式、深色状态栏、大字体底栏、年度回顾对比度四项 UI 缺口。
6. 补 `MealRepository` / 表单 ViewModel 的集成测试（先抽可注入的时钟/IO 接缝）。

## 9. 本文维护规则

- **每次会话结束前**必须回写：版本/分支/HEAD、验证结果、风险状态、新裁决。
- 只写**当前仍然成立**的事实；历史过程写 `DEVELOPMENT_LOG.md`，不要堆进本文。
- 每条事实标证据等级；没有实际运行过的检查不得写成「通过」。
- 风险关闭时保留 ID，状态改为「已解决（日期 + 证据）」，不要删除行。
- 本文与代码冲突时以代码为准，并立即修正本文。
