package com.fanji.mealnote.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 枚举转换器测试。
 *
 * 重点覆盖**解码容错**：历史数据库或人工改库可能留下未知枚举名，此时必须回落到默认值
 * 而不是抛出异常，否则整张表读取都会失败。
 */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `餐厅状态往返转换保持一致`() {
        RestaurantStatus.entries.forEach { status ->
            assertEquals(status, converters.stringToRestaurantStatus(converters.restaurantStatusToString(status)))
        }
    }

    @Test
    fun `评价往返转换保持一致`() {
        Verdict.entries.forEach { verdict ->
            assertEquals(verdict, converters.stringToVerdict(converters.verdictToString(verdict)))
        }
    }

    @Test
    fun `未知餐厅状态回落到待探访而非崩溃`() {
        assertEquals(RestaurantStatus.WANT_TO_EAT, converters.stringToRestaurantStatus("LEGACY_UNKNOWN"))
        assertEquals(RestaurantStatus.WANT_TO_EAT, converters.stringToRestaurantStatus(null))
        assertEquals(RestaurantStatus.WANT_TO_EAT, converters.stringToRestaurantStatus(""))
    }

    @Test
    fun `未知评价回落到一般而非崩溃`() {
        assertEquals(Verdict.MEH, converters.stringToVerdict("FIVE_STAR"))
        assertEquals(Verdict.MEH, converters.stringToVerdict(null))
        assertEquals(Verdict.MEH, converters.stringToVerdict(""))
    }
}
