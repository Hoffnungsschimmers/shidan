# 项目评估状态（2026-10-22）

## 目标与边界
- 以当前未提交工作区理解项目，核查不足，交付有证据、优先级和验收条件的发展准备报告。
- 初轮只评估；用户要求「补齐」后，落实 androidTest schema 资产接线、六条迁移路径测试、可重复资产检查脚本及必要文档。
- 不修改业务实现、生产 Migration、schema JSON、依赖、数据库/应用版本或 apk/；未 commit/push。
- 起点 main / e3c0f3a，已有工作区改动全部保留；origin 为 https://github.com/Hoffnungsschimmers/shidan.git。

## 结论与决策
- 四域评估与四域反证均完成；以当前源码及本机依赖为准，撤销 Coil2.7 有界封面必然原图解码、44dp 视觉尺寸必然触控不足等误报。
- 下一轮先验证生产保存/文件生命周期与备份恢复，不先加功能或整体抽接口。
- 静态路径确认不等于用户设备已发生；编译/资产检查不等于仪器测试通过。
- 1→2 历史封面 DEFAULT 差异真实存在，Room2.8.4 对实体未声明默认值时容忍；测试显式锁定该例外，不改历史 schema。

## 已交付文件
- docs/PROJECT_AUDIT_2026-10-22.md：系统地图、F01–F15 场景与验收、条件风险、UI 复核、下一轮 A–D 批次及设备清单。
- docs/PROJECT_AUDIT_EVIDENCE_2026-10-22.json：基线、实际检查、失败修正和未执行项。
- app/build.gradle.kts：历史 schema 仅接入 androidTest assets。
- app/src/androidTest/java/com/fanji/mealnote/data/local/AppDatabaseMigrationTest.kt：1→2、2→3、3→4、1→3、1→4、2→4 共六例；样本关系、已有值、默认值及外键断言。
- scripts/check-migration-assets.ps1：每份历史 JSON 恰好一份、SHA-256 与源码一致。
- README、PROJECT_OVERVIEW、BUILD_AND_TEST、CHANGELOG 同步入口/验收；OPTIMIZATION_PLAN 纠正删除撤销仅凭实体/路径无法恢复图片字节的前提，未实施撤销。

## 实际验证
- 自动发现脚本：29 类，OK (266 tests)，退出 0。
- 原生 testDebugUnitTest --rerun-tasks：XML 29 套件 / 266 实跑，0 失败、0 错误、0 跳过；仅既有 Sort 弃用警告。
- 补齐后 testDebugUnitTest + assembleDebugAndroidTest：成功；测试 Kotlin、schema 资产合并和测试 APK 打包实际执行，JVM 任务 UP-TO-DATE，未虚称再次实跑。
- 资产脚本负例检测旧 APK 缺 1.json；重建后四份 1/2/3/4.json 唯一且哈希一致。debug 应用 APK 无这些资产。
- 最终 lintDebug：No issues found，AndroidTest 分析实际执行，退出 0。
- 最终 assembleDebug/assembleRelease：成功，101 任务中 5 执行、96 UP-TO-DATE；Release 编译/R8/打包未强制重跑。
- Release APK v2 签名通过；首次 JDK21 内存提交错误 1455 后改显式 JDK17/有界堆成功。本轮产生的 hs_err 日志检查后移入 job tmp，未留仓库。
- 内存 SQLite：2→3、3→4 结构/默认值/索引/外键等价、样本保留；1→2 记录历史默认值例外。极大 ID 耗尽自增的合成机制已复现，未碰用户数据库。
- 最终构建首次调用因 PowerShell 对未整体引用的 -Porg.gradle.java.installations.paths 参数解析失败；引用完整参数后成功，不是代码编译失败。
- 交付检查通过：证据 JSON 可解析、六例源码计数匹配、报告链接存在、Debug/Release APK 均无迁移 schema 资产、git diff --check 无错误；受保护路径无改动，敏感文件名索引检查无条目（非全历史秘密扫描）。
- 可选网页发布因当前认证模式不支持 Artifact 被拒绝，未生成 URL、未重试或修改登录配置；持久交付为 docs/ 内的报告及证据。

## 当前修复批次（用户要求「处理」）
- 范围：F01 保存期间封面变更/回收、F02 恢复图片复制失败保留原数据；保持版本、依赖、schema 与 apk/ 不变，不 commit/push。
- F01：保存/换封面/移除/导入共用原子状态跃迁；保存中和已保存拒绝变更，迟到相机/导入只清理未选中且非数据库来源的新图。
- F02：复制任一失败在替换前中止；回收只走 Repository 的全表引用守卫，不能把事务提交后返回 Failure 当作文件未被引用。
- 已增加 F01 生产状态接缝的回归、暂停保存与真实临时文件校验、并发竞争测试；F02 实施与故障注入由独立范围完成。
- 验证完成：脚本和原生31类/299例通过，Lint通过；Debug/Release构建通过且本次Release编译/R8/打包实际执行，v2签名通过；四份迁移资产哈希通过。新增F01 12例/F02 21例；完整记录以 PROJECT_FIX_STATUS_2026-10-22.md 及对应证据JSON为准。删除/回收全部分支已人工复读；平台端到端仍未执行。

## 剩余验收与失败方案
- adb 无设备，本机无模拟器/AVD；六例迁移仪器测试未运行。连接测试设备后按 BUILD_AND_TEST 第 6 节运行指定测试类，不卸载真实数据。
- Repository/完整 ViewModel 编排、真实照片文件、SAF 完整往返、真实 WebDAV、UI/大字体/TalkBack/性能与 20 秒录入仍未验证。
- F01–F15 为后续修复清单，本轮没有修复这些业务问题；生产保存/恢复优先。
- 构建失败先分环境与代码，不机械删缓存/已有文件；证据不足降为待验证风险。恢复失败测试必须证明旧库与旧图片保持，不以摘要计数代替完整往返。
