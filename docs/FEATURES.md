# 功能清单(FEATURES)

> 以功能为主线的横切参考,把数据层与界面层串起来。稳定约束见 `AGENTS.md`;分层细节见 `docs/DATA_LAYER.md`、`docs/UI_LAYER.md`。
> 版本节奏与未来规划以 `ROADMAP.md`(第七节最新)为准。

---

## 1. 记录一餐(核心流程,≤20 秒)

**入口**:悬浮按钮 → `EntrySheet` 二选一。
- 「想吃,还没去」→ 新建店铺(仅店名必填)→ 存清单。
- 「已经吃过了」→ 选店(搜索/新建)→ 用餐表单 → 保存进详情。

**用餐表单字段**(按回忆顺序):三级评价(推荐/尚可/不推荐)→ 日期(默认今天)→ 吃了什么(选填)→ 花费(自由文本,选填)→ 入账金额(自动识别)→ 照片(≤9)→ 补充记录。

**关键约束**:一次记录不新增必填项/弹窗;`AddVisitViewModel` 原子防抖;照片先落盘再入库,取消/失败回收(见第 6 节)。

## 2. 清单(餐厅维度)

- 分段筛选:待探访 / 已用餐 / 全部(`HomeFilter`);搜索覆盖**店名 + 地址 + 餐品名**(不含备注);筛选标签数字基于全量不抖动。
- **排序(已落地)**:清单工具栏「排序:X」下拉可切 最近更新 / 最近添加 / 按名称(`RestaurantSort`,`ListSortPreference` 持久化)。纯逻辑 `ListSearch.kt#sortedForList`(名称用 `Collator(zh)`,时间相等按 id 兜底稳定)。测试 `RestaurantSortTest`(4)。
- **去过 N 次(已落地)**:清单里「已用餐」的店卡右侧显示「去过 N 次」。纯逻辑 `ListSearch.kt#visitCountsByRestaurant`(与餐品索引共用同一记录流,不增查询)。测试 `VisitCountsTest`(2)。
- 「照上次再来一份」:详情页 `latestRecordId` → `AddVisit(copyFrom)`,预填 verdict/dishes/priceText(校验餐厅归属)。
- **重复店名提醒(已落地)**:新建店铺输入已存在的店名时,名称卡下方显示非阻塞提示「清单里已经有「X」了」。纯逻辑 `ui/add/DuplicateNameCheck.kt#findDuplicateName`(归一化=去空白+压连续空白+小写;不删内部空格以免误判分店)。**只提醒不拦截**(同名分店合法)。`AddRestaurantViewModel.duplicateName` 由现有列表+当前输入派生。测试 `DuplicateNameCheckTest`(6)。
- 代码:`HomeViewModel`、`ListSearch.kt`、`WantListScreen.kt`。

## 3. 足迹(用餐记录维度)

- 分段:**时间线**(按月分组倒序)⇄ **统计**。搜索覆盖店名/地址/餐品/花费/备注。
- **时间线评价筛选(已落地)**:时间线工具栏「全部/推荐/尚可/不推荐」分段,仅过滤时间线、不影响统计口径(统计恒基于全量)。纯逻辑 `FootprintAggregation.kt#filterByVerdict`(`VerdictFilter`)。测试 `VerdictFilterTest`(5)。
- **月份分组小计(已落地)**:时间线每个月份标题右侧显示该月「N 次 · ¥X」(入账合计,随搜索/评价筛选联动)。纯逻辑 `FootprintAggregation.kt#ledgerSumOrNull`。测试 `LedgerSumTest`(2)。
- 统计:今年次数 / 去过的店 / 累计次数、花费**估算**、最近 12 月柱状图、评价分布、常去 Top5。概览含**本月次数**(本月/今年/去过/累计,`countInMonth`,`CountInMonthTest`)。
- **花费估算口径**:`priceText` 取文本最大数字(`parseEstimatedAmount`);全识别不出显示「还没有能识别的金额」而非 `¥0`;界面标注「估算」并显示可识别比例。
- 代码:`FootprintViewModel`、`FootprintAggregation.kt`、`FootprintScreen.kt`。

## 4. 记账 / 账本(v0.5 核心,schema v3→v4)

**设计原则**:记账是副产品,不给记录流程增加步骤。自由文本 `priceText` 不动,加一层结构化金额 `amountMinorUnits`(整数分,可空)。

- **录入**:花费框保持自由文本;失焦/保存时 `parseLedgerAmountMinor(text, personCount)` 自动识别预填;表单下方「金额确认条」(`AmountLedgerSection`),识别错点一下改,识别不出手动输入。三态:自动识别 → 手动覆盖(`amountOverridden`)→ 恢复自动。
- **金额解析规则**(`AmountText.kt`,测试锁死):去千分位逗号;**仅「人均」是单价需 × 人数,其余数字即整桌总额不乘人数**(「3个人吃了240」→ 24000 分);`BigDecimal` ×100 HALF_UP;上限 ¥100 万;识别不出返回 null(账本宁可缺条目不要错条目)。
- **账本口径**(`LedgerAggregation.kt`):本月/本年入账、平均每笔、最近 12 月金额柱、花钱最多 Top5(按累计金额)、覆盖说明(N 条已入账 / M 条写文字未入账)。**绝不从估算凑数**;一笔没有返回 `null` 而非 0。
- **餐厅详情累计花费(已落地)**:详情页「用餐记录」小标题在有入账时追加「· 累计 ¥X」。纯逻辑 `ui/detail/DetailStats.kt#totalLedgerMinor`(求和,null≠0)。测试 `DetailStatsTest`(4)。
- **红线**:金额为空是合法状态(区分「没花钱」与「没记金额」);金额一律整数分;金额禁止进 AppLog。
- **CSV 导出(已落地)**:设置→备份与恢复→「导出账本 CSV」,经 SAF 写 `text/csv`(无存储权限)。纯逻辑 `data/export/LedgerCsv.kt#buildLedgerCsv`:UTF-8 **BOM**(防 Excel 中文乱码)+ RFC4180 转义(逗号/引号/换行,文本列强制加引号、引号翻倍)+ emoji 代理对靠 UTF-8 保真不截断;列为 日期/店名/地址/评价/餐品/花费原文/入账金额(元)/就餐人数/备注,按用餐时间倒序,孤立记录跳过。框架侧 `LedgerCsvStore`。测试 `CsvExportSafetyTest`(13 例)。
- **每月预算(已落地)**:设置→账本→「每月预算」输入元(纯逻辑 `data/settings/BudgetPreference.kt#parseBudgetYuanToMinor`,存整数分,`SharedPreferences`)。足迹·账本卡在设了预算时显示本月进度条:`monthlyBudgetProgress`(`LedgerAggregation.kt`)算 本月已入账/预算 的比例与剩余,超支进度条变红并显示「已超预算 ¥X」,接近(≥85%)变橙。**仅应用内展示,不做系统通知**(避免 `POST_NOTIFICATIONS` 权限)。测试 `BudgetPreferenceParseTest`(5)+ `BudgetProgressTest`(5)。
- **历年汇总(已落地)**:足迹·统计页在有跨年数据(≥2 年)时显示「历年」卡,每年一行列出 次数 + 入账总额(无入账则退化估算),按年份倒序。纯逻辑 `FootprintAggregation.kt#yearlySummaries`(`null` 区分「没记」与「没花」)。测试 `YearlySummaryTest`(3)。

## 5. 随机选店(v0.5.1,按评价范围)

- **候选池重定义**(`RandomPick.kt`):来自全量店铺(不受分段/搜索影响);每店「当前评价」= 最近一次用餐评价(`latestVisitByRestaurant`,自己比时间戳不依赖上游排序)。
- **三档范围**(`RandomScope`,`RandomPreference` 持久化):仅推荐 / 推荐+尚可 / 全部;独立开关「待探访参与随机」(默认开);可选「排除最近 N 天吃过」(0/7/14/30,按自然日)。
- 候选为空隐藏按钮;结果对话框显示范围说明;「换一家」保证不重复上一家。
- 代码:`buildRandomCandidatePool`、设置项在 `SettingsScreen`。

## 6. 照片管理

- 存储:私有目录 `files/photos/`,DB 只存绝对路径;导入经 `PhotoStore`(降采样最长边 2048px,JPEG q88,EXIF 方向校正,文件头魔数校验);相册选择器带降级(`GalleryPicker`)。
- **生命周期不变式**(最易丢数据,见 `docs/SECURITY_PRIVACY.md` §3):先提交事务再删文件;文件只在无任何 DB 行引用时删(封面与用餐照片可能共享文件);编辑只回收「本次导入且未选中」(`reclaimableFormPhotos`);相机孤儿用 `rememberSaveable` 防护。
- 查看:全屏 `PhotoViewer`(滑动/双击缩放/拖动平移)。
- 清理:设置页「清理无用文件」(`cleanupOrphanPhotos`,跳过 10 分钟内新文件)。

## 7. 分享卡片

一次用餐渲染成竖版 3:4 图片交系统分享。`ShareCardSheet` 先预览后分享,`GraphicsLayer.toImageBitmap` 导出 PNG,经 `ShareImageStore`(`cacheDir/shared/`,24h 保留)+ FileProvider content URI 分享。代码:`ShareCard.kt`、`RestaurantDetailViewModel.shareCard`。

- **年度回顾卡片(已落地)**:足迹·统计页「历年」卡点某一年 → `YearReviewSheet`(复用同一 GraphicsLayer 录制导出骨架)渲染该年 次数/花费/最常去的店/评价分布 的竖版分享图。数据 `FootprintAggregation.kt#yearReviews`→`YearReviewCardData`(最常去按次数倒序+id 稳定,ledger 优先估算兜底)。分享经 `FootprintViewModel.shareYearReview`→`pendingShareUri`→系统 `ACTION_SEND`。测试 `YearReviewTest`(3)。

## 8. 备份与恢复(ZIP)

- **格式**:`mealnote-backup-yyyyMMdd-HHmm.zip` = `manifest.json + restaurants/dining_records/photos.json + photos/<name>.jpg`(`BackupFormat.FORMAT_VERSION=1`)。照片稳定命名 `record{id}_{sortOrder}.jpg`、封面 `cover{restaurantId}.jpg`。
- **导出/导入**:走 SAF(无存储权限);导入为全量替换单事务;遗留字段不导出;金额空不写键;新增字段用 `optXxx` 读不升版本。
- **安全**(`BackupStore.kt`):格式版本校验(拒更高版本)+ 总量 2GB/单条目 32MB/单图 32MB/预检 512MB + 路径穿越阻断 + ZIP 炸弹防护。旧备份无封面导入时忽略封面条目。
- 代码:`BackupStore.kt`、`SettingsViewModel`(export/requestImport/confirmImport)。
- **备份新鲜度提示(已落地)**:备份分组标题显示「今天已备份 / N 天前备份 / 从未备份」。成功导出或 WebDAV 上传后记录时间(`BackupStatePreference`);纯逻辑 `backupFreshnessLabel`(按自然日,时钟回拨兜底为今天)。测试 `BackupFreshnessTest`(5)。

## 9. WebDAV 同步(v0.4,最小实现)

- 仅 PUT/GET 单文件,上传即覆盖,无版本/增量/自动同步;复用 `BackupStore.export/restore`(下载复用全套导入防护)。
- 配置(`WebDavPreference`,独立 `mealnote_webdav`):服务器地址(必填)/账号/密码/远端文件名。密码明文存私有目录(界面提示)。
- **安全**(`WebDavStore.kt`):仅 https(http 限局域网);不自动跟随重定向(防 Basic 凭证外泄);`redactUrlCredentials` 脱敏回显;失败提示常驻可见带回显地址。
- **连通性检查(已落地)**:设置→服务器同步→「测试连接」对远端文件发 HEAD,归类为 已有备份/尚无备份/账号密码错误/需跳转/连不上 五态并给出可读提示,不改动数据。纯逻辑 `WebDavStore.kt#webDavProbeFor`(状态码→`WebDavProbe`)。测试 `WebDavProbeTest`(5)。真机/真服务器仍需实测(网络无法离线验证)。
- **红线**(ROADMAP §8):发布前必须在真实服务器跑通一次(v0.4.0 只做单测就发布,用户首用即撞 404)。
- 代码:`WebDavStore.kt`、`WebDavSection.kt`。

## 10. 运行日志(v0.4,AppLog)

- 内存环形缓冲 500 行 + 落盘 `cacheDir/logs/mealnote.log`(200KB 滚动),`Mutex` 串行;设置页一键导出(`Intent.ACTION_SEND`)。
- **脱敏**:只记事件与原因,不记店名/地址/餐品/备注/花费/路径/URI/凭据;异常只记类名 + 前 200 字。
- 代码:`AppLog.kt`、`SettingsViewModel.shareLog`。

## 11. 流畅模式(v0.4)

- `MotionPreference`(默认按设备:`isLowRamDevice` 或 API≤29 默认关)。关闭时:导航转场仅淡入淡出、`staggeredEnter` 跳过、`skeletonShimmer` 转静态占位、`EmptyStateBlock` 停止浮动、`imageReveal` 不做显影、`GlassSurface` 跳过实时模糊(离屏录制+全屏模糊是低端机卡顿最大来源)。
- 代码:`FluidMotion.kt`、`Glass.kt`、`Motion.kt`(`MealMotion` 四档弹簧)、`MealNoteApp.kt`。

## 12. 外观 / 深色模式

- `ThemePreference`:跟随系统 / 浅色 / 深色,MainActivity 顶层 `resolveDark()` 切换,包住全部弹窗。设置页分段控件。
- **纯白底 · 暖白浮层 · 图为主**视觉(继承 MIUIx/HyperOS 的浮层与弹簧语言):纯白页面底、暖白卡片 `#FBF8F4` + 1px 描边 + 大圆角、柔和**暖棕**投影、弹簧动效、液态玻璃(底部导航/详情顶栏与操作栏,着色同改暖白/暖炭)。深浅两套的卡片与照片都是暖向,色板与对比度实测表见 `DESIGN_SYSTEM.md` 第三节。

## 13. 存储管理与数据清理

- 设置页:照片存储用量(`photoDirectorySizeBytes`)、清理无用文件(孤儿照片)、清空全部数据(`replaceAllData(空集合)`,二次确认)。
- 代码:`SettingsViewModel`(refreshStats/cleanupOrphans/confirmClearData)。

---

## 功能 ↔ 主要代码对照

| 功能 | 数据层 | 界面层 |
|---|---|---|
| 记录一餐 | `MealRepository.addDiningRecord` | `AddVisitViewModel`/`AddVisitScreen` |
| 清单筛选搜索 | `observeRestaurants/observeAllDiningRecords` | `HomeViewModel`+`ListSearch` |
| 足迹统计 | 同上 | `FootprintViewModel`+`FootprintAggregation` |
| 记账 | `amountMinorUnits`+`AmountText` | `AmountLedgerSection`+`LedgerAggregation` |
| 随机选店 | `RandomPreference` | `RandomPick`+`HomeViewModel` |
| 照片 | `PhotoStore` | `PhotoViewer`/`GalleryPicker`/`MealPhotoSection` |
| 分享卡片 | `ShareImageStore` | `ShareCard`+`RestaurantDetailViewModel` |
| 备份恢复 | `BackupStore` | `SettingsViewModel` |
| WebDAV | `WebDavStore`+`WebDavPreference` | `WebDavSection` |
| 运行日志 | `AppLog` | `SettingsScreen` |
| 流畅/主题 | `MotionPreference`/`ThemePreference` | `MainActivity`+`SettingsScreen` |
