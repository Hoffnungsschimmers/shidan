package com.fanji.mealnote.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.backup.BackupStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页状态。
 *
 * 所有耗时操作（导出、导入、清理）都由 [isBusy] 统一守卫：这些操作会读写大量文件，
 * 并发执行可能产生互相干扰的中间态，因此同一时刻只允许一个进行中。
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val storageBytes: Long = 0L,
    val restaurantCount: Int = 0,
    val isBusy: Boolean = false,
    val busyLabel: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    /** 待确认的导入文件；非空时界面展示“将覆盖现有数据”的确认对话框。 */
    val pendingImportUri: Uri? = null,
    val showClearDataDialog: Boolean = false,
) {
    /** 无数据时禁用导出与清空，避免产生空备份或误触。 */
    val canExport: Boolean get() = !isBusy && !isLoading && restaurantCount > 0
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MealRepository,
    private val backupStore: BackupStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStats()
    }

    /** 重新读取存储用量与记录数。导入、清空后必须调用，否则界面显示的是旧数字。 */
    fun refreshStats() {
        viewModelScope.launch {
            // 存储统计涉及磁盘目录遍历，必须切到 IO 线程，不能占用主线程。
            val stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.photoDirectorySizeBytes() to repository.getRestaurantsForBackup().size
            }
            _uiState.update {
                it.copy(isLoading = false, storageBytes = stats.first, restaurantCount = stats.second)
            }
        }
    }

    // ---------------------------------------------------------------- 导出

    /**
     * 导出到用户通过系统文件选择器指定的位置。
     *
     * 目标 URI 由 `CreateDocument` 返回，因此应用无需任何存储权限即可写入
     * （SAF 已授予该 URI 的写权限），这也是本项目不申请 `<uses-permission>` 的原因。
     */
    fun exportTo(target: Uri) {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(isBusy = true, busyLabel = "正在导出…", message = null, errorMessage = null)
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            when (val result = backupStore.export(target)) {
                is MealResult.Success -> {
                    val summary = result.value
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = null,
                            message = "已导出 ${summary.restaurantCount} 家餐厅、${summary.recordCount} 条用餐记录、${summary.photoCount} 张照片",
                        )
                    }
                }

                is MealResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("导出失败"))
                }
            }
        }
    }

    // ---------------------------------------------------------------- 导入

    /** 用户选好备份文件：不立即导入，先弹确认框，因为导入会覆盖现有数据。 */
    fun requestImport(uri: Uri) =
        _uiState.update { it.copy(pendingImportUri = uri, message = null, errorMessage = null) }

    fun cancelImport() = _uiState.update { it.copy(pendingImportUri = null) }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(
                isBusy = true,
                busyLabel = "正在导入…",
                pendingImportUri = null,
                message = null,
                errorMessage = null,
            )
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            when (val result = backupStore.restore(uri)) {
                is MealResult.Success -> {
                    val summary = result.value
                    val skippedNote = if (summary.skippedPhotos > 0) {
                        "，${summary.skippedPhotos} 张照片无法读取已跳过"
                    } else {
                        ""
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = null,
                            message = "已恢复 ${summary.restaurantCount} 家餐厅、${summary.recordCount} 条用餐记录、${summary.photoCount} 张照片$skippedNote",
                        )
                    }
                    refreshStats()
                }

                is MealResult.Failure -> {
                    _uiState.update {
                        it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("导入失败"))
                    }
                    // 导入失败也可能已改写部分文件，重新统计以免显示失真。
                    refreshStats()
                }
            }
        }
    }

    // ------------------------------------------------------------ 存储清理

    /** 清理未被引用的孤儿照片。 */
    fun cleanupOrphans() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(isBusy = true, busyLabel = "正在清理…", message = null, errorMessage = null)
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            val removed = repository.cleanupOrphanPhotos()
            _uiState.update {
                it.copy(
                    isBusy = false,
                    busyLabel = null,
                    message = if (removed == 0) "没有需要清理的文件" else "已清理 $removed 个无用文件",
                )
            }
            refreshStats()
        }
    }

    // ------------------------------------------------------------ 清空数据

    fun requestClearData() =
        _uiState.update { it.copy(showClearDataDialog = true, message = null, errorMessage = null) }

    fun cancelClearData() = _uiState.update { it.copy(showClearDataDialog = false) }

    /**
     * 清空全部数据。
     *
     * 复用 `replaceAllData` 的空集合调用：清空顺序、外键约束与文件回收逻辑都已在该方法内
     * 统一处理，无需另写一份容易与备份逻辑产生分歧的实现。
     */
    fun confirmClearData() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(
                isBusy = true,
                busyLabel = "正在清空…",
                showClearDataDialog = false,
                message = null,
                errorMessage = null,
            )
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            when (val result = repository.replaceAllData(
                restaurants = emptyList(),
                records = emptyList(),
                photos = emptyList(),
                resolvedPaths = emptyMap(),
            )) {
                is MealResult.Success -> _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, message = "已清空全部数据")
                }

                is MealResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("清空失败"))
                }
            }
            refreshStats()
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    private fun MealError.toMessage(prefix: String): String = when (this) {
        is MealError.InvalidInput -> reason
        MealError.PhotoIoFailure -> "$prefix：文件读写失败，请检查存储空间"
        MealError.StorageFull -> "$prefix：存储空间不足"
        MealError.RestaurantNotFound, MealError.RecordNotFound -> "$prefix：数据状态异常，请重试"
        is MealError.DatabaseFailure -> "$prefix，请稍后重试"
    }
}
