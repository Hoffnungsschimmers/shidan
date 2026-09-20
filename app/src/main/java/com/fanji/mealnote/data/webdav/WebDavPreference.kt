package com.fanji.mealnote.data.webdav

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** WebDAV 同步配置。空地址表示未启用。 */
data class WebDavConfig(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteName: String = DEFAULT_REMOTE_NAME,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    /** 拼接后的远端文件地址，用于 PUT / GET。 */
    fun remoteUrl(): String = serverUrl.trimEnd('/') + "/" + remoteName.trimStart('/')

    companion object {
        const val DEFAULT_REMOTE_NAME = "mealnote-backup-latest.zip"
    }
}

/**
 * WebDAV 配置的持久化。
 *
 * 与 [ThemePreference][com.fanji.mealnote.data.settings.ThemePreference]
 * 一样用 `SharedPreferences` 零依赖存储。注意密码为明文存放在应用私有目录：
 * 这与备份包本身的敏感级别一致（能拿到 root 的人本来就能读数据库），
 * 界面会明确提示“仅用于你自己的服务器、建议专用账号”。
 */
@Singleton
class WebDavPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readStored())

    val config: StateFlow<WebDavConfig> = _config.asStateFlow()

    fun save(value: WebDavConfig) {
        val trimmed = value.copy(
            serverUrl = value.serverUrl.trim(),
            username = value.username.trim(),
            remoteName = value.remoteName.trim().ifBlank { WebDavConfig.DEFAULT_REMOTE_NAME },
        )
        preferences.edit {
            putString(KEY_URL, trimmed.serverUrl)
            putString(KEY_USER, trimmed.username)
            putString(KEY_PASSWORD, trimmed.password)
            putString(KEY_NAME, trimmed.remoteName)
        }
        _config.value = trimmed
    }

    private fun readStored(): WebDavConfig = WebDavConfig(
        serverUrl = preferences.getString(KEY_URL, "").orEmpty(),
        username = preferences.getString(KEY_USER, "").orEmpty(),
        password = preferences.getString(KEY_PASSWORD, "").orEmpty(),
        remoteName = preferences.getString(KEY_NAME, WebDavConfig.DEFAULT_REMOTE_NAME)
            .orEmpty().ifBlank { WebDavConfig.DEFAULT_REMOTE_NAME },
    )

    private companion object {
        const val PREFERENCES_NAME = "mealnote_webdav"
        const val KEY_URL = "server_url"
        const val KEY_USER = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_NAME = "remote_name"
    }
}
