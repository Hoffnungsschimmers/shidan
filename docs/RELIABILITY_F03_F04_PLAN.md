# 备份可靠性整改计划（F03 / F04 / F11 / F12）

> 制定日期：2026-10-23。基线：`docs/PROJECT_AUDIT_2026-10-22.md` 第 3 节 F03/F04/F11/F12。
> 本文件是**实施计划 + 验收门**，不是实现本身。F01/F02 已由
> `docs/PROJECT_FIX_STATUS_2026-10-22.md` 处理完毕，本计划接在其后。
> 涉及远程/备份的改动发布前必须在真实服务器跑通一次（AGENTS §5-3）；**未经用户逐轮确认不 commit**。

---

## 一、现状核对（读完 `data/backup/BackupStore.kt` 后的确认）

### F03：导出与恢复限额不对称 —— 确认属实

**导入侧**（`readArchive`）已有五道防线：

| 防线 | 常量 | 值 |
|---|---|---|
| 用户可选中文件总长 | `MAX_PICKED_FILE_BYTES` | 512MB |
| ZIP 条目数 | `MAX_ARCHIVE_ENTRIES` | 20 000 |
| 单个条目字节 | `MAX_PHOTO_BYTES` | 32MB |
| 解压后总量 | `MAX_TOTAL_BYTES` | 2GB |
| 单个 JSON 条目 | `MAX_JSON_BYTES` | 32MB |

**导出侧**（`writeArchive`，`BackupStore.kt:209-265`）**没有对应上限**：

- 不校验餐厅/记录/照片条目数是否超 `MAX_ARCHIVE_ENTRIES`；
- 不累计照片字节是否超 `MAX_TOTAL_BYTES` / 单张是否超 `MAX_PHOTO_BYTES`；
- 不校验单张封面/用餐照片文件是否超过 `MAX_PHOTO_BYTES`。

**后果**：数据量大到一定程度（几千张照片、超 2GB 解压量、超 20k 条目）时，应用
**自己导出的包**会被**自己的导入侧拒绝**。用户看到「备份成功」，换机恢复却报
「备份文件格式不正确或已损坏」。这是审计原文「可返回成功却无法被本应用恢复」的直接来源。

### F04：导入预算只累计照片、JSON/未知项不共用 —— 确认属实

- `totalBytes` 只在 `photos/` 分支累加（`BackupStore.kt:446-449`）；
- 四份 JSON 各自 `readTextCapped(MAX_JSON_BYTES)`，**不占总量预算**；
- **重复关键 JSON 条目**：`when` 对同名条目是赋值覆盖，后到的覆盖先到的，
  第一份的字节未被计入任何预算，且先到的合法数据可被后到的恶意条目顶掉；
- 未知前缀条目落入 `else` 什么都不做，不消费条目预算外的任何额度。

### F11：重导出重名 —— 确认属实

`stablePhotoName` = `record{diningRecordId}_{sortOrder}.jpg`（`BackupStore.kt:372-373`）。
若 photos 表存在重复的 `(diningRecordId, sortOrder)`（外部包导入未去重、或手工构造），
导出时 `fileNames` map 用 `associate` 互相覆盖，ZIP 出现重名条目，导入侧写到同一文件
后到的覆盖先到的 → 照片静默丢失。

### F12：金额越界 —— 确认属实

`parseRecords` 的 `amountMinorUnits`（`BackupStore.kt:507-508`）：
`(item.opt("amountMinorUnits") as? Number)?.toLong()?.takeIf { it > 0 }`
JSON 里写 `1e20`（Double）时 `toLong()` 窄化溢出；写超 `Long` 范围的 BigDecimal 同理。

---

## 二、实施设计（不破坏现有包格式，F03 不需要升 FORMAT_VERSION）

### 设计原则

1. **导出的包必须能由自己的导入侧读回** —— 两者共用同一组上限常量；
2. **不静默改写用户数据** —— 超限就明确失败并告诉用户修了什么，不悄悄截断；
3. **错误必须可见** —— 遵循 AGENTS §6「失败不消失、带自查信息」；
4. 保持 `FORMAT_VERSION` 不变（字段与结构不变，只是加校验）。

### F03 · 导出侧加对称上限（`writeArchive` 入口校验）

新增一个共享的「导出前预算检查」，与导入侧常量**同源**（把常量从 `private companion`
提到 top-level 或共享对象，导入导出都引用）：

| 新检查 | 超出时的返回 |
|---|---|
| `restaurants.size + records.size + photos.size > MAX_ARCHIVE_ENTRIES`（照片条目数按 `photos.size`） | `MealError.StorageFull`（文案建议：「数据量超过备份上限，请先清理部分照片后重试」） |
| `photos.size + 有封面的餐厅数 > MAX_ARCHIVE_ENTRIES` 中照片部分 | 同上 |
| 任一照片文件 `File(photo.filePath).length() > MAX_PHOTO_BYTES` | `MealError.PhotoIoFailure`（该张太大） |
| 封面文件同样检查 | 同上 |
| 预估总量（照片字节和 + JSON 估算）> `MAX_TOTAL_BYTES` | `MealError.StorageFull` |

实现要点：
- `export` 在 `writeArchive` 之前先跑『预算检查』，失败直接返回 `Failure`，**不写目标文件**；
- 用与导入完全相同的常量（单文件 32MB / 总量 2GB / 条目 20k），保证「导出不拒绝的东西，
  导入必能接受」；
- 新增单测：构造超 `MAX_PHOTO_BYTES` 的假文件、超条目数的列表，断言导出失败且不写包。

### F03 · **共享限额契约常量**（消除不对称的根因）

把现在 `private companion` 里的 `MAX_PHOTO_BYTES / MAX_TOTAL_BYTES / MAX_ARCHIVE_ENTRIES`
上移到 `internal`（或 `BackupLimits` 对象），`export` 与 `import` 都引用。**这是本批次的
核心动作** —— 之后任何人改导入上限都会连带看到导出用了同一常量，不会再次错位。

### F04 · 导入预算覆盖全部条目

`readArchive` 里把 `totalBytes` 改为累计**所有**条目（含 JSON、含未知条目）：
- 读 JSON 时也 `copyToCapped` 计字节，超 `MAX_JSON_BYTES` 抛错（现在就是这样），
  且**同时计入 `totalBytes`**（超 `MAX_TOTAL_BYTES` 抛错）；
- 未知前缀条目：也 `copyToCapped` 消费预算（**但不落盘**），计入 `totalBytes`；
- **重复关键 JSON**：读到重复的 `MANIFEST_ENTRY / RESTAURANTS_ENTRY / RECORDS_ENTRY / PHOTOS_ENTRY`
  时抛错（拒绝），而不是后者覆盖前者 —— 备份包本来就不该有重复键；
- 新增测试：恶意包重复 key 被拒、未知条目计入总量超限被拒、JSON 计入总量超限被拒。

### F11 · 稳定照片名不重名

`stablePhotoName` 在导出侧生成时，若 `(diningRecordId, sortOrder)` 冲突：
- 给重名的追加短序号 `record{recordId}_{sortOrder}_{n}.jpg`；
- 同时 `photosJson` 里 `fileNames` map 与 ZIP 条目名必须一致（现在天然一致，因为同源）；
- 导入侧无需改（按 `fileName` 解压到独立文件即可），但**补一个「同名不同内容不互相覆盖」的
  读取测试**（构造两个 sortOrder 相同的照片条目）。
- 简化为：`stablePhotoName` 用 `sortOrder` 但冲突时递增后缀，保证 ZIP 内唯一。

### F12 · 金额解析严格化

`parseRecords` 里金额解析改成：
```kotlin
val amount = item.opt("amountMinorUnits")
val minor = (amount as? Number)?.let {
    try {
        it.toLong()  // Double / Long / BigDecimal 都能 toLong，但会窄化
    } catch (e: Exception) { null }
}
```
改为：先验证值落在 `1..Int.MAX_VALUE` 的「分」范围（账本上限已有 `MAX_LEDGER_MINOR`，
约 1 亿元），不在范围内**整行视为坏数据** —— 按既有的「非法行过滤」约定跳过该记录
（与 `personCount` 的 `coerceIn` 处理一致，但金额**不静默夹紧**，超限就跳过）。
加测试：`1e20`、`Long.MAX_VALUE`、`-1`、`0`、正常值、超上限值。

### 测试清单（新增）

| 文件 | 覆盖 |
|---|---|
| `BackupExportLimitsTest` | 导出侧超条目/超单张/超总量均失败、不写包；共享常量与导入一致 |
| `BackupImportBudgetTest` | 重复 key 拒绝、JSON 计入总量、未知条目计入总量超限拒绝 |
| `BackupPhotoNameCollisionTest` | 重名照片不互相覆盖、导出-导入往返字节不变 |
| `BackupAmountSafetyTest` | F12 各越界值的过滤/保留行为 |

---

## 三、验收门

1. `scripts/run-unit-tests.ps1` 全过（含上述新增测试）；
2. `lintDebug` No issues；`assembleDebug / assembleRelease` 通过；
3. **删除/回收分支人工复读**：本批不触碰文件生命周期，但 `deleteUnreferencedFiles`
   的调用链要确认没被误动；
4. 手工验证（可先在 JVM 测试用临时目录做）：制作一个**超上限**备份包（合成文件）导出→
   用本应用导入，确认整次失败且旧数据完好（不出现「成功但缺照片」的中间态）；
5. 真实设备端到端（缺设备，列为待办）：SAF 完整往返 + 真实 WebDAV PUT→GET→恢复。

## 四、明确不做（本批）

- 不升 `FORMAT_VERSION`、不动 schema、不加列；
- 不做「部分恢复保留」（审计 4.2 提到），仍是全量替换单事务；
- 不修 `AppLog` 并发（F15）—— 那是另一批，本批聚焦备份闭环。
