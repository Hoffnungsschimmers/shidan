package com.fanji.mealnote.ui.visit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.data.local.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 单次用餐记录允许的照片上限。UI、ViewModel 与 Repository 共用同一约束。 */
const val MAX_VISIT_PHOTOS = 9

/**
 * 用餐评价表单状态。
 *
 * [saved] 为 true 表示记录已成功入库，界面据此触发导航，同时阻止销毁时回收照片。
 */
data class AddVisitUiState(
    val dateMillis: Long = System.currentTimeMillis(),
    val verdict: Verdict = Verdict.GOOD,
    val dishes: String = "",
    val priceText: String = "",
    val note: String = "",
    val photoPaths: List<String> = emptyList(),
    val isImportingPhotos: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 照片未达上限时才展示添加入口。 */
    val canAddPhoto: Boolean get() = photoPaths.size < MAX_VISIT_PHOTOS

    /** 保存按钮可用性。 */
    val canSave: Boolean get() = !isImportingPhotos && !isSaving && !saved
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddVisitViewModel @Inject constructor(
    private val repository: MealRepository,
    private val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restaurantId = MutableStateFlow(savedStateHandle.get<Long>(KEY_RESTAURANT_ID))
    private val _uiState = MutableStateFlow(
        AddVisitUiState(
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

    val uiState: StateFlow<AddVisitUiState> = _uiState.asStateFlow()

    init {
        // 只持久化“用户输入”字段，并用 distinctUntilChanged 过滤加载态变化，
        // 避免每次按键都写一次 saved instance state 而拖慢输入响应。
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

    /** 需要跨进程重建保留的字段集合，用于去重判断。 */
    private data class PersistedForm(
        val dateMillis: Long,
        val verdict: String,
        val dishes: String,
        val priceText: String,
        val note: String,
        val photoPaths: List<String>,
    )

    /**
     * 当前餐厅名称，用于表单标题。
     *
     * 餐厅 id 未设置时发射空字符串而非挂起，避免界面在首帧就停留在空白状态。
     */
    val restaurantName: StateFlow<String> = restaurantId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf("")
            } else {
                repository.observeRestaurantWithRecords(id)
                    .map { it?.restaurant?.name.orEmpty() }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = "",
        )

    /**
     * 注入路由参数。
     *
     * 同时写入 [savedStateHandle]：保存流程会短暂离开本页面，若进程在此期间被回收，
     * 重建后 `restaurantId` 必须能恢复，否则「保存」按钮会静默失效。
     */
    fun setRestaurantId(id: Long) {
        if (restaurantId.value != id) {
            restaurantId.value = id
            savedStateHandle[KEY_RESTAURANT_ID] = id
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

    /**
     * 批量导入相册图片，超出上限的部分自动截断。
     *
     * 剩余容量按当前已选数量实时计算，因此用户在多次选择之间不会突破 9 张限制。
     */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingPhotos = true, errorMessage = null) }
            val remaining = (MAX_VISIT_PHOTOS - _uiState.value.photoPaths.size).coerceAtLeast(0)
            if (remaining == 0) {
                _uiState.update { it.copy(isImportingPhotos = false, errorMessage = PHOTO_LIMIT_REACHED) }
                return@launch
            }
            val imported = photoStore.importUris(uris.take(remaining))
            _uiState.update { state ->
                state.copy(
                    photoPaths = (state.photoPaths + imported).distinct().take(MAX_VISIT_PHOTOS),
                    isImportingPhotos = false,
                    errorMessage = if (imported.isEmpty()) IMPORT_FAILED else null,
                )
            }
        }
    }

    /** 创建相机拍摄目标文件，交由界面启动拍摄流程。 */
    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    /**
     * 相机拍摄成功。
     *
     * 超出上限时立即回收刚产生的文件——相机已经写入磁盘，若不删除就会成为孤儿文件。
     */
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

    /** 相机被取消或失败，回收预创建的空文件。 */
    fun onCameraCancelled(file: File) {
        viewModelScope.launch { photoStore.deleteOwnedPhoto(file.absolutePath) }
    }

    fun removePhoto(index: Int) {
        val path = _uiState.value.photoPaths.getOrNull(index) ?: return
        _uiState.update { state ->
            state.copy(photoPaths = state.photoPaths.filterIndexed { i, _ -> i != index })
        }
        viewModelScope.launch { photoStore.deleteOwnedPhoto(path) }
    }

    /**
     * 提交保存。
     *
     * 防抖采用**原子化的状态跃迁**：在同一 `update` lambda 内完成“检查 + 置位”，
     * 避免两次快速点击都通过检查后各启动一个协程，从而写入重复用餐记录。
     * 餐厅 id 尚未注入时直接返回，避免把记录写到错误目标。
     */
    fun save() {
        val targetRestaurantId = restaurantId.value ?: return
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isSaving || state.isImportingPhotos || state.saved) {
                state
            } else {
                state.copy(isSaving = true, errorMessage = null)
            }
        }
        if (!snapshot.isSaving) return

        viewModelScope.launch {
            when (val result = repository.addDiningRecord(
                restaurantId = targetRestaurantId,
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
     * 页面销毁时回收未提交的图片。
     *
     * 两种必须跳过清理的情况：
     * 1. 保存已成功（[AddVisitUiState.saved]）—— 文件已被数据库引用；
     * 2. 进程重建 —— 照片仍属于用户正在编辑的表单，删除会导致重建后破图，
     *    且用户再次保存时会写入已失效的路径。
     */
    override fun onCleared() {
        if (!_uiState.value.saved && !wasRestoredFromSavedState()) {
            photoStore.deleteOwnedPhotos(_uiState.value.photoPaths)
        }
        super.onCleared()
    }

    /** 判断本实例是否由系统回收后的状态恢复而来。 */
    private fun wasRestoredFromSavedState(): Boolean =
        savedStateHandle.contains(KEY_NOTE) || savedStateHandle.contains(KEY_PHOTOS)

    private fun MealError.toSaveMessage(): String = when (this) {
        MealError.RestaurantNotFound -> "该餐厅记录已不存在，请返回列表后重试"
        is MealError.InvalidInput -> reason
        MealError.StorageFull -> "存储空间不足，请清理后重试"
        MealError.PhotoIoFailure -> "图片保存失败，请重新选择图片"
        else -> "用餐记录保存失败，请稍后重试"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PHOTO_LIMIT_REACHED = "最多可添加 $MAX_VISIT_PHOTOS 张图片"
        const val IMPORT_FAILED = "图片导入失败，请重新选择"
        const val KEY_RESTAURANT_ID = "visit_restaurant_id"
        const val KEY_DATE = "visit_date"
        const val KEY_VERDICT = "visit_verdict"
        const val KEY_DISHES = "visit_dishes"
        const val KEY_PRICE = "visit_price"
        const val KEY_NOTE = "visit_note"
        const val KEY_PHOTOS = "visit_photos"
    }
}
