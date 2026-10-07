# 安全与隐私(SECURITY & PRIVACY)

> 稳定规则见 `AGENTS.md` 第 6 节;本文档展开数据安全不变式、隐私红线与其代码落点。
> 事实来源:`PROJECT_OVERVIEW.md`、`DESIGN_SYSTEM.md`、`ROADMAP.md`、`AndroidManifest.xml`、`README.md`。

---

## 1. 权限与网络

- **唯一权限**:`android.permission.INTERNET`(`AndroidManifest.xml`),仅供 WebDAV 同步。
- 不配置同步时**不发起任何网络连接**;不授权 INTERNET 仅同步不可用,其余功能不受影响。
- 导入导出走系统文件选择器(SAF),因此**不申请任何存储权限**。
- `FileProvider`(authority `${applicationId}.fileprovider`,`exported=false`)用于分享卡片图片的临时授权。

## 2. 数据存储位置

- 数据库、照片、备份、日志全部存**应用私有目录**。
- 照片:`files/photos/`;分享卡片图片:`cacheDir/shared/`。
- 数据默认只保存在本机,可通过 ZIP 备份包完整迁移。
- **系统备份排除**(`res/xml/backup_rules.xml`、`data_extraction_rules.xml`):
  `photos/`、`meal_note.db`、**`mealnote_webdav`(存有 WebDAV 账号密码的 SharedPreferences)**
  均被排除——即便用户开启了系统云备份/换机迁移,数据与凭据也不会离开设备。
  新增任何存敏感信息的 prefs 文件时,必须同步在两个 xml 中加 `<exclude>`。

## 3. 数据安全四条核心不变式

1. **磁盘文件只在没有任何数据库行引用它时才允许删除**。所有删除统一走 `MealRepository.deleteUnreferencedFiles()`(一个文件可能同时是店铺封面与用餐照片——用户把用餐照片设成了封面)。
2. **先提交事务,再删除磁盘文件**,失败时数据一致。
3. 编辑表单**只回收「本次导入、且当前未被选中」的图片**(`reclaimableFormPhotos()` 纯函数);原封面与已落库照片无条件保留。
4. `restoredFromSavedState` 必须在构造时求值且声明在 `init` 之前(init 里的持久化协程会在构造期写回 savedStateHandle,否则清理逻辑静默失效——v0.3.3/v0.3.6 两个数据丢失 bug 的共同根因)。

> 删除/回收相关代码改动后必须**人工重读全部分支**:这类 bug 无报错、静默失效。

## 4. 隐私红线:AppLog

`AppLog` 只记事件与**脱敏来源**。**禁止记录**:
- 店名、备注、花费原文、金额
- 完整文件路径、URI 明文
- 备份内容
- 任何凭据

回显地址/文件名前必须用 `redactUrlCredentials()` 洗掉 URL 内的凭据(已用测试 `WebDavUrlSafetyTest` 锁住)。即便脱敏后的地址/文件名也**禁止进 AppLog**。

> 历史教训:旧版 `AppLog` 记的主机名恒为 `https:`(`substringBefore('/')` 撞上协议斜杠),诊断信息从发布起零价值——日志需在真机上读过才能确认有效。

## 5. WebDAV 安全约束

- 仅允许 **https**(内网网段放行 http);
- **Basic 认证**;密码明文存私有目录(已在界面提示);
- 下载复用备份导入的**同一套校验**(不为下载单开一条不校验的路径);
- 重定向防线:防止被重定向到非预期主机(v0.4.0 未暂存层的加固之一);
- **失败必须可见**:失败提示整块错误色 + 显式关闭出口,不自动消失,回显实际地址/文件名(洗掉凭据)以便自查(WebDAV 404 定位即为此)。

## 6. 备份导入安全

导入为**全量替换 + 单事务**(清空顺序 照片→记录→餐厅,与外键方向相反)。校验层:
- 格式版本校验;
- 512MB 总解压量预检(`MAX_PICKED_FILE_BYTES`,经验值);
- 单条目 / 单图限额(`MAX_RAW_COPY_BYTES` 32MB);
- 条目文件名规范化,**阻断路径穿越**(`BackupEntryNameSafetyTest` 覆盖);
- ZIP 炸弹防护。

备份 JSON 为独立格式,**不复用数据库实体**,历史遗留列不导出。

## 7. 照片文件安全

`PhotoStore` 删除强制 **canonical path 校验**,仅允许删除私有照片目录内的文件——防止路径逃逸删到目录外文件。

## 8. 签名与密钥

- Release 用项目根 `mealnote-release.jks` 自签名,凭据在 `local.properties`(`release.storeFile/storePassword/keyAlias/keyPassword`)。
- 两者均已 `.gitignore` 排除(`*.jks`、`local.properties`)。
- **密钥库是单点故障**:丢失后无法为已发布应用推送更新,需妥善备份;上架商店需换正式发布密钥。
- 提交前必须确认 `local.properties` 与 `*.jks` 未入库;**任何情况下不得回显凭据明文**。

## 9. 敏感数据边界(排期红线)

- **金额为空是合法状态**:报表永远区分「没花钱」(0)与「没记金额」(NULL);全识别不出显示「还没有能识别的金额」而非 `¥0`。
- 金额一律整数分;CSV / 备份含金额——均属用户敏感数据,禁止进 AppLog。
- **涉及远程服务器的功能,发布前必须在真实服务器上跑通一次**(单测通过证明不了网络功能可用)。
- 不引入存储 / 定位 / 通知类新权限(如预算提醒只做应用内展示,避免 `POST_NOTIFICATIONS`)。
