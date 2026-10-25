package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.MAX_DISHES_LENGTH
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.*
import org.junit.Test

/** 快记只复用餐品，不把历史感受和金额冒充本次事实。 */
class VisitPrefillTest {
    private fun record(id: Long = 9, restaurantId: Long = 1, dishes: String = "牛肉面、小笼包", time: Long = 100) =
        DiningRecordEntity(id = id, restaurantId = restaurantId, eatenAt = time, createdAt = time,
            verdict = Verdict.BAD, dishes = dishes, priceText = "人均60，3个人", amountMinorUnits = 18000, personCount = 3, note = "上次备注")

    @Test fun sameRestaurantCopiesOnlyDishesAndReference() {
        assertEquals(VisitPrefill("牛肉面、小笼包", "人均60，3个人"), prefillFromRecord(record(), 1))
    }
    @Test fun missingSourceDoesNotPrefill() { assertNull(prefillFromRecord(null, 1)) }
    @Test fun otherRestaurantDoesNotPrefill() { assertNull(prefillFromRecord(record(restaurantId = 2), 1)) }
    @Test fun emptySourceRemainsEmpty() {
        assertEquals(VisitPrefill("", ""), prefillFromRecord(record(dishes = "").copy(priceText = ""), 1))
    }
    @Test fun prefillHasNoOtherFactFields() {
        assertEquals(setOf("dishes", "referencePriceText"), VisitPrefill::class.java.declaredFields.map { it.name }.filterNot { '$' in it }.toSet())
    }
    @Test fun currentFactsRemainUnchanged() {
        val state = AddVisitUiState(dateMillis = 1234, verdict = Verdict.MEH, priceText = "本次", note = "今天",
            photoPaths = listOf("new"), personCount = 2, amountMinorUnits = 123, amountOverridden = true)
        assertEquals(state.copy(dishes = "面", referencePriceText = "60"), state.applyVisitPrefill(VisitPrefill("面", "60")))
    }
    @Test fun latePrefillCannotOverwriteTouchedOrBusyState() {
        val base = AddVisitUiState(dateMillis = 100, dishes = "已输入")
        for (state in listOf(base.copy(prefillTouched = true), base.copy(isSaving = true), base.copy(saved = true), base.copy(isImportingPhotos = true))) {
            assertSame(state, state.applyVisitPrefill(VisitPrefill("旧餐品", "200")))
        }
    }
    @Test fun suggestionsSortByTimeThenIdAndExcludeOtherRestaurant() {
        assertEquals(listOf("最新", "同日较新", "同日较旧", "旧"), recentDishSuggestions(listOf(
            record(1, dishes = "旧", time = 1), record(3, dishes = "同日较新", time = 2),
            record(2, dishes = "同日较旧", time = 2), record(4, dishes = "最新", time = 3),
            record(5, restaurantId = 2, dishes = "别家", time = 4)), 1))
    }
    @Test fun suggestionsSplitExplicitSeparatorsAndDeduplicate() {
        assertEquals(listOf("面", "红烧 牛肉", "汤", "饭", "茶", "饺子", "粥"), recentDishSuggestions(listOf(
            record(dishes = "面、红烧 牛肉，汤,饭\n茶;饺子；粥、面、  ")), 1))
    }
    @Test fun suggestionsAreLimitedToEight() {
        assertEquals((1..8).map { "餐品$it" }, recentDishSuggestions(listOf(record(dishes = (1..10).joinToString("、") { "餐品$it" })), 1))
    }
    @Test fun blankRecordsProduceNoSuggestions() { assertTrue(recentDishSuggestions(listOf(record(dishes = " \n、")), 1).isEmpty()) }
    @Test fun appendDoesNotDuplicateAndPreservesTypedText() {
        assertEquals(" 面，汤 ", appendRecentDish(" 面，汤 ", "汤"))
        assertEquals("面、汤", appendRecentDish("面", " 汤 "))
        assertEquals("面", appendRecentDish("面", " "))
    }
    @Test fun emojiTruncationRespectsCodePoints() {
        val result = appendRecentDish("", "🍜".repeat(MAX_DISHES_LENGTH + 1))
        assertEquals(MAX_DISHES_LENGTH, result.codePointCount(0, result.length))
        assertEquals("🍜".repeat(MAX_DISHES_LENGTH), result)
        assertEquals(MAX_DISHES_LENGTH, appendRecentDish("🍜".repeat(MAX_DISHES_LENGTH - 1), "汤").codePointCount(0, MAX_DISHES_LENGTH * 2 - 1))
    }
}
