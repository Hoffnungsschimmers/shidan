package com.fanji.mealnote.data.local

import androidx.room.TypeConverter

/**
 * Room 类型转换器：负责枚举与数据库 TEXT 列之间的映射。
 *
 * 设计要点：**解码必须容错**。数据库中可能存放旧版本写入的枚举名，若直接使用
 * [Enum.valueOf]，一旦某个常量被重命名或删除，整张表读取都会抛出
 * `IllegalArgumentException` 并导致应用崩溃。因此这里统一解析失败即回落到默认值，
 * 保证历史数据永远可读。
 */
class Converters {
    @TypeConverter
    fun restaurantStatusToString(value: RestaurantStatus): String = value.name

    @TypeConverter
    fun stringToRestaurantStatus(value: String?): RestaurantStatus =
        RestaurantStatus.entries.firstOrNull { it.name == value } ?: RestaurantStatus.WANT_TO_EAT

    @TypeConverter
    fun verdictToString(value: Verdict): String = value.name

    @TypeConverter
    fun stringToVerdict(value: String?): Verdict =
        Verdict.entries.firstOrNull { it.name == value } ?: Verdict.MEH
}
