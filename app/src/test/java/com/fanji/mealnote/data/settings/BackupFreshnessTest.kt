package com.fanji.mealnote.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 锁定备份新鲜度文案:从未/今天/昨天/N 天前,及时钟回拨兜底。 */
class BackupFreshnessTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val today = LocalDate.of(2026, 9, 26)
    private val now = millis(today, hour = 15)

    @Test
    fun neverBackedUp() {
        assertEquals("从未备份", backupFreshnessLabel(null, now, zone))
        assertEquals("从未备份", backupFreshnessLabel(0L, now, zone))
    }

    @Test
    fun today() {
        assertEquals("今天已备份", backupFreshnessLabel(millis(today, hour = 9), now, zone))
    }

    @Test
    fun yesterday() {
        assertEquals("昨天备份", backupFreshnessLabel(millis(today.minusDays(1)), now, zone))
    }

    @Test
    fun severalDaysAgo() {
        assertEquals("5 天前备份", backupFreshnessLabel(millis(today.minusDays(5)), now, zone))
    }

    @Test
    fun futureTimestampTreatedAsToday() {
        assertEquals("今天已备份", backupFreshnessLabel(millis(today.plusDays(2)), now, zone))
    }
}
