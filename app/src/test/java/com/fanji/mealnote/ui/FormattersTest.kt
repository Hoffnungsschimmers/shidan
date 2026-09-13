package com.fanji.mealnote.ui

import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 日期与枚举文案格式化测试。
 *
 * 这些函数直接决定界面上的中文输出，属于回归风险最高的纯逻辑，因此必须锁定格式。
 */
class FormattersTest {

    @Test
    fun `餐厅状态映射为约定术语`() {
        assertEquals("计划探访", RestaurantStatus.WANT_TO_EAT.displayName())
        assertEquals("已用餐", RestaurantStatus.EATEN.displayName())
    }

    @Test
    fun `评价映射为三级术语`() {
        assertEquals("推荐", Verdict.GOOD.displayName())
        assertEquals("尚可", Verdict.MEH.displayName())
        assertEquals("不推荐", Verdict.BAD.displayName())
    }

    @Test
    fun `日期格式化为年月日`() {
        val millis = ZonedDateTime.of(2026, 9, 12, 18, 30, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("2026年9月12日", millis.formatMealDate())
    }

    @Test
    fun `完整日期包含时分`() {
        val millis = ZonedDateTime.of(2026, 1, 5, 9, 7, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("2026年1月5日 09:07", millis.formatFullDate())
    }

    @Test
    fun `小于1KB时以字节展示`() {
        assertEquals("0 B", 0L.formatStorageSize())
        assertEquals("512 B", 512L.formatStorageSize())
        assertEquals("1023 B", 1023L.formatStorageSize())
    }

    @Test
    fun `存储用量按1024进制换算单位`() {
        assertEquals("1.0 KB", 1024L.formatStorageSize())
        assertEquals("1.5 KB", 1536L.formatStorageSize())
        assertEquals("1.0 MB", (1024L * 1024).formatStorageSize())
        assertEquals("2.5 GB", (2560L * 1024 * 1024).formatStorageSize())
    }

    @Test
    fun `存储用量使用西文小数点`() {
        // 某些地区（如德语区）默认使用逗号作为小数点，若跟随系统地区，
        // 同一数值在不同设备上会显示为 "1,5 MB"。此处锁定为西文句点。
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5 KB", 1536L.formatStorageSize())
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `时间线分组标题为年月`() {
        val millis = ZonedDateTime.of(2026, 9, 12, 18, 30, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("2026年9月", millis.formatMonthLabel())
    }

    @Test
    fun `跨年时月份分组不会混在一起`() {
        // 只取 month 字段会把 2025-12 与 2026-12 归为同一组，
        // 因此分组标题必须包含年份。这里用两个同月不同年的时间点验证。
        val december2025 = ZonedDateTime.of(2025, 12, 20, 12, 0, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val december2026 = ZonedDateTime.of(2026, 12, 20, 12, 0, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("2025年12月", december2025.formatMonthLabel())
        assertEquals("2026年12月", december2026.formatMonthLabel())
    }

    @Test
    fun `时间线条目为日加星期`() {
        // 2026-09-12 是星期六。
        val millis = ZonedDateTime.of(2026, 9, 12, 18, 30, 0, 0, ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals("12日 星期六", millis.formatDayLabel())
    }

    @Test
    fun `月份分组标题跟随系统时区`() {
        // 同一条记录在不同时区可能落在不同的月份分组里（例如 UTC 的 12-31 23:00
        // 在东八区已是 1 月 1 日）。分组必须使用本地时区，否则用户会看到
        // 一条记录出现在「上个月」的分组下。
        val utc = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val newYearEveUtc = ZonedDateTime.of(2025, 12, 31, 23, 0, 0, 0, ZoneId.of("UTC"))
                .toInstant().toEpochMilli()
            assertEquals("2025年12月", newYearEveUtc.formatMonthLabel())

            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            assertEquals("2026年1月", newYearEveUtc.formatMonthLabel())
        } finally {
            java.util.TimeZone.setDefault(utc)
        }
    }
}
