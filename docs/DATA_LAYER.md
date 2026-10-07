# 数据层(DATA LAYER)

> 代码级参考,由逐行阅读源码得出。稳定约束见 `AGENTS.md` 第 4 节;数据安全不变式见 `docs/SECURITY_PRIVACY.md`。
> 包路径 `com.fanji.mealnote.data`。分层:DAO 只做 SQL/映射,Repository 负责跨表一致性与文件生命周期,ViewModel 只消费 `MealResult`。

---

## 1. MealRepository — 唯一业务数据入口

`data/MealRepository.kt`。负责输入校验、事务边界、「数据库行 ↔ 磁盘照片文件」同步回收。

常量:`MAX_PHOTOS_PER_RECORD=9`、`MAX_PRICE_TEXT_LENGTH=40`、`MAX_DISHES_LENGTH=500`、`MAX_NOTE_LENGTH=2000`、`MAX_PERSON_COUNT=20`。

| 方法 | 用途与要点 |
|---|---|
| `observeRestaurants()` / `observeAllDiningRecords()` / `observeAllPhotos()` / `observeRestaurantWithRecords(id)` | 响应式查询直通 DAO |
| `getRestaurant(id)` / `getDiningRecord(recordId)` / `getPhotoPathsForRestaurant(id)` | 一次性读取,供编辑表单初始化(刻意不订阅,避免上游发射覆盖未保存输入) |
| `addRestaurant(name, address, photoPaths)` | `name.trim()` 空→`InvalidInput`;仅取 `photoPaths.firstOrNull()` 作封面;初始 `WANT_TO_EAT` |
| `updateRestaurant(restaurant)` | 事务内更新并读旧封面;**提交后**若封面被替换且旧值非空,`deleteUnreferencedFiles` 回收旧封面 |
| `addDiningRecord(restaurantId, eatenAt, verdict, dishes, priceText, note, photoPaths, amountMinorUnits?=null, personCount=1)` | 事务内「插记录+插照片+餐厅状态推进 EATEN」原子完成;照片先 `distinct().take(9)` |
| `updateDiningRecord(...)` | 事务内更新字段、删旧照片行、按新顺序重建;事务内收集「不再被引用的旧路径」,**提交后**删文件 |
| `deleteDiningRecord(recordId)` | 事务内删记录 + 同步餐厅状态(无记录回退 WANT_TO_EAT);提交后回收照片 |
| `deleteRestaurant(restaurantId)` | 事务内收集用餐照片+封面路径,删餐厅(外键级联删行);提交后回收全部文件 |
| `replaceAllData(...)` | 备份导入全量替换(见下) |
| `cleanupOrphanPhotos()` / `photoDirectorySizeBytes()` | 设置页「清理存储 / 存储用量」 |
| `get*ForBackup()` | 备份读取 |

核心不变量:
- **先提交事务、再删文件**:全部删除路径一致遵守。颠倒会导致回滚后仍引用已删文件,形成不可修复坏记录;正确顺序最坏留孤儿文件(可 `cleanupOrphanPhotos` 回收)。
- **删除守卫 `deleteUnreferencedFiles`**:删前重读全表引用集合(`getAllPhotoPaths + getRestaurantCoverPaths`),只删无任何行引用的文件(一张用餐照片可兼作封面,被两处引用)。必须事务提交后调用(事务内查询读到未提交中间态)。
- `amountMinorUnits?.takeIf { it > 0 }`:非正数金额落库为 `null`(0=未记录,免费餐写备注)。
- `personCount.coerceIn(1, 20)`;文本字段入库前 `normalizeText(limit)`。
- 业务分支 `RestaurantNotFound`/`RecordNotFound` 通过事务内抛 `RestaurantMissingException`/`RecordMissingException` 表达。
- `runCatchingDb`:`CancellationException` 重抛;`*Missing`→领域错误;`IOException`→`PhotoIoFailure`;其他 `Exception`→`DatabaseFailure`。空指针/SQL 约束错误不伪装,继续暴露。
- `replaceAllData` 清空顺序 photos→records→restaurants(与外键方向相反);沿用备份原始 id;按 `validRestaurantIds`/`validRecordIds` 过滤孤立行;`filePath` 用 `resolvedPaths` 映射,缺失则跳过;旧受管文件提交后统一回收。

## 2. MealDao — 纯 SQL

`data/local/MealDao.kt`。只做 SQL 与映射,无业务校验,被 Repository 独占调用。

- 餐厅:`observeRestaurants()`(`ORDER BY updatedAt DESC`)、`observeRestaurantWithRecords(id)`(`@Transaction`,关系查询同一快照)。
- 记录:`observeAllDiningRecords()`(`ORDER BY eatenAt DESC, id DESC`,SQL 层锁定「最近在上」)、`countDiningRecords(id)`。
- 照片:`deletePhotosForRecord`(只删行)、`getPhotoPathsForRestaurant`(JOIN,`ORDER BY d.eatenAt DESC, p.sortOrder ASC`)、`getAllPhotoPaths`。
- 孤儿判定:`getRestaurantCoverPaths()`(`WHERE recommendationPhotoPath != ''`)。
- 导出:`getAllRestaurants/DiningRecords/Photos`(`ORDER BY id ASC`);清空:`clearPhotos/clearDiningRecords/clearRestaurants`。

## 3. AppDatabase / Converters / Models

**AppDatabase**(`data/local/AppDatabase.kt`):`@Database(version=4, exportSchema=true)`,`@TypeConverters(Converters)`,抽象 `mealDao()`。每升 version 必须同步 Migration + 重导 `app/schemas/<版本>.json`;**禁止** `fallbackToDestructiveMigration()`。

**Converters**(`data/local/Converters.kt`):枚举 ↔ TEXT。写 `enum.name`;解码用 `entries.firstOrNull{it.name==value}`(非 `valueOf`)容错——`RestaurantStatus` 回落 `WANT_TO_EAT`、`Verdict` 回落 `MEH`。因此**枚举常量名即持久化编码,重命名/删除等同 schema 变更**。

**Models**(`data/local/Models.kt`):
- `RestaurantStatus{WANT_TO_EAT, EATEN}`、`Verdict{GOOD, MEH, BAD}`。
- `RestaurantEntity`(表 `restaurants`,索引 status/name):id、name(唯一必填)、address、recommendationPhotoPath(空串=未设)、status、createdAt、updatedAt。遗留只读列 `city/cuisine/tags/priceHint/sourceUrl/sourceNote`。
- `DiningRecordEntity`(表 `dining_records`,外键 restaurantId CASCADE):id、restaurantId、eatenAt、verdict(默认 GOOD)、dishes、`priceText`(v3,`@ColumnInfo(defaultValue="''")`)、`amountMinorUnits: Long?`(v4,**不带 DEFAULT**,null=未记金额)、`personCount`(v4,`@ColumnInfo(defaultValue="1")`)、note、createdAt。遗留列 `star`、`perPersonCost`。
- `PhotoEntity`(表 `photos`,外键 diningRecordId CASCADE):id、diningRecordId、filePath(私有目录绝对路径)、sortOrder。
- 关系:`DiningRecordWithPhotos`、`RestaurantWithRecords`。

## 4. 迁移(DatabaseModule)

`di/DatabaseModule.kt`,Hilt 单例。全部 `ALTER TABLE ADD COLUMN`(无数据搬运):

| 迁移 | SQL |
|---|---|
| 1→2 | `ALTER TABLE restaurants ADD COLUMN recommendationPhotoPath TEXT NOT NULL DEFAULT ''` |
| 2→3 | `ALTER TABLE dining_records ADD COLUMN priceText TEXT NOT NULL DEFAULT ''` |
| 3→4 | `ADD COLUMN amountMinorUnits INTEGER`(可空无 DEFAULT)+ `ADD COLUMN personCount INTEGER NOT NULL DEFAULT 1` |

`provideDatabase`:`Room.databaseBuilder(..., "meal_note.db").addMigrations(1→2,2→3,3→4)`,未启用 destructive migration。

**三方一致性铁律**:实体 `@ColumnInfo.defaultValue` ⇔ Migration SQL `DEFAULT` ⇔ schema json 逐字一致(`priceText=''`、`personCount=1`;`amountMinorUnits` 三方均无 DEFAULT)。

schema 版本列变化(`app/schemas/*.json`):v1 起遗留列(city/cuisine/tags/priceHint/sourceUrl/sourceNote、star/perPersonCost)始终保留;v2 加封面列;v3 加 priceText;v4 加 amountMinorUnits+personCount。v4 identityHash `b93fd210...`。

## 5. PhotoStore — 私有照片目录唯一读写入口

`data/PhotoStore.kt`,目录 `files/photos/`。常量:`MAX_IMAGE_EDGE_PX=2048`、`JPEG_QUALITY=88`、`MAX_RAW_COPY_BYTES=32MB`、`ORPHAN_MIN_AGE_MS=10min`。

- `importUris` / `importUrisWithReason`(附首个失败原因 `UNREADABLE`/`NOT_AN_IMAGE`) / `importFile`(备份恢复,直接字节复制不重编码) / `createCameraTarget`(经 FileProvider)。
- `deleteOwnedPhoto/deleteOwnedPhotos`(删前 `isOwnedByApp` 校验)、`deleteOrphanPhotos(referenced)`、`photoDirectorySizeBytes`。

安全/不变量:
- **路径穿越防护 `isOwnedByApp`**:`canonicalFile` 比较 + 要求文件**直接父目录**即照片目录(`canonical.parentFile == directory`)。所有删除都经此校验。
- 导入两阶段:先 `decodeDownsampled`(`inJustDecodeBounds` 算 2 的幂 `inSampleSize` + EXIF 方向校正);失败退回 `copyRawBytes`(≤32MB),再 `isImageFile` 校验文件头魔数(注:MP4 会被 ftyp 误放行,已知近似)。
- 孤儿清理跳过 `lastModified > cutoff` 新文件(避免误删导入中图片);引用集合用 `canonicalPath` 归一。
- `writeBitmap` 不 recycle(回收在 `importSingle` 的 finally),避免 `Cannot draw recycled bitmap`。全程 `Dispatchers.IO`;结果写 `AppLog`(脱敏)。

## 6. ShareImageStore

`data/ShareImageStore.kt`,目录 `cacheDir/shared/`(不计入照片用量、不被孤儿清理)。`PNG_QUALITY=100`、`RETENTION_MS=24h`。`writeShareImage` 写 PNG→FileProvider content URI(避免 `FileUriExposedException`);写前 `removeExpired` 删超 24h 文件;`CancellationException` 重抛并删半成品。

## 7. BackupStore — ZIP 备份读写

`data/backup/BackupStore.kt`。包结构:`manifest.json / restaurants.json / dining_records.json / photos.json / photos/<name>.jpg`。常量:`MAX_JSON_BYTES=32MB`、`MAX_PHOTO_BYTES=32MB`、`MAX_TOTAL_BYTES=2GB`、`MAX_NAME_LENGTH=128`、`MAX_PICKED_FILE_BYTES=512MB`。

- `export(target)`:读三表→流式打包。照片稳定命名 `record{recordId}_{sortOrder}.jpg`,封面 `cover{restaurantId}.jpg`(命名空间隔离,封面复用用餐照片也另存一份)。遗留字段不导出,金额为空不写键。
- `restore(source)`:轻量校验(>512MB 拒绝;扩展名明确非 zip 拒绝)→解压到 `cacheDir/backup_staging`→`importFile` 复制→`repository.replaceAllData`;finally 清理暂存。
- `defaultFileName()`:`mealnote-backup-yyyyMMdd-HHmm.zip`。

读取防御 `readArchive`:每 JSON `readTextCapped(32MB)`;单文件 `copyToCapped(32MB)`;累计 `>2GB` 抛 IOException(**ZIP 炸弹防护**);**路径穿越防护**只处理 `photos/` 前缀非目录条目 + `File(name).name` 只取文件名 + 名长 ≤128;版本校验 `formatVersion` 必须 `>0 && <= FORMAT_VERSION`(拒更高版本);解析容错(`optString/optLong`,未知枚举回落,封面缺失退化无封面,`amountMinorUnits` 经 `takeIf{>0}`,`personCount` `coerceIn(1,20)`)。错误映射:IOException→PhotoIoFailure、JSONException→InvalidInput、其他→DatabaseFailure。SAF `queryDisplayName/Size` 按列名解析索引。

`BackupModels.kt`:`FORMAT_VERSION = 1`。新增字段给默认值(`optXxx` 读),只有破坏性变更才升版本,导入只接受 `<=` 当前版本。

## 8. WebDavStore — 同步最小实现

`data/webdav/WebDavStore.kt`,仅 PUT/GET,远端固定单文件,上传即覆盖,无版本/增量/自动同步。用 `HttpURLConnection`(不引 OkHttp)。`TIMEOUT_MS=30_000`、`MAX_DOWNLOAD_BYTES=512MB`。

- `upload()`:`checkConfig`→`buildLocalBackup`(经 FileProvider 桥接 `BackupStore.export`→`cacheDir/webdav_upload.zip`)→`putFile`→finally 删本地。
- `downloadAndRestore()`:`getFile`→FileProvider Uri→复用 `backupStore.restore`(复用全套防护)→finally 删本地。
- `openConnection`:显式超时、`instanceFollowRedirects=false`、Basic Auth。

安全(**WebDAV URL 安全重点**):
- `isAllowedScheme`:只允许 https;http 仅放行局域网(localhost/127.0.0.1/192.168./10./172.16-31.)。
- **不自动跟随重定向**:3xx 一律中止(防 Basic 凭证送第三方);重定向到非 https 返回 -2,其他 3xx 返回 -3。
- **凭证脱敏 `redactUrlCredentials`**(internal,测试锁死):剥离 `user:pass@`。`remoteUrlForDisplay()` 回显路径洗凭证;`displayHost()` 只留主机名。
- 错误文案:401/403→账密错误;404→带回显地址+匿名提示;-1→文件过大;-2→重定向到不安全地址已中止;-3→检查跳转。日志不记 Location 原文。

`WebDavPreference.kt`:`SharedPreferences` 文件 `mealnote_webdav`;`WebDavConfig(serverUrl, username, password, remoteName=DEFAULT_REMOTE_NAME="mealnote-backup-latest.zip")`;**密码明文存私有目录**(界面已提示,建议专用账号)。

## 9. AppLog — 运行日志

`data/log/AppLog.kt`。内存环形缓冲(`MAX_MEMORY_LINES=500`)+ 落盘追加(`cacheDir/logs/mealnote.log`,`MAX_FILE_BYTES=200KB` 超限滚动丢前半),`Mutex` 串行。格式 `MM-dd HH:mm:ss.SSS L/tag: msg`。`sanitizeTag` 只留字母数字下划线取 32 字符;`appendSingleLine` 把 `\n\r` 换空格;`exportText()` 带设备/版本头去重取尾 500 行;`Uri.describeForLog()` 只留 `scheme:authority`。

**隐私边界**:只记事件与原因,不记店名/地址/餐品/备注/花费原文/文件绝对路径/URI 明文/备份内容;异常只记类名 + 前 200 字。

## 10. 结果模型与文本/金额工具

- **MealError.kt**(含 `MealResult`):`sealed MealError`:`RestaurantNotFound`、`RecordNotFound`、`InvalidInput(reason)`、`PhotoIoFailure`、`StorageFull`、`DatabaseFailure(cause)`。`sealed MealResult<T>`:`Success`/`Failure`,含 `map`、`getOrNull`。可预期失败必须走 `MealResult`,禁止假成功。
- **MealText.kt**:`String.normalizeText(limit)`:`trim()`(含全角 U+3000)→用 `codePointCount`/`offsetByCodePoints` **按 code point 截断**(非 `take(n)`),防切碎代理对损坏备份。超长静默截断。
- **AmountText.kt**:`parseLedgerAmountMinor(text, personCount): Long?`:先去千分位逗号;**仅「人均」是单价需 × 人数,其余数字即整桌总额不乘人数**(「3个人吃了240」→24000 而非 720);`BigDecimal` ×100 `HALF_UP`;上限 `MAX_LEDGER_MINOR=100_000_000` 分(¥100 万)越界返 null;无法解析返 null。`detectPersonCount` 探测「3个人/两人/2位」限 1..20。已知近似:`30-40` 取 40,数字紧跟单位(积分/分钟)会误判为金额。

## 11. 设置偏好六件套(data/settings/)

统一模式:`SharedPreferences` + `StateFlow`,零新依赖;枚举常量名即编码,读取容错回落默认。

| 偏好 | 文件 / key | 要点 |
|---|---|---|
| `ThemePreference` | `mealnote_settings` / `theme_mode` | `ThemeMode{SYSTEM,LIGHT,DARK}` 默认 SYSTEM;`resolveDark()@Composable` |
| `MotionPreference` | 同文件 / `fluid_motion`+`fluid_motion_manual` | 首装默认按设备(`isLowRamDevice` 或 API≤29 默认关);手动切后以手动为准 |
| `RandomPreference` | 同文件 / `random_scope`+`random_include_want`+`random_exclude_days` | `RandomScope{RECOMMENDED, RECOMMENDED_AND_OK, ALL}`;`RANDOM_EXCLUDE_DAY_OPTIONS=[0,7,14,30]`;默认 `RECOMMENDED_AND_OK, includeWantToList=true, excludeRecentDays=0` |
| `BudgetPreference` | 同文件 / `monthly_budget_minor` | 每月预算(整数分,null=未设);`parseBudgetYuanToMinor` 元→分解析并封顶;刻意不做通知(避免 `POST_NOTIFICATIONS`) |
| `ListSortPreference` | 同文件 / `list_sort` | 清单三档排序(最近更新/添加/名称),未知值回落默认 |
| `BackupStatePreference` | 同文件 / `last_backup_at` | 本地导出与 WebDAV 上传成功后 `markBackedUp()`;`backupFreshnessLabel` 纯函数出「从未/今天/昨天/N 天前」文案,时钟回拨兜底 |

六者共用 `mealnote_settings`;WebDAV 用独立 `mealnote_webdav`(存凭据,已从系统备份排除,见 SECURITY_PRIVACY §2)。

## 11b. 账本 CSV 导出(data/export/)

`LedgerCsv.kt`(纯逻辑)+ `LedgerCsvStore.kt`(SAF 写出)。`buildLedgerCsv(records, zone)` 产出
UTF-8 **BOM** + RFC4180 转义(逗号/引号/换行;emoji 按 code point 保真),入账金额由整数分换算、
未记账留空(不写 0)。`LedgerCsvStore.write(target, csv)` 经 SAF `OutputStream` 写出,
`CancellationException` 重抛。入口在设置页「导出账本 CSV」;金额属用户数据禁止进 AppLog。
测试:`CsvExportSafetyTest`。

## 12. 整体数据流与约束速查

1. Hilt 提供 `AppDatabase`(单例 `meal_note.db`)→`MealDao`;`PhotoStore/AppLog/各 Preference` 单例。
2. 读:UI → Repository `observe*`(Flow)/`get*`(一次性)→ DAO;照片由 PhotoStore 读私有目录。
3. 写:ViewModel 收集表单+已导入路径 → Repository `add/update/delete*`(事务内改库,提交后回收文件)→ `MealResult`;文本经 `normalizeText`,金额经 `parseLedgerAmountMinor`/`takeIf{>0}`。
4. 照片生命周期:import/camera 落盘(未入库)→表单保存后被引用→删除/替换/取消经守卫回收→孤儿由 `cleanupOrphanPhotos`+`deleteOrphanPhotos` 清理。
5. 备份:export 读三表打包→ZIP;restore 解压暂存→importFile→replaceAllData。WebDAV 复用同一 export/restore 加网络层。

约束速查:①跨表写必须在 `withTransaction` 内原子;②先提交事务再删文件;③删除守卫(双引用场景);④迁移三方一致 + 禁 destructive;⑤枚举名即编码 + 遗留列永久保留;⑥金额非正落 null、仅人均乘人数;⑦文本按 code point 截断;⑧路径穿越防护(PhotoStore + BackupStore);⑨ZIP 炸弹/超大文件防护;⑩WebDAV 仅 https、不跟随重定向、凭证脱敏;⑪`CancellationException` 一律重抛;⑫可预期失败走 `MealResult`,程序缺陷继续抛。
