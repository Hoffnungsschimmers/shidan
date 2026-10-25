package com.fanji.mealnote.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 将业务时间在 [zone] 中的自然日转换为 Material DatePicker 使用的 UTC 零点。
 *
 * 仅用于选择器初值，不回写或迁移已有记录；原时间的时分秒不参与日期选择。
 */
fun businessMillisToPickerUtcMillis(businessMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(businessMillis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

/**
 * 将 Material DatePicker 的 UTC 自然日转换为 [zone] 中当天起点的业务时间。
 *
 * 仅在确认选择日期后使用。由 atStartOfDay 处理夏令时，而不是加减固定时差；
 * 日期往返保持自然日，业务时间的时分秒会归一到当天起点。
 */
fun pickerUtcMillisToBusinessMillis(pickerUtcMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(pickerUtcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
