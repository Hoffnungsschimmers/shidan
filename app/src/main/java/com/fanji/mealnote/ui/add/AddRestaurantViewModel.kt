package com.fanji.mealnote.ui.add

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
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.log.AppLog
import com.fanji.mealnote.ui.tryTransition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val appLog: AppLog,
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

    /** 现有餐厅列表快照,用于重复店名提醒。 */
    private val restaurants = MutableStateFlow<List<RestaurantEntity>>(emptyList())

    /**
     * 与当前输入的店名同名的现有店铺名;无则为 null。
     *
     * 单独暴露而不折进 [uiState]:它由「现有列表 + 当前输入」派生,折进 uiState 会与
     * 表单持久化流互相触发。界面据此显示一句非阻塞提醒,用户仍可执意保存(同名分店合法)。
     */
    val duplicateName: StateFlow<String?> = combine(
        restaurants,
        _uiState.map { it.name }.distinctUntilChanged(),
    ) { list, name ->
        list.findDuplicateName(name)?.name
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

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
        savedStateHandle.contains(KEY_NAME) || savedStateHandle.contains(KEY_PHOTOS)

    init {
        // 订阅现有餐厅,供重复店名提醒使用。
        viewModelScope.launch {
            repository.observeRestaurants().collect { restaurants.value = it }
        }
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
     * 原子地占用「图片处理中」通道（[AddRestaurantUiState.isImportingPhotos]）。
     *
     * 相册导入与相机结果都会整体替换 [AddRestaurantUiState.photoPaths] 并删除被替换的文件，
     * 两者必须互斥。早先版本在协程内部才置位，两个结果可以交错完成：后完成的一方用旧快照
     * 覆盖新状态，还会把对方刚选中的文件当成「被替换的旧图」删掉。
     *
     * 这里把「判断」与「置位」压进一次 CAS：返回 true 表示本次拿到了处理权；
     * false 表示已有导入/拍照在收尾，调用方应丢弃本次结果。复位统一在 finally 里做，
     * 保证文件回收也落在临界区内，不会被下一次操作抢先。
     */
    private fun claimPhotoPipeline(): Boolean =
        _uiState.tryTransition { state ->
            if (state.isImportingPhotos) null
            else state.copy(isImportingPhotos = true, errorMessage = null)
        } != null

    /**
     * 导入相册选择结果。封面只保留一张，新图替换旧图时回收旧文件。
     *
     * 占用通道必须在**启动协程之前**完成（见 [claimPhotoPipeline]）：导入是异步的，
     * 置位晚于启动就会出现上面描述的并发交错；已在导入中则直接丢弃本次结果。
     *
     * 注意删除必须发生在状态更新**之后**（把 IO 移出 `update` lambda），
     * 且不能读取 `update` lambda 的参数做删除：该 lambda 可能被重复执行，
     * 在其中做磁盘 IO 会阻塞主线程并导致同一文件被删除多次。
     */
    fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!claimPhotoPipeline()) return
        viewModelScope.launch {
            try {
                val (imported, failure) = photoStore.importUrisWithReason(uris.take(1))
                val newCover = imported.lastOrNull()
                // 导入失败时并没有发生「替换」：表单里现有的封面仍被界面与随后的保存引用，
                // 绝不能进入回收集合（否则会删掉文件、状态却仍指向它，保存后得到破图）。
                val obsolete = if (newCover == null) {
                    emptyList()
                } else {
                    _uiState.value.photoPaths.filterNot { it == newCover }
                }
                _uiState.update { state ->
                    if (newCover == null) {
                        state.copy(errorMessage = failure.toImportMessage())
                    } else {
                        state.copy(photoPaths = listOf(newCover), errorMessage = null)
                    }
                }
                if (newCover == null) {
                    appLog.warn("cover", "import failed failure=$failure count=${uris.size}")
                }
                if (obsolete.isNotEmpty()) {
                    photoStore.deleteOwnedPhotos(obsolete)
                }
            } finally {
                // 无论成功、失败还是异常，都必须释放通道，否则保存会一直被拒绝。
                _uiState.update { it.copy(isImportingPhotos = false) }
            }
        }
    }

    /** 创建相机拍摄目标文件。调用方需在回调中二选一：保留或 [onCameraCancelled]。 */
    fun createCameraTarget(): Pair<File, Uri> = photoStore.createCameraTarget()

    /**
     * 相机拍摄成功，将该文件设为封面，并回收此前已选中的其他图片。
     *
     * 与相册导入共用同一条通道（[claimPhotoPipeline]）：拍照结果同样要在启动协程前原子占位，
     * 否则可能与正在进行的导入交错，互相覆盖 [AddRestaurantUiState.photoPaths]、误删对方的文件。
     * 通道被占用（或图片列表已被并发修改）时本次拍摄结果无法保留，按取消处理删除目标文件
     * ——[PhotoStore.createCameraTarget] 要求回调必须二选一「保留或删除」，不能留下孤儿文件。
     *
     * 文件删除是磁盘 IO，必须放入协程：早期版本在 `update` 的 lambda 内直接调用会阻塞
     * 主线程，且 lambda 可能被重复执行导致同一文件被删除多次。
     */
    fun onCameraPhoto(file: File) {
        // 先读快照、再在跃迁里比对，等价于一次 CAS：只有「通道空闲且图片列表没被别人改过」
        // 才接受拍摄结果。跃迁返回的是写入后的状态，旧路径必须从快照里取。
        val snapshot = _uiState.value
        val claimed = _uiState.tryTransition { state ->
            if (state.isImportingPhotos || state.photoPaths != snapshot.photoPaths) {
                null
            } else {
                state.copy(
                    photoPaths = listOf(file.absolutePath),
                    isImportingPhotos = true,
                    errorMessage = null,
                )
            }
        }
        if (claimed == null) {
            onCameraCancelled(file)
            return
        }
        viewModelScope.launch {
            try {
                photoStore.deleteOwnedPhotos(snapshot.photoPaths.filterNot { it == file.absolutePath })
            } finally {
                _uiState.update { it.copy(isImportingPhotos = false) }
            }
        }
    }

    /** 相机被取消或拍摄失败，回收预创建的空文件，避免产生孤儿文件。 */
    fun onCameraCancelled(file: File) {
        viewModelScope.launch { photoStore.deleteOwnedPhoto(file.absolutePath) }
    }

    /**
     * 移除表单里的封面，并在安全时回收对应文件。
     *
     * 「从列表移除」与「是否正在保存」通过同一次 CAS 取得一致：若保存先一步置位，
     * 这里拿到的状态 [AddRestaurantUiState.isSaving] 为 true，只移除不删文件——
     * 保存可能已经提交，删掉会让刚写入的记录指向不存在的图片（最坏留个孤儿，
     * 可由清理入口回收）；若移除先完成，保存随后拿到的快照里已没有这张图，删除才是安全的。
     */
    fun removePhoto(index: Int) {
        val snapshot = _uiState.value
        val removed = snapshot.photoPaths.getOrNull(index) ?: return
        val state = _uiState.tryTransition { current ->
            if (current.photoPaths.getOrNull(index) != removed) {
                null
            } else {
                current.copy(photoPaths = current.photoPaths.filterIndexed { i, _ -> i != index })
            }
        } ?: return
        // 已保存成功时同理：界面正在返回上一页，文件已被数据库引用。
        if (state.isSaving || state.savedId != null) return
        viewModelScope.launch { photoStore.deleteOwnedPhoto(removed) }
    }

    /**
     * 提交保存。
     *
     * 防抖采用**原子状态跃迁**（[tryTransition]）：把「是否已在保存 / 正在导入图片 / 已保存」
     * 的判断与 `isSaving` 的置位压进同一次 CAS。早先版本用 `updateAndGet` 后再判断
     * `!snapshot.isSaving`，但 `updateAndGet` 返回的是**写入后**的状态——重复点击时
     * `snapshot.isSaving` 恒为 true，守卫永远不会返回，两次点击会各自写一次数据库。
     *
     * 拿到 null 表示状态机已忙碌，本次点击直接丢弃；名称为空时跃迁只翻起
     * [AddRestaurantUiState.showNameError] 并返回，同样不进入保存。
     *
     * 保存使用的是跃迁成功后那一刻的状态快照：点击之后用户若继续修改表单，
     * 改动不会混入已经提交的这一笔。
     */
    fun save() {
        val snapshot = _uiState.tryTransition { state ->
            when {
                state.savedId != null || state.isSaving || state.isImportingPhotos -> null
                state.name.isBlank() -> state.copy(showNameError = true)
                else -> state.copy(isSaving = true, errorMessage = null)
            }
        } ?: return
        if (snapshot.showNameError) return

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
                        it.copy(isSaving = false, errorMessage = result.error.toSaveMessage(SaveKind.RESTAURANT))
                    }
            }
        }
    }

    /**
     * 页面销毁时回收未提交的图片。
     *
     * 三种必须跳过清理的情况：
     * 1. 保存已成功（[AddRestaurantUiState.savedId] 非空）—— 文件已被数据库引用；
     * 2. 保存已开始但结果尚未回写（[AddRestaurantUiState.isSaving]）—— 事务可能已经提交，
     *    此时删除会让刚写入的记录指向不存在的图片；最坏只是留下孤儿，可由清理入口回收；
     * 3. 进程重建（[restoredFromSavedState]）—— 文件对应的是用户仍在编辑的表单，
     *    若此时删除，重建后的界面会显示破图，用户重新保存时会写入失效路径。
     */
    override fun onCleared() {
        val state = _uiState.value
        if (!state.isSaving && state.savedId == null && !restoredFromSavedState) {
            photoStore.deleteOwnedPhotos(state.photoPaths)
        }
        super.onCleared()
    }

    private companion object {
        const val KEY_NAME = "add_name"
        const val KEY_ADDRESS = "add_address"
        const val KEY_PHOTOS = "add_photos"
    }
}
