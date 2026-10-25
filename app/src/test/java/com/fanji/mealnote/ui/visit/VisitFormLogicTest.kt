package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.ui.components.ledgerSummaryLabel
import org.junit.Assert.*
import org.junit.Test

class VisitFormLogicTest {
    @Test fun referencePriceIsNotAutomaticallyBooked() {
        val state = AddVisitUiState(dateMillis = 100).applyVisitPrefill(VisitPrefill("面", "人均60，3个人"))
        assertEquals("", state.priceText)
        assertNull(state.effectiveAmountMinor)
        assertEquals(1, state.personCount)
    }
    @Test fun explicitReferenceUsesNormalPersonAndAmountRules() {
        val text = "人均60，3个人"
        val people = personCountForPrice(text, 1, false)
        assertEquals(3, people)
        assertEquals(18000L, AddVisitUiState(dateMillis = 100, priceText = text, personCount = people).effectiveAmountMinor)
    }
    @Test fun manuallySelectedPeopleAreNotOverwritten() {
        assertEquals(2, personCountForPrice("人均60，3个人", 2, true))
        assertEquals(12000L, AddVisitUiState(dateMillis = 100, priceText = "人均60，3个人", personCount = 2).effectiveAmountMinor)
    }
    @Test fun removedPeopleTextResetsUntouchedDefault() { assertEquals(1, personCountForPrice("128", 3, false)) }
    @Test fun successfulImportIsSilent() { assertNull(photoImportFeedback(3, 3, 3, null)) }
    @Test fun partialFailureReportsImportedAndFailed() {
        assertEquals("已导入 2 张，1 张未导入；部分文件不是有效图片，可重新选择", photoImportFeedback(3, 3, 2, PhotoStore.ImportFailure.NOT_AN_IMAGE))
    }
    @Test fun allFailuresRemainVisible() { assertTrue(photoImportFeedback(2, 2, 0, null)!!.contains("2 张未导入")) }
    @Test fun capacityOmissionIsNotDescribedAsReadFailure() {
        val text = photoImportFeedback(5, 2, 2, null)!!
        assertTrue(text.contains("3 张未尝试"))
        assertFalse(text.contains("读取失败"))
    }
    @Test fun failureAndCapacityAreReportedSeparately() {
        val text = photoImportFeedback(5, 2, 1, null)!!
        assertTrue(text.contains("1 张未导入"))
        assertTrue(text.contains("3 张未尝试"))
    }
    @Test fun ledgerLabelHasExactlyOneCurrencySign() {
        assertEquals("自动识别 · ¥128 · 2 人", ledgerSummaryLabel(12800, 2, false))
        assertEquals(1, ledgerSummaryLabel(12800, 2, true).count { it == '¥' })
    }
    @Test fun missingAmountIsExplicitlyNotBooked() {
        assertEquals("自动识别 · 未入账", ledgerSummaryLabel(null, 1, false))
        assertEquals("已确认 · 未入账", ledgerSummaryLabel(null, 1, true))
    }
}
