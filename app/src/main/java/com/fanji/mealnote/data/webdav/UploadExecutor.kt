package com.fanji.mealnote.data.webdav

import com.fanji.mealnote.data.MealResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** 本地备份成功后才上传；失败原因原样保留，临时包只清理本次操作拥有的文件。 */
internal suspend fun <T> executeBackupUpload(
    local: File,
    buildBackup: suspend (File) -> MealResult<T>,
    upload: suspend (File) -> Unit,
): MealResult<T> = try {
    currentCoroutineContext().ensureActive()
    when (val built = buildBackup(local)) {
        is MealResult.Failure -> built
        is MealResult.Success -> {
            // 导出可能只做阻塞 IO：开始 PUT 前显式观察取消，避免退出后仍覆盖远端。
            currentCoroutineContext().ensureActive()
            upload(local)
            currentCoroutineContext().ensureActive()
            built
        }
    }
} finally {
    local.delete()
}
