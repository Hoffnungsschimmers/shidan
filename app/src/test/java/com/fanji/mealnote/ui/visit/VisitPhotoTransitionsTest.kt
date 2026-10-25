package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.ui.edit.EditVisitUiState
import com.fanji.mealnote.data.unreferencedPhotoCandidates
import com.fanji.mealnote.ui.tryTransition
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class VisitPhotoTransitionsTest {
    @Test fun addSaveSnapshotCannotBeChangedByLatePhotoEvents() {
        val flow = MutableStateFlow(AddVisitUiState(dateMillis = 100, photoPaths = listOf("new")))
        val snapshot = flow.tryTransition { it.copy(isSaving = true) }!!
        assertNull(flow.removeAddVisitPhoto(0))
        assertFalse(flow.acceptAddVisitPhoto("camera"))
        assertEquals(snapshot, flow.value)
    }
    @Test fun editSaveSnapshotAndOriginalReferencesRemainProtected() {
        val state = EditVisitUiState(dateMillis = 100, isLoading = false, photoPaths = listOf("old", "new"), originalPhotoPaths = listOf("old"))
        val flow = MutableStateFlow(state.copy(isSaving = true))
        assertNull(flow.removeEditVisitPhoto(1))
        assertFalse(flow.acceptEditVisitPhoto("camera"))
        assertEquals(state.copy(isSaving = true), flow.value)
    }
    @Test fun photoEventsDuringImportAreRejected() {
        val add = MutableStateFlow(AddVisitUiState(dateMillis = 100, isImportingPhotos = true, photoPaths = listOf("a")))
        val edit = MutableStateFlow(EditVisitUiState(dateMillis = 100, isLoading = false, isImportingPhotos = true, photoPaths = listOf("a")))
        assertNull(add.removeAddVisitPhoto(0)); assertFalse(add.acceptAddVisitPhoto("b"))
        assertNull(edit.removeEditVisitPhoto(0)); assertFalse(edit.acceptEditVisitPhoto("b"))
    }
    @Test fun savedFormsRejectLatePhotoCallbacks() {
        val add = MutableStateFlow(AddVisitUiState(dateMillis = 100, saved = true, photoPaths = listOf("a")))
        val edit = MutableStateFlow(EditVisitUiState(dateMillis = 100, isLoading = false, saved = true, photoPaths = listOf("a")))
        assertNull(add.removeAddVisitPhoto(0)); assertFalse(add.acceptAddVisitPhoto("b"))
        assertNull(edit.removeEditVisitPhoto(0)); assertFalse(edit.acceptEditVisitPhoto("b"))
    }
    @Test fun unloadedOrMissingEditCannotAcceptPhotos() {
        for (state in listOf(EditVisitUiState(dateMillis = 100), EditVisitUiState(dateMillis = 100, isLoading = false, notFound = true))) {
            assertFalse(MutableStateFlow(state).acceptEditVisitPhoto("a"))
        }
    }
    @Test fun capacityAndDuplicateCallbacksNeverGrowList() {
        val flow = MutableStateFlow(AddVisitUiState(dateMillis = 100, photoPaths = listOf("a")))
        assertTrue(flow.acceptAddVisitPhoto("a")); assertEquals(listOf("a"), flow.value.photoPaths)
        flow.value = flow.value.copy(photoPaths = (1..MAX_VISIT_PHOTOS).map { "$it" })
        assertFalse(flow.acceptAddVisitPhoto("overflow")); assertEquals(MAX_VISIT_PHOTOS, flow.value.photoPaths.size)
    }
    @Test fun idleRemovalHappensBeforeSaveSnapshot() {
        val flow = MutableStateFlow(AddVisitUiState(dateMillis = 100, photoPaths = listOf("a", "b")))
        assertEquals("a", flow.removeAddVisitPhoto(0))
        assertEquals(listOf("b"), flow.tryTransition { it.copy(isSaving = true) }!!.photoPaths)
    }
    @Test fun reclamationRechecksSelectionAfterReferenceLookup() {
        val flow = MutableStateFlow(AddVisitUiState(dateMillis = 100, photoPaths = listOf("a")))
        assertEquals("a", flow.removeAddVisitPhoto(0))
        val candidates = listOf("a")
        val referenced = emptySet<String>()
        // 模拟 DAO 查询期间迟到的相机回调；删除决策读取此刻状态而不是查询前快照。
        assertTrue(flow.acceptAddVisitPhoto("a"))
        assertTrue(unreferencedPhotoCandidates(candidates, referenced) { it in flow.value.photoPaths }.isEmpty())
    }
    @Test fun reclamationPreservesDatabaseAndOriginalReferences() {
        val original = setOf("original")
        assertEquals(listOf("unused"), unreferencedPhotoCandidates(
            listOf("cover", "record", "original", "unused"), setOf("cover", "record"), original::contains))
    }
    @Test fun editingRemovalDoesNotAlterOriginalProtectionSet() {
        val flow = MutableStateFlow(EditVisitUiState(dateMillis = 100, isLoading = false, photoPaths = listOf("old"), originalPhotoPaths = listOf("old")))
        assertEquals("old", flow.removeEditVisitPhoto(0))
        assertEquals(listOf("old"), flow.value.originalPhotoPaths)
        assertNull(flow.removeEditVisitPhoto(0))
    }
}
