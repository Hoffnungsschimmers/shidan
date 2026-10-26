# 开发日志（DEVELOPMENT_LOG）

> **追加式记录每一次实质工作。** 最新条目在最上方；只追加、不改写历史（纠错也以新条目说明）。
> 项目「当前状态」以 [`PROJECT_STATUS.md`](PROJECT_STATUS.md) 为准；发版变更以 [`../CHANGELOG.md`](../CHANGELOG.md) 为准。
> 记录规则见 [`../AGENTS.md`](../AGENTS.md) §9。

## 记录模板

```markdown
## YYYY-MM-DD — 一句话任务名

- **执行者：** AI 代理 / 用户
- **起点：** 分支 + HEAD
- **范围：** 只读调研 / 代码改动 / 文档改动 / 构建发布
- **改动文件：** 逐条列出（新增 / 修改 / 删除）
- **验证：** 实际执行的命令 + 原始结果；没跑就写「未运行」
- **未验证：** 明确列出
- **决策 / 发现：** 需要在未来复用的结论
- **遗留 / 下一步：** 待办与阻塞
```

---

## 2026-10-26 — v0.6.2 代码提交完成，回写交付状态

- **执行者：** AI 代理（Claude Code）
- **起点：** `polish/quick-record-experience` @ `e8652d7`（用户本轮授权提交）
- **范围：** 仅提交结果文档同步，不再修改生产代码或 APK。
- **改动文件：** `docs/PROJECT_STATUS.md` 版本/提交/工作区与 R-01 关闭；`docs/RELEASE_0.6.2.md` 实际提交结果；本日志追加。
- **验证：** 精确暂存 23 个源码/测试/版本/文档文件，`git diff --cached --check` 通过，暂存敏感文件名检查未命中；`git commit` → `[polish/quick-record-experience e8652d7] fix: 照片回收与备份上传可靠性修复并打包(v0.6.2)`，`23 files changed, 1117 insertions(+), 88 deletions(-)`；随后 `git status --short` 无输出。`git diff main...HEAD --shortstat` → `89 files changed, 5581 insertions(+), 1079 deletions(-)`。同一代码树 v0.6.2 构建/签名验证详见上一条。
- **未验证：** 设备/真实 SAF/WebDAV 门槛仍未完成；本轮未 push、未创建远程 Release。APK 与签名凭据仍不入库。
- **决策 / 发现：** R-01 已随代码提交解决，不保留「工作区修复尚未提交」的过期当前事实；`e8652d7` 是此 APK 对应的代码提交，后续仅文档同步不改变包内容。
- **遗留 / 下一步：** 提交本条实际结果文档同步并核对最终工作树；由用户安装同签名类型待验收包，先备份且不卸载旧应用；远端 `https://github.com/Hoffnungsschimmers/shidan.git` 未推送。

## 2026-10-26 — v0.6.2 本地待验收安装包产出

- **执行者：** AI 代理（Claude Code）
- **起点：** `polish/quick-record-experience` @ `cb954b1` + 前轮全部未提交改动。
- **范围：** 用户本轮明确授权「打包新版本，并将最新代码提交 git」。按补丁版升至 18/0.6.2，保留包名/DB v4/偏好/依赖，不做远程 Release/push；提交待下一步实际执行。
- **改动文件：** `app/build.gradle.kts` 版本；`CHANGELOG.md` 将 v0.6.1 后打磨记录归入 0.6.2 并保留阶段原文；`README.md`、`PROJECT_OVERVIEW.md`、`docs/README.md`、`docs/PROJECT_STATUS.md` 和本日志同步；新增 `docs/RELEASE_0.6.2.md`；新增未跟踪 `apk/食单-v0.6.2-{release,debug}.apk`，不覆盖/删除旧包。
- **验证：**
  - 统一 `gradlew.bat -p C:\Users\2540\mealnote-workspace "-Porg.gradle.java.installations.paths=D:\env\jdk-17.0.16+8" --console=plain --no-build-cache`；`testDebugUnitTest --rerun-tasks` → `BUILD SUCCESSFUL in 1m 33s`，38 类/399 例 XML 全部 0 跳过/失败/错误；`lintDebug` → `BUILD SUCCESSFUL in 1m 12s`、`No issues found.`；`assembleDebug assembleRelease assembleDebugAndroidTest` → `BUILD SUCCESSFUL in 3m 1s`。
  - `scripts/check-migration-assets.ps1` → 4 份历史 schema `PASS`，SHA-256 一致；未执行六例迁移仪器测试。
  - build-tools 36.0.0 `apksigner verify --verbose --print-certs` → 两包 `Verifies`、v2=true，退出码 0；同类型证书 SHA-256 与 `apk/食单-v0.6.1-{release,debug}.apk` 一致。
  - `aapt dump badging` → 两包 `com.fanji.mealnote` / 18 / 0.6.2 / 食单 / minSdk26 / targetSdk36。
  - 安装包复制后 `Get-FileHash -Algorithm SHA256` → 与构建源一致；Release 2,144,195B / `692407F65DCBD21819A48C153F72F07F828739978892B8CB2405ECDDEBFA68A6`，Debug 19,195,336B / `7C2CE5F52EB5755F56EEDE778005CC73B2A861FCAC07D54B102DF6C480784E32`。
  - `git diff --check` 通过；`git ls-files -- '*.apk' '*.jks' '*.keystore' local.properties '.env*'` 无输出；新增 APK 与签名文件均忽略，没有回显口令。
- **未验证：** `adb devices -l` 空；设备升级/迁移/数据保留/UI/SAF/真实 WebDAV 未验收，因此本次为本地待验收包，不声明达到正式发布门槛。
- **决策 / 发现：** 用户授权新版本与本地 Git 提交，不等于授权 push 或正式远程发布；同包名/证书与递增版本满足覆盖安装条件，但不是设备数据保留实测。提醒先备份、不要卸载旧版、同类型覆盖。
- **遗留 / 下一步：** 校验暂存内容并 commit；实际 Git 结果用新增日志记录。设备/真实服务器门槛与原风险仍保留。

## 2026-10-26 — WebDAV 本地备份失败透传与上传执行器回归

- **执行者：** AI 代理（Claude Code）
- **起点：** `polish/quick-record-experience` @ `cb954b1` + 前轮未提交照片/迁移/语法修复与文档（全部保留）。
- **范围：** 用户授权继续可靠性工作。仅修本地导出 Failure 透传、上传阶段取消与临时包隔离；不改 HTTP/URL 策略、不新增错误类型/UI/依赖/schema/版本，不发远程请求，未 commit/push，未动 `apk/`。
- **改动文件：**
  - 修改 `data/webdav/WebDavStore.kt`：`buildLocalBackup` 返回 `MealResult<UploadSummary>`，复用 `MealResult.map` 原样透传 Failure；每次创建独立 cache 临时包，由执行器管理 finally；取消分支置首；下载三处多余 `UNCHECKED_CAST` 移除（利用既有协变，语义不变）。
  - 新增 `data/webdav/UploadExecutor.kt`：生产上传接缝，导出失败不调用 PUT，导出前/PUT 前后观察取消，异常与 Failure 保留，finally 仅尝试删除本次临时文件。
  - 新增 `data/webdav/UploadExecutorTest.kt`：13 例直接调用生产执行器，真实临时文件+导出/上传替身，覆盖原始 Failure/原因身份、完整字节、导出/上传异常和取消、已有取消、阻塞 IO 后取消观察、重叠操作临时包隔离。
  - 同步 `AGENTS.md`、`README.md`、`OPTIMIZATION_PLAN.md`、`docs/BUILD_AND_TEST.md`、`docs/DATA_LAYER.md`、`docs/PROJECT_STATUS.md` 与本日志；新增 `docs/WEBDAV_ERROR_WORK_STATUS.md` 工作状态。
- **验证（英文 junction + JDK 17）：**
  - 修改前 `& C:\Users\2540\mealnote-workspace\scripts\run-unit-tests.ps1` → `BUILD SUCCESSFUL in 4s`，`OK (386 tests)`。
  - 初次修改后脚本 → `compileDebugUnitTestKotlin FAILED`：两处只抛异常的 lambda `Cannot infer type for type parameter 'T'. Specify it explicitly.`；已显式指定测试泛型 `UploadSummary`，生产代码编译当时通过。
  - 最终 `gradlew.bat -p C:\Users\2540\mealnote-workspace testDebugUnitTest "-Porg.gradle.java.installations.paths=D:\env\jdk-17.0.16+8" --console=plain --no-build-cache` → `BUILD SUCCESSFUL in 9s`；同参数独立 `lintDebug` → `BUILD SUCCESSFUL in 1m 28s`，报告 `No issues found.`；`assembleDebug assembleRelease` → `BUILD SUCCESSFUL in 3m 11s`。
  - 首选脚本最终复核 → `BUILD SUCCESSFUL in 3s`，`OK (399 tests)`，从源码发现 38 类；源码搜索 `Found 399 total occurrences across 38 files.`。上传 XML：`tests="13" skipped="0" failures="0" errors="0"`。
  - `git diff --check` 无输出、退出码 0；schema/版本配置/依赖/`apk/` diff 为空；人工重读导出失败/成功/取消/异常、PUT 与 finally 回收，以及下载原有失败分支。
- **未验证：** 完整 Android FileProvider/ContentResolver/BackupStore/HTTP 调用链、真实服务器上传/下载/恢复、设备/SAF、签名均未运行；未重跑迁移仪器测试（仍无设备）。
- **决策 / 发现：** `[代码]` 本地失败直接沿用已有 MealResult，不必为修此问题扩大到所有 UI 的 NetworkFailure 类型；真实网络错误分类后置。`[实测]` 13 例验证生产编排及临时文件，不代表真实 WebDAV 可用。独立临时包只解决本地互相覆盖/删除，不解决远端单文件最后写入覆盖；取消不能撤销已发出的阻塞 PUT。
- **遗留 / 下一步：** 接测试手机执行迁移/SAF 往返；在设备填写专用 WebDAV 测试配置并获覆盖测试文件授权后做真实服务器闭环。HTTP 错误分类、下载固定临时包、进程死亡留下临时包、备份对称限额/相机通道仍后续处理；不扩大本轮范围。

## 2026-10-26 — 可靠性工作包：构建基线、照片回收保护与迁移测试增强

- **执行者：** AI 代理（Claude Code；迁移测试窄范围协作，主线程审查与统一构建）
- **起点：** `polish/quick-record-experience` @ `cb954b1`；已有 `AddVisitViewModel.kt` 3 行语法修复及未提交文档保留。
- **范围：** 当前树构建基线 → 照片保护修复/回归 → 迁移测试增强。未改版本、schema、依赖、UI 或 WebDAV，未动 `apk/`，未 commit/push。
- **改动文件：**
  - 修改 `data/PhotoReclamation.kt`：共享 canonical 文件身份解析；引用解析失败先于任何删除中止；候选解析失败保留；孤儿磁盘接缝支持显式时间阈值、直接父目录所有权校验。
  - 修改 `data/PhotoStore.kt`：孤儿清理接入生产接缝，失败显式传播至设置页，不返回「没有需要清理」。
  - 修改 `data/MealRepository.kt`：定向回收归一引用身份；提交后回收遇坏引用保留整批并写警告，不把成功保存误报失败；真实删除失败只记录计数；新增既有 `AppLog` 注入。
  - 新增 `data/PhotoReclamationTest.kt`：17 例 JVM 生产接缝回归，真实临时文件+注入解析失败，覆盖共享/别名、坏引用、权限拒绝、根目录/候选解析失败、年龄边界、子目录/目录外身份、可选空路径、实时保护与取消。
  - 修改 `androidTest/data/local/AppDatabaseMigrationTest.kt`：原有 6 路径保留（并非只有 1→2）；三条迁移至 v4 的路径追加真实 Room 打开与 DAO/Converters/嵌套关联检查，省略新列的 SQL 插入证明默认值。
  - 同步 `AGENTS.md`、`README.md`、`OPTIMIZATION_PLAN.md`、`docs/BUILD_AND_TEST.md`、`docs/DATA_LAYER.md`、`docs/PROJECT_STATUS.md` 与本日志；新增 `docs/RELIABILITY_WORK_STATUS.md` 保存工作状态。
- **验证（英文 junction + 显式 JDK 17）：**
  - 修改前 `& C:\Users\2540\mealnote-workspace\scripts\run-unit-tests.ps1` → `BUILD SUCCESSFUL in 1m 49s`，`OK (369 tests)`；随后 `gradlew.bat -p C:\Users\2540\mealnote-workspace lintDebug "-Porg.gradle.java.installations.paths=D:\env\jdk-17.0.16+8" --console=plain --no-build-cache` → `BUILD SUCCESSFUL in 1m 28s`；同参数 `assembleDebug assembleRelease` → `BUILD SUCCESSFUL in 3m 38s`。
  - 修改后同脚本 → `BUILD SUCCESSFUL in 1m 11s`，`OK (386 tests)`；同参数原生 `testDebugUnitTest assembleDebugAndroidTest` → `BUILD SUCCESSFUL in 24s`。
  - 最终树同参数分开执行 `testDebugUnitTest` → `BUILD SUCCESSFUL in 33s`；`lintDebug` → `BUILD SUCCESSFUL in 56s`，报告 `No issues found.`；`assembleDebug assembleRelease assembleDebugAndroidTest` → `BUILD SUCCESSFUL in 2m 16s`。最终 `PhotoReclamationTest` XML：`tests="17" skipped="0" failures="0" errors="0"`。
  - `scripts/check-migration-assets.ps1` → 四份 `PASS assets/com.fanji.mealnote.data.local.AppDatabase/{1,2,3,4}.json`，SHA-256 内容一致；最终 APK 再检查一致。**不代表仪器测试执行**。
  - `Grep @Test app/src/test` → `Found 386 total occurrences across 37 files.`；`git diff --check` → 无输出，退出码 0；schema/依赖/版本配置/`apk/` diff 为空。
  - 删除分支人工重读：换封面、换记录照片、删记录、删店、全量替换、表单回收均仍提交后再守卫；单张所有权/批量失败计数、孤儿年龄/候选边界逐项检查。未顺手重构相机或导入通道。
- **验证失败与恢复：**
  - 初次构建在配置期失败：`Could not read workspace metadata ... kotlin-dsl/accessors/.../metadata.bin`；停 daemon、关闭配置/构建缓存后仍有 47 处 transforms metadata 缺失。核对完整目标后把 `~/.gradle/caches/8.14.3` 隔离到同级 `8.14.3-mealnote-quarantine-20261026` 保留回退，未删依赖下载缓存；重建恢复。
  - 资产检查与后续 Gradle 串联的一次后台任务输出只有资产检查，退出码 0 不足以证明后续任务执行；已更正提前的口头结论，拆开并实际补跑，最终结果以上述原始输出为准。
- **未验证：** `adb devices -l` 空；六例 Android Room 迁移未设备运行；完整 Repository/Android PhotoStore 集成、真实 SAF/备份往返/WebDAV/UI 未验证；未重新运行签名验证。
- **决策 / 发现：** `[代码]` 无法解析引用时不可用原字符串猜测关联文件；孤儿扫描整次显式失败，提交后定向回收则保守保留并脱敏警告。`[实测]` 生产磁盘接缝回归通过不等于 Android 生命周期或迁移通过。迁移六路径已存在，补强验证深度而非堆重复用例；封面历史 DEFAULT 差异沿用 D-01。
- **遗留 / 下一步：** 接测试设备执行增强六路径迁移与真实 SAF 往返；独立一轮处理 WebDAV 错误通道/真实服务器闭环；相机处理/备份限额风险仍未修。所有改动未提交，干净 HEAD 仍包含已知语法错误；commit 需用户逐轮授权。

## 2026-10-26 — 全项目现状摸底，建立状态与日志体系

- **执行者：** AI 代理（Claude Code）
- **起点：** `polish/quick-record-experience` @ `cb954b1` + 工作区 1 个未提交修改
- **范围：** 只读全仓调研（构建/数据层/UI/产品文档/工作区/测试/安全 7 域 + 3 项补洞）；
  建立状态文档；同步测试计数与文档索引。**未改任何生产代码。**
- **改动文件：**
  - 新增 `docs/PROJECT_STATUS.md`（当前状态单一事实来源）
  - 新增 `docs/DEVELOPMENT_LOG.md`（本文件）
  - 修改 `AGENTS.md`（新增 §9 状态与变更记录强制规则；补文档索引；更新已知瓶颈）
  - 修改 `docs/README.md`（文档地图、接手顺序、维护约定）
  - 修改 `docs/BUILD_AND_TEST.md`（测试计数 35/354 → 36/369；补 `MealErrorMessagesTest`；标注 PowerShell 7 依赖）
  - 修改 `README.md`（构建状态补 2026-10-26 实测行）
  - 修改 `PROJECT_OVERVIEW.md`（顶部加「当前状态以 PROJECT_STATUS 为准」指针；§15 文档索引补两项）
- **验证：**
  - `scripts/run-unit-tests.ps1`（英文 junction 路径）→ `BUILD SUCCESSFUL`，`OK (369 tests)`，退出码 0。
  - `git status --porcelain` 复核：改动前工作区只有 `AddVisitViewModel.kt` 一处未提交修改。
  - **未运行**：`lintDebug`、`assembleDebug`、`assembleRelease`、`connectedDebugAndroidTest`。
- **未验证：** 真机 UI / 深色 / 大字体 / TalkBack / 20 秒目标；迁移仪器测试；真实 SAF 与 WebDAV 服务器。
- **决策 / 发现：**
  - **HEAD `cb954b1` 含确定语法错误**（`AddVisitViewModel.kt` 类体残留 `super.onCleared()` 与多余 `}`），
    工作区未提交的 3 行删除即修复。修复提交前 HEAD 不可构建。
  - **相机照片绕过 `PhotoStore` 处理链**：`createCameraTarget()` 让相机直接写 `files/photos`，
    四个 VM 只落路径，无降采样/JPEG88/EXIF/32MB 上限。>32MB 照片可导出但恢复会整包失败。
  - **1→2 迁移 `DEFAULT ''` 不会导致升级崩溃**（Room 2.8.4 字节码核对 + SQLite 实验），保持现状，
    只需修正文档措辞。已记入 `PROJECT_STATUS.md` D-01，后续不要重复调查。
  - 测试口径统一为 **36 类 / 369 例**（本次实跑）；历史文档中的 29/266、31/299、35/354 是旧快照。
  - 其余高风险：备份恢复 OOM / 2GB staging、F03/F04 未实现、流畅模式降级不完整、
    深色与大字体问题、高风险模块零测试、签名密钥单点故障。
- **遗留 / 下一步：**
  1. 提交工作区 3 行修复（需用户确认）。
  2. 接设备跑迁移仪器测试 6 例 + 真实 SAF / WebDAV 冒烟。
  3. 修复相机照片通道；再处理 F03/F04 与 UI 缺口。
