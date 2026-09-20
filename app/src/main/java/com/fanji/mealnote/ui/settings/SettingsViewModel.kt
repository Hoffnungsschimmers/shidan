package com.fanji.mealnote.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.backup.BackupStore
import com.fanji.mealnote.data.log.AppLog
import com.fanji.mealnote.data.settings.MotionPreference
import com.fanji.mealnote.data.settings.ThemeMode
import com.fanji.mealnote.data.settings.ThemePreference
import com.fanji.mealnote.data.webdav.WebDavConfig
import com.fanji.mealnote.data.webdav.WebDavPreference
import com.fanji.mealnote.data.webdav.WebDavStore
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
    val logBytes: Long = 0L,
    val isBusy: Boolean = false,
    val busyLabel: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    /** 待确认的导入文件；非空时界面展示“将覆盖现有数据”的确认对话框。 */
    val pendingImportUri: Uri? = null,
    val showClearDataDialog: Boolean = false,
    /** 待分享的日志文本；非空时界面发起系统分享，消费后复位。 */
    val pendingLogText: String? = null,
) {
    /** 无数据时禁用导出与清空，避免产生空备份或误触。 */
    val canExport: Boolean get() = !isBusy && !isLoading && restaurantCount > 0
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MealRepository,
    private val backupStore: BackupStore,
    private val themePreference: ThemePreference,
    private val motionPreference: MotionPreference,
    private val webDavPreference: WebDavPreference,
    private val webDavStore: WebDavStore,
    private val appLog: AppLog,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * 主题模式。
     *
     * 直接暴露 [ThemePreference] 的 Flow 而不是复制一份到 [SettingsUiState]：
     * 主题是全局状态，复制进页面状态会让「两处真相」有机会不一致，
     * 也会在每次切换时多做一次无意义的状态拷贝。
     */
    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    /** 切换主题。写入是同步的，界面会立刻反映，无需等待。 */
    fun setThemeMode(value: ThemeMode) = themePreference.setMode(value)

    /**
     * 流畅模式。
     *
     * 与主题一样直接暴露偏好的 Flow：这是全局渲染开关，复制进页面状态
     * 会让开关与实际渲染有机会不一致。
     */
    val fluidMotion: StateFlow<Boolean> = motionPreference.fluid

    /** 切换流畅模式，立即生效，下次启动保持。 */
    fun setFluidMotion(value: Boolean) = motionPreference.setFluid(value)

    init {
        refreshStats()
    }

    /** 重新读取存储用量与记录数。导入、清空后必须调用，否则界面显示的是旧数字。 */
    fun refreshStats() {
        viewModelScope.launch {
            // 存储统计涉及磁盘目录遍历，必须切到 IO 线程，不能占用主线程。
            val stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Triple(
                    repository.photoDirectorySizeBytes(),
                    repository.getRestaurantsForBackup().size,
                    appLog.sizeBytes(),
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    storageBytes = stats.first,
                    restaurantCount = stats.second,
                    logBytes = stats.third,
                )
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

    // ------------------------------------------------------------ 运行日志

    /**
     * 导出运行日志并交给系统分享。
     *
     * 日志只含事件与脱敏后的来源，不含店名、备注等隐私内容（见 `AppLog`），
     * 用户可直接把分享出去的文本发给开发者定位问题。
     */
    fun shareLog() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(isBusy = true, busyLabel = "正在准备日志…", message = null, errorMessage = null)
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            val text = appLog.exportText()
            _uiState.update {
                it.copy(isBusy = false, busyLabel = null, pendingLogText = text)
            }
        }
    }

    /** 界面已发起分享，复位一次性事件，避免返回本页重复弹出。 */
    fun consumeLogText() = _uiState.update { it.copy(pendingLogText = null) }

    // ------------------------------------------------------------ WebDAV 同步

    /** WebDAV 配置，界面直接订阅，保存后即时生效。 */
    val webDavConfig: StateFlow<WebDavConfig> = webDavPreference.config

    /** 保存 WebDAV 配置（地址、账号、密码、远端文件名）。 */
    fun saveWebDavConfig(value: WebDavConfig) {
        webDavPreference.save(value)
        _uiState.update { it.copy(message = "已保存同步配置", errorMessage = null) }
    }

    /** 上传当前全部数据到 WebDAV（覆盖远端同名文件）。 */
    fun uploadToWebDav() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(isBusy = true, busyLabel = "正在上传…", message = null, errorMessage = null)
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            when (val result = webDavStore.upload()) {
                is MealResult.Success -> {
                    val summary = result.value
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = null,
                            message = "已上传 ${summary.restaurantCount} 家餐厅、${summary.recordCount} 条用餐记录、${summary.photoCount} 张照片",
                        )
                    }
                }

                is MealResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("上传失败"))
                }
            }
        }
    }

    /**
     * 从 WebDAV 下载备份并恢复。
     *
     * 与手动导入**同一语义**：全量替换本地数据。调用前界面必须二次确认，
     * 复用 `pendingImportUri` 的确认框机制太绕，这里由调用方在界面层弹框，
     * 本方法只执行下载与恢复。
     */
    fun downloadFromWebDav() {
        val snapshot = _uiState.updateAndGet { state ->
            if (state.isBusy) state else state.copy(isBusy = true, busyLabel = "正在下载…", message = null, errorMessage = null)
        }
        if (!snapshot.isBusy) return

        viewModelScope.launch {
            when (val result = webDavStore.downloadAndRestore()) {
                is MealResult.Success -> {
                    val summary = result.value
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = null,
                            message = "已恢复 ${summary.restaurantCount} 家餐厅、${summary.recordCount} 条用餐记录、${summary.photoCount} 张照片",
                        )
                    }
                    refreshStats()
                }

                is MealResult.Failure -> {
                    _uiState.update {
                        it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("下载失败"))
                    }
                    refreshStats()
                }
            }
        }
    }

    private fun MealError.toMessage(prefix: String): String = when (this) {
        is MealError.InvalidInput -> reason
        MealError.PhotoIoFailure -> "$prefix：文件读写失败，请检查存储空间"
        MealError.StorageFull -> "$prefix：存储空间不足"
        MealError.RestaurantNotFound, MealError.RecordNotFound -> "$prefix：数据状态异常，请重试"
        is MealError.DatabaseFailure -> "$prefix，请稍后重试"
    }
}
