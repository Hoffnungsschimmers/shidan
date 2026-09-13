package com.fanji.mealnote.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.PhotoStore
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
 * 编辑餐厅表单状态。
 *
 * 与新建表单的关键差异：本页**已有落库数据**，因此多出 [isLoading]（首次填充）与
 * [notFound]（记录已被他处删除）两种状态；[saved] 表示更新已提交，界面据此返回上一页。
 *
 * [originalCoverPath] 保存进入页面时数据库中的封面路径，用于判断用户是否真的换过图：
 * 只有换过才需要回收旧文件，否则会把用户刚保留的那张删掉。
 */
data class EditRestaurantUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val address: String = "",
    val status: com.fanji.mealnote.data.local.RestaurantStatus? = null,
    val photoPaths: List<String> = emptyList(),
    val originalCoverPath: String = "",
    val isImportingPhotos: Boolean = false,
    val isSaving: Boolean = false,
    val showNameError: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = !isLoading && !notFound && name.isNotBlank() &&
            !isImportingPhotos && !isSaving && !saved
}

@HiltViewModel
class EditRestaurantViewModel @Inject constructor(
    private val repository: MealRepository,
    private val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restaurantId = MutableStateFlow(savedStateHandle.get<Long>(KEY_RESTAURANT_ID))

    private val _uiState = MutableStateFlow(
        EditRestaurantUiState(
            name = savedStateHandle.get<String>(KEY_NAME).orEmpty(),
            address = savedStateHandle.get<String>(KEY_ADDRESS).orEmpty(),
            photoPaths = savedStateHandle.get<ArrayList<String>>(KEY_PHOTOS).orEmpty(),
        )
    )
    val uiState: StateFlow<EditRestaurantUiState> = _uiState.asStateFlow()

    init {
        // 持久化职责与新建表单一致：只保存用户可编辑字段，并用 distinctUntilChanged
        // 过滤掉加载态变化，避免每次按键都写一次 saved instance state。
        viewModelScope.launch {
            _uiState
                .map { state -> PersistedForm(state.name, state.address, state.photoPaths) }
                .distinctUntilChanged()
                .collect { persisted ->
                    savedStateHandle[KEY_NAME] = persisted.name
                    savedStateHandle[KEY_ADDRESS] = persisted.address
                    savedStateHandle[KEY_PHOTOS] = ArrayList(persisted.photoPaths)
                }
        }
    }

    private data class PersistedForm(
        val name: String,
        val address: String,
        val photoPaths: List<String>,
    )

    /**
     * 注入路由参数并加载现有数据。
     *
     * 若本地已有未提交的编辑内容（进程被回收后重建），则**不覆盖**用户输入，只补齐
     * 初始封面等非用户字段，否则重建会让用户丢失刚输入的内容。
     */
    fun setRestaurantId(id: Long) {
        if (restaurantId.value == id) return
        restaurantId.value = id
        savedStateHandle[KEY_RESTAURANT_ID] = id
        viewModelScope.launch { load(id) }
    }

    private suspend fun load(id: Long) {
        val restaurant = repository.getRestaurant(id)
        if (restaurant == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val restored = savedStateHandle.contains(KEY_NAME)
        _uiState.update { state ->
            if (restored) {
                // 保留用户输入，只补上数据库侧信息。
                state.copy(
                    isLoading = false,
                    status = restaurant.status,
                    originalCoverPath = restaurant.recommendationPhotoPath,
                )
            } else {
                state.copy(
                    isLoading = false,
                    name = restaurant.name,
                    address = restaurant.address,
                    status = restaurant.status,
                    originalCoverPath = restaurant.recommendationPhotoPath,
                    photoPaths = restaurant.recommendationPhotoPath
                        .takeIf(String::isNotBlank)
                        ?.let(::listOf)
                        .orEmpty(),
                )
            }
        }
    }

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, showNameError = false, errorMessage = null) }

    fun onAddressChange(value: String) =
        _uiState.update { it.copy(address = value, errorMessage = null) }

    /** 导入新封面；替换旧图时立即回收上一张，避免用户反复更换后残留无用图片。 */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingPhotos = true, errorMessage = null) }
            val imported = photoStore.importUris(uris.take(1))
            val newCover = imported.lastOrNull()
            _uiState.update { state ->
                if (newCover == null) {
                    state.copy(isImportingPhotos = false, errorMessage = IMPORT_FAILED)
                } else {
                    val obsolete = state.photoPaths.filterNot { it == newCover }
                    state.copy(
                        photoPaths = listOf(newCover),
                        isImportingPhotos = false,
                        errorMessage = null,
                    ).also { photoStore.deleteOwnedPhotos(obsolete) }
                }
            }
        }
    }

    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    fun onCameraPhoto(file: File) {
        val previous = _uiState.value.photoPaths
        _uiState.update { it.copy(photoPaths = listOf(file.absolutePath), errorMessage = null) }
        viewModelScope.launch {
            photoStore.deleteOwnedPhotos(previous.filterNot { it == file.absolutePath })
        }
    }

    fun onCameraCancelled(file: File) {
        viewModelScope.launch { photoStore.deleteOwnedPhoto(file.absolutePath) }
    }

    /**
     * 移除封面。
     *
     * 此处**不删除文件**：该图仍是数据库中记录的封面，用户可能只是暂时不想展示；
     * 真正的回收发生在保存成功之后（由 [save] 与 Repository 协同完成），
     * 或页面销毁且未保存时（见 [onCleared]）。
     */
    fun removePhoto(index: Int) {
        _uiState.update { state ->
            state.copy(photoPaths = state.photoPaths.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * 提交更新。
     *
     * 防抖同样是原子状态跃迁：先置 [EditRestaurantUiState.isSaving] 再判断是否被抢先置位，
     * 避免两次快速点击都通过检查后各启动一个协程。
     */
    fun save() {
        val targetId = restaurantId.value ?: return
        val snapshot = _uiState.updateAndGet { state ->
            when {
                state.isLoading || state.notFound || state.isSaving ||
                    state.isImportingPhotos || state.saved -> state
                state.name.isBlank() -> state.copy(showNameError = true)
                else -> state.copy(isSaving = true, errorMessage = null)
            }
        }
        if (!snapshot.isSaving || snapshot.showNameError) return

        viewModelScope.launch {
            val existing = repository.getRestaurant(targetId)
            if (existing == null) {
                _uiState.update { it.copy(isSaving = false, notFound = true) }
                return@launch
            }
            // 保留 status：状态由用餐记录数量驱动，编辑表单不应改变它。
            val updated = existing.copy(
                name = snapshot.name.trim(),
                address = snapshot.address.trim(),
                recommendationPhotoPath = snapshot.photoPaths.firstOrNull().orEmpty(),
            )
            when (val result = repository.updateRestaurant(updated)) {
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
     * 页面销毁时回收“新导入但未保存”的图片。
     *
     * 三种必须跳过清理的情况：
     * 1. 已保存成功 —— 文件已被数据库引用；
     * 2. 进程重建 —— 照片仍属于用户正在编辑的表单；
     * 3. 该路径本就是数据库中的封面 —— 删除会破坏已保存的数据。
     */
    override fun onCleared() {
        val state = _uiState.value
        if (!state.saved && !wasRestoredFromSavedState()) {
            photoStore.deleteOwnedPhotos(state.photoPaths.filterNot { it == state.originalCoverPath })
        }
        super.onCleared()
    }

    private fun wasRestoredFromSavedState(): Boolean =
        savedStateHandle.contains(KEY_NAME) || savedStateHandle.contains(KEY_PHOTOS)

    private fun MealError.toSaveMessage(): String = when (this) {
        MealError.RestaurantNotFound -> "该餐厅记录已不存在，请返回列表后重试"
        is MealError.InvalidInput -> reason
        MealError.StorageFull -> "存储空间不足，请清理后重试"
        MealError.PhotoIoFailure -> "图片保存失败，请重新选择图片"
        else -> "餐厅记录保存失败，请稍后重试"
    }

    private companion object {
        const val IMPORT_FAILED = "图片导入失败，请重新选择"
        const val KEY_RESTAURANT_ID = "edit_restaurant_id"
        const val KEY_NAME = "edit_restaurant_name"
        const val KEY_ADDRESS = "edit_restaurant_address"
        const val KEY_PHOTOS = "edit_restaurant_photos"
    }
}
