package com.fanji.mealnote.ui.edit

import com.fanji.mealnote.ui.tryTransition
import kotlinx.coroutines.flow.MutableStateFlow

internal fun MutableStateFlow<EditRestaurantUiState>.beginRestaurantSave(): EditRestaurantUiState? =
    tryTransition { state ->
        when {
            state.isLoading || state.notFound || state.isSaving ||
                state.isImportingPhotos || state.saved -> null
            state.name.isBlank() -> state.copy(showNameError = true)
            else -> state.copy(isSaving = true, errorMessage = null)
        }
    }

internal fun MutableStateFlow<EditRestaurantUiState>.beginCoverImport(): Boolean =
    tryTransition { state ->
        if (!state.canChangeCover) null
        else state.copy(isImportingPhotos = true, errorMessage = null)
    } != null

/** 只有成功取得封面变更权的一方才拿到旧图回收集合；保存快照与换图共用同一 CAS。 */
internal fun MutableStateFlow<EditRestaurantUiState>.changeCover(
    path: String,
    fromRecord: Boolean = false,
    finishImport: Boolean = false,
): List<String>? {
    while (true) {
        val state = value
        if (state.isLoading || state.notFound || state.isSaving || state.saved ||
            (state.isImportingPhotos && !finishImport) ||
            (finishImport && !state.isImportingPhotos) ||
            (fromRecord && path !in state.recordPhotoPaths)
        ) return null
        val next = state.copy(
            photoPaths = listOf(path),
            isImportingPhotos = false,
            errorMessage = null,
        )
        if (compareAndSet(state, next)) {
            return reclaimableFormPhotos(
                state.photoPaths, state.originalCoverPath, state.recordPhotoPaths, path,
            )
        }
    }
}

internal fun MutableStateFlow<EditRestaurantUiState>.removeCover(index: Int): List<String>? {
    val removed = value.photoPaths.getOrNull(index) ?: return null
    while (true) {
        val state = value
        if (!state.canChangeCover || state.photoPaths.getOrNull(index) != removed) return null
        val next = state.copy(photoPaths = state.photoPaths.filterIndexed { i, _ -> i != index })
        if (compareAndSet(state, next)) {
            return reclaimableFormPhotos(
                listOf(removed), state.originalCoverPath, state.recordPhotoPaths, "",
            )
        }
    }
}

private val EditRestaurantUiState.canChangeCover: Boolean
    get() = !isLoading && !notFound && !isSaving && !saved && !isImportingPhotos
