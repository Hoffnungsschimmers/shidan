package com.fanji.mealnote.data

import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MealResult] 与 [MealError] 的行为测试。
 *
 * 结果类型是数据层与 UI 层之间的契约，必须保证 `map` 不吞掉失败、`getOrNull` 不把
 * 失败伪装成空成功。
 */
class MealResultTest {

    @Test
    fun `map 对成功值做变换`() {
        val result: MealResult<Int> = MealResult.Success(2)
        val mapped = result.map { it * 3 }
        assertEquals(MealResult.Success(6), mapped)
    }

    @Test
    fun `map 不改变失败结果`() {
        val error = MealError.RestaurantNotFound
        val result: MealResult<Int> = MealResult.Failure(error)
        val mapped = result.map { it * 3 }
        assertTrue(mapped is MealResult.Failure)
        assertEquals(error, (mapped as MealResult.Failure).error)
    }

    @Test
    fun `getOrNull 对失败返回 null 而非默认值`() {
        val failure: MealResult<Long> = MealResult.Failure(MealError.PhotoIoFailure)
        assertEquals(null, failure.getOrNull())
        assertEquals(7L, MealResult.Success(7L).getOrNull())
    }

    @Test
    fun `错误类型可用于穷举分支`() {
        // 编译期穷举检查：新增 MealError 子类时此 when 会因不完整而编译失败，
        // 从而强制所有展示层补全新文案。
        fun describe(error: MealError): String = when (error) {
            MealError.RestaurantNotFound -> "餐厅不存在"
            MealError.RecordNotFound -> "记录不存在"
            is MealError.InvalidInput -> error.reason
            MealError.PhotoIoFailure -> "图片读写失败"
            MealError.StorageFull -> "存储空间不足"
            is MealError.DatabaseFailure -> "数据库错误"
        }
        assertEquals("餐厅不存在", describe(MealError.RestaurantNotFound))
        assertEquals("名称为空", describe(MealError.InvalidInput("名称为空")))
        assertEquals("存储空间不足", describe(MealError.StorageFull))
    }

    @Test
    fun `评价枚举顺序稳定以保证持久化兼容`() {
        // 枚举常量名写入数据库（Converters 使用 name），顺序与名称都不可随意变更。
        assertEquals(listOf("GOOD", "MEH", "BAD"), Verdict.entries.map { it.name })
    }
}
