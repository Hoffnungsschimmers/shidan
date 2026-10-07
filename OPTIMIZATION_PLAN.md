# 食单全面优化规划（2026-09 轮）

> 本文件是本轮「修复打磨 + 动效/UI/排版优化 + 中度结构重构」的**执行清单与验收门**，
> 与 `AGENTS.md`、`DESIGN_SYSTEM.md` 冲突时以后者为准。
> 范围裁定：遵循 ROADMAP「验证能力优先于新增功能」与 AGENTS §7 —— **本轮不加新功能**。
> 工作区已压着 ≈1237 行未提交、未经设备验证的 v0.5.2+v0.6.0 功能（CSV 导出 / 预算 /
> 清单排序 / 备份新鲜度 / WebDAV 连通性检查 / 重复店名提醒 / 年度回顾 / 单店累计入账），
> 继续堆新功能只会扩大未验证表面积。

## 2026-10-06 状态校准（本轮整理时核过代码）

- 上文的 ≈1237 行已增长为 **37 个文件 `+3429 / −1001` + ≈30 个未跟踪文件**，
  且里面还混着第三块工作：**暖食欲视觉重做已铺满全域**（不止全局层与清单域）。
- **阶段 0 全部完成**（见下表状态列）。**阶段 2/3 有个别条目被顺手做掉**（已在行内标注）。
- 一条改变排期前提的实测：`testDebugUnitTest` **在本机已可用**（`--rerun` 强制实跑
  28 套件 / 252 例全过，`ClassNotFoundException` 未复现）。AGENTS §3/§5 与
  `docs/BUILD_AND_TEST.md` 已按此更正；`scripts/run-unit-tests.ps1` 成为首选跑法，
  「手工同步 README 测试类清单」不再是默认约定 —— **阶段 0 的第 5 条与横切线第 5 条到此收敛**。
- 因此本文件的验收门可照旧，但不必再为「清单漂移」写额外脚本。

## 背景结论（调研快照，2026-09-29）

- 未提交批次本身完整：9 个新生产文件全部有接线、有纯函数、有 JVM 测试，无孤儿代码。
- 真正的系统性风险是三条横切债：无设备验证；ViewModel/MealRepository 零测试；
  测试清单与文档同步机制**第三次**失守（README 列 22 类，实际曾达 29 类 / 250 例）。
- 一个 P0 安全偏差：`mealnote_webdav`（含 WebDAV 账号密码的 SharedPreferences）
  未被系统备份 / 设备迁移排除，凭据会离开设备。
- 代码级问题集中在：错误通道分叉（6 份 `MealError→文案` when 已出现文案漂移）、
  WebDAV 错误吞噬与 3 处 `UNCHECKED_CAST`、设计令牌未收编（≈60 处硬编码尺寸、
  5 处散装弹簧参数、`Type.kt` 缺 `headlineSmall` 导致 ShareCard 静默回落 Roboto）、
  动效违规（导航转场 popEnter 无弹簧、staggeredEnter 恒传 0）、
  无障碍缺口（liveRegion 全项目 0 处、4 处触摸区 <48dp、2 处固定高度含文字容器）。

## 阶段 0 · 规划落盘 + 安全与文档修复

| # | 事项 | 状态 |
|---|---|---|
| 0.1 | 新建本文件 | ✅ |
| 0.2 | `backup_rules.xml` / `data_extraction_rules.xml` 排除 `mealnote_webdav.xml`（WebDAV 账号密码不随系统备份/换机迁移离开设备）；`docs/SECURITY_PRIVACY.md` 记录不变式 | ✅ 已核（两份 XML 的 `cloud-backup` 与 `device-transfer` 都含 `sharedpref/mealnote_webdav.xml`；不变式已写入 SECURITY_PRIVACY §2 **并补进 AGENTS §6**） |
| 0.3 | 删除 `PendingPhotoCleanupConcurrencyTest`（被测「暂存区」机制已从 MealRepository 删除，测试验证的是自己的副本） | ✅ 已删；CHANGELOG 0.4.0 里「由该测试继续覆盖」的失效表述已更正 |
| 0.4 | README / `docs/BUILD_AND_TEST.md` 测试清单补 7 个缺失类、修正计数（删除过时测试后 28 类 / 245 例，随本轮新增再更新） | ✅ 28 类 / **252 例**（实测数字，两处已一致；改用脚本后不再依赖手工清单） |
| 0.5 | 文档漂移修复：`docs/DATA_LAYER.md`（设置偏好三件套→六件套 + `data/export/`）、`docs/ARCHITECTURE.md` 包地图、`docs/UI_LAYER.md` 新功能章节、`Theme.kt` 过时注释、README「我的」页描述 | ✅（`Theme.kt` 阴影注释与 README 三栏/我的页描述本轮补齐） |

> 调研代理曾报「迁移测试包名拼错」，人工复核为**误报**（包声明与目录一致），不改动；
> 其对 `di.DatabaseModule` 的引用随阶段 5 Migrations 迁移一并归位。

## 阶段 1 · 数据层正确性与错误处理（纯逻辑，JVM 可验证）

| # | 事项 | 状态 |
|---|---|---|
| 1.1 | `MealError` 新增 `NetworkFailure`（结果通道，不入库）；`WebDavStore.buildLocalBackup` 透传原始失败、移除 3 处 `UNCHECKED_CAST`、catch 分支整理（`CancellationException` 永远第一）、`testConnection` 不再挪用 `InvalidInput` | ⬜ |
| 1.2 | `BackupStore` 两处 `catch(Exception)→DatabaseFailure` 细化归类（CE 置首） | ⬜ |
| 1.3 | `PhotoStore.deleteOrphanPhotos`：canonical 解析失败的路径**视为受保护**而非剔除保护集；`deleteUnreferencedFiles` 删除失败计数上报 `AppLog`（脱敏）。**改完人工重读删除全部分支** | ⬜ |
| 1.4 | 抽 `MealErrorMessages.kt`：6 份 `MealError→文案` when 收敛为单一纯函数 + 穷举测试；4 份 `toImportMessage` 副本合一 | ⬜ |
| 1.5 | 店名 / 地址入库前纳入 `MealText.normalizeText(limit)` 上限（code point 安全，补截断测试）；照片上限双常量归一 | ⬜ |
| 1.6 | `isAllowedScheme`、`WebDavConfig.remoteUrl()` 补单元测试 | ⬜ |
| 1.7 | `SettingsViewModel` 抽 `runExclusive{}`，消 7 处忙碌保护样板（行为不变） | ⬜ |

## 阶段 2 · 设计令牌与排版

| # | 事项 | 状态 |
|---|---|---|
| 2.1 | 新建 `ui/theme/Dimens.kt`：页边距 20 / 卡内 16~18 / 列表节奏 14 / 顶栏底栏高 / 按钮最小高等语义令牌；收编 ≈60 处硬编码字面量，并拉回违规值（WantList 卡内 12→16、Settings/WebDAV 14/4 节奏、EmptyStateBlock 16→20、PickRestaurant contentPadding、RowDivider 56dp 魔法值转共享常量） | ⬜ |
| 2.2 | 新建 `MealSprings` 弹簧常量表（Press/Enter/Counter/Transitions/Sheet），替换 Motion.kt、Miuix.kt、MainScaffold.kt、VerdictSelector、MealNoteApp 五处散装参数 | 🔶 部分：`MealNoteApp` 两处内联 `spring` 已改取 `MealMotion.settle`；常量表未建，`Miuix.kt`/`MainScaffold.kt`/`VerdictSelector.kt`/`Motion.kt` 仍是散装参数 |
| 2.3 | `Type.kt` 补 `headlineSmall` / `titleSmall`；修 ShareCard 店名静默回落 Roboto；ShareCard 与 YearReviewCard ≈70 行重复骨架抽共享卡壳 | 🔶 前两项 ✅（`Type.kt` 已有 `headlineSmall`/`titleSmall`，业务组件与两张卡都在用）；**共享卡壳未做** |
| 2.4 | 圆角野值治理：底部导航 / 详情底栏 28→阶梯、FAB 20→24；图表微圆角在 DESIGN_SYSTEM 新增「图表微元素豁免」条款 | ⬜ |
| 2.5 | 排版修复：统计柱顶金额 `maxLines=1` 补 `overflow=TextEllipsis`；OverviewCard 四格窄屏挤压加最小宽约束 | ⬜ |

## 阶段 3 · 动效统一（全部带流畅模式降级）

| # | 事项 | 状态 |
|---|---|---|
| 3.1 | `MealNoteApp` 转场对称化：popEnter/popExit 的 slide/fade 从 tween 改弹簧，与 enter 同参数；DESIGN_SYSTEM §七 写明「降级路径允许 fade、Material 自带指示器豁免」 | 🔶 代码侧 ✅（四段转场的位移都用 `MealMotion.settle`）；**DESIGN_SYSTEM 的例外条款未写** —— 这是新增规范而非修漂移，留给用户裁定（见下方「待裁定」） |
| 3.2 | `PhotoViewer` 开合改弹簧淡入淡出；释放定时器的 delay 与动画时长由同一常量派生 | ⬜ |
| 3.3 | 足迹页时间线⇄统计 tab 加 `AnimatedContent`（fade+scale 弹簧）；Add/EditVisit `showMore` 改 `AnimatedVisibility(expandVertically+fadeIn)` + `animateItem` | ⬜ |
| 3.4 | `PickRestaurantScreen` staggeredEnter 传真实 index（现恒 0）并补 `animateItem`；详情页 VisitCard 补 staggeredEnter | ⬜ |
| 3.5 | Settings 忙碌横幅 `AnimatedVisibility` 进出，消除 busy 高度跳变 | ⬜ |

**待裁定（动效红线，需要产品/视觉决策，不属于漂移修复）**：未提交批次在
`MainScaffold.kt`（`tween(120/100/140/80/60)` 等）与 `WantListScreen.kt` 新增了若干
**有终点的短促 fade/alpha 过渡**，与 DESIGN_SYSTEM §七「过渡型动效一律弹簧、禁线性/缓动」
的现行措辞冲突（§七目前只承认「循环型动画」这一个例外）。两条路选一条：
**(A)** 把 §七 改成「主体运动（位移/缩放）必须弹簧，叠加的 alpha 分量与流畅模式降级路径允许
短 fade，Material 自带指示器豁免」——代码已按这个形状写好；
**(B)** 不动规范，把这些 fade 全部换成 `MealMotion.quick`/`settle`。

## 阶段 4 · UI / 无障碍修复

| # | 事项 | 状态 |
|---|---|---|
| 4.1 | `AnimatedCounter` 组件内置 semantics 锁定终值播报；移除 3 处调用方手工包裹 | ⬜ |
| 4.2 | liveRegion 落地：Settings 消息/忙碌横幅、详情页操作结果播报（失败不消失红线不变） | ⬜ |
| 4.3 | 详情页照片 contentDescription 逐张编号「第 N 张，共 M 张」 | ⬜ |
| 4.4 | 触摸区 ≥48dp：SortMenu 触发器、AmountLedgerSection 两处文字按钮、移除照片 IconButton 44→48 | ⬜ |
| 4.5 | 含文字固定高容器改 `heightIn(min=)`：详情页玻璃顶栏 height(56)、PhotoActionTile size(92) | ⬜ |
| 4.6 | 两处 notFound 裸文本改 EmptyStateBlock + 返回出口；PickRestaurant 空态补「去新建」action | ⬜ |
| 4.7 | WantList 粘性头缝隙与硬底色对齐玻璃语言；Settings 存储占用行改 MiuixListRow 语言；深色 MessageBanner 改 surfaceContainerHigh | ⬜ |

## 阶段 5 · 结构拆分（行为不变的搬迁）

| # | 事项 | 状态 |
|---|---|---|
| 5.1 | `FootprintScreen`（1171 行）按卡拆入 `ui/home/stats/`；`SettingsScreen` 按 section 同法拆 | ⬜ |
| 5.2 | 纯函数归位 + 补测：`compactLedgerAmount`→Formatters（与 formatLedgerAmount 规则统一）；`defaultBackupName` 复用 `BackupStore.defaultFileName`；`effectiveAmountMinor` 与详情页 visits 派生并入 `DetailStats`；分享 Intent 构造 2 份合一 | ⬜ |
| 5.3 | `Migrations` 从 `di/DatabaseModule` 迁至 `data/local/Migrations.kt`（androidTest 同步引用） | ⬜ |
| 5.4 | 依赖清理：移除无引用的 espresso / compose-ui-test / ui-test-manifest（androidx-test runner 视迁移测试需要保留） | ⬜ |

## 每阶段验收门（AGENTS §5）

1. 英文路径 `C:\Users\2540\mealnote-workspace` + 显式 JDK 17：
   `scripts/run-unit-tests.ps1`（首选，自动发现测试类；`testDebugUnitTest` 现已可用，二者等价）
   → `lintDebug` → `assembleDebug`；
2. 全部完成后 `assembleRelease`；
3. 无 schema 变更、无 versionCode/versionName 变动、未经用户逐轮确认不 commit。

## 明确延后（下一轮候选）

- **删除撤销**（ROADMAP v0.6.x）：推荐**进程内存撤销**方案草案——删除仍走现有
  `deleteUnreferencedFiles` 时序，仅在 snackbar 存活期内把删除前的实体与文件引用
  暂存 ViewModel 内存，撤销时整体重插；进程死亡即不可撤销。零 schema、不动文件
  生命周期协议。软删列方案（schema v5 + Migration）暂不采用。
- **WebDAV 自动备份**：门槛未解锁——手动同步从未在真实服务器验证（AGENTS §5-3、ROADMAP）。
- **表单 VM 骨架去重**（PersistedForm + 照片导入限流 4 份复制）与 **MealRepository 拆
  BackupRepository**：触及照片文件生命周期等「改后必须人工逐分支重读」的高危代码，
  单独成轮，且先补 ViewModel 测试再动。
- **真机验证清单**（工作区未提交批次已达 `+3429 / −1001`，加上本轮全部改动统一待确认）：CSV 导出/预算/排序/
  年度回顾/重复名提醒的 UI 走查；深色模式；大字体裁切（玻璃顶栏、PhotoActionTile）；
  TalkBack（liveRegion 播报、照片逐张编号、AnimatedCounter 终值）；流畅模式关闭时全部
  新增动效的降级外观；WebDAV 连通性检查在真实服务器跑通一次。
