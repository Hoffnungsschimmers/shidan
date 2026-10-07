# 界面层(UI LAYER)

> 代码级参考。视觉/交互权威规范见 `DESIGN_SYSTEM.md`;导航与状态模式见 `docs/ARCHITECTURE.md`。
> 包路径 `com.fanji.mealnote.ui`。分四部分:主壳与首页域、表单/详情域、组件体系、主题/材质/动效。

---

## 一、主壳与底部导航(home/MainScaffold.kt)

`MainTab{ WANT("清单",Bookmark), FOOTPRINT("足迹",Restaurant), MINE("我的",Person) }`。

- **不用 `Scaffold.bottomBar`**:玻璃要求内容从导航栏下方穿过(否则模糊的是空白)。改为把 `GlassNavBar` 浮在内容之上,各标签页自行用 `contentPadding` 预留底部空间(`MainContentBottomPadding = 64dp + 40dp`)。
- 内容层注册为玻璃背景源 `glassBackdropSource(backdrop, enabled=fluid)` + `statusBarsPadding()`;`AnimatedContent` 切换三标签(流畅开:淡入+0.985 scaleIn(`MealMotion.settle`);关:仅 120ms 淡入淡出)。
- `GlassNavBar`:`GlassSurface`(topStart/topEnd 30dp,blurRadius 34dp)。选中药丸**在三个槽之间滑动**(`BoxWithConstraints` + `animateDpAsState` + `offset { }`  lambda 重载),而不是每个槽各自淡入淡出;药丸是纯装饰 → `clearAndSetSemantics {}`。Row `selectableGroup()`,每项 `NavItem` 用 `selectable(selected, role=Tab)` 且**已选中时 onClick 不重复触发**;选中项图标 `pop()` 弹跳 1.12x + 主色 + 标签字重升到 Bold(颜色之外的第二个区分维度,大字体/深色下仍分得出),切换时一次 `rememberSelectionHaptic` 触觉。
- `FloatingAddButton`:58dp 圆角方形(`shapes.medium`=20dp,与卡片圆角语言一致),`primary → primaryDeep` 渐变 + 同色系 `softShadow(14dp)`(按下收到 6dp),`pressScale(0.9f)` + 加号按下旋转 90°(提示「有东西会展开」);用 `AnimatedVisibility` 缩放进出而非 `if` 直接增删(主操作按钮凭空消失会被读成「我按错了什么」);`contentDescription="新增记录"`。
- `EntrySheet`(`ModalBottomSheet`,skipPartiallyExpanded,32dp 顶圆角,`tonalElevation=0`):二选一。「已经吃过了」(核心高频动作)置顶且主视觉强调(暖光渐变底 + 实心渐变图标砖 + 右侧箭头,`titleLarge`)→`onStartVisit`;「想吃,还没去」为扁平 `surfaceVariant` 底(`titleMedium`)→`onAddRestaurant`。两项用 `staggeredEnter(0/1)` 错位进场。两选项互斥(一家店要么没去要么去过),故用面板一次选清而非表单里放状态开关。

## 二、首页域逻辑(home/)

首页域的算法全部抽成 `internal` 顶层纯函数(不读时钟,`today`/`zone` 作参数),ViewModel 只「取当前时钟 + 组装状态」。这是可测试性约定(见 `docs/BUILD_AND_TEST.md` 第 7 节)。

### 2.1 清单页(HomeViewModel + ListSearch)

`HomeViewModel`(`@HiltViewModel`,注入 `MealRepository` + `RandomPreference`)。**两级数据流(性能)**:上游 `data = combine(observeRestaurants, observeAllDiningRecords, randomPreference.config)` 算出全量店铺/餐品索引/计数/随机池(与搜索无关);再 `combine(data, _query, _filter)` → `HomeUiState`,`stateIn(WhileSubscribed(5000))`。这样搜索每敲一字只重新过滤列表,不再重算随机池与餐品索引。

`HomeFilter{ WANT_TO_EAT("待探访"), EATEN("已用餐"), ALL("全部") }`。`HomeUiState` 关键:
- `restaurants` = 筛选+搜索后的结果;`*Count` 始终基于**全量数据**(筛选标签数字不随筛选抖动)。
- `latestVerdicts`(restaurantId → **最近一次**评价):卡片用它回答「这家上次吃得怎么样」——这一眼决定要不要再去,而「待探访/已用餐」把去过 8 次和去过 1 次的店画成一样。与随机池共用同一份 `latestVisitByRestaurant(records)` 归并结果,不额外查库。
- `totalVisitCount`(派生,`visitCounts.values.sum()`)= hero 的「一共这几餐」。
- `randomCandidates` 与 `restaurants` **刻意不是同一批**:随机池来自全量店铺按评价范围筛,不受分段/搜索影响;为空时界面隐藏随机入口。
- 派生:`isSearchMiss`(query 非空且结果空)、`isEmpty`(totalCount==0)。

**ListSearch.kt**(从 ViewModel 抽出以便测试,`HomeFilterTest` 曾因照抄逻辑导致假绿):
- `dishNamesByRestaurant(records)`:按 restaurantId 归并非空餐品名(保留顺序不去重)。
- `visitCountsByRestaurant(records)`:按店计数,清单「去过 N 次」标签用它。
- `RestaurantEntity.matches(keyword, filter, dishNames)`:先状态过滤;空关键字全匹配;否则店名/地址/餐品名任一子串匹配(忽略大小写)。
- **搜索覆盖餐品名**但刻意不含备注(备注什么都写,会模糊「清单 vs 足迹」分工);清单已订阅记录流(随机池要用),此扩展不增加数据库查询。
- `List<RestaurantEntity>.sortedForList(mode)`:清单三档排序(`RestaurantSort{LAST_UPDATED,LAST_ADDED,NAME}`,偏好 `ListSortPreference` 持久化);同值按 id 稳定兜底(`RestaurantSortTest`)。清单工具栏右侧 `SortMenu` 下拉切换(`WantListScreen`),随机池顺序不受影响(恒 updatedAt DESC)。

### 2.2 随机选店(RandomPick.kt)

- `LatestVisit(verdict, eatenAt)`;`latestVisitByRestaurant(records)`:按 restaurantId 归并「最近一次」用餐(**自己比时间戳,不依赖上游排序**——若上游 `ORDER BY` 被改,依赖它会静默变成取最早评价)。
- `buildRandomCandidatePool(restaurants, latestVisits, config, today, zone)`:
  - `cutoff = if(excludeRecentDays>0) today.minusDays(n) else null`;
  - 无最近用餐(待探访)→ `config.includeWantToList` 决定是否入池;
  - 有最近用餐 → `scope.accepts(latest.verdict) && (cutoff==null || 最近用餐日不在 cutoff 当天或之后)`。
  - 排除按**自然日**(`LocalDate` 比较)而非 24h 滚动窗口(与界面「7 天内吃过」读法一致)。
  - 返回顺序沿用入参 `updatedAt DESC`,不二次排序(抽选等概率)。
- `RandomScope.accepts`:`ALL`→全部;`RECOMMENDED_AND_OK`→GOOD/MEH;`RECOMMENDED`→仅 GOOD。
- `candidateScopeDescription()`:结果对话框一句话说明「范围:X,含待探访,排除 N 天吃过的」(避免「仅推荐档抽到待探访新店」的困惑)。
- `List<T>.randomOtherThan(previous)`:「换一家」保证候选>1 时不重复上一家(否则 1/N 概率抽同一家被读成「按钮坏了」)。

### 2.3 足迹页(FootprintViewModel + FootprintAggregation)

`FootprintViewModel`(注入 Repository)。**两级数据流(性能)**:上游 `data = combine(observeRestaurants, observeAllDiningRecords, observeAllPhotos)` 算出全量 `FootprintEntry` 列表 + 一份含全部统计但 `sections`/`query` 为空的 `base` 状态;再 `combine(data, _query)` 只把过滤后的时间线 `sections` 与 `query` 填回 `base.copy(...)`。搜索每敲一字只重新过滤时间线,不再重算 12 个月图表/排行榜/账本汇总。
- `FootprintEntry(record, restaurantName, restaurantAddress, photos)`:在 VM 里摊平店名/地址(界面层不持有「记录→餐厅」关联)。
- 记录所属餐厅已删则跳过(备份导入不保证一致,不显示「未知餐厅」)。
- 搜索(`FootprintEntry.matches`)覆盖**店名/地址/餐品/花费/备注**(比清单更宽:足迹是「按任意内容找我吃过的东西」)。
- 统计基于**全量**而非筛选结果。

**FootprintAggregation.kt**(纯函数,`MONTHS_IN_CHART=12`、`MAX_TOP_RESTAURANTS=5`):
- `Long.toYearMonth(zone)`、`FootprintEntry.isInYear(year, zone)`:显式传时区(UTC 12-31 23:00 与东八区 1-1 07:00 属不同月份)。
- `estimatedAmount()`:`priceText` 非空则 `parseEstimatedAmount()`(取文本最大数字,估算口径);区分「没填」(不计入分母)与「填了识别不出」(界面如实提示)。
- `estimatedSpendIn(year, zone)`:全识别不出返回 `null` 而非 `0.0`(「约 ¥0」会被读成「今年没花钱」)。
- `toMonthlyCounts(today, zone, 12)`:以 today 向前推固定 12 月(横轴固定,缺月画短柱)。
- `toTopRestaurants(5)`:按次数倒序,同次数按 id 升序(稳定,避免无理由跳动)。
- `toSections()`:按月份分组,依赖上游 `eatenAt DESC` 排序保持「新月在前、月内新记录在前」。
- **历年与年度回顾**:`List<FootprintEntry>.yearReviews(zone)` 按年聚合出 `YearReviewCardData`(年度次数/常去店/评价分布/入账优先口径,`YearReviewTest`);统计 tab 的「历年」卡逐行展示,点击行调 `FootprintViewModel.shareYearReview(year)` 把该年渲染成 `YearReviewCard` 分享图(复用 ShareCard 的 GraphicsLayer 录制骨架)。

### 2.4 账本(LedgerAggregation.kt)

**与花费估算是两套刻意分开的口径**:估算 = `priceText` 取最大数字(带「估算」字样);账本 = `amountMinorUnits`(分,逐笔确认,不带「估算」)。账本只认入账金额,**绝不从文本估算凑数**(宁可少算不可错算)。

- `MonthlyAmount(yearMonth, label, amountMinor?)`:`amountMinor==null` 表示该月无入账。
- `RestaurantSpend(restaurantId, restaurantName, amountMinor, visitCount)`;`LedgerCoverage(recognizedCount, textOnlyCount)`。
- `ledgerSpendIn(year, zone)`:年度入账总额,一笔都没有返回 `null`(区别于 0)。
- `ledgerMonthlyAmounts(today, zone, 12)`:最近 12 月入账,横轴固定同次数图。
- `ledgerAverageAmount()`:平均每笔(四舍五入到分),无记录 null。
- `toTopSpendRestaurants(5)`:按累计金额倒序,同额按 id 升序;未入账不计金额也不计笔数。
- `ledgerCoverageIn(year, zone)`:`recognizedCount`(有结构化金额)与 `textOnlyCount`(写了花费文本但没入账)→界面提示「另有 N 条写的是文字,可编辑时补录」。
- **每月预算**:`BudgetPreference` 存每月预算(整数分,设置页「每月预算」行对话框输入);`monthlyBudgetProgress(spentMinor, budgetMinor)` 纯函数产出 `BudgetProgress?`(本月进度/超支/剩余;预算未设或本月无消费返回 null,`BudgetProgressTest`),足迹·账本概览卡显示进度条。刻意只做应用内展示,不做通知。

`FootprintUiState` 同时承载估算字段(`estimatedSpendThisYear`、`amountRecognized/UnrecognizedCount`、`monthlyCounts`、`topRestaurants`)与账本字段(`ledgerSpendThisYear/ThisMonth`、`ledgerAverageAmount`、`ledgerMonthlyAmounts`、`topSpendRestaurants`、`ledgerCoverage`)。`ledgerSpendThisMonth` 直接在 VM 内按 `toYearMonth(zone)==today` 过滤求和。

### 2.5 WantListScreen / FootprintScreen

- `WantListScreen(onOpenRestaurant, onAddRestaurant)`:清单标签内容。版面顺序(本轮重排)**页头 → 统计卡 → 吸顶工具栏 → 分组标题 → 卡片列表**:
  - `PageHeader(title, subtitle, eyebrow)` 的 `eyebrow` 是按时段变的问候语(`greetingFor(LocalTime.now().hour)`,一次会话内 `remember` 固定,跨零点不打断正在浏览的人);
  - `ListStatsCard` 用 `heroGradient` 暖光底 + 三枚 `StatCapsule`(想去=琥珀 / 去过=绿 / 餐数=墨色),并把随机入口收进卡内右下角(`MiuixChip` + 「N 家在范围内」),不再是压在列表上的一整条实心大按钮;
  - stickyHeader `ListToolbar` = `MealSearchField` + `MiuixSegmented`(带 `accent` 语义色与计数角标)+ `SortMenu`。**工具栏底色与 1dp 分隔线只在 `derivedStateOf { 内容已从下方滚过 }` 为真时淡入** —— 常驻底色会在页头与列表之间切出一条硬边;
  - `SortMenu` 改为 `MiuixChip(current.label, icon = Sort)` + `DropdownMenu`(当前项带 ✓),替换原来那行孤零零右对齐的「排序:最近更新」纯文字。`accent` 传入的是普通(非 `@Composable`)lambda,语义色必须在调用处先取好局部变量,否则读 `MaterialTheme.colorScheme` 编译不过;
  - 加载态用 `ListSkeleton`(与真实卡片同尺寸),不再用整页转圈;卡片按下时封面反向放大(`MiuixCard(interactionSource=…)` + `rememberPressProgress`)。
  - 分段用 `MiuixSegmented(HomeFilter.entries)`;随机候选池为空时隐藏入口(「点了没反应」比「看不见」更糟)。
- `FootprintScreen(onOpenRestaurant)`:足迹标签内容,分段在「时间线 / 统计」间切换;统计含概览/花费估算/12 月柱状图(估算与入账两张)/评价分布/常去 Top5 + 账本卡片组(本月进度含预算条)/历年卡(点击分享年度回顾)。图表约定见 `DESIGN_SYSTEM.md` 第六节。

> 注:两个 Screen 的完整 Composable 结构较长(FootprintScreen 已超 1100 行,拆分计划见 `OPTIMIZATION_PLAN.md` 阶段 5),本文只记其数据来源与分段结构;视觉细节以 DESIGN_SYSTEM 为准。

## 三、表单/详情域(detail/add/edit/visit)

四个表单 ViewModel 的**共有架构模式**(详见 `docs/ARCHITECTURE.md` 第 5 节):原子状态跃迁防抖、SavedStateHandle 只持久化用户输入、`restoredFromSavedState` 构造时求值声明在 init 前、Room 订阅自动刷新。

### 3.1 detail 域

- `RestaurantDetailScreen`(738 行):头图(250dp) + 悬浮信息卡 + 记录时间线 + 玻璃标题栏(据滚动 `derivedStateOf` 淡入) + 底部玻璃操作栏。三终态:isLoading→加载块;`detail==null`→「这家店已经不在了」;else→正常内容。底部栏「记录这一餐」恒显示;「照上次」仅 `latestRecordId` 非空时出现。VisitCard 编辑/删除用显式图标(非长按)。分享经 `pendingShareUri` + `buildShareIntent`(`ACTION_SEND`+`image/png`+`EXTRA_STREAM`+`FLAG_GRANT_READ_URI_PERMISSION`)。详情页只展示/查看照片,不导入/回收。信息卡「累计 ¥X」来自 `DetailStats.totalLedgerMinor(records)`(null≠0:一条都没入账时不显示)。
- `RestaurantDetailViewModel`(225 行):`localState`(本地瞬时:弹窗/删除进度)与 `observeRestaurantWithRecords(id)` 订阅流 `combine`,避免弹窗态被上游覆盖。初始必须 `isLoading=true && detail=null`(否则 id 未注入时闪现空态)。`shareCard(bitmap)` 写盘(文件名带时间戳防覆盖)。删除用原子防抖,成功置 `deletionCompleted`。

### 3.2 add 域

- `AddRestaurantScreen`(181 行) + `AddRestaurantViewModel`(260 行):新建店铺。`continueToVisit` 由入口决定保存后去向(表单无记录类型开关)。字段:店名(必填)、地址、封面(≤1)。`photoPaths` 恒 0/1。`canSave = name.isNotBlank() && !isImporting && !isSaving && savedId==null`。相机 `pendingCameraPath` 用 `rememberSaveable`(防进程回收产生孤儿)。照片导入后**先更新状态再删除 obsolete**(IO 移出 update lambda 防重复删/阻塞主线程)。`onCleared` 仅 `savedId==null && !restoredFromSavedState` 时回收。
- **重复店名提醒**(`DuplicateNameCheck.kt`):`normalizeRestaurantName`(去空白+忽略大小写)与 `List<RestaurantEntity>.findDuplicateName(input)`;输入店名命中已有店时表单顶部出现提醒卡,**只提醒不拦截**(同名店是合法场景,重复粘贴才是常见误操作)。`DuplicateNameCheckTest`。

### 3.3 edit 域

- `EditRestaurantViewModel`(390 行)含核心纯函数 **`reclaimableFormPhotos(formPhotoPaths, originalCoverPath, recordPhotoPaths, keep)`**:`protectedPaths = recordPhotoPaths + originalCoverPath + keep`,只回收不在保护集里的(即「本次导入且当前未选中」)。9 个单测覆盖(项目最易丢数据的逻辑)。加载用 `restoredFromSavedState` 区分是否保留用户输入(不能在 load 里查 `savedStateHandle.contains`,否则编辑页永远空白)。save 时 `existing.copy(...)` **保留 status**(状态由记录数驱动)。`removePhoto` 不删文件(仍是 DB 封面,回收在 save 成功或 onCleared)。「从用餐照片选封面」`useRecordPhotoAsCover` 使 restaurants 与 photos 指向同文件(文件别名来源)。
- `EditVisitViewModel`(386 行):含 `originalPhotoPaths` 区分新导入/已落库,`removePhoto` 只删新导入的。`effectiveAmountMinor` 派生:`if(amountOverridden) amountMinorUnits else parseLedgerAmountMinor(priceText, personCount)`。加载时 `personCountTouched = record.personCount>1`、`amountOverridden = amountMinorUnits!=null`。11 个 SavedState key,金额为空时 remove key 而非写 null。

### 3.4 visit 域

- `AddVisitViewModel`(448 行,`MAX_VISIT_PHOTOS=9`):字段顺序按回忆顺序(评价→日期→餐品→花费→照片→[折叠]记账+备注)。**屏幕默认精简**:`AddVisitScreen`/`EditVisitScreen` 用本地 `showMore`(rememberSaveable)把 `AmountLedgerSection`+备注折叠进 Text 档展开按钮,默认只露核心字段;折叠不影响入账(`effectiveAmountMinor` 仍从花费派生)。编辑页 `showMore` 在加载后若原记录有备注/改过金额/人数>1 则自动展开一次(`autoExpandApplied` 只应用一次)。`restaurantName` 经 `flatMapLatest` 订阅;`setRestaurantId` 写入 savedStateHandle(保存流程离页,进程回收后需恢复)。预填:`prefillFrom` 若 `restoredFromSavedState` 直接 return(不覆盖用户已改内容);`dateMillis` 用现在。`removePhoto` 无条件删(全部本次导入)。
- `PickRestaurantViewModel`(73 行):`combine(observeRestaurants, _query)`;空词按 `updatedAt DESC`,否则店名/地址子串匹配;`isSearchMiss`。独立选店页解决「刚吃完想马上记一笔」高频场景。
- `VisitPrefill.kt`:`VisitPrefill` **刻意只 3 字段**(verdict/dishes/priceText);`prefillFromRecord(source, targetRestaurantId)` 强制校验餐厅归属(防 A 店餐品写进 B 店),source 为 null 或异店返回 null。测试用反射锁定字段集。

### 3.5 Formatters.kt

- `RestaurantStatus.displayName()`:待探访/已用餐;`Verdict.displayName()`:推荐/尚可/不推荐。
- 日期:`formatMealDate()`(yyyy年M月d日)、`formatFullDate()`、`formatMonthLabel()`、`formatDayLabel()`,均 `ZoneId.systemDefault()`。
- `formatStorageSize()`:1024 进制,`Locale.US`(避免德语区逗号小数点)。
- `parseEstimatedAmount()`:去千分位逗号,匹配 `\d+(?:\.\d+)?` 取**最大**数字,识别不出 null(估算口径)。
- `formatEstimatedAmount()`:<1万 `¥round`(不显小数),≥1万 `¥%.1f万`。
- `formatLedgerAmount()`:账本精确口径,整元 `¥x`,有零头 `movePointLeft(2).stripTrailingZeros()`,不做「万」缩写。
- `coverInitial(name)`:无封面卡片显示的「店名首字」。**按 code point 取**(高代理项时 `take(2)`):直接 `first()` 会把 emoji 切成半个方块,而这里正好是「唯一视觉内容」的位置。
- `greetingFor(hour)`:清单页 `eyebrow` 问候语(5-10 早上好 / 11-13 中午好 / 14-17 下午好 / 18-22 晚上好 / 其余 夜深了)。`hour` 作参数而非内部读时钟,时段边界由 `FormattersTest` 锁定。

## 四、组件体系(components/)

三层划分:`Miuix.kt`(纯视觉通用)、`MealComponents.kt`(业务语义)、`Glass.kt`(玻璃材质);外加共享积木 `FormSections`/`CommonStates`/`VerdictSelector`/`AmountLedgerSection`/`PhotoViewer`/`GalleryPicker`/`ShareCard`。

### 4.1 Miuix.kt(基础组件)

- `MiuixCard(onClick=null,...)`:`onClick==null` 纯展示;非空自动 `pressScale` + clickable + **按下投影收缩到 35%**(动效常量 `PRESSED_ELEVATION_FACTOR`,卡片「被按进页面」而不是浮着抖)。层叠顺序 `pressScale→softShadow→clip→background→clickable→padding` —— 写错一次(如 `clip` 放 `background` 之后)就会出现「圆角里漏出直角阴影」,且只在特定圆角档下看得见。新增 `interactionSource` 参数:需要让**卡内内容**(封面)跟随按压状态时由调用方自建并传入,配合 `rememberPressProgress`。
- `MiuixButtonStyle{Filled, Tonal, Outlined, Text}`;`MiuixButton(label, onClick, loading, style, height=54dp)`:`Filled&&active` 才加同色系 `softShadow`(12dp→按下 5dp) 并铺 `primary → primaryDeep` 垂直渐变(纯色档白字只有 3.18:1 的历史问题在此收深到 4.66:1、渐变暗端 7.14:1;实测公式见 `DESIGN_SYSTEM.md` 第三节);按下浮出一层 14% 白提亮;`heightIn(min=height)` 防大字体裁字;loading 保持宽度换转圈;无障碍 `clearAndSetSemantics` 补 `contentDescription`/`role=Button`/loading→`stateDescription="处理中"`/active→`onClick` 否则 `disabled`。
- `MiuixChip(label, onClick, icon, selected, accent, height=40dp)`:轻量胶囊入口,给「排序」「抽一家」这类**不该占满一行**的次级动作。底色/描边/文字三处 `animateColorAsState(quick)` 同步过渡,按下时图标单独收紧 0.86(用只改绘制的 `graphicScale`,不改布局尺寸,否则同行文字会抖)。
- `MiuixSegmented<T>`:高度 `maxOf(46dp, textHeight+inset*2+10dp)`(大字体撑高);指示块 `animateDpAsState(MealMotion.settle)` + `clearAndSetSemantics{}`,有 `accent` 时在纯白指示块上**再叠一层同色渐变**(不是直接用半透明色填底,那样底槽颜色会透上来,选中块看着像没填满);文字选中时颜色 + **字重**同时变;计数角标缩放。容器 `selectableGroup()` + 每项 `selectable(selected, role=Tab)`,切换时一次触觉反馈。
- `MiuixTextField`:标签固定上方(非浮动);容器与描边由**外层自绘**(`softShadow` + `background` + `border`),TextField 全部容器色/indicator 置透明 —— 这样聚焦才是「描边淡入主色并加粗到 1.6dp + 6dp 主色光晕 + 底色转白」的连续过渡,而不是 Material 默认那种瞬间切换的下划线。error 用 errorContainer,cursor/selection 主色。
- `MiuixTopBar`(无背景色,heightIn(min=60dp))、`MiuixIconButton`(48dp,pressScale 0.88f,图标 23dp)、`SectionHeader`(titleLarge+Bold,左侧 3×18dp 主色竖条作分组锚点,竖条 `clearAndSetSemantics{}`)、`MiuixListRow`(设置行,`destructive` 整行 error 色,可点行自动补右侧箭头,40dp 图标砖 12% 语义底)。
- `StatCapsule(value, label, accent)`:大数字滚动 + 说明;**语义锁定终值**(`clearAndSetSemantics { contentDescription = "$label $value" }`),否则读屏会念出动画中间值。

### 4.2 MealComponents.kt(业务组件)

- `MealPill`(私有):徽章公共外壳 = 圆角胶囊 + **前置色点 + 文字**。不再用整块反白彩色胶囊(暖底上一排彩卡会吵),色点 `clearAndSetSemantics{}` 且文字始终在(状态不只靠颜色)。
- `StatusBadge`(琥珀 WANT / 绿 EATEN)、`VerdictBadge`(绿/暖石灰/红)。
- `RestaurantCover` / `RestaurantCoverSurface`:封面。`RestaurantCover` 是固定 `size`(默认 92dp)包装,`Surface` 版尺寸完全由调用方 modifier 决定(随机弹窗要 1.7:1 横幅)。有图:`rememberAsyncImagePainter` + `Image` + `imageReveal(解码完成淡入+1.04 收拢)` + 按下反向放大 `zoom`;**外层必须有界**,否则 Coil 按原图解码(等价于旧版对 `AsyncImage` 显式尺寸的要求)。无图:暖光渐变 + `coverInitial(name)` 首字(不是灰底 + 小图标 —— 缺图卡不该看起来像加载失败)。
- `RestaurantRow`(封面 + 店名 `headlineSmall` 22sp + 地址 + 底部元信息行`VerdictBadge` / 「去过 N 次」,不含卡片外壳供复用)。元信息行**只在真有内容时出现**:一排齐刷刷的「待探访」与分段筛选本身重复,是噪音。
- `ConfirmDialog`(确认按钮 error 色)、`MessageBanner`(`destructive` 时 errorContainer)、`PhotoActionTile`/`GalleryPhotoAction`/`CameraPhotoAction`(92dp 图片入口)、`SelectedPhoto`(必须显式尺寸防 OOM,右上移除按钮)、`PhotoPlaceholder`(与缺图封面同一束 `heroGradient` 暖光,避免「一种灰表示没图、另一种灰也表示没图」)。

### 4.3 表单积木与共享状态

- `FormSections.kt`:`MealDateRow`(点开日期选择器)、`MealPhotoSection`(SectionHeader + LazyRow,`canAddPhoto=false` 隐藏入口)、`FormErrorLine`、`FormBottomGap`。段间 22dp、段内 12dp。
- `CommonStates.kt`:`MealSearchField`(WantList/Footprint/Pick 共用,ImeAction.Search;药丸形 + 聚焦四件事同时发生:底色转白/描边淡入主色/放大镜变主色/整条上浮 6dp;清除按钮**常驻 trailingIcon 槽位** + `AnimatedVisibility`,做成条件槽位就没有退出动画且右侧瞬间空一截)、`PageHeader`(`eyebrow` 小字 + `displaySmall` 大标题 + 说明)、`EmptyStateBlock`(暖光圆底 + 极缓慢上下浮动:空页面是停留最久的地方,完全静止会读成「卡住了」)、`LoadingBlock`(固定纵向 padding 防高度跳变;**仅用于局部**如详情页/保存中)、`ListSkeleton`(整页列表加载态,按真实卡片尺寸画占位 + `skeletonShimmer`)。
- `VerdictSelector.kt`:三选一 `selectableGroup()` + 每项 `selectable(role=RadioButton)`;选中态容器色+边框+缩放弹簧过渡。
- `AmountLedgerSection.kt`(304 行,入账金额确认条):默认零操作展示识别金额,改错才展开手动输入。含 `PersonCountRow`(1..20 步进)、`AmountEditor`(Decimal 键盘);纯逻辑 `perCapitaText`、`parseDraftToMinor`(去 `¥`/`,`,×100 HALF_UP 转分)。
- `PhotoViewer.kt`(269 行):全屏 `Dialog`,左右滑切换、双击 1x↔2.5x、放大拖动平移(禁翻页);**不做双指缩放**(`detectTransformGestures` 消费全部指针使 pager 失效);顶栏「N/total」`clearAndSetSemantics` 播报「第 N 张,共 total 张」;`ContentScale.Fit`。
- `GalleryPicker.kt`(168 行,含降级):`PickVisualMedia` 在无系统 Picker 无 GMS 设备上会崩,`isSystemPickerReady` 判断(API≥33 直接 true,否则 `getSynchronousResult`),失败退回 `GetContent`/`GetMultipleContents`;两路均无需存储权限。

### 4.4 ShareCard.kt(265 行,分享卡片)

卡片是渲染而非固定图,先预览再分享(照片竖版裁剪无法预判,Compose 只能录制已绘制内容)。`ShareCardData`(不复用 DB 实体);`CARD_ASPECT_RATIO=3:4`。`ShareCardSheet` 用 `rememberGraphicsLayer` + `drawWithContent{ layer.record{drawContent()}; drawContent() }`,点分享 `layer.toImageBitmap().asAndroidBitmap()`(挂起等渲染完成),`isCapturing` 防连点。`ShareCardContent` 全出血:背景照片(Crop)或主色渐变→三段压深遮罩→右上「食单」水印→左下 VerdictBadge/店名/日期地址/餐品/花费胶囊/备注。

## 五、主题 / 材质 / 动效(theme/ + components/)

### 5.1 主题令牌(theme/)

- `Color.kt`:**纯白底 · 暖白浮层**色板。浅色 Canvas `#FFFFFF`(纯白)/Surface `#FBF8F4`(暖白卡片,配 1px `#EFE9E1` 描边 + 暖棕阴影)/Primary 深翠绿 `#0A8558`/Secondary 琥珀褐 `#9E570F`/Tertiary 暖石灰 `#6B6459`/Error 暖红 `#C33A32`;深色走暖炭 Canvas `#131110`、Primary 提亮 `#4FD39A`。另有两档**纯装饰**:`*Vivid`(`#12B377`/`#D9822B`,配白字不足 AA,故不承载文字)与陶土红 `#B4453A`(只允许进分享卡/年度回顾这类脱离应用状态的图片版面,**禁止**进入任何状态语义)。阴影色改暖棕偏移 `#241A10`(上一版冷蓝灰 `#202A3A` 压在暖底上会发脏,这是卡片「灰扑扑」的根因)。玻璃着色同改暖白/暖炭。语义色分工全局固定。
- `Theme.kt`:`lightColorScheme`/`darkColorScheme`;圆角阶梯 `AppShapes` **10/16/20/26/32dp**(封面变大后整体上调一档,小圆角包住大图会「没包住」);**`MealTokens`**(M3 无槽位补充令牌:shadowAmbient/Spot、primaryShadow、glass*、`primaryVivid`、`primaryDeep`(实心按钮渐变暗端)、`secondaryVivid`、`accentTerracotta`、`heroGradient`、isDark)经 `staticCompositionLocalOf` 下发,`MaterialTheme.mealTokens` 访问;`Modifier.softShadow(shape, elevation, ambient, spot)` 固定 `clip=false` 取主题阴影色;`MealNoteTheme(darkTheme)` 组装。
- `Type.kt`:`FontFamily.SansSerif`;大标题负字距(displayMedium 40/-1.0、displaySmall 34/-0.8、headlineSmall 22/-0.2…)。**本轮补齐 `headlineSmall`(22sp,卡片店名)、`titleSmall`(14sp SemiBold)、`displayMedium`**:`headlineSmall` 此前已被 `ShareCard.kt` 引用却从未声明,一直静默落到 M3 默认值(字号对不上、字重是 Regular)。

### 5.2 玻璃降级(Glass.kt,220 行)

原理:`Modifier.blur()` 只模糊自身,毛玻璃需模糊身后内容→`GraphicsLayer` 录制滚动内容 + `RenderEffect` 模糊 + 按坐标偏移画模糊副本。
- `rememberGlassBackdrop()` / `Modifier.glassBackdropSource(backdrop, enabled)`(每帧一次离屏录制,勿多层嵌套)。
- `GlassSurface(...,blurRadius=28dp, fluid=true)`:`canBlur = fluid && SDK_INT>=31`;`drawBehind` 三步(模糊副本→材质渐变→顶部折射高光)+ border。`applyBlurEffect`(`@RequiresApi S`,`RenderEffect.createBlurEffect(..., CLAMP)`)。
- **两级降级**:API<31 无 RenderEffect→拟态玻璃(高不透明着色+高光);流畅模式关→即便 API31+ 也跳过实时模糊(实时模糊是低端机卡顿最大来源)。

### 5.3 动效(FluidMotion.kt + Motion.kt)

- `LocalFluidMotion = staticCompositionLocalOf { true }`,由 MainActivity 顶层提供,`isFluidMotion` 读取。
- **`MealMotion`**(本轮新增):四档弹簧规格 `settle`(落定:位移/尺寸/数字)、`bouncy`(按压反馈)、`pop`(高过冲:导航图标弹跳)、`quick`(颜色/透明度等小变化,必须比位移更快)。**组件不得各写 `spring(...)`** —— 散落参数会随时间漂移成五六种手感,「同一屏里两个控件不像同一个应用」就是这么来的。`MealNoteApp` 的四个导航转场原先用 `tween(220)`,是项目自己约定的「禁缓动」的唯一例外,已一并改为 `settle`。
- `Modifier.pressScale(pressedScale=0.965f)`(用 `bouncy`)、`rememberPressProgress(interactionSource)`(归一化 0→1 按压进度,供卡内内容做连续效果)、`AnimatedCounter`(整数滚动)、`Modifier.staggeredEnter(index)`(`enabled=false` 直接返回;否则阶梯延迟 `min(index,5)*18ms` + alpha + translationY 10dp,`settle` 落定——刻意收敛避免长列表瀑布拖尾)。
- `Modifier.skeletonShimmer(enabled)`:骨架屏扫光。**这是唯一允许线性的地方** —— 循环型动画没有终点,弹簧不适用,且任何缓动都会在循环接缝看出「呼吸感」。扫光是持续的每帧绘制成本,流畅模式关闭时只画静态占位块。
- `Modifier.imageReveal(revealed, zoom, enabled)`:解码完成淡入 + 从 1.04 收拢,并把按压缩放合并进**同一个** `graphicsLayer`(叠两层会各建一个图层,长列表上是白给的绘制开销)。
- `rememberSelectionHaptic()`:选中/切换/主操作各一次 `LongPress` 触觉;滚动与纯浏览**不震**(每次都震就分不清哪次对应哪次生效)。
- 流畅关时:导航转场仅淡入淡出、staggeredEnter 跳过、`skeletonShimmer` 转静态、GlassSurface 跳过实时模糊。

## 六、设置域(settings/)

- `SettingsViewModel`(474 行,依赖最多):外观/流畅/随机/预算/清单排序/备份新鲜度的偏好转发 + 备份导出/恢复 + 账本 CSV 导出 + WebDAV 上传/下载 + 运行日志分享 + 清空数据。长任务共用忙碌横幅(`isBusy`,每段 `updateAndGet` 原子跃迁防重入);本地导出与 WebDAV 上传成功后 `BackupStatePreference.markBackedUp()`。`MealError→文案` 映射在 `MealError.toMessage(prefix)`。
- `WebDavSection.kt`:配置表单(地址/账号/密码;回显一律先 `redactUrlCredentials()`)+「测试连接」(HEAD 探测,`webDavProbeFor` 五态归类:可达 / 需认证 / 未找到 / 协议不安全 / 网络异常,`WebDavProbeTest`)+ 上传 / 下载。失败提示整块错误色、不自动消失(AGENTS §6「失败必须可见」)。
- 备份新鲜度:「我的」页备份行副标题显示上次备份「今天 / 昨天 / N 天前 / 从未」(`backupFreshnessLabel` 纯函数,时钟回拨按今天兜底)。
- 导出账本 CSV:SAF 创建 `mealnote-ledger-*.csv`,纯逻辑见 `data/export/`(DATA_LAYER §11b)。
- 其余行:存储占用(照片目录大小)/清理无用文件/分享运行日志/清空数据(二次确认)/关于。
