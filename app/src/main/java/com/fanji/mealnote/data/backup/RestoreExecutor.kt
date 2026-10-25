package com.fanji.mealnote.data.backup

import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.ReplaceSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 恢复的落盘与提交边界；生产入口和故障注入测试共用此执行器。
 *
 * [reclaimUnreferenced] 必须查询当前数据库引用后才删除文件：replace 返回失败或抛错，
 * 也可能是事务已提交后的旧文件回收失败，绝不能据此假定新文件尚未被引用。
 */
internal suspend fun executeRestore(
    stagedPhotos: List<File>,
    importPhoto: suspend (File) -> String?,
    replace: suspend (Map<String, String>) -> MealResult<ReplaceSummary>,
    reclaimUnreferenced: suspend (Collection<String>) -> Unit,
): MealResult<ReplaceSummary> {
    val resolvedPaths = linkedMapOf<String, String>()
    var outcome: MealResult<ReplaceSummary>? = null
    var primaryError: Throwable? = null
    try {
        for (staged in stagedPhotos) {
            currentCoroutineContext().ensureActive()
            // 一次复制完成后必须先登记路径，再响应父协程取消。否则跨 withContext 返回时
            // 的及时取消可能丢掉已经落盘的返回值，使本次回收漏掉那张新图。
            val copied = withContext(NonCancellable) { importPhoto(staged) }
            if (copied != null) resolvedPaths[staged.name] = copied
            currentCoroutineContext().ensureActive()
            if (copied == null) {
                return MealResult.Failure(MealError.PhotoIoFailure).also { outcome = it }
            }
        }
        // 空照片备份同样需要检查取消；替换绝不在 NonCancellable 内执行。
        currentCoroutineContext().ensureActive()
        return replace(resolvedPaths).also { outcome = it }
    } catch (error: Throwable) {
        primaryError = error
        throw error
    } finally {
        if (resolvedPaths.isNotEmpty()) {
            try {
                withContext(NonCancellable) {
                    // 仅提交本次导入的候选路径，成功时也回收被入库校验丢弃的孤儿图。
                    // 查引用或删除失败时不退回直接删除，宁可留下孤儿也不破坏已提交的数据。
                    reclaimUnreferenced(resolvedPaths.values.toList())
                }
            } catch (cleanupError: Exception) {
                val original = primaryError
                if (cleanupError is CancellationException && original !is CancellationException) {
                    // 回收自身抛出的取消也必须重抛，不可转成普通失败。
                    original?.let(cleanupError::addSuppressed)
                    throw cleanupError
                } else if (original != null) {
                    // 回收失败不能覆盖原始异常，尤其不能把取消变成普通失败。
                    if (original !== cleanupError) original.addSuppressed(cleanupError)
                } else if (outcome !is MealResult.Failure) {
                    throw cleanupError
                }
                // 已有失败结果时保留它；成功后回收失败则抛错，由恢复入口转换为可见失败。
            }
        }
    }
}
