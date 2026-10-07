# 架构(ARCHITECTURE)

> 代码级参考。稳定约束见 `AGENTS.md`;产品级架构叙述见 `PROJECT_OVERVIEW.md` 第五节。
> 事实来源:`MealNoteApp.kt`、`MainActivity.kt`、`MealNoteApplication.kt`、`di/DatabaseModule.kt`、`build.gradle.kts` 及各域源码。

---

## 1. 分层数据流

```
Compose Screen          界面展示与用户事件
  └─ ViewModel          页面状态、防抖、savedStateHandle 持久化、错误文案
       └─ MealRepository  输入校验(normalizeText)、事务边界、照片文件生命周期
            ├─ MealDao       纯 SQL 与对象映射(Room)
            ├─ PhotoStore    files/photos/ 图片读写、EXIF 校正、降采样(最长边 2048px)、安全删除
            ├─ BackupStore   版本化 ZIP 备份包导出与导入
            └─ WebDavStore   WebDAV 上传/下载(复用 BackupStore.export/restore)
```

界面横切模块:`ui/theme/`(调色板/形状/阴影令牌)、`ui/components/`(通用组件 + 动效 + 玻璃材质 + 业务组件)。

单向依赖:上层只依赖下层;Repository 是唯一业务入口,DAO 不含业务校验,ViewModel 只消费 `MealResult`。

## 2. 应用入口与依赖注入

- `MealNoteApplication`(`@HiltAndroidApp`):唯一 Application,仅作 Hilt 入口。
- `MainActivity`(`@AndroidEntryPoint`):`enableEdgeToEdge()`;注入 `ThemePreference`、`MotionPreference`;在 `setContent` 顶层读取主题模式与流畅偏好,用 `MealNoteTheme` 包住整个 UI(含后续对话框),经 `CompositionLocalProvider(LocalFluidMotion provides fluid)` 下发流畅开关,再进 `MealNoteApp()`。
  - 主题/流畅偏好在此统一读取而非各屏自读:确保主题包住所有弹窗。
- `di/DatabaseModule`(Hilt `@Module`,`SingletonComponent`):提供 `AppDatabase`(单例,`meal_note.db`,注册三条 Migration)与 `MealDao`。`PhotoStore`/`AppLog`/各 `Preference` 亦为单例(见各自 `@Inject`/模块声明)。

技术栈锁定见 `AGENTS.md` 第 2 节。

## 3. 导航路由(MealNoteApp.kt)

**两层结构**:`Routes.MAIN` 是唯一「壳」路由,内部用底部导航切换三标签(**不把标签拆成独立路由**,否则返回键在标签间来回跳);其余都是压栈的二级页面。

| 路由常量 | 模板 | 参数 | 目的屏 |
|---|---|---|---|
| `MAIN` | `main` | — | `MainScaffold`(清单/足迹/我的) |
| `ADD_RESTAURANT` | `add_restaurant?nextVisit={nextVisit}` | `nextVisit: Bool`(默认 false) | `AddRestaurantScreen` |
| `PICK_RESTAURANT` | `pick_restaurant` | — | `PickRestaurantScreen` |
| `RESTAURANT_DETAIL` | `restaurant/{restaurantId}` | `restaurantId: Long` | `RestaurantDetailScreen` |
| `ADD_VISIT` | `restaurant/{restaurantId}/visit?copyFrom={copyFrom}` | `restaurantId: Long`、`copyFrom: Long`(默认 `-1`=无来源) | `AddVisitScreen` |
| `EDIT_RESTAURANT` | `restaurant/{restaurantId}/edit` | `restaurantId: Long` | `EditRestaurantScreen` |
| `EDIT_VISIT` | `visit/{recordId}/edit` | `recordId: Long` | `EditVisitScreen` |

参数约定:
- `copyFrom` 用**哨兵值 `NO_COPY_SOURCE = -1L`** 而非可空 Long(记录 id 恒正,负数不冲突;Navigation 可空 Long 易出错)。`AddVisitScreen` 收到时 `copyFrom.takeIf { it != NO_COPY_SOURCE }`。
- 构造器函数:`Routes.addRestaurant(nextVisit)`、`restaurantDetail(id)`、`addVisit(id, copyFrom)`、`editRestaurant(id)`、`editVisit(recordId)`。

**返回栈压平**(两个私有扩展):
- `openVisitForm(restaurantId, copyFrom)`:`navigate(...) { popUpTo(MAIN) { inclusive=false } }`。进用餐表单有三条路径(清单选店 / 详情页 / 新建店铺后接着填),统一清到 MAIN,使返回键在任何入口下都退回主界面。
- `backToDetail(restaurantId)`:保存用餐后跳详情,同样 `popUpTo(MAIN)`——看完刚写的记录后返回即到清单/足迹,而非回到已提交的表单。

**转场动画**(据 `LocalFluidMotion` 切换):流畅开启时「新页右侧推入 `width/6` + 淡入,旧页向左退 `width/12`」(轻微视差,非整屏平移);关闭时只做淡入淡出(120ms),低端机不承担位移动画。

## 4. 导航流(用户旅程)

```
悬浮按钮
├─「想吃,还没去」→ AddRestaurant(nextVisit=false) → 存清单 → popBack 回列表
└─「已经吃过了」  → PickRestaurant
                     ├─ 选已有店 → openVisitForm(id)         → AddVisit → backToDetail
                     └─「新建一家」→ AddRestaurant(nextVisit=true)
                                        → onSaved → openVisitForm(newId) → AddVisit → backToDetail
详情页
├─「记录这一餐」→ openVisitForm(id)                    → AddVisit
├─「照上次」    → openVisitForm(id, copyFrom=latestId) → AddVisit(预填 verdict/dishes/priceText)
├─「编辑店铺」  → EditRestaurant → onSaved → popBack(详情由 Room 订阅自动刷新)
└─ 记录卡「编辑」→ EditVisit      → onSaved → popBack
```

## 5. 状态管理架构模式(四个表单 ViewModel 共有)

1. **原子状态跃迁防抖**:`updateAndGet` 内同时检查 + 置位 `isSaving`/`isDeleting`,防两次快速点击各起协程写重复数据。
2. **SavedStateHandle 持久化**:`init` 中 `distinctUntilChanged` 只持久化用户输入字段(不含加载态/错误),防每键一次 Bundle 写入。
3. **`restoredFromSavedState` 构造时求值 + 声明在 `init` 之前**(关键不变式):`init` 持久化协程构造期即写 handle,若延后判断会恒 `true`,导致 (a) `onCleared` 回收永不执行→孤儿文件泄漏;(b) 编辑页永远空白(走「保留用户输入」分支覆盖 DB 数据)。这是 v0.3.3 / v0.3.6 两个数据丢失 bug 与「编辑页空白」bug 的共同根因。
4. **数据由 Room 订阅自动刷新**:删除/编辑保存后详情页无需手动重载(`observeRestaurantWithRecords` + `stateIn(WhileSubscribed(5000))`)。
5. **本地瞬时状态与数据库流 `combine`**:详情 VM 把弹窗/提示/删除进度等本地交互与订阅流合并,避免被上游发射覆盖。

## 6. 包结构地图

```
com.fanji.mealnote/
├─ MainActivity / MealNoteApplication          入口
├─ data/
│  ├─ MealRepository            业务一致性、事务、文件生命周期
│  ├─ MealError(含 MealResult)  统一错误/结果模型
│  ├─ MealText / AmountText     文本规范化(code point)/金额解析(分)
│  ├─ PhotoStore / ShareImageStore
│  ├─ local/                    AppDatabase、MealDao、Models、Converters
│  ├─ backup/                   BackupStore、BackupModels
│  ├─ log/AppLog
│  ├─ settings/                 Theme/Motion/Random/Budget/ListSort/BackupState
│  │                            Preference(SharedPreferences,共 6 个)
│  ├─ export/                   LedgerCsv(纯逻辑)、LedgerCsvStore(SAF 写出)
│  └─ webdav/                   WebDavStore、WebDavPreference
├─ di/DatabaseModule            Hilt + 三条 Migration
└─ ui/
   ├─ MealNoteApp               NavHost、转场、路由表
   ├─ Formatters                日期/金额/存储量格式化
   ├─ theme/                    Color、Theme、Type
   ├─ components/               Miuix、Motion、FluidMotion、Glass、MealComponents、
   │                            FormSections、VerdictSelector、CommonStates、
   │                            AmountLedgerSection、PhotoViewer、GalleryPicker、
   │                            ShareCard、YearReviewCard
   ├─ home/                     MainScaffold、WantList、Footprint、Ledger/Random/Search 逻辑
   ├─ add/ visit/               新建店铺(+DuplicateNameCheck)、选店、用餐表单
   ├─ detail/ edit/             详情(+DetailStats)、编辑店铺、编辑用餐
   └─ settings/                 SettingsScreen、SettingsViewModel、WebDavSection
```

详见 `docs/DATA_LAYER.md`(数据层)与 `docs/UI_LAYER.md`(界面层)。
