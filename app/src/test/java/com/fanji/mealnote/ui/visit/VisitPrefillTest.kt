package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「照上次再来一份」预填规则的测试。
 *
 * 这里锁的主要是**设计决策**而非计算：哪些字段该带入、哪些绝不能带。
 * 以后若有人想「顺手把备注也带上」，这些用例会拦下来。
 */
class VisitPrefillTest {

    private fun record(
        restaurantId: Long = 1L,
        verdict: Verdict = Verdict.GOOD,
        dishes: String = "牛肉面、小笼包",
        priceText: String = "128",
        note: String = "环境不错",
    ) = DiningRecordEntity(
        id = 9L,
        restaurantId = restaurantId,
        verdict = verdict,
        dishes = dishes,
        priceText = priceText,
        note = note,
    )

    @Test
    fun `同一家店的记录会带入评价餐品与花费`() {
        val prefill = prefillFromRecord(record(), targetRestaurantId = 1L)

        assertEquals(Verdict.GOOD, prefill?.verdict)
        assertEquals("牛肉面、小笼包", prefill?.dishes)
        assertEquals("128", prefill?.priceText)
    }

    @Test
    fun `来源记录不存在时不预填`() {
        // 记录可能已被他处删除，此时应保持空白表单而不是崩溃。
        assertNull(prefillFromRecord(null, targetRestaurantId = 1L))
    }

    @Test
    fun `来源属于别家店时不预填`() {
        // 记录 id 来自路由参数。若用户先打开 A 店表单、又快速切到 B 店，
        // 把 A 店的餐品写进 B 店会产出一条「看着正常、内容却是错的」记录。
        val other = record(restaurantId = 2L)
        assertNull(prefillFromRecord(other, targetRestaurantId = 1L))
    }

    @Test
    fun `评价会带入而不是固定为推荐`() {
        val prefill = prefillFromRecord(
            record(verdict = Verdict.BAD),
            targetRestaurantId = 1L,
        )
        assertEquals(Verdict.BAD, prefill?.verdict)
    }

    @Test
    fun `空餐品与空花费原样带入`() {
        // 上次没填餐品/花费时不该凭空造出内容，也不该被当成「无来源」而跳过预填。
        val prefill = prefillFromRecord(
            record(dishes = "", priceText = ""),
            targetRestaurantId = 1L,
        )
        assertEquals("", prefill?.dishes)
        assertEquals("", prefill?.priceText)
        assertEquals(Verdict.GOOD, prefill?.verdict)
    }

    @Test
    fun `结果里不可能出现备注`() {
        // VisitPrefill 只有三个字段，这是结构性保证：
        // 备注属于当次的具体感受（「今天排队很久」），带入是误导。
        // 这里用反射确认字段集合，防止以后有人加字段时无意破坏这个约束。
        val fields = VisitPrefill::class.java.declaredFields
            .map { it.name }
            .filterNot { it.contains("$") }   // 编译器生成的辅助字段
            .toSet()

        assertEquals(setOf("verdict", "dishes", "priceText"), fields)
    }
}
