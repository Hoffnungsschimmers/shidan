package com.fanji.mealnote.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.data.SaveKind
import com.fanji.mealnote.data.toImportMessage
import com.fanji.mealnote.data.toSaveMessage
import com.fanji.mealnote.data.log.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 从「表单里当前的图片」中挑出**可以立即回收**的那些。
 *
 * ## 为什么不能简单地「删掉上一张」
 *
 * 表单里的 `photoPaths` 在首次加载时就是**数据库当前的封面**。如果换图时无条件
 * 把上一张删掉，就会出现这条路径：
 *
 * 1. 打开某家已有封面的店 → `photoPaths = [原封面]`；
 * 2. 从相册导入新图 → 旧封面文件在导入的**那一刻**被删除；
 * 3. 用户按返回放弃编辑 → 数据库仍指向原封面，但文件已经没了。
 *
 * 结果：列表和详情页的封面变成空白，且无法恢复。因此必须显式排除仍在被引用的路径：
 *
 * - [originalCoverPath] —— 数据库当前的封面，取消编辑后界面还要用它；
 * - [recordPhotoPaths] —— 本店用餐记录的照片，删除会破坏那条记录
 *   （用户可能正是把其中一张设成了封面）；
 * - [keep] —— 本次新选中、当然要保留的那张。
 *
 * 抽成纯函数是为了能被单元测试直接覆盖：这是本项目最容易造成数据丢失的一处逻辑。
 */
internal fun reclaimableFormPhotos(
    formPhotoPaths: List<String>,
    originalCoverPath: String,
    recordPhotoPaths: Collection<String>,
    keep: String,
): List<String> {
    val protectedPaths = recordPhotoPaths.toSet() + originalCoverPath + keep
    return formPhotoPaths.filterNot(protectedPaths::contains)
}

/**
 * 编辑餐厅表单状态。
 *
 * 与新建表单的关键差异：本页**已有落库数据**，因此多出 [isLoading]（首次填充）与
 * [notFound]（记录已被他处删除）两种状态；[saved] 表示更新已提交，界面据此返回上一页。
 *
 * [originalCoverPath] 保存进入页面时数据库中的封面路径，用于判断用户是否真的换过图：
 * 只有换过才需要回收旧文件，否则会把用户刚保留的那张删掉。
 *
 * [recordPhotoPaths] 是本店全部用餐照片的路径。它有两个用途：
 * 1. 作为「从用餐照片选封面」的候选列表；
 * 2. **保护这些文件不被误删** —— 用户可能把某张用餐照片设成封面，
 *    此后取消编辑时若把它当成「新导入的图」回收，那条用餐记录的照片就没了。
 *    因此 [onCleared] 的回收集合必须同时排除 [originalCoverPath] 与 [recordPhotoPaths]。
 */
data class EditRestaurantUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val address: String = "",
    val photoPaths: List<String> = emptyList(),
    val originalCoverPath: String = "",
    /** 本店全部用餐照片，按「最近吃的在前」排序。 */
    val recordPhotoPaths: List<String> = emptyList(),
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
    private val appLog: AppLog,
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

    /**
     * 本实例是否由系统回收后的状态恢复而来。
     *
     * **必须在构造时求值，且必须声明在 `init` 之前**（属性初始化按声明顺序执行）：
     * 下面 `init` 里的持久化逻辑会把这些 key 写回 `savedStateHandle`，
     * 之后再判断就恒为 true，`onCleared` 里的图片回收会永久失效
     * （详见 `AddVisitViewModel` 的同名字段）。
     */
    private val restoredFromSavedState: Boolean =
        savedStateHandle.contains(KEY_NAME) || savedStateHandle.contains(KEY_PHOTOS)

    init {
        // 只持久化“用户输入”相关字段，并用 distinctUntilChanged 过滤掉加载态变化，
        // 避免每输入一个字符就产生一次 Bundle 写入。注意不能在这里跳过首帧：
        // 进程重建场景下首帧就是待恢复的用户输入，跳过会导致重建后改动丢失。
        // 全新进入与重建恢复的区分统一由构造时捕获的 [restoredFromSavedState] 承担，
        // 见 [load] 与 [onCleared]。
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
     *
     * 注意 `isLoading` 条件：进程重建后 `restaurantId` 已从 [SavedStateHandle] 恢复，
     * 与传入 id 相等，但数据尚未加载，此时必须继续走 [load]，否则页面永远停在加载中。
     */
    fun setRestaurantId(id: Long) {
        if (restaurantId.value == id && !_uiState.value.isLoading) return
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
        // 用餐照片属于数据库侧信息，不是用户输入，因此无论是否从进程重建恢复都要加载。
        val recordPhotos = repository.getPhotoPathsForRestaurant(id)
        // 必须用构造时捕获的 [restoredFromSavedState]，不能在这里查
        // `savedStateHandle.contains(KEY_NAME)`：`init` 里的持久化协程在构造期间
        // 就会把空表单写回 handle，此后该判断恒为 true，导致正常进入编辑页也走
        // “保留用户输入”分支，店名/地址/封面永远是空白——这正是编辑页空白的根因。
        _uiState.update { state ->
            if (restoredFromSavedState) {
                // 保留用户输入，只补上数据库侧信息。
                state.copy(
                    isLoading = false,
                    originalCoverPath = restaurant.recommendationPhotoPath,
                    recordPhotoPaths = recordPhotos,
                )
            } else {
                state.copy(
                    isLoading = false,
                    name = restaurant.name,
                    address = restaurant.address,
                    originalCoverPath = restaurant.recommendationPhotoPath,
                    recordPhotoPaths = recordPhotos,
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

    /**
     * 导入新封面；替换时回收上一张，但**只回收本次导入的**。
     *
     * 上一张若是数据库当前的封面或某条用餐记录的照片，必须保留 ——
     * 用户可能随后取消编辑，那时界面还要靠这些文件。详见 [reclaimableFormPhotos]。
     */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty() || !_uiState.beginCoverImport()) return
        viewModelScope.launch {
            try {
                val (imported, failure) = photoStore.importUrisWithReason(uris.take(1))
                val newCover = imported.lastOrNull()
                if (newCover == null) {
                    _uiState.update { it.copy(errorMessage = failure.toImportMessage()) }
                    appLog.warn("cover", "import failed failure=$failure count=${uris.size}")
                } else {
                    val obsolete = _uiState.changeCover(newCover, finishImport = true)
                    if (obsolete == null) {
                        reclaimUnusedPhoto(File(newCover))
                    } else {
                        photoStore.deleteOwnedPhotos(obsolete)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = null.toImportMessage()) }
            } finally {
                _uiState.update { it.copy(isImportingPhotos = false) }
            }
        }
    }

    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    /** 拍摄成功：新图成为封面，回收范围同样受 [reclaimableFormPhotos] 约束。 */
    fun onCameraPhoto(file: File) {
        val obsolete = _uiState.changeCover(file.absolutePath)
        if (obsolete == null) {
            reclaimUnusedPhoto(file)
        } else if (obsolete.isNotEmpty()) {
            viewModelScope.launch { photoStore.deleteOwnedPhotos(obsolete) }
        }
    }

    fun onCameraCancelled(file: File) {
        reclaimUnusedPhoto(file)
    }

    // 迟到或重复的相机/导入回调不能回收当前选中图，它可能已属于保存快照。
    private fun reclaimUnusedPhoto(file: File) {
        val state = _uiState.value
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(file.absolutePath),
            originalCoverPath = state.originalCoverPath,
            recordPhotoPaths = state.recordPhotoPaths + state.photoPaths,
            keep = "",
        )
        if (reclaimable.isNotEmpty()) {
            viewModelScope.launch { photoStore.deleteOwnedPhotos(reclaimable) }
        }
    }

    /**
     * 把某张用餐照片设为封面。
     *
     * 这是**文件别名**的来源：设置后 `restaurants.recommendationPhotoPath` 与
     * `photos.filePath` 指向同一个文件。因此：
     * - 被替换掉的「新导入」图片可以立即回收（它没有被任何行引用）；
     * - 但 [EditRestaurantUiState.originalCoverPath] 与 [EditRestaurantUiState.recordPhotoPaths]
     *   里的路径绝不能在这里删除，它们仍被数据库引用。
     *
     * @param path 必须来自 [EditRestaurantUiState.recordPhotoPaths]，否则不做任何修改。
     */
    fun useRecordPhotoAsCover(path: String) {
        val obsolete = _uiState.changeCover(path, fromRecord = true) ?: return
        if (obsolete.isNotEmpty()) {
            viewModelScope.launch { photoStore.deleteOwnedPhotos(obsolete) }
        }
    }

    /**
     * 移除封面，并在安全时回收对应文件。
     *
     * 与早先版本不同，这里**会回收**本次新导入且没有任何数据库行引用的图片：
     * 用户导入新封面后又移除，文件否则会成为永久孤儿。能否删除仍交给
     * [reclaimableFormPhotos] 判定——原封面（[EditRestaurantUiState.originalCoverPath]）
     * 与用餐照片（[EditRestaurantUiState.recordPhotoPaths]）永远保留，取消编辑后还要用它们。
     *
     * 移除与保存通过同一状态 CAS 排序：保存先取得快照时拒绝移除；
     * 移除先完成时，保存快照已不含该图，才允许回收。导入中与已保存同样拒绝变更。
     */
    fun removePhoto(index: Int) {
        val reclaimable = _uiState.removeCover(index) ?: return
        if (reclaimable.isNotEmpty()) {
            viewModelScope.launch { photoStore.deleteOwnedPhotos(reclaimable) }
        }
    }

    /**
     * 提交更新。
     *
     * 防抖采用**原子状态跃迁**（[beginRestaurantSave]）：把「加载中 / 记录不存在 / 保存中 /
     * 导入图片中 / 已保存」的判断与 `isSaving` 的置位压进同一次 CAS。早先版本用
     * `updateAndGet` 后再判断 `!snapshot.isSaving`，但 `updateAndGet` 返回的是**写入后**
     * 的状态——重复点击时 `snapshot.isSaving` 恒为 true，守卫永远不会返回，
     * 两次点击会各自启动一次更新。
     *
     * 拿到 null 表示状态机已忙碌或数据尚不可保存，本次点击直接丢弃；名称为空时
     * 跃迁只翻起 [EditRestaurantUiState.showNameError] 并返回。
     */
    fun save() {
        val targetId = restaurantId.value ?: return
        val snapshot = _uiState.beginRestaurantSave() ?: return
        if (snapshot.showNameError) return

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
                        it.copy(isSaving = false, errorMessage = result.error.toSaveMessage(SaveKind.RESTAURANT))
                    }
            }
        }
    }

    /**
     * 页面销毁时回收“新导入但未保存”的图片。
     *
     * 整体跳过清理的三种情况：
     * 1. 已保存成功（[EditRestaurantUiState.saved]）—— 文件已被数据库引用；
     * 2. 保存已开始但结果尚未回写（[EditRestaurantUiState.isSaving]）—— 事务可能已经提交，
     *    此时删除会让刚写入的记录指向不存在的图片；最坏只是留下孤儿，可由清理入口回收；
     * 3. 进程重建（[restoredFromSavedState]）—— 照片仍属于用户正在编辑的表单。
     *
     * 通过上面的整体判断后，逐张还会排除：
     * - 该路径本就是数据库中的封面（[EditRestaurantUiState.originalCoverPath]）；
     * - 该路径是本店某条用餐记录的照片（[EditRestaurantUiState.recordPhotoPaths]）——
     *   用户可能把它选成了封面，但那个文件同时也属于一条用餐记录，删掉就破坏了那条记录。
     */
    override fun onCleared() {
        val state = _uiState.value
        if (!state.isSaving && !state.saved && !restoredFromSavedState) {
            val reclaimable = reclaimableFormPhotos(
                formPhotoPaths = state.photoPaths,
                originalCoverPath = state.originalCoverPath,
                recordPhotoPaths = state.recordPhotoPaths,
                keep = "",
            )
            photoStore.deleteOwnedPhotos(reclaimable)
        }
        super.onCleared()
    }

    private companion object {
        const val KEY_RESTAURANT_ID = "edit_restaurant_id"
        const val KEY_NAME = "edit_restaurant_name"
        const val KEY_ADDRESS = "edit_restaurant_address"
        const val KEY_PHOTOS = "edit_restaurant_photos"
    }
}
