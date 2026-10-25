package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.ui.edit.EditVisitUiState
import com.fanji.mealnote.ui.tryTransition
import kotlinx.coroutines.flow.MutableStateFlow

/** 图片变更与保存占位竞争同一个 CAS；忙碌时不改变提交快照。 */
internal fun MutableStateFlow<AddVisitUiState>.acceptAddVisitPhoto(path: String): Boolean =
    tryTransition { state ->
        if (!state.canSave || !state.canAddPhoto) null
        else state.copy(photoPaths = (state.photoPaths + path).distinct(), prefillTouched = true, errorMessage = null)
    } != null

internal fun MutableStateFlow<EditVisitUiState>.acceptEditVisitPhoto(path: String): Boolean =
    tryTransition { state ->
        if (!state.canSave || !state.canAddPhoto) null
        else state.copy(photoPaths = (state.photoPaths + path).distinct(), errorMessage = null)
    } != null

internal fun MutableStateFlow<AddVisitUiState>.removeAddVisitPhoto(index: Int): String? {
    val path = value.photoPaths.getOrNull(index) ?: return null
    return tryTransition { state ->
        if (!state.canSave || state.photoPaths.getOrNull(index) != path) null
        else state.copy(photoPaths = state.photoPaths.filterIndexed { i, _ -> i != index }, prefillTouched = true)
    }?.let { path }
}

internal fun MutableStateFlow<EditVisitUiState>.removeEditVisitPhoto(index: Int): String? {
    val path = value.photoPaths.getOrNull(index) ?: return null
    return tryTransition { state ->
        if (!state.canSave || state.photoPaths.getOrNull(index) != path) null
        else state.copy(photoPaths = state.photoPaths.filterIndexed { i, _ -> i != index })
    }?.let { path }
}
