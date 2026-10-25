package com.fanji.mealnote.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MealDatesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneOffset.UTC
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    @Test
    fun shanghaiMonthStartUsesLocalDayNotPreviousUtcDay() {
        val businessMillis = Instant.parse("2026-09-30T16:30:00Z").toEpochMilli()
        val pickerMillis = businessMillisToPickerUtcMillis(businessMillis, shanghai)

        assertEquals(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), pickerMillis)
        assertEquals(
            Instant.parse("2026-09-30T16:00:00Z").toEpochMilli(),
            pickerUtcMillisToBusinessMillis(pickerMillis, shanghai),
        )
    }

    @Test
    fun utcNormalizesTimeToStartOfSameDay() {
        val businessMillis = Instant.parse("2026-10-01T18:42:31.123Z").toEpochMilli()
        val midnightMillis = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()

        assertEquals(midnightMillis, businessMillisToPickerUtcMillis(businessMillis, utc))
        assertEquals(midnightMillis, pickerUtcMillisToBusinessMillis(midnightMillis, utc))
    }

    @Test
    fun losAngelesMonthEndUsesLocalDayNotNextUtcDay() {
        val businessMillis = Instant.parse("2026-10-01T06:30:00Z").toEpochMilli()
        val pickerMillis = businessMillisToPickerUtcMillis(businessMillis, losAngeles)

        assertEquals(Instant.parse("2026-09-30T00:00:00Z").toEpochMilli(), pickerMillis)
        assertEquals(
            Instant.parse("2026-09-30T07:00:00Z").toEpochMilli(),
            pickerUtcMillisToBusinessMillis(pickerMillis, losAngeles),
        )
    }

    @Test
    fun monthYearAndLeapDayBoundariesRoundTripInBothDirections() {
        val dates = listOf(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2024, 3, 1),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 10, 1),
        )
        for (zone in listOf(shanghai, utc, losAngeles)) {
            for (date in dates) assertDayRoundTrips(date, zone)
        }
    }

    @Test
    fun pickerUsesUtcCalendarDayRegardlessOfTargetZone() {
        val date = LocalDate.of(2026, 10, 1)
        // UTC 零点在洛杉矶属于前一日；UTC 深夜在上海属于后一日，都不能改变选择日期。
        for (zone in listOf(shanghai, utc, losAngeles)) {
            for (hour in listOf(0, 23)) {
                val pickerMillis = date.atTime(hour, 30).toInstant(utc).toEpochMilli()
                assertEquals(
                    date.atStartOfDay(zone).toInstant().toEpochMilli(),
                    pickerUtcMillisToBusinessMillis(pickerMillis, zone),
                )
            }
        }
    }

    @Test
    fun losAngelesSpringDstRoundTripsAndHas23HourDay() {
        val date = LocalDate.of(2026, 3, 8)
        for (dayOffset in -1L..1L) assertDayRoundTrips(date.plusDays(dayOffset), losAngeles)

        val start = pickerUtcMillisToBusinessMillis(pickerMillis(date), losAngeles)
        val next = pickerUtcMillisToBusinessMillis(pickerMillis(date.plusDays(1)), losAngeles)
        assertEquals(Instant.parse("2026-03-08T08:00:00Z").toEpochMilli(), start)
        assertEquals(23L * 60 * 60 * 1_000, next - start)
    }

    @Test
    fun losAngelesAutumnDstRoundTripsAndHas25HourDay() {
        val date = LocalDate.of(2026, 11, 1)
        for (dayOffset in -1L..1L) assertDayRoundTrips(date.plusDays(dayOffset), losAngeles)

        val start = pickerUtcMillisToBusinessMillis(pickerMillis(date), losAngeles)
        val next = pickerUtcMillisToBusinessMillis(pickerMillis(date.plusDays(1)), losAngeles)
        assertEquals(Instant.parse("2026-11-01T07:00:00Z").toEpochMilli(), start)
        assertEquals(25L * 60 * 60 * 1_000, next - start)
        // 秋季重复的两个 01:30 都应落在同一个自然日。
        for (instant in listOf("2026-11-01T08:30:00Z", "2026-11-01T09:30:00Z")) {
            assertEquals(
                pickerMillis(date),
                businessMillisToPickerUtcMillis(Instant.parse(instant).toEpochMilli(), losAngeles),
            )
        }
    }

    @Test
    fun legacyUtcMidnightIsProjectedByExistingLocalDayWithoutMigration() {
        val legacyMillis = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()

        // 不把旧 UTC 零点编码猜作原本的选择日期，仍与当前本地日期展示一致。
        assertEquals(
            pickerMillis(LocalDate.of(2026, 9, 30)),
            businessMillisToPickerUtcMillis(legacyMillis, losAngeles),
        )
        assertEquals(
            pickerMillis(LocalDate.of(2026, 10, 1)),
            businessMillisToPickerUtcMillis(legacyMillis, shanghai),
        )
    }

    private fun pickerMillis(date: LocalDate): Long =
        date.atStartOfDay(utc).toInstant().toEpochMilli()

    private fun assertDayRoundTrips(date: LocalDate, zone: ZoneId) {
        val expectedPicker = pickerMillis(date)
        val expectedBusinessStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        for (hour in listOf(0, 12, 23)) {
            val businessMillis = date.atTime(hour, 30).atZone(zone).toInstant().toEpochMilli()
            val picker = businessMillisToPickerUtcMillis(businessMillis, zone)
            assertEquals("业务日到选择日: $date / $zone / $hour", expectedPicker, picker)
            assertEquals(
                "业务时间往返归一到当天起点: $date / $zone / $hour",
                expectedBusinessStart,
                pickerUtcMillisToBusinessMillis(picker, zone),
            )
        }
        val business = pickerUtcMillisToBusinessMillis(expectedPicker, zone)
        assertEquals("选择日到业务日: $date / $zone", expectedBusinessStart, business)
        assertEquals(
            "选择值反向往返不变: $date / $zone",
            expectedPicker,
            businessMillisToPickerUtcMillis(business, zone),
        )
    }
}
