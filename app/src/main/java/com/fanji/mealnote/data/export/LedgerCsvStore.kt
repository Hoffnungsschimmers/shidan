package com.fanji.mealnote.data.export

import android.content.Context
import android.net.Uri
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账本 CSV 导出的**框架侧**：查数据、走 SAF 写文件。转义/编码等纯逻辑在 [buildLedgerCsv]。
 *
 * 与 [com.fanji.mealnote.data.backup.BackupStore] 分开:备份是完整快照(可恢复),CSV 是
 * 给人看/导表格的只读视图,不含照片与内部结构,也不用于恢复。走 SAF 因此无需存储权限。
 */
@Singleton
class LedgerCsvStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MealRepository,
) {
    /**
     * 导出账本到 [target]。返回写入的记录行数。
     *
     * 空数据不算失败:导出一个只有表头的 CSV 也是合理的(用户可能就是想要个模板)。
     */
    suspend fun export(target: Uri): MealResult<Int> = withContext(Dispatchers.IO) {
        try {
            val restaurants = repository.getRestaurantsForBackup()
            val records = repository.getDiningRecordsForBackup()
            val restaurantIds = restaurants.mapTo(HashSet()) { it.id }
            val rowCount = records.count { it.restaurantId in restaurantIds }

            val csv = buildLedgerCsv(restaurants, records, ZoneId.systemDefault())

            val output = context.contentResolver.openOutputStream(target, "wt")
                ?: return@withContext MealResult.Failure(MealError.StorageFull)
            output.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
                stream.flush()
            }

            MealResult.Success(rowCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            MealResult.Failure(MealError.PhotoIoFailure)
        } catch (error: Exception) {
            MealResult.Failure(MealError.DatabaseFailure(error))
        }
    }

    /** 默认文件名，形如 `mealnote-ledger-20260926-1720.csv`。 */
    fun defaultFileName(): String {
        val stamp = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmm", Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(java.time.Instant.now())
        return "mealnote-ledger-$stamp.csv"
    }
}
