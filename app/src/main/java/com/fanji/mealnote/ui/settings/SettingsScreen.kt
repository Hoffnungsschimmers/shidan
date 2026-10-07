package com.fanji.mealnote.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.BuildConfig
import com.fanji.mealnote.data.backup.BackupFormat
import com.fanji.mealnote.data.settings.RANDOM_EXCLUDE_DAY_OPTIONS
import com.fanji.mealnote.data.settings.RandomScope
import com.fanji.mealnote.data.settings.ThemeMode
import com.fanji.mealnote.data.settings.backupFreshnessLabel
import com.fanji.mealnote.data.settings.randomExcludeDaysLabel
import com.fanji.mealnote.ui.components.ConfirmDialog
import com.fanji.mealnote.ui.components.MessageBanner
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixListRow
import com.fanji.mealnote.ui.components.MiuixSegmented
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.formatLedgerAmount
import com.fanji.mealnote.ui.formatStorageSize
import kotlinx.coroutines.delay

/**
 * 设置页（底部导航的「我的」标签页）。
 *
 * 顶部不放返回按钮：它是标签页而不是二级页面，返回按钮会暗示「这里可以退出」，
 * 而实际上用户应该用底部导航切换。页面标题使用列表内的页面级大标题。
 *
 * @param contentBottomPadding 内容底部留白，需要避开底部导航栏。
 */
@Composable
fun SettingsScreen(
    contentBottomPadding: Dp = 32.dp,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 导出：CreateDocument 让用户自选保存位置，返回的 URI 由 SAF 授予写权限，
    // 因此无需申请任何存储权限。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    // 账本 CSV 导出：同样走 SAF，返回的 URI 自带写权限。
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportLedgerCsv) }

    // 导入：OpenDocument 已限制为 zip，减少用户选错文件的概率。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::requestImport) }

    // 成功提示自动消失，避免用户离开本页后仍看到过期的「已导出」。
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(MESSAGE_DURATION_MS)
            viewModel.consumeMessage()
        }
    }
    // 失败提示**不自动消失**：4 秒横幅等于没提示 —— WebDAV 上传失败后用户看不到原因，
    // 只会隔几分钟再点一次下载，然后收到一句「服务器上还没有备份」，两头都对不上。
    // 保留 consumeError 出口，由用户点「知道了」关闭。

    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("我的", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "数据备份、存储占用与应用信息。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "外观",
                            subtitle = "深色模式默认跟随系统",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            MiuixSegmented(
                                options = ThemeMode.entries.toList(),
                                selected = themeMode,
                                onSelect = viewModel::setThemeMode,
                                label = { it.label() },
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "流畅模式",
                            subtitle = "关闭后动画与玻璃模糊降级，低端机更流畅",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            val fluid by viewModel.fluidMotion.collectAsStateWithLifecycle()
                            MiuixListRow(
                                icon = Icons.Rounded.Speed,
                                title = "完整动效与实时模糊",
                                subtitle = if (fluid) "已开启，若滑动卡顿可关掉试试" else "已关闭，界面更流畅省电",
                                trailingText = if (fluid) "开" else "关",
                                onClick = { viewModel.setFluidMotion(!fluid) },
                            )
                        }
                    }
                }

                item {
                    val randomConfig by viewModel.randomConfig.collectAsStateWithLifecycle()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "随机选店",
                            subtitle = "清单页「不知道吃啥？随机选一家」按什么范围抽店",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 分段控件本身只有选项文字，不说明它管什么，
                                // 因此每一组都要有一行说明——不能靠位置让用户猜。
                                FieldCaption("评价范围：按每家店最近一次的评价")
                                MiuixSegmented(
                                    options = RandomScope.entries.toList(),
                                    selected = randomConfig.scope,
                                    onSelect = viewModel::setRandomScope,
                                    label = { it.label },
                                )
                            }
                        }
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                FieldCaption("排除最近吃过的店")
                                MiuixSegmented(
                                    options = RANDOM_EXCLUDE_DAY_OPTIONS,
                                    selected = randomConfig.excludeRecentDays,
                                    onSelect = viewModel::setRandomExcludeRecentDays,
                                    label = { randomExcludeDaysLabel(it) },
                                )
                            }
                        }
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            val includeWant = randomConfig.includeWantToList
                            MiuixListRow(
                                icon = Icons.Rounded.Storefront,
                                title = "待探访参与随机",
                                subtitle = if (includeWant) {
                                    "还没吃过的店也进候选池"
                                } else {
                                    "只从有评价的店里抽"
                                },
                                trailingText = if (includeWant) "开" else "关",
                                onClick = { viewModel.setRandomIncludeWant(!includeWant) },
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "账本",
                            subtitle = "设置每月吃饭预算，足迹·账本卡会显示本月进度",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            MiuixListRow(
                                icon = Icons.Rounded.Savings,
                                title = "每月预算",
                                subtitle = if (monthlyBudget > 0L) {
                                    "超支时账本卡进度条会变红"
                                } else {
                                    "未设置，点此设定后按月对照花费"
                                },
                                trailingText = if (monthlyBudget > 0L) {
                                    "¥${monthlyBudget.formatLedgerAmount()}"
                                } else {
                                    "未设"
                                },
                                onClick = viewModel::openBudgetDialog,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "备份与恢复",
                            subtitle = "${backupFreshnessLabel(lastBackupAt, System.currentTimeMillis(), java.time.ZoneId.systemDefault())} · 导出 ZIP 备份包，换机或重装后可完整恢复",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            MiuixListRow(
                                icon = Icons.Rounded.FileDownload,
                                title = "导出全部数据",
                                subtitle = if (uiState.restaurantCount > 0) {
                                    "共 ${uiState.restaurantCount} 家店，含用餐记录与照片"
                                } else {
                                    "暂无可导出的数据"
                                },
                                enabled = uiState.canExport,
                                onClick = { exportLauncher.launch(defaultBackupName()) },
                            )
                            RowDivider()
                            MiuixListRow(
                                icon = Icons.Rounded.FileUpload,
                                title = "从备份文件恢复",
                                subtitle = "会覆盖当前全部数据，操作前请先导出",
                                enabled = !uiState.isBusy,
                                onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            )
                            RowDivider()
                            MiuixListRow(
                                icon = Icons.Rounded.TableChart,
                                title = "导出账本 CSV",
                                subtitle = "用餐记录导出为表格，可用 Excel / Numbers 打开",
                                enabled = !uiState.isBusy,
                                onClick = { exportCsvLauncher.launch(viewModel.defaultLedgerCsvName()) },
                            )
                        }
                    }
                }

                item {
                    WebDavSection(
                        enabled = !uiState.isBusy,
                        onUpload = viewModel::uploadToWebDav,
                        onDownload = viewModel::downloadFromWebDav,
                        onTest = viewModel::testWebDav,
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(title = "存储占用")
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("照片与封面", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(2.dp))
                                Text(
                                        "保存在应用私有目录，仅同步时上传到你自己的服务器",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        uiState.storageBytes.formatStorageSize(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            RowDivider()
                            MiuixListRow(
                                icon = Icons.Rounded.CleaningServices,
                                title = "清理无用文件",
                                subtitle = "回收保存中断遗留的残留图片",
                                enabled = !uiState.isBusy && !uiState.isLoading,
                                onClick = viewModel::cleanupOrphans,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(title = "危险操作")
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            MiuixListRow(
                                icon = Icons.Rounded.DeleteForever,
                                title = "清空全部数据",
                                subtitle = "删除所有店铺、用餐记录与照片",
                                enabled = !uiState.isBusy && uiState.restaurantCount > 0,
                                destructive = true,
                                onClick = viewModel::requestClearData,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(title = "关于")
                        Text(
                            text = "食单是一款本地优先的个人餐厅收藏与用餐记录应用。\n所有数据默认只保存在这台设备上；仅在使用服务器同步时连接你自己的服务器。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "版本 ${BuildConfig.VERSION_NAME} · 备份格式 v${BackupFormat.FORMAT_VERSION}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = "运行日志",
                            subtitle = "遇到问题时导出，发给开发者定位",
                        )
                        MiuixCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 2.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        ) {
                            MiuixListRow(
                                icon = Icons.Rounded.BugReport,
                                title = "导出运行日志",
                                subtitle = if (uiState.isLoading) {
                                    "正在统计…"
                                } else {
                                    "约 ${uiState.logBytes.formatStorageSize()}，只含事件不含隐私内容"
                                },
                                enabled = !uiState.isBusy && !uiState.isLoading,
                                onClick = viewModel::shareLog,
                            )
                        }
                    }
                }
            }

            // 忙碌指示与提示条固定在底部，保证用户在任何滚动位置都能看到进度。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.isBusy) {
                    MiuixCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 8.dp,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Text(uiState.busyLabel.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                uiState.message?.let { MessageBanner(it) }
                uiState.errorMessage?.let {
                    MessageBanner(
                        message = it,
                        destructive = true,
                        actionLabel = "知道了",
                        onAction = viewModel::consumeError,
                    )
                }
            }
        }
    }

    uiState.pendingImportUri?.let {
        ConfirmDialog(
            title = "导入并覆盖数据？",
            message = "当前设备上的全部店铺、用餐记录与照片将被备份文件中的内容替换，且无法撤销。建议先导出一份备份。",
            confirmLabel = "覆盖并导入",
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::cancelImport,
        )
    }

    if (uiState.showClearDataDialog) {
        ConfirmDialog(
            title = "清空全部数据？",
            message = "所有店铺、用餐记录与照片都会被永久删除，且无法恢复。请确认已导出备份。",
            confirmLabel = "清空数据",
            onConfirm = viewModel::confirmClearData,
            onDismiss = viewModel::cancelClearData,
        )
    }

    if (uiState.showBudgetDialog) {
        BudgetDialog(
            currentMinor = monthlyBudget,
            onConfirm = viewModel::setMonthlyBudget,
            onDismiss = viewModel::closeBudgetDialog,
        )
    }

    // 日志分享：文本经 chooser 发给微信/邮件等，消费后复位避免重复弹出。
    LaunchedEffect(uiState.pendingLogText) {
        val text = uiState.pendingLogText ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "食单运行日志")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "导出运行日志"))
        viewModel.consumeLogText()
    }
}

/**
 * 「设置每月预算」对话框。
 *
 * 输入元、内部转成整数分(见 `parseBudgetYuanToMinor`)。留空即清除预算。
 */
@Composable
private fun BudgetDialog(
    currentMinor: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(if (currentMinor > 0L) currentMinor.formatLedgerAmount() else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("每月预算", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "设置每月吃饭预算（元）。留空或填 0 则取消预算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MiuixTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "预算金额（元）",
                    placeholder = "例如 2000",
                    singleLine = true,
                    keyboardType = KeyboardType.Decimal,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 卡片内的行分隔线。
 *
 * 左侧留出 56dp 缩进，与 [MiuixListRow] 的图标宽度对齐 —— 通栏的分隔线会把
 * 一行切成两半，缩进后视觉上归属于「行与行之间」而非「卡片被切开」。
 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 卡片内部分项的说明行。
 *
 * 只用于「控件本身没有文字标签」的场合（如分段控件）：一组选项摆在那里不说明它管什么，
 * 就等于把理解成本推给用户。沿用既有的 `labelLarge` + 次级文字色，不引入新色值。
 */
@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 主题模式的中文文案。
 *
 * 「跟随系统」不用「自动」：后者会让人以为是「根据时间自动切换深色」，
 * 而实际含义是「与系统设置保持一致」。
 */
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private const val MESSAGE_DURATION_MS = 4_000L

/** 生成导出对话框的默认文件名。 */
private fun defaultBackupName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
        .format(java.util.Date())
    return "mealnote-backup-$stamp.zip"
}
