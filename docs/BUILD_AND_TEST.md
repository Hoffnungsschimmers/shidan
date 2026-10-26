# 构建与测试(BUILD & TEST)

> 代码级参考。构建/验证的稳定规则见 `AGENTS.md` 第 3、5 节;本文档补充完整命令、环境坑与测试清单。
> 事实来源:`README.md`、`PROJECT_OVERVIEW.md`、`build.gradle.kts`、`gradle/libs.versions.toml`、`app/src/test`。

---

## 1. 环境要求

| 项 | 值 |
|---|---|
| JDK | 17(本机位于 `D:\env\jdk-17.0.16+8`) |
| Android SDK | compileSdk / targetSdk 36,minSdk 26 |
| Gradle 插件 | AGP 8.13.2 / Kotlin 2.3.21 / KSP 2.3.11 / Hilt 2.57.2 |
| `local.properties` | 需含 `sdk.dir`;可选 `release.storeFile/storePassword/keyAlias/keyPassword`(缺失则产出未签名 release 包,不报错) |

依赖仓库通过阿里云镜像代理(`settings.gradle.kts`),`RepositoriesMode.FAIL_ON_PROJECT_REPOS`(模块内不得再声明仓库)。

## 2. 标准构建命令

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

产物:
- Debug APK → `app/build/outputs/apk/debug/app-debug.apk`
- Release:R8 混淆 + 资源裁剪(`isMinifyEnabled`/`isShrinkResources`),自签名(v2 方案,解包看不到 `META-INF/*.RSA` 属正常)。

## 3. 中文路径与 JDK 定位

项目目录含中文时部分 Gradle 任务乱码 / `ClassNotFoundException`。已建英文目录联接(非副本):
```
C:\Users\2540\mealnote-workspace  ->  C:\Users\2540\Desktop\mealnote
```
建议所有 Gradle 命令从英文入口执行并显式传 JDK 17:
```powershell
.\gradlew.bat -p C:\Users\2540\mealnote-workspace `
  testDebugUnitTest lintDebug assembleDebug `
  -Porg.gradle.java.installations.paths="D:\env\jdk-17.0.16+8"
```
只装了 JDK 21 时会报 `Cannot find a Java installation ... languageVersion=17`,上述参数即解法。

> 注:英文目录联接**仍在用**,不要当冗余删掉:`AGENTS.md` §3 的构建命令以 `C:\Users\2540\mealnote-workspace` 为入口,2026-10-20 实测可用。中文路径直跑会触发部分任务的乱码/类路径问题。

## 4. 已知环境问题(与被测代码无关)

| 问题 | 处理 |
|---|---|
| `testDebugUnitTest` 曾报全体测试类 `ClassNotFoundException at initializationError` | **2026-10-06 复测未复现**(28 套件 / 252 例全过),**2026-10-20 打磨轮再次实跑 29 套件 / 266 例全过**。历史记录的原因是 AGP 的 `bundleDebugClassesToRuntimeJar` 产物(`.../runtime_app_classes_jar/debug/.../classes.jar`)未进测试工作进程运行时类路径。若复发,改用 JDK 直跑(第 5 节)或 `scripts/run-unit-tests.ps1` |
| 找不到 JDK 17 工具链 | 显式 `-Porg.gradle.java.installations.paths="D:/env/jdk-17.0.16+8"` |
| `build-cache-1\*.part 拒绝访问` | 追加 `--no-build-cache`,必要时删 `~/.gradle/caches/build-cache-1` |
| accessors/transforms 的 `metadata.bin` 缺失 | 2026-10-26 实测：停止 daemon 与关闭配置缓存不能解决；检查完整目标路径后将 `~/.gradle/caches/8.14.3` 隔离到同级 `8.14.3-mealnote-quarantine-20261026`，保留回退，不删依赖下载缓存。重建后测试与构建恢复 |
| R8 报 `classes.dex 另一个程序正在使用此文件` | `.\gradlew.bat --stop` → 删 `app/build/intermediates/dex/release/` → 重建 |
| `lintDebug` 与 `assembleDebug` 同跑偶发 R8 内部错误 | 分开单独跑、重跑 |
| 改了测试源码但任务 UP-TO-DATE | 手动删 `app/build/tmp/kotlin-classes/debugUnitTest` 后重编 |

Lint 策略(`app/build.gradle.kts`):禁用 `GradleDependency`/`AndroidGradlePluginVersion`/`NewerVersionAvailable`/`ObsoleteSdkInt`;`abortOnError = true`、`warningsAsErrors = false`、`checkDependencies = true`。

## 5. JDK 直跑单元测试(备用路径;日常用 `testDebugUnitTest` 或 `scripts/run-unit-tests.ps1`)

```bash
"$JDK17/bin/java.exe" \
  -cp "app/build/intermediates/classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs;\
app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar;\
<junit-4.13.2.jar>;<kotlin-stdlib.jar>;<hamcrest-core-1.3.jar>;<kotlinx-coroutines-core-jvm.jar>" \
  org.junit.runner.JUnitCore <测试类全限定名...>
```
依赖 jar 位于 `~/.gradle/caches/modules-2/files-2.1/`。**优先用 `scripts/run-unit-tests.ps1`**,
它不需要维护任何类清单;只有走上面这条手工命令时才需要同步 README「已知环境问题」里的类清单(本项目已三次踩此坑)。

协程原语(`MutableStateFlow`)来自 `kotlinx-coroutines-core-jvm`,不在 app 的 runtime jar 里,直跑必须显式带上。
脚本按 JDK 安装目录的 `release` 文件校验 `JAVA_VERSION=17`:本机 `JAVA_HOME` 指向 JDK 21 时,它不会把 21 当成 17
传给 Gradle(2026-10-20 实测的失败形态:Cannot find a Java installation ... languageVersion=17),而是回退到 `D:\env\jdk-17*`。

## 6. 测试清单(app/src/test,JVM 单元)

当前 **38 个测试套件、399 例**(2026-10-26 WebDAV 错误通道工作包实跑 `OK (399 tests)`;计数以 `scripts/run-unit-tests.ps1` 从源码发现的结果为准;`grep -rc "@Test" app/src/test` 可复核)。首选跑法是 `scripts/run-unit-tests.ps1`——它从 `app/src/test/java` 自动发现测试类,**不存在类清单,也就不可能漂移**;`testDebugUnitTest` 可在英文联接目录跑通,早先记录的 `ClassNotFoundException` 未再复现,JDK 直跑是备用路径,用它才需要手工同步清单。下表是**阅读用**索引,新增/删除测试类时顺手更新即可,不作硬性约定。测试类:

| 测试类 | 覆盖 |
|---|---|
| `data.MealResultTest` | 统一结果模型 |
| `data.MealTextTest` | 文本规范化(含 emoji 代理对穷举) |
| `data.MealErrorMessagesTest` | `MealError` → 用户文案的穷举映射与保存错误分支（15例） |
| `data.PhotoReclamationTest` | 生产照片回收接缝（17例）：真实临时文件、共享/别名引用、坏路径整次中止、目录边界、年龄阈值与取消；非完整 Android PhotoStore/Repository 集成测试 |
| `data.local.ConvertersTest` | 枚举 ↔ 字符串转换、未知值容错 |
| `data.AmountParsingTest` | 金额解析(人均/桌价/千分位/识别不出返回 null) |
| `data.backup.BackupEntryNameSafetyTest` | 备份条目名路径穿越防护 |
| `data.backup.RestoreExecutorTest` | 生产恢复执行器的复制失败/取消、提交前后失败与引用安全回收（21例，依赖替身+真实临时文件） |
| `data.export.CsvExportSafetyTest` | 账本 CSV 转义/BOM/emoji 保真 |
| `data.settings.BackupFreshnessTest` | 备份新鲜度文案:从未/今天/昨天/N 天前/时钟回拨兜底 |
| `data.settings.BudgetPreferenceParseTest` | 预算元→分解析/清除/封顶 |
| `data.webdav.WebDavProbeTest` | WebDAV 连通性检查状态码归类 |
| `data.webdav.WebDavUrlSafetyTest` | WebDAV URL 安全 / 凭据脱敏 |
| `data.webdav.UploadExecutorTest` | 生产上传执行器（13例）：本地导出失败原样返回且不 PUT、完整字节、异常/取消、PUT 前取消与临时包隔离；真实临时文件+导出/上传替身，不是真实 WebDAV/SAF 验收 |
| `ui.FormattersTest` | 日期/金额/存储量格式化 |
| `ui.MealDatesTest` | DatePicker UTC 自然日 ↔ 本地业务日、跨月/年/闰日/DST（8例） |
| `ui.StateTransitionsTest` | `tryTransition` 的 CAS 语义(含多线程「只有一个赢家」) |
| `ui.add.DuplicateNameCheckTest` | 重复店名归一化与命中 |
| `ui.detail.DetailStatsTest` | 详情页「累计入账」:求和、null≠0、跳过未记账 |
| `ui.home.HomeFilterTest` | 清单筛选/搜索、命中摘要、优先级/清空/数据变化/emoji（28例） |
| `ui.home.RestaurantSortTest` | 清单排序(最近更新/添加/名称,稳定兜底) |
| `ui.home.FootprintAggregationTest` | 足迹统计聚合(时区/跨年) |
| `ui.home.FootprintSearchAndMonthTest` | 足迹搜索命中 / 按月入账合计 |
| `ui.home.CountInMonthTest` | 「本月次数」按自然月计数(含时区归属) |
| `ui.home.LedgerAggregationTest` | 账本金额聚合 |
| `ui.home.LedgerSumTest` | 时间线月份小计:求和、null≠0、跳过未记账 |
| `ui.home.VisitCountsTest` | 清单「去过 N 次」按店计数 |
| `ui.home.VerdictFilterTest` | 足迹时间线评价筛选 |
| `ui.home.BudgetProgressTest` | 每月预算进度(超支/剩余/未设) |
| `ui.home.YearlySummaryTest` | 历年汇总(倒序/null 口径) |
| `ui.home.YearReviewTest` | 年度回顾(最常去/评价分布/ledger 优先) |
| `ui.home.RandomScopeFilterTest` | 随机选店候选池过滤 |
| `ui.edit.ReclaimableFormPhotosTest` | 表单照片回收(只回收本次导入未选中) |
| `ui.edit.EditRestaurantTransitionsTest` | 生产保存/换封面/移除/导入 CAS 顺序与竞争、保存快照文件保护（12例；非完整ViewModel测试） |
| `ui.edit.EditVisitLoadingTest` | 生产加载接缝：NULL/已有金额保留、显式自动、恢复草稿（4例） |
| `ui.visit.VisitPrefillTest` | 快记仅餐品/参考花费、迟到预填、最近候选、emoji（13例） |
| `ui.visit.VisitFormLogicTest` | 明确采用参考花费、多人人均换算、部分导入/容量反馈与金额摘要（11例） |
| `ui.visit.VisitPhotoTransitionsTest` | 生产用餐图片 CAS 接缝：提交/导入/加载互斥、容量/重复回调/原图保护、引用查询后实时保护（10例；非完整VM或真实PhotoStore测试） |

androidTest:`data.local.AppDatabaseMigrationTest`：**6 例**，覆盖 1→2、2→3、3→4、1→3、1→4、2→4。样本含餐厅、记录、照片，检查关系、枚举、时间/文本、已有封面/花费及新增 NULL/1 默认值；全部调用生产 Migration。2026-10-26 增强：迁移至 v4 的三条路径在 Helper 校验后再由真实 Room 打开，验证生成 DAO/Converters/嵌套关系，以及省略新列的 SQL 插入默认值。`assembleDebugAndroidTest` 与 4 份 schema 资产 SHA-256 检查已通过，**尚未在设备运行**。

2026-10-22 补齐 `androidTest.assets` → `app/schemas`，仅进入测试 APK；`ksp room.schemaLocation` 只负责导出，不能替代资源接线。无设备时先编译并校验资产：
```powershell
.\gradlew.bat -p C:\Users\2540\mealnote-workspace assembleDebugAndroidTest `
  "-Porg.gradle.java.installations.paths=D:\env\jdk-17.0.16+8"
.\scripts\check-migration-assets.ps1
```
脚本检查每份历史 JSON 在测试 APK 中恰好出现一次，并比对 SHA-256。**脚本需 PowerShell 7(`pwsh`)**：它使用 `[Convert]::ToHexString`，Windows PowerShell 5.1 没有该方法会直接报错。检查失败不会被当作仪器测试通过；接设备后再跑：
```powershell
.\gradlew.bat -p C:\Users\2540\mealnote-workspace connectedDebugAndroidTest `
  "-Porg.gradle.java.installations.paths=D:\env\jdk-17.0.16+8" `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.fanji.mealnote.data.local.AppDatabaseMigrationTest"
```
使用测试设备且不要卸载用户真实数据。测试操作独立的 `migration-test` 数据库；覆盖不包括真实照片文件读写和旧版 APK 覆盖安装。

历史例外：1→2 为封面列添加 `DEFAULT ''`，而 v2 起新建 schema 未声明 SQL 默认值。Room 2.8.4 容忍实体未声明默认值时的差异；本轮用 `PRAGMA table_info` 显式断言已知封面差异及 priceText/amount/personCount 默认值，不宣称所有历史路径与全新建库严格等价，也不修改历史 schema。

> **2026-10-26 已裁决(不要重复调查)**：按 Room 2.8.4 字节码，`TableInfo` 只在实体侧声明了默认值时才比较默认值，期望为 `null` 时跳过，因此该差异**不会**抛 `Migration didn't properly handle`，v1/v2→v4 不会因此崩溃。**不要删除 `DEFAULT ''`**——有数据的老用户执行 `ADD COLUMN NOT NULL` 会直接失败；给实体补默认值则必须升 DB v5 并重建表。详见 `docs/PROJECT_STATUS.md` §6 D-01。

## 7. 测试策略(约定)

- 纯逻辑抽成**不读时钟/时区的顶层函数**,`today`/`zone` 作参数(范例 `FootprintAggregation.kt`)。
- **把设计决策锁进测试**(如「花费全识别不出返回 null 而非 0」「排行榜同次数按 id 升序」)。
- 分支逻辑一律抽成 `internal` 顶层纯函数,测试直接调它——**不要在测试里照抄一份逻辑**(`HomeFilterTest` 曾因照抄导致「改了生产代码测试照样绿」,已抽到 `ListSearch.kt` 修掉)。
- 直接调用链覆盖缺口:`MealRepository` 与完整 ViewModel 生命周期。2026-10-22 新增的封面状态跃迁与恢复执行器已直接调用生产逻辑，但依赖仍为测试替身，不能算完整 Android Room/SAF/PhotoStore 集成覆盖。Repository 可直接用真实 Room + 临时文件做集成测试，不以先抽接口或整体重构为前提；ViewModel 的可控暂停/失败测试按需增加窄依赖接缝，使用已有协程测试调度器。纯函数/CAS 测试不代表完整保存与文件生命周期已验证。

## 8. 发布门槛

见 `AGENTS.md` 第 5 节。核心:四项 Gradle 任务全过 + schema 等价性验证 + 远程功能真机跑通 + 文档同步 + 真机核心流程冒烟。
