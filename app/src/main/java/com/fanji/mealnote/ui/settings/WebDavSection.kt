package com.fanji.mealnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.webdav.WebDavConfig
import com.fanji.mealnote.ui.components.ConfirmDialog
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixListRow
import com.fanji.mealnote.ui.components.MiuixTextField
import com.fanji.mealnote.ui.components.SectionHeader

/**
 * WebDAV 同步区：自有服务器备份上传 / 下载。
 *
 * ## 交互
 *
 * - 未配置时只展示一个“设置同步”入口，点开展开表单，避免占用篇幅；
 * - 已配置时展示地址 + 上传/下载两个动作；修改配置需再次展开表单；
 * - 下载会覆盖本地全部数据，先弹二次确认框（与手动导入同一语义）。
 *
 * 密码框用密码键盘 + 遮罩显示；保存后表单收起，不在界面上长期展示密码。
 */
@Composable
fun WebDavSection(
    enabled: Boolean,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.webDavConfig.collectAsStateWithLifecycle()
    var expanded by rememberSaveable(config.isConfigured) { mutableStateOf(!config.isConfigured) }
    var showDownloadConfirm by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "服务器同步",
            subtitle = "把备份包存到你自己的 WebDAV，换机时直接下载",
        )
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        ) {
            if (!expanded) {
                MiuixListRow(
                    icon = Icons.Rounded.Sync,
                    title = if (config.isConfigured) config.serverUrl else "设置同步",
                    subtitle = if (config.isConfigured) {
                        "远端文件：${config.remoteName}，点此修改配置"
                    } else {
                        "支持坚果云、群晖、NAS 等 WebDAV"
                    },
                    enabled = enabled,
                    onClick = { expanded = true },
                )
            } else {
                WebDavForm(
                    config = config,
                    enabled = enabled,
                    onSave = {
                        viewModel.saveWebDavConfig(it)
                        expanded = false
                    },
                    onCancel = { expanded = false },
                )
                return@MiuixCard
            }
            if (config.isConfigured && !expanded) {
                MiuixListRow(
                    icon = Icons.Rounded.CloudUpload,
                    title = "上传当前数据",
                    subtitle = "覆盖服务器上的备份文件",
                    enabled = enabled,
                    onClick = onUpload,
                )
                MiuixListRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = "从服务器恢复",
                    subtitle = "会覆盖本机全部数据，请先备份",
                    enabled = enabled,
                    onClick = { showDownloadConfirm = true },
                )
            }
        }
        if (config.isConfigured && !expanded) {
            Text(
                text = "仅上传到你自己的服务器；建议使用专用账号，密码只保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }

    if (showDownloadConfirm) {
        ConfirmDialog(
            title = "从服务器恢复并覆盖？",
            message = "本机全部店铺、用餐记录与照片将被服务器上的备份替换，且无法撤销。建议先在本地导出一份备份。",
            confirmLabel = "覆盖并恢复",
            onConfirm = {
                showDownloadConfirm = false
                onDownload()
            },
            onDismiss = { showDownloadConfirm = false },
        )
    }
}

@Composable
private fun WebDavForm(
    config: WebDavConfig,
    enabled: Boolean,
    onSave: (WebDavConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var url by rememberSaveable(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var username by rememberSaveable(config.username) { mutableStateOf(config.username) }
    var password by rememberSaveable(config.password) { mutableStateOf(config.password) }
    var remoteName by rememberSaveable(config.remoteName) { mutableStateOf(config.remoteName) }
    var showError by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.height(10.dp))
        MiuixTextField(
            value = url,
            onValueChange = { url = it; showError = false },
            label = "服务器地址",
            placeholder = "https://dav.example.com/webdav",
            singleLine = true,
            isError = showError && url.isBlank(),
            supportingText = if (showError && url.isBlank()) "请填写服务器地址" else "以 https 开头，局域网可用 http 内网地址",
            keyboardType = KeyboardType.Uri,
        )
        MiuixTextField(
            value = username,
            onValueChange = { username = it },
            label = "账号（选填）",
            placeholder = "WebDAV 用户名",
            singleLine = true,
        )
        MiuixTextField(
            value = password,
            onValueChange = { password = it },
            label = "密码（选填）",
            placeholder = "WebDAV 密码或应用专用密码",
            singleLine = true,
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
        )
        MiuixTextField(
            value = remoteName,
            onValueChange = { remoteName = it },
            label = "远端文件名",
            placeholder = WebDavConfig.DEFAULT_REMOTE_NAME,
            singleLine = true,
        )
        Spacer(Modifier.height(6.dp))
        MiuixButton(
            label = "保存配置",
            onClick = {
                if (url.isBlank()) {
                    showError = true
                } else {
                    onSave(
                        WebDavConfig(
                            serverUrl = url.trim(),
                            username = username.trim(),
                            password = password,
                            remoteName = remoteName.trim().ifBlank { WebDavConfig.DEFAULT_REMOTE_NAME },
                        ),
                    )
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
        )
        MiuixButton(
            label = "取消",
            onClick = onCancel,
            style = MiuixButtonStyle.Text,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
        )
        Spacer(Modifier.height(6.dp))
    }
}
