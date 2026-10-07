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

> 注:`PROJECT_OVERVIEW.md` 称英文联接「已冗余可删」,但 README 仍建议使用。以 README 为准继续使用较稳妥。

## 4. 已知环境问题(与被测代码无关)

| 问题 | 处理 |
|---|---|
| `testDebugUnitTest` 曾报全体测试类 `ClassNotFoundException at initializationError` | **2026-10-06 复测未复现**:`--rerun` 强制执行 28 套件 / 252 例全过。历史记录的原因是 AGP 的 `bundleDebugClassesToRuntimeJar` 产物(`.../runtime_app_classes_jar/debug/.../classes.jar`)未进测试工作进程运行时类路径。若复发,改用 JDK 直跑(第 5 节)或 `scripts/run-unit-tests.ps1` |
| 找不到 JDK 17 工具链 | 显式 `-Porg.gradle.java.installations.paths="D:/env/jdk-17.0.16+8"` |
| `build-cache-1\*.part 拒绝访问` | 追加 `--no-build-cache`,必要时删 `~/.gradle/caches/build-cache-1` |
| R8 报 `classes.dex 另一个程序正在使用此文件` | `.\gradlew.bat --stop` → 删 `app/build/intermediates/dex/release/` → 重建 |
| `lintDebug` 与 `assembleDebug` 同跑偶发 R8 内部错误 | 分开单独跑、重跑 |
| 改了测试源码但任务 UP-TO-DATE | 手动删 `app/build/tmp/kotlin-classes/debugUnitTest` 后重编 |

Lint 策略(`app/build.gradle.kts`):禁用 `GradleDependency`/`AndroidGradlePluginVersion`/`NewerVersionAvailable`/`ObsoleteSdkInt`;`abortOnError = true`、`warningsAsErrors = false`、`checkDependencies = true`。

## 5. JDK 直跑单元测试(本机唯一可用的自动验证)

```bash
"$JDK17/bin/java.exe" \
  -cp "app/build/intermediates/classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs;\
app/build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar;\
<junit-4.13.2.jar>;<kotlin-stdlib.jar>;<hamcrest-core-1.3.jar>" \
  org.junit.runner.JUnitCore <测试类全限定名...>
```
依赖 jar 位于 `~/.gradle/caches/modules-2/files-2.1/`。**优先用 `scripts/run-unit-tests.ps1`**,
它不需要维护任何类清单;只有走上面这条手工命令时才需要同步 README「已知环境问题」里的类清单(本项目已三次踩此坑)。

## 6. 测试清单(app/src/test,JVM 单元)

当前 **28 个测试套件、252 例**(计数以 `grep -rc "@Test" app/src/test` 为准,下表逐类清单同步维护;
新增/删除测试类必须同步本表)。首选跑法是 **`scripts/run-unit-tests.ps1`**——它从
`app/src/test/java` 自动发现测试类,**不存在类清单,也就不可能漂移**;下文的手工 JUnitCore
命令是备用路径,用它才需要手工同步清单。`testDebugUnitTest` 可在英文联接目录跑通,
早先记录的 `ClassNotFoundException` 未再复现,JDK 直跑作为备用。测试类:

| 测试类 | 覆盖 |
|---|---|
| `data.MealResultTest` | 统一结果模型 |
| `data.MealTextTest` | 文本规范化(含 emoji 代理对穷举) |
| `data.local.ConvertersTest` | 枚举 ↔ 字符串转换、未知值容错 |
| `data.AmountParsingTest` | 金额解析(人均/桌价/千分位/识别不出返回 null) |
| `data.backup.BackupEntryNameSafetyTest` | 备份条目名路径穿越防护 |
| `data.export.CsvExportSafetyTest` | 账本 CSV 转义/BOM/emoji 保真 |
| `data.settings.BackupFreshnessTest` | 备份新鲜度文案:从未/今天/昨天/N 天前/时钟回拨兜底 |
| `data.settings.BudgetPreferenceParseTest` | 预算元→分解析/清除/封顶 |
| `data.webdav.WebDavProbeTest` | WebDAV 连通性检查状态码归类 |
| `data.webdav.WebDavUrlSafetyTest` | WebDAV URL 安全 / 凭据脱敏 |
| `ui.FormattersTest` | 日期/金额/存储量格式化 |
| `ui.add.DuplicateNameCheckTest` | 重复店名归一化与命中 |
| `ui.detail.DetailStatsTest` | 详情页「累计入账」:求和、null≠0、跳过未记账 |
| `ui.home.HomeFilterTest` | 清单筛选/搜索 |
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
| `ui.visit.VisitPrefillTest` | 「照上次再来一份」预填(反射锁定字段集合) |

androidTest:`data.local.AppDatabaseMigrationTest`(仪器测试,**从未运行**,需真机/模拟器)。

## 7. 测试策略(约定)

- 纯逻辑抽成**不读时钟/时区的顶层函数**,`today`/`zone` 作参数(范例 `FootprintAggregation.kt`)。
- **把设计决策锁进测试**(如「花费全识别不出返回 null 而非 0」「排行榜同次数按 id 升序」)。
- 分支逻辑一律抽成 `internal` 顶层纯函数,测试直接调它——**不要在测试里照抄一份逻辑**(`HomeFilterTest` 曾因照抄导致「改了生产代码测试照样绿」,已抽到 `ListSearch.kt` 修掉)。
- 零覆盖缺口:`MealRepository` 与全部 ViewModel(需 Room 测试环境 + 协程调度器,Repository 是具体类需先抽接口)。

## 8. 发布门槛

见 `AGENTS.md` 第 5 节。核心:四项 Gradle 任务全过 + schema 等价性验证 + 远程功能真机跑通 + 文档同步 + 真机核心流程冒烟。
