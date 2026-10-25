package com.fanji.mealnote.ui.edit

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.*
import org.junit.Test

class EditVisitLoadingTest {
    private fun record(amount: Long? = null) = DiningRecordEntity(restaurantId = 1, eatenAt = 100, createdAt = 100,
        dishes = "面", priceText = "128", amountMinorUnits = amount, personCount = 2)
    @Test fun editingNotePreservesNullAmountEvenWhenPriceIsRecognizable() {
        val state = EditVisitUiState(dateMillis = 100).applyExistingVisit(record(), listOf("old"), "店", false)
        assertTrue(state.amountOverridden)
        assertNull(state.copy(note = "新备注").effectiveAmountMinor)
        assertNull(state.copy(priceText = "200").effectiveAmountMinor)
    }
    @Test fun onlyExplicitAutoRestoreRecalculatesAmount() {
        val state = EditVisitUiState(dateMillis = 100).applyExistingVisit(record(), emptyList(), "", false)
        assertEquals(12800L, state.copy(amountOverridden = false, amountMinorUnits = null).effectiveAmountMinor)
    }
    @Test fun savedAmountAndOriginalPhotosArePreserved() {
        val state = EditVisitUiState(dateMillis = 100).applyExistingVisit(record(6000), listOf("old"), "店", false)
        assertEquals(6000L, state.effectiveAmountMinor)
        assertEquals(listOf("old"), state.originalPhotoPaths)
        assertEquals(state.originalPhotoPaths, state.photoPaths)
        assertTrue(state.personCountTouched)
    }
    @Test fun restoredDraftRetainsUserInputsAndAmountMode() {
        val draft = EditVisitUiState(dateMillis = 200, dishes = "本次", note = "草稿", priceText = "60", photoPaths = listOf("new"))
        val loaded = draft.applyExistingVisit(record(10000), listOf("old"), "店", true)
        assertEquals(draft.copy(isLoading = false, restaurantName = "店", originalPhotoPaths = listOf("old")), loaded)
        assertEquals(6000L, loaded.effectiveAmountMinor)
    }
}
