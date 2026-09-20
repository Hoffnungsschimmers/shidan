package com.fanji.mealnote.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.data.log.AppLog
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.visit.MAX_VISIT_PHOTOS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 编辑用餐记录的表单状态。
 *
 * [originalPhotoPaths] 记录进入页面时数据库中已有的照片，用于区分“用户自己新导入的图”
 * 与“已落库的图”：只有前者才允许在未保存的情况下回收，后者删除会破坏已有数据。
 */
data class EditVisitUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val restaurantName: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val verdict: Verdict = Verdict.GOOD,
    val dishes: String = "",
    val priceText: String = "",
    val note: String = "",
    val photoPaths: List<String> = emptyList(),
    val originalPhotoPaths: List<String> = emptyList(),
    val isImportingPhotos: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    val canAddPhoto: Boolean get() = photoPaths.size < MAX_VISIT_PHOTOS

    val canSave: Boolean
        get() = !isLoading && !notFound && !isImportingPhotos && !isSaving && !saved
}

@HiltViewModel
class EditVisitViewModel @Inject constructor(
    private val repository: MealRepository,
    private val photoStore: PhotoStore,
    private val appLog: AppLog,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val recordId = MutableStateFlow(savedStateHandle.get<Long>(KEY_RECORD_ID))

    private val _uiState = MutableStateFlow(
        EditVisitUiState(
            dateMillis = savedStateHandle.get<Long>(KEY_DATE) ?: System.currentTimeMillis(),
            verdict = savedStateHandle.get<String>(KEY_VERDICT)
                ?.let { name -> Verdict.entries.firstOrNull { it.name == name } }
                ?: Verdict.GOOD,
            dishes = savedStateHandle.get<String>(KEY_DISHES).orEmpty(),
            priceText = savedStateHandle.get<String>(KEY_PRICE).orEmpty(),
            note = savedStateHandle.get<String>(KEY_NOTE).orEmpty(),
            photoPaths = savedStateHandle.get<ArrayList<String>>(KEY_PHOTOS).orEmpty(),
        )
    )
    val uiState: StateFlow<EditVisitUiState> = _uiState.asStateFlow()

    /**
     * 本实例是否由系统回收后的状态恢复而来。
     *
     * **必须在构造时求值，且必须声明在 `init` 之前**（属性初始化按声明顺序执行）：
     * 下面 `init` 里的持久化逻辑会把这些 key 写回 `savedStateHandle`
     * （`Dispatchers.Main.immediate` + StateFlow 首帧同步发射，构造期间就会写入），
     * 之后再判断就**恒为 true** —— 早期版本正是如此，导致
     * 「放弃表单时回收未提交图片」这段逻辑从未真正执行，图片一直泄漏成孤儿文件。
     */
    private val restoredFromSavedState: Boolean =
        savedStateHandle.contains(KEY_NOTE) || savedStateHandle.contains(KEY_PHOTOS)

    init {
        viewModelScope.launch {
            _uiState
                .map { state ->
                    PersistedForm(
                        dateMillis = state.dateMillis,
                        verdict = state.verdict.name,
                        dishes = state.dishes,
                        priceText = state.priceText,
                        note = state.note,
                        photoPaths = state.photoPaths,
                    )
                }
                .distinctUntilChanged()
                .collect { persisted ->
                    savedStateHandle[KEY_DATE] = persisted.dateMillis
                    savedStateHandle[KEY_VERDICT] = persisted.verdict
                    savedStateHandle[KEY_DISHES] = persisted.dishes
                    savedStateHandle[KEY_PRICE] = persisted.priceText
                    savedStateHandle[KEY_NOTE] = persisted.note
                    savedStateHandle[KEY_PHOTOS] = ArrayList(persisted.photoPaths)
                }
        }
    }

    private data class PersistedForm(
        val dateMillis: Long,
        val verdict: String,
        val dishes: String,
        val priceText: String,
        val note: String,
        val photoPaths: List<String>,
    )

    /** 注入路由参数并加载现有记录。进程重建时保留用户已输入的内容。 */
    fun setRecordId(id: Long) {
        // 进程重建后 recordId 已从 SavedStateHandle 恢复，与传入 id 相等，
        // 但数据尚未加载，此时必须继续走 load，否则页面永远停在加载中。
        if (recordId.value == id && !_uiState.value.isLoading) return
        recordId.value = id
        savedStateHandle[KEY_RECORD_ID] = id
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(id: Long) {
        val existing = repository.getDiningRecord(id)
        if (existing == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val orderedPaths = existing.photos.sortedBy { it.sortOrder }.map { it.filePath }
        val restaurantName = repository.getRestaurant(existing.record.restaurantId)?.name.orEmpty()
        // 必须用构造时捕获的 [restoredFromSavedState]，不能在这里查
        // `savedStateHandle.contains(...)`：`init` 的持久化协程在构造期间就会把
        // 空表单写回 handle，此后该判断恒为 true，导致正常进入编辑页也走
        // “保留用户输入”分支，表单永远是空白。
        _uiState.update { state ->
            if (restoredFromSavedState) {
                state.copy(
                    isLoading = false,
                    restaurantName = restaurantName,
                    originalPhotoPaths = orderedPaths,
                )
            } else {
                state.copy(
                    isLoading = false,
                    restaurantName = restaurantName,
                    dateMillis = existing.record.eatenAt,
                    verdict = existing.record.verdict,
                    dishes = existing.record.dishes,
                    priceText = existing.record.priceText,
                    note = existing.record.note,
                    photoPaths = orderedPaths,
                    originalPhotoPaths = orderedPaths,
                )
            }
        }
    }

    fun onDateChange(value: Long) =
        _uiState.update { it.copy(dateMillis = value, errorMessage = null) }

    fun onVerdictChange(value: Verdict) =
        _uiState.update { it.copy(verdict = value, errorMessage = null) }

    fun onDishesChange(value: String) =
        _uiState.update { it.copy(dishes = value, errorMessage = null) }

    fun onPriceChange(value: String) =
        _uiState.update { it.copy(priceText = value, errorMessage = null) }

    fun onNoteChange(value: String) =
        _uiState.update { it.copy(note = value, errorMessage = null) }

    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingPhotos = true, errorMessage = null) }
            val remaining = (MAX_VISIT_PHOTOS - _uiState.value.photoPaths.size).coerceAtLeast(0)
            if (remaining == 0) {
                _uiState.update { it.copy(isImportingPhotos = false, errorMessage = PHOTO_LIMIT_REACHED) }
                return@launch
            }
            val (imported, failure) = photoStore.importUrisWithReason(uris.take(remaining))
            _uiState.update { state ->
                state.copy(
                    photoPaths = (state.photoPaths + imported).distinct().take(MAX_VISIT_PHOTOS),
                    isImportingPhotos = false,
                    errorMessage = if (imported.isEmpty()) failure.toImportMessage() else null,
                )
            }
            if (imported.isEmpty()) {
                appLog.warn("visit", "import failed failure=$failure count=${uris.size}")
            }
        }
    }

    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    fun onCameraPhoto(file: File) {
        if (_uiState.value.photoPaths.size >= MAX_VISIT_PHOTOS) {
            viewModelScope.launch { photoStore.deleteOwnedPhoto(file.absolutePath) }
            _uiState.update { it.copy(errorMessage = PHOTO_LIMIT_REACHED) }
            return
        }
        _uiState.update { state ->
            state.copy(
                photoPaths = (state.photoPaths + file.absolutePath).distinct().take(MAX_VISIT_PHOTOS),
                errorMessage = null,
            )
        }
    }

    fun onCameraCancelled(file: File) {
        viewModelScope.launch { photoStore.deleteOwnedPhoto(file.absolutePath) }
    }

    /**
     * 从表单中移除一张照片。
     *
     * 只删除“本次新导入”的文件：已落库照片的磁盘回收统一由 Repository 在事务提交后
     * 处理，此处提前删除会让“取消编辑”无法还原。
     */
    fun removePhoto(index: Int) {
        val state = _uiState.value
        val path = state.photoPaths.getOrNull(index) ?: return
        _uiState.update { it.copy(photoPaths = it.photoPaths.filterIndexed { i, _ -> i != index }) }
        if (path !in state.originalPhotoPaths) {
            viewModelScope.launch { photoStore.deleteOwnedPhoto(path) }
        }
    }

    fun save() {
        val targetId = recordId.value ?: return
        val snapshot = _uiState.updateAndGet { state ->
            when {
                state.isLoading || state.notFound || state.isSaving ||
                    state.isImportingPhotos || state.saved -> state
                else -> state.copy(isSaving = true, errorMessage = null)
            }
        }
        // 被抢先置位或已保存时 updateAndGet 会返回原状态，isSaving 仍为 false。
        if (!snapshot.isSaving) return

        viewModelScope.launch {
            when (val result = repository.updateDiningRecord(
                recordId = targetId,
                eatenAt = snapshot.dateMillis,
                verdict = snapshot.verdict,
                dishes = snapshot.dishes,
                priceText = snapshot.priceText,
                note = snapshot.note,
                photoPaths = snapshot.photoPaths,
            )) {
                is MealResult.Success ->
                    _uiState.update { it.copy(isSaving = false, saved = true) }

                is MealResult.Failure ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.error.toSaveMessage())
                    }
            }
        }
    }

    /**
     * 页面销毁时回收“本次新导入但未保存”的图片。
     *
     * 已落库照片（[EditVisitUiState.originalPhotoPaths]）不在此列：它们仍被数据库引用，
     * 用户取消编辑后应完整保留。进程重建时同样跳过，否则用户重建后会看到破图。
     */
    override fun onCleared() {
        val state = _uiState.value
        if (!state.saved && !restoredFromSavedState) {
            photoStore.deleteOwnedPhotos(state.photoPaths.filterNot { it in state.originalPhotoPaths })
        }
        super.onCleared()
    }

    private fun MealError.toSaveMessage(): String = when (this) {
        MealError.RecordNotFound -> "该用餐记录已不存在，请返回后重试"
        MealError.RestaurantNotFound -> "该餐厅记录已不存在，请返回列表后重试"
        is MealError.InvalidInput -> reason
        MealError.StorageFull -> "存储空间不足，请清理后重试"
        MealError.PhotoIoFailure -> "图片保存失败，请重新选择图片"
        else -> "用餐记录保存失败，请稍后重试"
    }

    private companion object {
        const val PHOTO_LIMIT_REACHED = "最多可添加 $MAX_VISIT_PHOTOS 张图片"
        const val KEY_RECORD_ID = "edit_visit_record_id"
        const val KEY_DATE = "edit_visit_date"
        const val KEY_VERDICT = "edit_visit_verdict"
        const val KEY_DISHES = "edit_visit_dishes"
        const val KEY_PRICE = "edit_visit_price"
        const val KEY_NOTE = "edit_visit_note"
        const val KEY_PHOTOS = "edit_visit_photos"
    }
}

/** 导入失败原因的用户文案，见 AddRestaurantViewModel 处的同名函数说明。 */
private fun PhotoStore.ImportFailure?.toImportMessage(): String = when (this) {
    PhotoStore.ImportFailure.NOT_AN_IMAGE -> "所选文件不是有效的图片，请换一张重试"
    else -> "图片导入失败，请重新选择"
}
