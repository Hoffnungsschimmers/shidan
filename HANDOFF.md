# 饭记项目交接文档

> 交接日期：2026-09-11  
> 项目位置：`C:\Users\2540\Desktop\饭`  
> 英文路径入口：`C:\Users\2540\mealnote-workspace`（目录联接，指向同一项目，不是副本）  
> 当前阶段：Android 原型可运行，产品模型已重新聚焦，下一步应先重做设计再继续开发

## 1. 给下一位 Codex 的启动指令

接手后请先完整阅读：

1. `C:\Users\2540\Desktop\饭\HANDOFF.md`
2. `C:\Users\2540\Desktop\饭\PROJECT_PLAN.md`
3. `C:\Users\2540\Desktop\饭\README.md`
4. `C:\Users\2540\Desktop\饭\CHANGELOG.md`

然后检查实际代码与构建状态，不要仅依据旧 APK 或旧聊天描述。项目还不是 Git 仓库，修改前建议先初始化 Git 并保存当前基线，但不要未经用户要求创建提交。

**重要：暂停继续凭感觉修改 Compose UI。** 下一阶段先获取竞品截图、确定视觉方向、产出高保真原型和 `DESIGN_SYSTEM.md`，经用户确认后再实现。

## 2. 产品定位与已经确认的需求

“饭记”是本地优先的个人餐饮记录 App，不是点评平台，也不是复杂的餐厅资料库。产品核心是：

- 快速记下“听说了，之后想吃”的店；
- 吃完后用极简方式留下评价、餐品和照片；
- 以后可以通过记录回忆和做选择；
- 数据默认本地保存，重视隐私和可迁移性。

### 已确认的两种记录模式

#### 模式 A：听说了，之后吃

只保留：

- 店名：唯一必填项；
- 地址：可选；
- 推荐图片：可选，目前设计为一张封面图。

保存后进入“想吃”。

#### 模式 B：已经吃了

先记录店名、可选地址和图片，然后直接进入本次评价。吃后评价只保留：

- 三级评价：**夯 / 一般 / 避雷**；
- 餐品名字；
- 餐品照片；
- 附录：可选，用来写环境、感受或小故事。

### 用户明确不要的主要字段

- 星级；
- 菜系；
- 标签；
- 预估人均和实际人均；
- 推荐链接；
- “为什么想吃”；
- 强制填写的用餐感想；
- “吃了什么”这种宽泛重复字段。

数据实体里目前仍保留了一些旧字段，以保证早期结构兼容，但新 UI 不应再展示它们。后续可通过正式 Migration 再清理，不要直接删列或清库。

## 3. 用户对 UI 的反馈和竞品参考

用户明确认为当前 UI 很差、过于原型化。当前界面不应作为最终设计基础。

竞品参考：微信小程序“红黑食录”。用户提供的内部短链：

```text
#小程序://红黑食录/HmQ9MzYJ4MB9puH
```

普通浏览器无法解析该微信内部链接，公开搜索也没有找到可信界面截图。下一对话应让用户提供以下截图或录屏：

- 首页/列表；
- 新增想吃；
- 吃后评价；
- 餐厅详情；
- 有照片的记录；
- 导航或其他关键页面。

参考竞品时只提取信息架构、内容密度、图片比例、排版、交互和视觉规律，不复制品牌、文案或受保护素材。

## 4. 推荐并已配置的设计—开发流程

之后按以下顺序工作：

```text
竞品截图与用户需求
→ 梳理信息架构和主流程
→ 提出 2–3 个视觉方向
→ 在对话内制作可交互手机原型
→ Figma 高保真设计与组件
→ 用户确认
→ 编写 DESIGN_SYSTEM.md
→ Jetpack Compose 实现
→ 单元/UI/Lint/构建验证
→ 真机验收
```

设计阶段先确认：

- 一个清晰、可描述的视觉概念；
- 首页、想吃新增、吃后评价、详情四个核心页面；
- 空状态、加载、错误、图片缺失等状态；
- 真实 Android 手机尺寸和安全区域；
- 颜色、字体、间距、圆角、图片比例、组件和动效；
- 无障碍、大字体和深色模式。

## 5. 已安装的 Skills 与插件

### 本地 Skills

位于 `C:\Users\2540\.codex\skills`：

1. `ui-ux-kit`
   - 视觉概念、竞品审计、反模板化设计、设计系统；
   - 完整 references 已恢复，共 5 个。
2. `mobile-app-design-builder`
   - 移动端用户流程、真实手机画布、高保真设计、状态矩阵；
   - 完整 references 已恢复，共 6 个。
3. `compose-circuit-skills`
   - Kotlin、Android、Jetpack Compose、架构、性能、无障碍和测试；
   - 虽然名称含 Circuit，也适用于当前普通 Compose + ViewModel 项目；
   - 完整 references 已恢复，共 13 个。

这些 Skills 是在当前聊天中安装的，新聊天应能自动发现；若未显示，重启或重新打开 Codex 后再检查。

### 已有内置能力

- `visualize`：在对话中做交互原型和界面方案；
- `computer-use`：查看浏览器、模拟器和桌面工具；
- `imagegen`：生成插画、封面、空状态和应用商店素材；
- GitHub 工具：后续仓库、Issue、PR 和发布管理。

### Figma 插件

Figma 已安装并连接：

- 账号：`l2540335944@gmail.com`
- 团队：`yi lin's team`
- Plan key：`team::1494337354646866416`
- 套餐：Starter
- 当前席位：View

读取设计和设计到代码可用；新建/编辑文件可能受 View 席位限制。如果写入失败，应先确认 Figma 编辑权限，不要反复调用。

## 6. 当前技术架构

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Room
- Hilt
- StateFlow + ViewModel
- Coil
- Java 17
- minSdk 26
- compileSdk / targetSdk 36
- 应用包名：`com.fanji.mealnote`
- 当前版本：`0.1.0`

核心结构：

```text
Compose Screen
→ ViewModel
→ MealRepository
→ Room DAO / PhotoStore
```

主要页面：

- `HomeScreen`
- `AddRestaurantScreen`
- `RestaurantDetailScreen`
- `AddVisitScreen`

路由位于：

`app/src/main/java/com/fanji/mealnote/ui/MealNoteApp.kt`

## 7. 当前数据结构与迁移

数据库：`meal_note.db`，当前 Room version = 2，schema 导出开启。

主要实体：

- `RestaurantEntity`
- `DiningRecordEntity`
- `PhotoEntity`

数据库从 v1 到 v2 增加：

```text
restaurants.recommendationPhotoPath TEXT NOT NULL DEFAULT ''
```

Migration 位于：

`app/src/main/java/com/fanji/mealnote/di/DatabaseModule.kt`

Schema 位于：

`app/schemas/com.fanji.mealnote.data.local.AppDatabase/`

注意：项目已经移除 `fallbackToDestructiveMigration()`，不能为了修构建重新加回去。所有后续 schema 修改都必须提供 Migration 和升级测试。

图片管理位于 `PhotoStore.kt`，当前已处理：

- 相机取消时清理目标文件；
- 移除未保存图片时清理；
- 放弃表单时清理未提交图片；
- 只删除 App 自有照片目录中的文件。

## 8. 当前代码状态：必须注意的不一致

### 8.1 源码能编译，但最后一轮完整验证后又发生了修改

最后执行的验证是：

```powershell
C:\Users\2540\mealnote-workspace\gradlew.bat -p C:\Users\2540\mealnote-workspace compileDebugKotlin
```

结果：`BUILD SUCCESSFUL`。

但是最新数据库/推荐图片调整之后，尚未重新执行完整：

```powershell
testDebugUnitTest lintDebug assembleDebug
```

因此新对话第一步应完整验证，并修复暴露的问题。

### 8.2 吃后评价逻辑可能仍保留旧参数

重点检查：

- `ui/visit/AddVisitViewModel.kt`
- `data/MealRepository.kt`
- `ui/visit/AddVisitScreen.kt`

虽然界面尝试移除了星级和价格，但 ViewModel/Repository 可能仍保留 `star`、`costText`、`perPersonCost` 或 `toPriceOrNull()`。这属于未完成重构，必须统一：

- UI 状态删除旧字段；
- ViewModel 删除旧事件；
- Repository 的新增记录接口删除旧参数；
- 旧数据库列可暂时保留默认值，以兼容已有数据；
- 详情页不得显示星级和人均。

### 8.3 最新 APK 不是最新源码

现有 APK：

`app/build/outputs/apk/debug/app-debug.apk`

最后生成时间为 2026-09-10 21:56:36，早于部分最新源码调整。因此不要直接把它交给用户测试。完成全量验证后重新生成 APK。

### 8.4 目录不是 Git 仓库

当前 `C:\Users\2540\Desktop\饭` 没有 `.git`，没有提交历史，也不能方便回滚。建议新对话优先：

```powershell
git init
```

然后检查 `.gitignore` 并建立基线。只有用户明确要求时才创建 commit。

### 8.5 中文路径问题

直接从中文路径运行部分 Gradle 单元测试或 Android 工具时，曾出现乱码和 `ClassNotFoundException`。已建立英文目录联接：

`C:\Users\2540\mealnote-workspace`

推荐所有 Gradle 命令从英文入口执行，例如：

```powershell
C:\Users\2540\mealnote-workspace\gradlew.bat `
  -p C:\Users\2540\mealnote-workspace `
  testDebugUnitTest lintDebug assembleDebug
```

源文件仍是同一份，不要复制项目。

## 9. 已完成的工程修复

- 固定与 AGP 8.13.2、SDK 36 兼容的 Compose/AndroidX 版本；
- 修复 Room 嵌套 Relation；
- 修复状态标签误用 `Triple`；
- 修复旧版 Photo Picker 启动参数；
- 餐厅不存在时新增到访不再假成功；
- 建立照片基本生命周期清理；
- 开启 Room schema 导出；
- 移除破坏性迁移；
- 增加首批 `FormattersTest`；
- 添加 README、CHANGELOG 和项目书；
- 建立英文目录入口解决中文路径测试问题。

## 10. 下一对话建议执行顺序

### 第一步：建立可靠基线

1. 阅读交接文档和代码；
2. 检查当前文件与数据库 schema；
3. 统一清理吃后评价的旧星级/价格参数；
4. 从英文目录执行：

```powershell
C:\Users\2540\mealnote-workspace\gradlew.bat `
  -p C:\Users\2540\mealnote-workspace `
  testDebugUnitTest lintDebug assembleDebug
```

5. 确认 v1→v2 Migration 与 schema 输出；
6. 视用户意愿初始化 Git。

### 第二步：暂停代码式 UI 猜测，重新设计

1. 请求用户上传“红黑食录”关键截图/录屏；
2. 使用 `mobile-app-design-builder` 做流程与状态矩阵；
3. 使用 `ui-ux-kit` 做竞品审计、视觉概念和设计系统；
4. 使用 `visualize` 先给用户看可交互原型；
5. 若 Figma 有编辑权限，再做高保真稿和组件；
6. 用户明确确认后才重写 Compose UI。

### 第三步：按确认设计实现

建议最先完成四个核心页面：

- 首页/列表；
- 记一家店；
- 吃后评价；
- 餐厅详情。

随后补：

- 编辑/删除；
- 图片大图预览；
- 空、错、加载状态；
- 数据导出/恢复；
- Room Migration 测试；
- Compose UI 测试；
- 正式发布签名和版本流程。

## 11. 验收标准

下一版交给用户前至少满足：

```powershell
testDebugUnitTest
lintDebug
assembleDebug
```

并在 Android 设备上验证：

1. 听说了→填店名→可选地址/推荐图→进入想吃；
2. 已经吃了→建店→进入评价；
3. 夯/一般/避雷三选一；
4. 餐品名字、照片和可选附录保存正确；
5. 首页与详情正确显示推荐图和状态；
6. 覆盖安装可从数据库 v1 升级到 v2，旧数据不丢失；
7. 相机取消、移除图片和放弃表单不会留下孤儿文件；
8. 重新启动后记录仍存在。

## 12. 关键文件索引

```text
PROJECT_PLAN.md
README.md
CHANGELOG.md
HANDOFF.md

gradle/libs.versions.toml
app/build.gradle.kts

app/src/main/java/com/fanji/mealnote/data/MealRepository.kt
app/src/main/java/com/fanji/mealnote/data/PhotoStore.kt
app/src/main/java/com/fanji/mealnote/data/local/Models.kt
app/src/main/java/com/fanji/mealnote/data/local/MealDao.kt
app/src/main/java/com/fanji/mealnote/data/local/AppDatabase.kt
app/src/main/java/com/fanji/mealnote/di/DatabaseModule.kt

app/src/main/java/com/fanji/mealnote/ui/MealNoteApp.kt
app/src/main/java/com/fanji/mealnote/ui/home/HomeScreen.kt
app/src/main/java/com/fanji/mealnote/ui/home/HomeViewModel.kt
app/src/main/java/com/fanji/mealnote/ui/add/AddRestaurantScreen.kt
app/src/main/java/com/fanji/mealnote/ui/add/AddRestaurantViewModel.kt
app/src/main/java/com/fanji/mealnote/ui/visit/AddVisitScreen.kt
app/src/main/java/com/fanji/mealnote/ui/visit/AddVisitViewModel.kt
app/src/main/java/com/fanji/mealnote/ui/detail/RestaurantDetailScreen.kt
app/src/main/java/com/fanji/mealnote/ui/detail/RestaurantDetailViewModel.kt
app/src/main/java/com/fanji/mealnote/ui/theme/

app/src/test/java/com/fanji/mealnote/ui/FormattersTest.kt
app/schemas/com.fanji.mealnote.data.local.AppDatabase/
```

## 13. 可直接复制到新对话的提示词

```text
请接手 C:\Users\2540\Desktop\饭 下的“饭记”Android 项目。先完整阅读 C:\Users\2540\Desktop\饭\HANDOFF.md、PROJECT_PLAN.md、README.md 和 CHANGELOG.md，并检查实际代码，不要假设旧 APK 是最新的。

第一步先统一清理吃后评价里遗留的星级/价格参数，并从英文路径 C:\Users\2540\mealnote-workspace 运行 testDebugUnitTest、lintDebug、assembleDebug，确保数据库 v1→v2 Migration 和现有数据安全。当前项目不是 Git 仓库；未经我明确要求不要 commit。

UI 不要继续凭感觉直接改 Compose。先让我提供“红黑食录”的首页、新增、评价、详情和图片记录截图/录屏；随后使用已安装的 mobile-app-design-builder、ui-ux-kit 和 visualize 做流程、视觉方向、可交互原型及 DESIGN_SYSTEM.md。Figma 插件已连接，但账号是 View 席位，写入失败时先提示权限问题。等我确认设计后，再使用 compose-circuit-skills 实现 Compose UI并完整测试。

产品已确认：两种入口“听说了，之后吃”和“已经吃了”；想吃只需店名、可选地址和一张推荐图；吃后评价只有夯/一般/避雷、餐品名字、照片和可选附录。不要重新加入星级、菜系、标签、人均、推荐链接或强制感想。
```
