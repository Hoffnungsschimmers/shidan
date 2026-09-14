package com.fanji.mealnote.ui.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.ShareImageStore
import com.fanji.mealnote.data.local.RestaurantWithRecords
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 餐厅详情界面状态。
 *
 * 三种终态互斥：加载中（[isLoading]）、记录不存在（[notFound]）、正常内容（[detail] 非空）。
 * [deletionCompleted] 与 [errorMessage] 是一次性事件：界面消费后必须调用对应的 `clear*`
 * 方法复位，否则返回本页时会重复触发导航或重复弹提示。
 */
data class RestaurantDetailUiState(
    val isLoading: Boolean = true,
    val detail: RestaurantWithRecords? = null,
    val notFound: Boolean = false,
    val pendingDeleteRecordId: Long? = null,
    val showDeleteRestaurantDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val deletionCompleted: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 按用餐日期倒序的记录列表，最新一次在最前。 */
    val visits get() = detail?.records?.sortedByDescending { it.record.eatenAt }.orEmpty()

    /** 最近一次用餐时间，无记录时为 null。 */
    val latestVisitAt: Long? get() = visits.firstOrNull()?.record?.eatenAt
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RestaurantDetailViewModel @Inject constructor(
    private val repository: MealRepository,
    private val shareImageStore: ShareImageStore,
) : ViewModel() {
    private val restaurantId = MutableStateFlow<Long?>(null)

    /** 与数据库订阅流分离的本地界面状态，避免删除弹窗等瞬时状态被上游发射覆盖。 */
    private val localState = MutableStateFlow(RestaurantDetailUiState(isLoading = true))

    /**
     * 待分享图片的 content URI。
     *
     * 渲染位图必须在界面层完成（Compose 只能录制已经绘制过的内容），
     * 但写文件与生成 URI 属于数据层职责，因此由界面把位图交回来，
     * ViewModel 写好之后通过这个流把 URI 交还给界面去发起分享 Intent。
     * 一次性事件，界面消费后必须调用 [consumeShareUri] 复位。
     */
    private val _pendingShareUri = MutableStateFlow<Uri?>(null)
    val pendingShareUri: StateFlow<Uri?> = _pendingShareUri.asStateFlow()

    /**
     * 餐厅详情数据流。
     *
     * 注意初始值必须是 `isLoading = true` 且 `detail = null`：若沿用默认构造（同样 isLoading），
     * 界面会在 id 尚未注入时短暂渲染“记录不存在”的空状态，造成闪烁。
     *
     * 数据来自 Room 订阅，因此删除记录或编辑保存后页面会自动刷新，无需手动重载。
     */
    val uiState: StateFlow<RestaurantDetailUiState> = combine(
        restaurantId.flatMapLatest { id ->
            if (id == null) {
                flowOf(RestaurantDetailUiState(isLoading = true))
            } else {
                repository.observeRestaurantWithRecords(id).map { detail ->
                    RestaurantDetailUiState(
                        isLoading = false,
                        detail = detail,
                        notFound = detail == null,
                    )
                }
            }
        },
        localState,
    ) { fromDb, local ->
        // 数据库侧负责内容，本地侧负责瞬时交互状态（弹窗、提示、删除进度）。
        fromDb.copy(
            pendingDeleteRecordId = local.pendingDeleteRecordId,
            showDeleteRestaurantDialog = local.showDeleteRestaurantDialog,
            isDeleting = local.isDeleting,
            deletionCompleted = local.deletionCompleted,
            errorMessage = local.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RestaurantDetailUiState(isLoading = true),
    )

    /** 注入路由参数。重复设置同一 id 时跳过，避免重建上游 Flow 造成无谓重查询。 */
    fun setRestaurantId(id: Long) {
        if (restaurantId.value != id) restaurantId.value = id
    }

    // ------------------------------------------------------------ 分享卡片

    /**
     * 把界面渲染好的分享卡片写入缓存并交回 content URI。
     *
     * 文件名带时间戳：`FileProvider` 的 URI 一旦被接收方持有就会在分享期间保持有效，
     * 覆盖同名文件会让正在读取的接收方拿到半张图。
     */
    fun shareCard(bitmap: Bitmap) {
        viewModelScope.launch {
            val fileName = "mealnote-share-${System.currentTimeMillis()}.png"
            val uri = shareImageStore.writeShareImage(bitmap, fileName)
            if (uri == null) {
                localState.update {
                    it.copy(errorMessage = "生成分享图片失败，请检查存储空间后重试")
                }
            } else {
                _pendingShareUri.value = uri
            }
        }
    }

    fun consumeShareUri() {
        _pendingShareUri.value = null
    }

    /** 请求删除某条用餐记录；界面据此弹出确认对话框。 */
    fun requestDeleteRecord(recordId: Long) =
        localState.update { it.copy(pendingDeleteRecordId = recordId, errorMessage = null) }

    fun cancelDeleteRecord() = localState.update { it.copy(pendingDeleteRecordId = null) }

    fun requestDeleteRestaurant() =
        localState.update { it.copy(showDeleteRestaurantDialog = true, errorMessage = null) }

    fun cancelDeleteRestaurant() = localState.update { it.copy(showDeleteRestaurantDialog = false) }

    /**
     * 确认删除用餐记录。
     *
     * 防抖同样使用原子状态跃迁：对话框在删除过程中保持显示（按钮禁用），
     * 因此连续点击不会产生并发的删除请求。
     */
    fun confirmDeleteRecord() {
        val recordId = localState.value.pendingDeleteRecordId ?: return
        val snapshot = localState.updateAndGet { state ->
            if (state.isDeleting) state else state.copy(isDeleting = true, errorMessage = null)
        }
        if (!snapshot.isDeleting) return

        viewModelScope.launch {
            when (val result = repository.deleteDiningRecord(recordId)) {
                is MealResult.Success -> localState.update {
                    // 成功后关闭对话框；数据由 Room 订阅自动刷新。
                    it.copy(isDeleting = false, pendingDeleteRecordId = null, deletionCompleted = true)
                }

                is MealResult.Failure -> localState.update {
                    it.copy(isDeleting = false, pendingDeleteRecordId = null, errorMessage = result.error.toMessage())
                }
            }
        }
    }

    /**
     * 确认删除整个餐厅。
     *
     * 成功后通过 [RestaurantDetailUiState.deletionCompleted] 通知界面返回列表：
     * 此时详情页订阅的餐厅已不存在，继续停留会渲染“记录不存在”的空状态。
     */
    fun confirmDeleteRestaurant() {
        val targetId = restaurantId.value ?: return
        val snapshot = localState.updateAndGet { state ->
            if (state.isDeleting) state else state.copy(isDeleting = true, errorMessage = null)
        }
        if (!snapshot.isDeleting) return

        viewModelScope.launch {
            when (val result = repository.deleteRestaurant(targetId)) {
                is MealResult.Success -> localState.update {
                    it.copy(isDeleting = false, showDeleteRestaurantDialog = false, deletionCompleted = true)
                }

                is MealResult.Failure -> localState.update {
                    it.copy(isDeleting = false, showDeleteRestaurantDialog = false, errorMessage = result.error.toMessage())
                }
            }
        }
    }

    /** 消费一次性删除完成事件，防止返回本页时重复导航。 */
    fun consumeDeletionCompleted() = localState.update { it.copy(deletionCompleted = false) }

    /** 消费错误提示。 */
    fun consumeError() = localState.update { it.copy(errorMessage = null) }

    private fun MealError.toMessage(): String = when (this) {
        MealError.RestaurantNotFound -> "该餐厅记录已不存在"
        MealError.RecordNotFound -> "该用餐记录已不存在"
        is MealError.InvalidInput -> reason
        MealError.PhotoIoFailure -> "照片文件删除失败，可在设置中清理存储空间"
        MealError.StorageFull -> "存储空间不足"
        is MealError.DatabaseFailure -> "删除失败，请稍后重试"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
