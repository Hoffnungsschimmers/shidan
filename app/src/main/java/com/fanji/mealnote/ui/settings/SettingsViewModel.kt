package com.fanji.mealnote.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.backup.BackupStore
import com.fanji.mealnote.data.export.LedgerCsvStore
import com.fanji.mealnote.data.log.AppLog
import com.fanji.mealnote.data.settings.BackupStatePreference
import com.fanji.mealnote.data.settings.BudgetPreference
import com.fanji.mealnote.data.settings.MotionPreference
import com.fanji.mealnote.data.settings.parseBudgetYuanToMinor
import com.fanji.mealnote.data.settings.RandomPickConfig
import com.fanji.mealnote.data.settings.RandomPreference
import com.fanji.mealnote.data.settings.RandomScope
import com.fanji.mealnote.data.settings.ThemeMode
import com.fanji.mealnote.data.settings.ThemePreference
import com.fanji.mealnote.data.webdav.WebDavConfig
import com.fanji.mealnote.data.webdav.WebDavPreference
import com.fanji.mealnote.data.webdav.WebDavStore
import com.fanji.mealnote.ui.tryTransition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    /** 是否展示「设置每月预算」对话框。 */
    val showBudgetDialog: Boolean = false,
) {
    /** 无数据时禁用导出与清空，避免产生空备份或误触。 */
    val canExport: Boolean get() = !isBusy && !isLoading && restaurantCount > 0
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MealRepository,
    private val backupStore: BackupStore,
    private val ledgerCsvStore: LedgerCsvStore,
    private val themePreference: ThemePreference,
    private val motionPreference: MotionPreference,
    private val randomPreference: RandomPreference,
    private val budgetPreference: BudgetPreference,
    private val backupStatePreference: BackupStatePreference,
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

    /**
     * 随机选店的候选配置。
     *
     * 同样直接暴露偏好的 Flow：清单页的随机池订阅的是**同一个** [RandomPreference.config]，
     * 复制一份进页面状态会让「设置页显示的范围」与「清单页实际抽店用的范围」有机会不一致。
     */
    val randomConfig: StateFlow<RandomPickConfig> = randomPreference.config

    fun setRandomScope(scope: RandomScope) = randomPreference.setScope(scope)

    fun setRandomIncludeWant(value: Boolean) = randomPreference.setIncludeWantToList(value)

    fun setRandomExcludeRecentDays(days: Int) = randomPreference.setExcludeRecentDays(days)

    /** 上次成功备份/上传时间(epoch millis;0=从未),设置页据此提示新鲜度。 */
    val lastBackupAt: StateFlow<Long> = backupStatePreference.lastBackupAt

    /**
     * 每月预算(分)。直接暴露偏好 Flow,足迹页账本卡订阅同一来源,不复制进页面状态。
     */
    val monthlyBudget: StateFlow<Long> = budgetPreference.monthlyBudgetMinor

    /** 打开/关闭「设置每月预算」对话框。 */
    fun openBudgetDialog() = _uiState.update { it.copy(showBudgetDialog = true) }

    fun closeBudgetDialog() = _uiState.update { it.copy(showBudgetDialog = false) }

    /** 提交预算输入(元文本)。空/非正视为清除预算。 */
    fun setMonthlyBudget(text: String) {
        budgetPreference.setMonthlyBudgetMinor(parseBudgetYuanToMinor(text))
        closeBudgetDialog()
    }

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

    /**
     * 长任务的统一入口：原子地把界面切到忙碌态；已有任务在跑时返回 null。
     *
     * 为什么不用 `updateAndGet { if (isBusy) state else state.copy(isBusy = true) }` 再看返回值：
     * 它返回的是**写入之后**的状态，`isBusy` 恒为 true，据此写的 `if (!snapshot.isBusy) return`
     * 永远不会生效，两次并发点击会各自启动一次导出/导入。这里改用 [tryTransition]，
     * 只有真正完成「闲 → 忙」跃迁的调用拿到非空状态，其余并发调用拿到 null 直接返回。
     *
     * 顺带清空上一条成功提示与常驻错误：它们描述的是上一次操作，留着会和新任务的进度矛盾。
     *
     * @return 写入后的状态；已有长任务在跑时返回 null。
     */
    private fun beginBusy(label: String): SettingsUiState? =
        _uiState.tryTransition { state ->
            if (state.isBusy) {
                null
            } else {
                state.copy(isBusy = true, busyLabel = label, message = null, errorMessage = null)
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
        if (beginBusy("正在导出…") == null) return

        viewModelScope.launch {
            try {
                when (val result = backupStore.export(target)) {
                    is MealResult.Success -> {
                        val summary = result.value
                        backupStatePreference.markBackedUp()
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
            } catch (e: CancellationException) {
                // 协程被取消（ViewModel 销毁、进程收尾）不是业务失败：原样抛给协程框架，
                // 不要在这里写成错误提示。
                throw e
            } catch (t: Throwable) {
                // 兜底：Store 层的意外异常若漏出去，isBusy 会永远停在 true、按钮从此禁用。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
            }
        }
    }

    /**
     * 导出账本为 CSV（可导入 Excel / Numbers / 表格软件）。
     *
     * 目标 URI 由 `CreateDocument("text/csv")` 返回,同样无需存储权限。空数据也允许导出
     * (只有表头的模板),因此不受 [SettingsUiState.canExport] 的「记录数>0」限制。
     */
    fun exportLedgerCsv(target: Uri) {
        if (beginBusy("正在导出账本…") == null) return

        viewModelScope.launch {
            try {
                when (val result = ledgerCsvStore.export(target)) {
                    is MealResult.Success -> _uiState.update {
                        it.copy(
                            isBusy = false,
                            busyLabel = null,
                            message = "已导出 ${result.value} 条用餐记录为 CSV",
                        )
                    }

                    is MealResult.Failure -> _uiState.update {
                        it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("导出账本失败"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：任何意外异常都不能让 isBusy 留在 true，否则导出按钮永久禁用。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
            }
        }
    }

    /** 账本 CSV 的默认文件名，供文件选择器预填。 */
    fun defaultLedgerCsvName(): String = ledgerCsvStore.defaultFileName()

    // ---------------------------------------------------------------- 导入
    /** 用户选好备份文件：不立即导入，先弹确认框，因为导入会覆盖现有数据。 */
    fun requestImport(uri: Uri) =
        _uiState.update { it.copy(pendingImportUri = uri, message = null, errorMessage = null) }

    fun cancelImport() = _uiState.update { it.copy(pendingImportUri = null) }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        if (beginBusy("正在导入…") == null) return
        // 已进入忙碌态才收起确认框：确认的职责已经交给忙碌横幅，
        // 让它继续盖在进度上方会挡住状态，也容易诱导用户重复确认。
        _uiState.update { it.copy(pendingImportUri = null) }

        viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：导入中途抛出意外异常时，事务可能已落库也可能未落库，
                // 因此复位忙碌态后同样重新统计，避免父页继续显示导入前的旧数字。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
                refreshStats()
            }
        }
    }

    // ------------------------------------------------------------ 存储清理

    /** 清理未被引用的孤儿照片。 */
    fun cleanupOrphans() {
        if (beginBusy("正在清理…") == null) return

        viewModelScope.launch {
            try {
                val removed = repository.cleanupOrphanPhotos()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = null,
                        message = if (removed == 0) "没有需要清理的文件" else "已清理 $removed 个无用文件",
                    )
                }
                refreshStats()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：清理中途的异常不能让「清理」按钮永久禁用。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
                refreshStats()
            }
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
        if (beginBusy("正在清空…") == null) return
        // 进入忙碌态后再收起对话框，避免「点了确认但框还开着」的错觉。
        _uiState.update { it.copy(showClearDataDialog = false) }

        viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：清空是单事务，异常时通常整体回滚；仍复位忙碌态并重新统计，
                // 让用户看到的是数据库的真实现状而不是一个永远转圈的按钮。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
                refreshStats()
            }
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
        if (beginBusy("正在准备日志…") == null) return

        viewModelScope.launch {
            try {
                val text = appLog.exportText()
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, pendingLogText = text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：读日志失败也要复位忙碌态，否则整页设置按钮都会被禁用。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
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

    /** 测试 WebDAV 连通性(不改动数据)。成功给绿色提示,失败给常驻错误。 */
    fun testWebDav() {
        if (beginBusy("正在测试连接…") == null) return

        viewModelScope.launch {
            try {
                when (val result = webDavStore.testConnection()) {
                    is MealResult.Success -> _uiState.update {
                        it.copy(isBusy = false, busyLabel = null, message = result.value)
                    }

                    is MealResult.Failure -> _uiState.update {
                        it.copy(isBusy = false, busyLabel = null, errorMessage = result.error.toMessage("连接测试失败"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：网络层的意外异常（证书、套接字库等）不能把按钮永久锁死。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
            }
        }
    }

    /** 上传当前全部数据到 WebDAV（覆盖远端同名文件）。 */
    fun uploadToWebDav() {
        if (beginBusy("正在上传…") == null) return

        viewModelScope.launch {
            try {
                when (val result = webDavStore.upload()) {
                    is MealResult.Success -> {
                        val summary = result.value
                        backupStatePreference.markBackedUp()
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
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：上传中途断网或套接字异常可能绕过 MealResult，必须复位忙碌态。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
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
        if (beginBusy("正在下载…") == null) return

        viewModelScope.launch {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 兜底：下载/恢复中途的意外异常可能留下部分新数据，复位后重新统计。
                _uiState.update {
                    it.copy(isBusy = false, busyLabel = null, errorMessage = "操作失败，请重试")
                }
                refreshStats()
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
