package com.fanji.mealnote.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份新鲜度:记录「上次成功备份/上传」的时间,在设置页提示用户多久没备份了。
 *
 * 只是一个善意提醒,不做强制、不发通知。与其它偏好同一模式(SharedPreferences + StateFlow)。
 */
@Singleton
class BackupStatePreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _lastBackupAt = MutableStateFlow(readLast())

    /** 上次成功备份/上传的时间(epoch millis);0 表示从未备份。 */
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    /** 备份/上传成功后调用,记录当前时间。 */
    fun markBackedUp(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(KEY_LAST_BACKUP, nowMillis) }
        _lastBackupAt.value = nowMillis
    }

    private fun readLast(): Long = preferences.getLong(KEY_LAST_BACKUP, 0L).coerceAtLeast(0L)

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_LAST_BACKUP = "last_backup_at"
    }
}

/**
 * 备份新鲜度文案(纯逻辑,便于单测)。按**自然日**计算,与用户「几天前」的直觉一致。
 *
 * - `null` 或 `<=0` → 「从未备份」;
 * - 今天 → 「今天已备份」;昨天 → 「昨天备份」;更早 → 「N 天前备份」;
 * - 时钟回拨导致 last 在未来 → 视为今天(不显示负数)。
 */
internal fun backupFreshnessLabel(lastMillis: Long?, nowMillis: Long, zone: ZoneId): String {
    if (lastMillis == null || lastMillis <= 0L) return "从未备份"
    val lastDate = Instant.ofEpochMilli(lastMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(lastDate, today)
    return when {
        days <= 0L -> "今天已备份"
        days == 1L -> "昨天备份"
        else -> "$days 天前备份"
    }
}
