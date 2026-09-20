# 味笺 v0.4.0 交接文档（新功能对话用）

> 交接日期：2026-09-17
> 项目位置：`C:\Users\2540\Desktop\mealnote`
> 英文构建入口：`C:\Users\2540\mealnote-workspace`（目录联接，指向同一项目，不是副本）
> 当前版本：**v0.4.0（versionCode 13）已暂存、已验证，未提交**
> 数据库：version 3，未改动 schema

## 1. 新对话第一步：读什么、做什么

接手后先读（按顺序）：

1. 本文件 `HANDOFF-0.4.0.md`
2. `CHANGELOG.md` 的 `## 0.4.0` 节（本轮全部变更的权威记录）
3. `README.md`（当前状态 V0.4、构建状态表已更新）
4. `PROJECT_PLAN.md`、`DESIGN_SYSTEM.md`（产品与视觉约束，不可违反）

然后确认工作区状态：

```powershell
git status --short | head -n 50
git diff --cached --stat | tail -n 3
```

预期：40 个文件已暂存（34 modified + 6 new），`apk/` 不在暂存区（被 gitignore 忽略）。
HEAD 仍为 `d4251e9`（v0.4.0 commit 尚未创建，在等用户确认）。

**不要做的事**：未经用户明确要求，不要 commit，不要改版本号，不要动 `apk/` 目录，
不要升级 AGP/Kotlin/Compose 依赖（见 README 开发约定）。

## 2. v0.4.0 包含什么（相对 v0.3.9 的增量）

### 修复

- **编辑页空白**：`EditRestaurantViewModel` / `EditVisitViewModel` 的 `load()` 曾用
  `savedStateHandle.contains(KEY_...)` 判断进程重建，但 `init` 的持久化协程构造期就写回
  空表单，该判断恒 true，正常进入也走“保留用户输入”分支。现复用构造时捕获的
  `restoredFromSavedState`；`setRestaurantId/setRecordId` 补上“重建后 id 相等但未加载
  时继续 load”的条件。
- **相册导入失败（两层）**：
  1. 选择器层：新增 `ui/components/GalleryPicker.kt`（`rememberGalleryPicker`），
     系统 Picker 不可用时降级 `GetContent`/`GetMultipleContents`，四个表单页已接入；
  2. 解码层：`PhotoStore` 解码失败后退回原始字节直拷（32MB 上限）+ 文件头魔数校验，
     失败提示按 `UNREADABLE` / `NOT_AN_IMAGE` 区分。
- **备份丢封面**：`BackupStore.writeArchive` 现在真正打包 `cover{id}.jpg`，
  且只对磁盘存在的文件写名；条目写入加 `try/finally closeEntry`；导入加
  512MB 上限与扩展名预检。
- **其他**：`Formatters` 术语统一为“待探访”；`AddRestaurantViewModel` 磁盘删除移出
  `update` lambda；`ShareCard` 分享按钮 `try/finally` 复位；`runCatchingDb`、
  备份导出/恢复、`ShareImageStore` 透传 `CancellationException`；设置页版本改读
  `BuildConfig`/`BackupFormat`；`Glass.kt` 加显式版本检查消 Lint NewApi 误报；
  `.gitignore` 加 `.mimosa/`。

### 新增

- **运行日志**：`data/log/AppLog.kt`（内存 500 条 + `cacheDir/logs/` 落盘 200KB 滚动，
  只记事件与脱敏来源）。设置页“运行日志”区一键经系统分享导出。
- **流畅模式**：`data/settings/MotionPreference.kt` + `ui/components/FluidMotion.kt`
 （`LocalFluidMotion`）。关闭后玻璃跳过离屏录制与实时模糊、列表取消入场动画、
  转场只做淡入淡出。低内存设备或 API ≤ 29 首次安装默认关闭。提供方在
  `MainActivity`，消费方：`MealNoteApp`（转场）、`MainScaffold`、
  `RestaurantDetailScreen`、`WantListScreen`、`FootprintScreen`、
  `PickRestaurantScreen`。
- **WebDAV 同步（最小实现）**：`data/webdav/WebDavPreference.kt`（配置持久化，
  密码明文存私有目录）+ `WebDavStore.kt`（`HttpURLConnection` PUT/GET，零新依赖，
  只许 https / http 内网段，401/403/404 分文案，下载复用 `BackupStore.restore`
  同一套校验）+ `ui/settings/WebDavSection.kt`（配置表单、上传、下载二次确认）。
  需 `INTERNET` 权限（`AndroidManifest.xml` 已加）。`file_paths.xml` 新增
  `cache_root` 供中转包走 FileProvider。
- **权限变化**：`AndroidManifest.xml` 新增 `INTERNET`。README 隐私描述尚未同步
  （仍写“不联网、不上传”），新功能若涉及文案必须先改 README 再写界面。

### 版本与验证（2026-09-17 实测）

- `app/build.gradle.kts`：versionCode 13，versionName 0.4.0。
- `testDebugUnitTest`：98 用例，0 失败 0 错误 0 跳过。
- `lintDebug`：0 错误。
- `assembleDebug` / `assembleRelease`：成功；release 包 `apksigner verify` v2 通过，
  包信息 `versionCode=13 versionName=0.4.0`，数据库仍 v3。
- 产物（`apk/`，不入库）：`味笺-v0.4.0-debug.apk`（18,965,856 B，
  sha256 `f7e930a0265d29451d0e344aae56098506367efec65e70c5ad1c1129fdfd8926`）、
  `味笺-v0.4.0-release.apk`（2,078,555 B，
  sha256 `dfe104429be9d23a1fb56d093d7bf685de2175018f0ff0b8ca106f9f90232cc`）。

## 3. 新功能开发约定（必须遵守）

1. **构建入口**：一律从英文联接执行，显式传 JDK 17：
   ```powershell
   ./gradlew.bat -p "C:/Users/2540/mealnote-workspace" :app:compileDebugKotlin -Porg.gradle.java.installations.paths="D:/env/jdk-17.0.16+8"
   ```
   中文路径直接跑 Gradle 会乱码；缓存锁加 `--no-build-cache`。
2. **提交**：用户逐轮确认后才 commit；commit 信息沿用 `feat/fix/docs: ……（vX.Y.Z）` 格式，
   正文写清根因与验证。`apk/` 产物只交付不入库。
3. **版本号**：新功能合并后才升版（本轮已是 0.4.0，新功能从 0.4.1 或 0.5.0 起，
   由用户定）。`SettingsScreen` 版本号已改读 `BuildConfig`，升版只改
   `app/build.gradle.kts` 一处。
4. **视觉**：一切 UI 改动必须符合 `DESIGN_SYSTEM.md`（语义色、圆角阶梯、弹簧动效、
   无障碍语义）。流畅模式关闭时的降级外观同样要测。
5. **数据层**：可预期失败走 `MealResult`/`MealError`；先提交事务再删文件；
   文件删除走 `deleteUnreferencedFiles` 守卫；`CancellationException` 必须重抛；
   schema 变更必须配 Migration（当前 v3）。
6. **日志隐私**：`AppLog` 只记事件与脱敏来源，禁止记店名、备注、花费原文、
   完整路径、URI 明文、备份内容。新功能加日志先过这一条。

## 4. 已知未做事项（新功能时可顺手评估）

- 无 `2→3` 迁移仪器测试，无 Compose UI 测试（只有 JVM 单元测试）。
- ~~死代码：`MealRepository.trackPendingPhotos/releasePendingPhotos/pendingCleanupCount`
  无调用者；`EditRestaurantUiState.status` 只写不用。~~
  **（2026-09-20 已删**，暂存区并发语义由独立协议测试继续覆盖，见 CHANGELOG 0.4.0「其他」。**）**
- ~~底部导航用 `clickable + Role.Tab`，屏幕阅读器可能读不出选中态。~~
  **（2026-09-20 已改为 `selectableGroup()` + `selectable(selected)` 配对写法。）**
- WebDAV 未做自动同步、版本管理、失败重试；密码明文存储（与备份同级，已在界面提示）。
  另外 2026-09-20 修复了重定向检查从未生效的问题（详见 CHANGELOG）。
- 旧备份包无封面文件，导入新包到旧版应用时封面条目被忽略（前向兼容未验证真机）。
- `MAX_PICKED_FILE_BYTES`（512MB）、`MAX_RAW_COPY_BYTES`（32MB）为拍脑袋值，
  真机反馈后可调。

## 4.1 工作区现状更新（2026-09-20）

暂存区之外还有一层**未暂存**的加固与重构（已记入 CHANGELOG 0.4.0「其他」）：
WebDAV 重定向防线修复、AppLog 加固、`read == 0` / SAF 列索引修正、
死代码删除、底部导航无障碍、界面组件拆分（新增 `CommonStates.kt` /
`FormSections.kt` / `VerdictSelector.kt`，**这三个文件当前未跟踪**）。
提交 v0.4.0 前需把这三个文件与 `HANDOFF-0.4.0.md` 一并 `git add`，否则提交不可编译。

## 5. 关键文件索引（v0.4.0 新增标 ★）

```text
app/build.gradle.kts（code 13 / 0.4.0）
app/src/main/AndroidManifest.xml（INTERNET）
app/src/main/res/xml/file_paths.xml（cache_root）
app/src/main/java/com/fanji/mealnote/MainActivity.kt（提供 LocalFluidMotion）
app/src/main/java/com/fanji/mealnote/data/log/AppLog.kt ★
app/src/main/java/com/fanji/mealnote/data/settings/MotionPreference.kt ★
app/src/main/java/com/fanji/mealnote/data/webdav/WebDavPreference.kt ★
app/src/main/java/com/fanji/mealnote/data/webdav/WebDavStore.kt ★
app/src/main/java/com/fanji/mealnote/data/PhotoStore.kt（两阶段导入）
app/src/main/java/com/fanji/mealnote/data/backup/BackupStore.kt（封面打包+预检）
app/src/main/java/com/fanji/mealnote/ui/MealNoteApp.kt（转场随流畅模式切换）
app/src/main/java/com/fanji/mealnote/ui/components/GalleryPicker.kt ★
app/src/main/java/com/fanji/mealnote/ui/components/FluidMotion.kt ★
app/src/main/java/com/fanji/mealnote/ui/components/Glass.kt（fluid/enabled 参数）
app/src/main/java/com/fanji/mealnote/ui/components/Motion.kt（staggeredEnter enabled）
app/src/main/java/com/fanji/mealnote/ui/components/Miuix.kt（visualTransformation）
app/src/main/java/com/fanji/mealnote/ui/settings/SettingsViewModel.kt（日志/WebDAV/流畅）
app/src/main/java/com/fanji/mealnote/ui/settings/SettingsScreen.kt（三个新区）
app/src/main/java/com/fanji/mealnote/ui/settings/WebDavSection.kt ★
```

## 6. 给新对话的提示词（可直接复制）

```text
请接手 C:\Users\2540\Desktop\mealnote 的味笺 Android 项目。先读 HANDOFF-0.4.0.md、
CHANGELOG.md 的 0.4.0 节、README.md，再用 git status 确认 40 个文件已暂存、
HEAD 仍为 d4251e9（v0.4.0 未提交）。

当前版本 v0.4.0（code 13）：编辑空白、旧机相册导入、备份丢封面已修；
新增运行日志一键导出、流畅模式、WebDAV 同步最小实现。验证基线：98 单元测试全过、
Lint 0 错误、双包构建成功、release v2 签名通过、数据库 v3。

我要做的新功能是：<在这里描述>。约束：构建一律从 C:\Users\2540\mealnote-workspace
英文入口加 JDK17 参数执行；视觉遵守 DESIGN_SYSTEM.md；数据层走 MealResult、
先事务后删文件、守卫删除、重抛 CancellationException；日志禁隐私内容；
未经我确认不要 commit、不要升版、不要动 apk/、不要升级依赖。
```
