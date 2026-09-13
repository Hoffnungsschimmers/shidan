package com.fanji.mealnote.data.backup

/**
 * 备份格式常量。
 *
 * 与数据库实体解耦的原因：实体上带有历史遗留字段（`city`、`cuisine`、`tags` 等
 * `@Deprecated` 列），若直接序列化实体会把无意义的旧字段写进备份文件，且一旦实体改名
 * 就会破坏所有历史备份的兼容性。备份 JSON 的字段名在此处显式约定。
 *
 * 字段命名与 `PROJECT_PLAN.md` 8.3 的包结构保持一致：
 * ```
 * mealnote-backup-YYYYMMDD-HHmm.zip
 * ├─ manifest.json
 * ├─ restaurants.json
 * ├─ dining_records.json
 * ├─ photos.json
 * └─ photos/<name>.jpg
 * ```
 */
object BackupFormat {
    /**
     * 当前备份格式版本。
     *
     * 1 = 首个正式版本，包含 restaurants / dining_records / photos 三张表与照片文件。
     * 导入时只接受 `<=` 当前版本的备份：更高版本可能包含本应用无法理解的字段。
     *
     * 兼容性规则：**新增字段必须提供默认值**（读取时用 `optXxx`），只有破坏性变更
     * （删除字段、改变语义）才提升版本号。
     */
    const val FORMAT_VERSION = 1
}
