package com.fanji.mealnote.data.webdav

import android.content.Context
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.backup.BackupStore
import com.fanji.mealnote.data.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 备份上传 / 下载的最小实现。
 *
 * ## 范围
 *
 * 只做两件事：把本地备份包 PUT 到服务器；把服务器上的备份包 GET 回来。
 * 远端文件固定为单个（默认 `mealnote-backup-latest.zip`），上传即覆盖，
 * 不做版本管理、不做增量、不做自动同步——那是云同步产品的范畴，
 * 本项目“本地优先”的定位不需要。
 *
 * ## 为什么不用 OkHttp
 *
 * 项目锁定依赖版本（见 README 开发约定），为两个接口引入 OkHttp
 * 需要验证整套版本矩阵。`HttpURLConnection` 是平台自带，
 * PUT / GET 各十几行即可，且行为完全可控（超时、重定向由我们显式处理）。
 *
 * ## 安全
 *
 * - 只允许 `https`（局域网调试可用 `http://192.168.* / 10.* / localhost`）；
 * - 认证用 Basic Auth，密码只存在应用私有 SharedPreferences；
 * - 远端内容视为不可信输入，下载后走与 SAF 导入**同一套** `BackupStore.restore`，
 *   体积上限、ZIP 炸弹防护、路径穿越防护全部复用；
 * - 传输异常统一为 `MealError.PhotoIoFailure`（网络读写失败），
 *   配置缺失为 `InvalidInput`，401/403 单独提示“账号或密码错误”，
 *   404 单独提示“服务器上还没有备份”。
 */
@Singleton
class WebDavStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupStore: BackupStore,
    private val preference: WebDavPreference,
    private val appLog: AppLog,
) {
    /** 上传结果：备份摘要沿用本地导出的统计。 */
    data class UploadSummary(
        val restaurantCount: Int,
        val recordCount: Int,
        val photoCount: Int,
    )

    /** 上传当前全部数据到 WebDAV。 */
    suspend fun upload(): MealResult<UploadSummary> = withContext(Dispatchers.IO) {
        val config = preference.config.value
        val check = checkConfig(config)
        if (check != null) return@withContext check

        // 先在本地生成一份备份包，再整体 PUT：流式边读边传失败时难以重试，
        // 整包 PUT 失败可直接整体报错，用户重试即可。
        val local = File(context.cacheDir, "webdav_upload.zip")
        try {
            val built = buildLocalBackup(local)
                ?: return@withContext MealResult.Failure(MealError.PhotoIoFailure)
            putFile(config, local)
            appLog.info("webdav", "upload ok size=${local.length()} url=${config.displayHost()}")
            MealResult.Success(
                UploadSummary(
                    restaurantCount = built.restaurantCount,
                    recordCount = built.recordCount,
                    photoCount = built.photoCount,
                )
            )
        } catch (error: WebDavHttpException) {
            appLog.warn("webdav", "upload failed code=${error.code}")
            MealResult.Failure(error.toMealError("上传失败"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            appLog.warn("webdav", "upload failed error=${error.javaClass.simpleName}")
            MealResult.Failure(MealError.PhotoIoFailure)
        } finally {
            local.delete()
        }
    }

    /** 从 WebDAV 下载备份并恢复（全量替换，与手动导入同一语义）。 */
    suspend fun downloadAndRestore(): MealResult<com.fanji.mealnote.data.backup.RestoreSummary> =
        withContext(Dispatchers.IO) {
            val config = preference.config.value
            val check = checkConfig(config)
            if (check != null) {
                @Suppress("UNCHECKED_CAST")
                return@withContext check as MealResult<com.fanji.mealnote.data.backup.RestoreSummary>
            }

            val local = File(context.cacheDir, "webdav_download.zip")
            try {
                getFile(config, local)
                appLog.info("webdav", "download ok size=${local.length()}")
                // 复用 SAF 导入的同一套校验与恢复：ZIP 炸弹、路径穿越、版本校验全都有。
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    local,
                )
                // FileProvider 的 cache-path 需覆盖该文件，见 res/xml/file_paths.xml。
                backupStore.restore(uri)
            } catch (error: WebDavHttpException) {
                appLog.warn("webdav", "download failed code=${error.code}")
                @Suppress("UNCHECKED_CAST")
                MealResult.Failure(error.toMealError("下载失败")) as MealResult<com.fanji.mealnote.data.backup.RestoreSummary>
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                appLog.warn("webdav", "download failed error=${error.javaClass.simpleName}")
                @Suppress("UNCHECKED_CAST")
                MealResult.Failure(MealError.PhotoIoFailure) as MealResult<com.fanji.mealnote.data.backup.RestoreSummary>
            } finally {
                local.delete()
            }
        }

    // ------------------------------------------------------------ 内部实现

    private fun checkConfig(config: WebDavConfig): MealResult<Nothing>? {
        if (!config.isConfigured) {
            return MealResult.Failure(MealError.InvalidInput("请先填写 WebDAV 服务器地址"))
        }
        if (!isAllowedScheme(config.serverUrl)) {
            return MealResult.Failure(MealError.InvalidInput("仅支持 https 地址（局域网可用 http 内网地址）"))
        }
        return null
    }

    private fun isAllowedScheme(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("https://")) return true
        if (!lower.startsWith("http://")) return false
        // 明文 http 仅放行局域网：坚果云/群晖内网、自建 NAS 常见场景。
        val host = lower.removePrefix("http://").substringBefore('/').substringBefore(':')
        return host == "localhost" || host == "127.0.0.1" ||
            host.startsWith("192.168.") || host.startsWith("10.") ||
            host.matches(Regex("""172\.(1[6-9]|2\d|3[01])\..*"""))
    }

    private data class LocalBackup(val restaurantCount: Int, val recordCount: Int, val photoCount: Int)

    /** 在本地生成备份包：复用 BackupStore 的导出 JSON 与照片打包逻辑。 */
    private suspend fun buildLocalBackup(target: File): LocalBackup? {
        // BackupStore.export 只写 SAF Uri；这里用 file Uri 桥接：
        // FileProvider 的 Uri 走 ContentResolver.openOutputStream 同样可写。
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        return when (val result = backupStore.export(uri)) {
            is MealResult.Success -> LocalBackup(
                result.value.restaurantCount,
                result.value.recordCount,
                result.value.photoCount,
            )

            is MealResult.Failure -> null
        }
    }

    private suspend fun putFile(config: WebDavConfig, file: File) {
        val connection = openConnection(config, "PUT")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/zip")
            connection.setRequestProperty("Content-Length", file.length().toString())
            file.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw redirectAwareError(connection, code)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun getFile(config: WebDavConfig, target: File) {
        val connection = openConnection(config, "GET")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw redirectAwareError(connection, code)
            val length = connection.contentLengthLong
            if (length > MAX_DOWNLOAD_BYTES) throw WebDavHttpException(-1)
            var bytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        if (read == 0) continue
                        bytes += read
                        if (bytes > MAX_DOWNLOAD_BYTES) throw WebDavHttpException(-1)
                        output.write(buf, 0, read)
                    }
                }
            }
            if (bytes == 0L) throw IOException("empty body")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(config: WebDavConfig, method: String): HttpURLConnection {
        val connection = URL(config.remoteUrl()).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        if (config.username.isNotBlank()) {
            val credentials = "${config.username}:${config.password}"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            connection.setRequestProperty("Authorization", "Basic $encoded")
        }
        return connection
    }

    /**
     * 把 HTTP 错误码翻译为可上报的异常。
     *
     * 必须在拿到 `responseCode` 之后再读 `Location`：连接尚未建立时读到的永远是空，
     * 早期版本在 `openConnection` 里提前检查，等于这道防线从未生效。
     * 重定向一律不自动跟随（会把 Basic 凭证送到第三方），而是中止并提示用户检查地址。
     */
    private suspend fun redirectAwareError(connection: HttpURLConnection, code: Int): WebDavHttpException {
        if (code in 300..399) {
            val location = runCatching { connection.getHeaderField("Location").orEmpty() }.getOrDefault("")
            appLog.warn("webdav", "redirect blocked code=$code")
            // 位置信息不记原文：Location 可能回显带凭证的内网地址，只记是否安全。
            if (location.isNotBlank() && !location.lowercase().startsWith("https://")) {
                return WebDavHttpException(-2)
            }
            return WebDavHttpException(-3)
        }
        return WebDavHttpException(code)
    }

    private class WebDavHttpException(val code: Int) : IOException("WebDAV http $code")

    private fun WebDavHttpException.toMealError(prefix: String): MealError = when (code) {
        401, 403 -> MealError.InvalidInput("账号或密码错误，请检查后重试")
        404 -> MealError.InvalidInput("服务器上还没有备份，请先上传")
        -1 -> MealError.InvalidInput("服务器上的备份文件过大，无法下载")
        -2 -> MealError.InvalidInput("服务器重定向地址不安全，已中止")
        -3 -> MealError.InvalidInput("服务器要求跳转，请检查地址后重试")
        else -> MealError.PhotoIoFailure
    }

    private companion object {
        const val TIMEOUT_MS = 30_000

        /** 下载上限：与本地导入的解压总量上限对齐，防止超大远端文件耗尽存储。 */
        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
    }
}

/** 脱敏后的主机名，用于日志。 */
private fun WebDavConfig.displayHost(): String =
    serverUrl.substringBefore('/').ifBlank { serverUrl }
