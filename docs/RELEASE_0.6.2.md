# 食单 v0.6.2

## 交付

- 日期：2026-10-26；versionCode **18**；应用名「食单」；包名 `com.fanji.mealnote`；数据库 **v4**（无 schema 变更）。
- 用户授权：打包新版本并提交最新代码。分支 `polish/quick-record-experience`；起点 `cb954b1` + 前轮未提交修改。
- **本地待验收安装包**，不创建远程 Release，不 push。真实设备升级/迁移/UI/SAF/WebDAV 未验收，不能视为正式发布门槛已满足。
- 内容：v0.6.1 后快记/竞态/恢复打磨、照片引用保护、WebDAV 本地备份失败透传、语法修复与测试/文档更新；详细见 CHANGELOG 0.6.2。

| 包 | 本机相对路径 | 字节数 | SHA-256 |
|---|---|---:|---|
| Release（通常安装这个） | `apk/食单-v0.6.2-release.apk` | 2,144,195 | `692407F65DCBD21819A48C153F72F07F828739978892B8CB2405ECDDEBFA68A6` |
| Debug | `apk/食单-v0.6.2-debug.apk` | 19,195,336 | `7C2CE5F52EB5755F56EEDE778005CC73B2A861FCAC07D54B102DF6C480784E32` |

两份交付文件与本轮构建源 APK 的 SHA-256 一致。历史 APK 未覆盖或删除；两份新 APK 被 gitignore 排除，不入 Git。

## 实际验证

统一英文 junction 工作路径、JDK 17、`--no-build-cache --console=plain`：

- `testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL in 1m 33s**；38 类 / 399 例，XML 全部 0 跳过/失败/错误。
- `lintDebug` → **BUILD SUCCESSFUL in 1m 12s**，报告 **No issues found.**
- `assembleDebug assembleRelease assembleDebugAndroidTest` → **BUILD SUCCESSFUL in 3m 1s**。
- `scripts/check-migration-assets.ps1` → 四份历史 schema **PASS** 且 SHA-256 一致；仅编译/资产检查，**未执行迁移仪器测试**。
- `apksigner verify --verbose --print-certs` → 两包均 **Verifies**、**v2=true**，退出码 0；证书 SHA-256 分别与 v0.6.1 同类型包一致。
- `aapt dump badging` → 两包均 `com.fanji.mealnote` / `versionCode=18` / `versionName=0.6.2` / label 食单 / minSdk 26 / targetSdk 36。
- `git diff --check` 通过；`local.properties`、签名密钥库、APK 未跟踪。没有读取或输出签名口令。

证书 SHA-256（证书指纹不是口令）：
- Release：`9cf9cd69b9a736a8e8042cf381f8ad7d248b6375e6ac328438bcf10799abd228`
- Debug：`be5b09045ec8318901d4451968685988b9c4fbfcb1930ba3ce118dd01e8e103c`

## 安装与限制

- **更新前先导出备份，不要卸载旧版。** 同类型证书与包名一致且 versionCode 增加，具备覆盖更新条件；实际设备升级与数据保留仍未测试，不作保证。
- Debug 与 Release 不能互相覆盖；请安装与当前应用相同类型的包。
- 相机照片处理、备份对称限额/内存压力、网络错误分类等风险仍见 PROJECT_STATUS，不因为出包关闭。
- 未连接设备、未访问真实服务器、未替用户安装或恢复数据。
- 代码与配套文档已提交：`e8652d7` — `fix: 照片回收与备份上传可靠性修复并打包(v0.6.2)`；23 文件。提交后工作树干净，后续仅同步实际提交结果文档。
- 分支 `polish/quick-record-experience`；origin 为 `https://github.com/Hoffnungsschimmers/shidan.git`，**本轮未 push**。
