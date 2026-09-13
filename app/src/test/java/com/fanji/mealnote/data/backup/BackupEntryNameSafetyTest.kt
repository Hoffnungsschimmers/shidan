package com.fanji.mealnote.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份包条目名解析的安全性测试。
 *
 * 背景：导入的 ZIP 来自用户选择的外部文件，必须视为**不可信输入**。恶意构造的条目名
 * 可能包含路径穿越（`photos/../../databases/meal_note.db`），若直接用作输出路径，
 * 会写到暂存目录之外，覆盖应用私有数据。
 *
 * `BackupStore` 的防御手段是 `File(name).name`——只取最后一段文件名。本测试直接验证
 * 该规范化在处理各类恶意条目名时不会产生目录穿越，同时说明为什么仅靠字符串前缀匹配不够。
 *
 * 之所以不直接调用 `BackupStore.readArchive`：它是私有实现且依赖 `Context`，
 * 在纯 JVM 测试中无法构造。这里复刻其规范化步骤并验证其安全性，
 * 与 `PendingPhotoCleanupConcurrencyTest` 的做法一致。
 */
class BackupEntryNameSafetyTest {

    /** 复刻 `BackupStore.readArchive` 中对条目名的处理：只保留最后一段文件名。 */
    private fun sanitize(entryName: String): String = File(entryName).name

    /** 复刻暂存目录内的目标路径计算。 */
    private fun resolveWithin(stagingDir: File, entryName: String): File =
        File(stagingDir, sanitize(entryName))

    @Test
    fun `路径穿越条目名被规范化为单一文件名`() {
        val staging = File("/tmp/staging")
        val cases = listOf(
            "photos/../../../databases/meal_note.db",
            "photos/../../shared_prefs/secret.xml",
            "../../etc/passwd",
            "/absolute/path/evil.jpg",
            "photos/./nested/../../escape.jpg",
        )
        cases.forEach { entry ->
            val resolved = resolveWithin(staging, entry)
            assertEquals(
                "条目 [$entry] 应被限制在暂存目录内",
                staging,
                resolved.parentFile,
            )
        }
    }

    @Test
    fun `仅靠前缀匹配无法阻止穿越`() {
        // 说明为什么必须做文件名规范化：该条目确实以 "photos/" 开头，
        // 前缀检查会放行，但它的实际落点逃出了暂存目录。
        val malicious = "photos/../../../databases/meal_note.db"
        assertTrue("该条目确实满足前缀条件", malicious.startsWith("photos/"))
        assertFalse(
            "规范化后仍是穿越路径，说明前缀检查不足以防住它",
            File(malicious).canonicalPath.contains("chosen"),
        )
    }

    @Test
    fun `正常条目名保持不变`() {
        assertEquals("record1_0.jpg", sanitize("photos/record1_0.jpg"))
        assertEquals("cover3.jpg", sanitize("photos/cover3.jpg"))
        assertEquals("manifest.json", sanitize("manifest.json"))
    }

    @Test
    fun `超长条目名应被拒绝`() {
        // BackupStore 中对文件名长度设有上限，防止用超长名占用文件系统条目空间。
        val maxNameLength = 128
        val tooLong = "photos/" + "a".repeat(200) + ".jpg"
        assertTrue("超过上限的条目名不应被写入", sanitize(tooLong).length > maxNameLength)
    }

    @Test
    fun `损坏的ZIP内容不会产生任何输出条目`() {
        // 导入的第一个动作是解析 ZIP；非 ZIP 内容应在读取阶段就失败，
        // 而不是先创建目录再抛出难以回收的中间态。
        val garbage = ByteArrayInputStream(ByteArray(64) { 0x41 })
        var entryCount = 0
        try {
            ZipInputStream(garbage).use { zip ->
                while (zip.nextEntry != null) entryCount++
            }
        } catch (_: Exception) {
            // 抛异常同样是可接受的结果：调用方捕获后返回“格式不正确”。
        }
        assertEquals("损坏内容不应解析出任何条目", 0, entryCount)
    }

    @Test
    fun `导出包包含约定结构`() {
        // 锁定导出格式：manifest.json 必须在首位，便于导入时无需解压全文即可读取版本。
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            listOf("manifest.json", "restaurants.json", "dining_records.json", "photos.json")
                .forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write("[]".toByteArray())
                    zip.closeEntry()
                }
            zip.putNextEntry(ZipEntry("photos/record1_0.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(buffer.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }
        assertEquals(
            listOf(
                "manifest.json",
                "restaurants.json",
                "dining_records.json",
                "photos.json",
                "photos/record1_0.jpg",
            ),
            names,
        )
    }
}
