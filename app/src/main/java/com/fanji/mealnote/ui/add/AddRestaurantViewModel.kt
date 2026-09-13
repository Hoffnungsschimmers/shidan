package com.fanji.mealnote.ui.add

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
 * 新建餐厅表单状态。
 *
 * [savedId] 一旦被赋值为非空即表示保存已提交，界面据此导航并在销毁时避免回收已入库的图片。
 * 因为封面最多一张，[photoPaths] 实际长度恒为 0 或 1，保留 List 是为了与 PhotoStore 的
 * 批量接口保持一致。
 *
 * 用户输入（[name]、[address]、[photoPaths]）会通过 [SavedStateHandle] 持久化，
 * 进程被系统回收后重建仍能恢复；加载态与错误提示不持久化，避免重建后停留在“保存中”假象。
 *
 * 注意：**表单不再包含「记录类型」开关**。用户是「想去的店」还是「已经吃过」由入口决定
 * （见 `MainScaffold` 的新增面板），保存后要去哪里也由导航参数决定。
 * 早期版本把这一选择放进表单，会让用户以为可以先把店存成「想去」再原地改成「吃过」，
 * 而这两条路径在数据层是不同的操作（后者要立刻创建一条用餐记录）。
 */
data class AddRestaurantUiState(
    val name: String = "",
    val address: String = "",
    val photoPaths: List<String> = emptyList(),
    val isImportingPhotos: Boolean = false,
    val isSaving: Boolean = false,
    val showNameError: Boolean = false,
    val errorMessage: String? = null,
    val savedId: Long? = null,
) {
    /** 保存按钮可用性：名称非空、无图片在处理中、且尚未提交过。 */
    val canSave: Boolean
        get() = name.isNotBlank() && !isImportingPhotos && !isSaving && savedId == null
}

@HiltViewModel
class AddRestaurantViewModel @Inject constructor(
    private val repository: MealRepository,
    private val photoStore: PhotoStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AddRestaurantUiState(
            name = savedStateHandle.get<String>(KEY_NAME).orEmpty(),
            address = savedStateHandle.get<String>(KEY_ADDRESS).orEmpty(),
            photoPaths = savedStateHandle.get<ArrayList<String>>(KEY_PHOTOS).orEmpty(),
        )
    )
    val uiState: StateFlow<AddRestaurantUiState> = _uiState.asStateFlow()

    init {
        // 只持久化“用户输入”相关字段，并用 distinctUntilChanged 过滤掉加载态变化，
        // 避免每输入一个字符就产生一次 Bundle 写入（SavedStateHandle 的写入最终会
        // 落到 Activity 的 saved instance state，过于频繁会拖慢输入响应）。
        viewModelScope.launch {
            _uiState
                .map { state ->
                    PersistedForm(
                        name = state.name,
                        address = state.address,
                        photoPaths = state.photoPaths,
                    )
                }
                .distinctUntilChanged()
                .collect { persisted ->
                    savedStateHandle[KEY_NAME] = persisted.name
                    savedStateHandle[KEY_ADDRESS] = persisted.address
                    savedStateHandle[KEY_PHOTOS] = ArrayList(persisted.photoPaths)
                }
        }
    }

    /** 需要跨进程重建保留的字段集合，用于去重判断。 */
    private data class PersistedForm(
        val name: String,
        val address: String,
        val photoPaths: List<String>,
    )

    fun onNameChange(value: String) =
        _uiState.update { it.copy(name = value, showNameError = false, errorMessage = null) }

    fun onAddressChange(value: String) =
        _uiState.update { it.copy(address = value, errorMessage = null) }

    /**
     * 导入相册选择结果。封面只保留一张，新图替换旧图时立即回收旧文件。
     */
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
                    // 只删除“被替换掉的旧图”，避免误删刚导入的新文件。
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

    /** 创建相机拍摄目标文件。调用方需在回调中二选一：保留或 [onCameraCancelled]。 */
    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    /**
     * 相机拍摄成功，将该文件设为封面，并回收此前已选中的其他图片。
     *
     * 文件删除是磁盘 IO，必须放入协程：早期版本在 `update` 的 lambda 内直接调用会阻塞
     * 主线程，且 lambda 可能被重复执行导致同一文件被删除多次。
     */
    fun onCameraPhoto(file: File) {
        val previous = _uiState.value.photoPaths
        _uiState.update { it.copy(photoPaths = listOf(file.absolutePath), errorMessage = null) }
        viewModelScope.launch {
            photoStore.deleteOwnedPhotos(previous.filterNot { it == file.absolutePath })
        }
    }

    /** 相机被取消或拍摄失败，回收预创建的空文件，避免产生孤儿文件。 */
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
     * 防抖采用**原子化的状态跃迁**：先用 [MutableStateFlow.update] 把 `isSaving` 置位，
     * 再在同一个 lambda 内判断是否已经被其他调用抢先置位。这样两次快速点击只会进入一次
     * 保存流程（早期版本先读 `value` 再 `launch`，两次点击都可能在置位前读到 false，
     * 从而产生重复餐厅）。
     *
     * 表单数据在置位之后重新读取，确保保存的是最新输入，而不是点击瞬间的快照。
     */
    fun save() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isSaving || state.isImportingPhotos || state.savedId != null) {
                // 已在进行中：原样返回，外层通过 savedId/isSaving 判定为重复提交。
                state
            } else if (state.name.isBlank()) {
                state.copy(showNameError = true)
            } else {
                state.copy(isSaving = true, errorMessage = null)
            }
        }
        if (!snapshot.isSaving || snapshot.showNameError || snapshot.savedId != null) return
        if (snapshot.name.isBlank()) return

        viewModelScope.launch {
            when (val result = repository.addRestaurant(
                name = snapshot.name,
                address = snapshot.address,
                photoPaths = snapshot.photoPaths,
            )) {
                is MealResult.Success ->
                    _uiState.update { it.copy(isSaving = false, savedId = result.value) }

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
     * 1. 保存已成功（[AddRestaurantUiState.savedId] 非空）—— 文件已被数据库引用；
     * 2. 进程重建（[wasRestoredFromSavedState]）—— 文件对应的是用户仍在编辑的表单，
     *    若此时删除，重建后的界面会显示破图，用户重新保存时会写入失效路径。
     */
    override fun onCleared() {
        if (_uiState.value.savedId == null && !wasRestoredFromSavedState()) {
            photoStore.deleteOwnedPhotos(_uiState.value.photoPaths)
        }
        super.onCleared()
    }

    /** 判断本实例是否由系统回收后的状态恢复而来。 */
    private fun wasRestoredFromSavedState(): Boolean =
        savedStateHandle.contains(KEY_NAME) || savedStateHandle.contains(KEY_PHOTOS)

    private fun MealError.toSaveMessage(): String = when (this) {
        is MealError.InvalidInput -> reason
        MealError.StorageFull -> "存储空间不足，请清理后重试"
        MealError.PhotoIoFailure -> "图片保存失败，请重新选择图片"
        else -> "餐厅记录保存失败，请稍后重试"
    }

    private companion object {
        const val IMPORT_FAILED = "图片导入失败，请重新选择"
        const val KEY_NAME = "add_name"
        const val KEY_ADDRESS = "add_address"
        const val KEY_PHOTOS = "add_photos"
    }
}
