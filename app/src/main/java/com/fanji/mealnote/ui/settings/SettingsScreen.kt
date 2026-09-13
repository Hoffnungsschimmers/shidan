package com.fanji.mealnote.ui.settings

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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.ui.components.ConfirmDialog
import com.fanji.mealnote.ui.components.MessageBanner
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixListRow
import com.fanji.mealnote.ui.components.SectionHeader
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

    // 导出：CreateDocument 让用户自选保存位置，返回的 URI 由 SAF 授予写权限，
    // 因此无需申请任何存储权限。
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    // 导入：OpenDocument 已限制为 zip，减少用户选错文件的概率。
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::requestImport) }

    // 提示语自动消失，避免用户离开本页后仍看到过期的“已导出”信息。
    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(MESSAGE_DURATION_MS)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            delay(MESSAGE_DURATION_MS)
            viewModel.consumeError()
        }
    }

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
                            title = "备份与恢复",
                            subtitle = "导出 ZIP 备份包，换机或重装后可完整恢复",
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
                        }
                    }
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
                                        "保存在应用私有目录，不会上传",
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
                            text = "味笺是一款本地优先的个人餐厅收藏与用餐记录应用。\n所有数据只保存在这台设备上，不联网、不上传。",
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
                                "版本 $APP_VERSION · 备份格式 v$BACKUP_FORMAT_VERSION",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                uiState.errorMessage?.let { MessageBanner(it) }
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

/** 与 `BuildConfig` 保持一致的应用版本；集中在此便于随版本号一起更新。 */
private const val APP_VERSION = "0.3.1"

/** 与 `BackupFormat.FORMAT_VERSION` 保持一致，用于在界面上告知用户备份格式世代。 */
private const val BACKUP_FORMAT_VERSION = 1

private const val MESSAGE_DURATION_MS = 4_000L

/** 生成导出对话框的默认文件名。 */
private fun defaultBackupName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
        .format(java.util.Date())
    return "mealnote-backup-$stamp.zip"
}
