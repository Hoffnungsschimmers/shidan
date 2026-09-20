package com.fanji.mealnote.data.log

import android.content.Context
import android.net.Uri
import android.os.Build
import com.fanji.mealnote.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用内运行日志。
 *
 * ## 为什么需要它
 *
 * 用户反馈的“导入失败”“编辑空白”在开发机上无法复现，只能靠用户把现场信息发回来。
 * 系统 logcat 需要连电脑抓，门槛太高；应用内记一份精简日志，
 * 用户在设置页一键导出 txt 发过来即可定位。
 *
 * ## 隐私边界
 *
 * 只记**事件与原因**，不记内容：
 * - 记：导入了几张、失败在解码还是写盘、异常类名与前 200 字信息；
 * - 不记：店名、地址、餐品、备注、花费原文、文件绝对路径、URI 明文、备份 JSON 内容。
 * URI 只记 scheme + authority（如 `content:com.android.providers.media.documents`），
 * 文件只记所在目录名与扩展名，足够判断来源又不泄露隐私。
 *
 * ## 实现
 *
 * 内存环形缓冲（上限 500 条）+ 落盘追加（上限 200KB，超限滚动覆盖）。
 * 所有写操作经 [Mutex] 串行，调用方无需关心线程。
 * 落盘在 `cacheDir/logs/`，随系统缓存管理，不计入“照片占用”。
 */
@Singleton
class AppLog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val buffer = ArrayDeque<String>(MAX_MEMORY_LINES + 16)

    private fun logFile(): File {
        val dir = File(context.cacheDir, LOG_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE_NAME)
    }

    suspend fun info(tag: String, message: String) = write("I", tag, message)

    suspend fun warn(tag: String, message: String) = write("W", tag, message)

    suspend fun error(tag: String, message: String, cause: Throwable? = null) =
        write("E", tag, if (cause == null) message else "$message | ${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(200)}")

    /** 写入一条已格式化的完整日志行（形如 `MM-dd HH:mm:ss.SSS I/tag: msg`）。 */
    suspend fun writeLine(line: String) {
        val sanitized = line.take(MAX_LINE_CHARS)
        mutex.withLock {
            buffer.addLast(sanitized)
            while (buffer.size > MAX_MEMORY_LINES) buffer.removeFirst()
        }
        withContext(Dispatchers.IO) {
            appendSingleLine(sanitized)
        }
    }

    private suspend fun write(level: String, tag: String, message: String) {
        val sanitized = sanitizeTag(tag)
        val line = "${STAMP_FORMAT.format(Date())} $level/$sanitized: ${message.take(MAX_LINE_CHARS)}"
        mutex.withLock {
            buffer.addLast(line)
            while (buffer.size > MAX_MEMORY_LINES) buffer.removeFirst()
        }
        withContext(Dispatchers.IO) {
            appendSingleLine(line)
        }
    }

    /**
     * 单行追加（含滚动）。调用方负责在 IO 线程执行。
     *
     * 写入前截掉行内换行：一行输入若含换行会污染滚动逻辑（readLines 会把它拆成多行），
     * 也会让导出的日志行数超过内存缓冲上限。
     */
    private fun appendSingleLine(rawLine: String) {
        val line = rawLine.replace('\n', ' ').replace('\r', ' ')
        runCatching {
            val file = logFile()
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                // 滚动：丢弃前半部分，保留最近的现场。
                val lines = file.readLines()
                file.writeText((lines.drop(lines.size / 2) + line).joinToString("\n") + "\n")
            } else {
                file.appendText(line + "\n")
            }
        }
    }

    /** 标签只允许字母数字与下划线，防止外部输入污染日志定位。 */
    private fun sanitizeTag(tag: String): String {
        val cleaned = tag.filter { it.isLetterOrDigit() || it == '_' }.take(32)
        return cleaned.ifBlank { "app" }
    }

    /** 导出日志文本（含设备与版本头），供设置页分享。 */
    suspend fun exportText(): String = withContext(Dispatchers.IO) {
        val header = buildString {
            appendLine("味笺运行日志")
            appendLine("包名=${context.packageName} 版本=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
            appendLine("系统=Android ${Build.VERSION.RELEASE}(API ${Build.VERSION.SDK_INT}) 机型=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("导出时间=${STAMP_FORMAT.format(Date())}")
            appendLine("----")
        }
        val memory = mutex.withLock { buffer.toList() }
        val fileLines = runCatching { logFile().takeIf { it.exists() }?.readLines().orEmpty() }.getOrDefault(emptyList())
        // 文件与内存可能重复，以文件为主、内存补尾。
        header + (fileLines + memory).distinct().takeLast(MAX_MEMORY_LINES).joinToString("\n")
    }

    /** 日志文件大小，供设置页展示。 */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { logFile().takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L)
    }

    private companion object {
        const val LOG_DIR_NAME = "logs"
        const val LOG_FILE_NAME = "mealnote.log"
        const val MAX_MEMORY_LINES = 500
        const val MAX_FILE_BYTES = 200L * 1024
        /** 单行上限，防止异常信息过长把日志文件撑爆。 */
        const val MAX_LINE_CHARS = 2_000
        val STAMP_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }
}

/** 脱敏后的 URI 描述：只保留 scheme 与 authority，不记完整路径。 */
fun Uri.describeForLog(): String = "${scheme.orEmpty()}:${authority.orEmpty()}"
