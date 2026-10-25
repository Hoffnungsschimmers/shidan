package com.fanji.mealnote.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 备份包条目名规范化的安全性测试。
 *
 * 背景：导入的 ZIP 来自用户选择的外部文件，必须视为**不可信输入**。恶意构造的条目名
 * 可能包含路径穿越（`photos/../../databases/meal_note.db`），若直接用作输出路径，
 * 会写到暂存目录之外，覆盖应用私有数据。
 *
 * 本测试直接调用生产代码 [safePhotoEntryName]。旧版本在这里复刻了一遍 `File(name).name`
 * 再断言副本，生产实现被改坏时测试依旧全绿——因此重写为对真实入口的断言。
 *
 * 条目名按 ZIP 规范以 `/` 分隔；断言只依赖「结果是暂存目录内的单一文件名」这一事实，
 * 不对反斜杠在 Windows / Linux 上的差异（后者把反斜杠当普通文件名字符）作任何假设。
 */
class BackupEntryNameSafetyTest {

    @Test
    fun `正常条目名原样返回`() {
        assertEquals("record1_0.jpg", safePhotoEntryName("record1_0.jpg"))
        assertEquals("cover3.jpg", safePhotoEntryName("cover3.jpg"))
    }

    @Test
    fun `带目录的条目名只保留文件名`() {
        assertEquals("a.jpg", safePhotoEntryName("photos/a.jpg"))
    }

    @Test
    fun `路径穿越条目名被压平且不逃出暂存目录`() {
        val cases = mapOf(
            "photos/../../../databases/meal_note.db" to "meal_note.db",
            "photos/../../shared_prefs/secret.xml" to "secret.xml",
            "../etc/passwd" to "passwd",
            "photos/./nested/../../escape.jpg" to "escape.jpg",
        )
        val stagingDir = File("backup_staging")
        cases.forEach { (entry, expected) ->
            val name = requireNotNull(safePhotoEntryName(entry)) { "条目 [$entry] 不应被丢弃" }
            assertEquals("条目 [$entry] 应只保留最后一段文件名", expected, name)
            assertFalse(
                "条目 [$entry] 的结果不能包含路径分隔符",
                name.contains('/') || name.contains('\\'),
            )
            assertFalse("条目 [$entry] 的结果不能是上级目录引用", name.contains(".."))
            assertEquals(
                "条目 [$entry] 解析后的文件必须直接位于暂存目录内",
                stagingDir,
                File(stagingDir, name).parentFile,
            )
        }
    }

    @Test
    fun `绝对路径只保留文件名`() {
        assertEquals("evil.jpg", safePhotoEntryName("/absolute/path/evil.jpg"))
    }

    @Test
    fun `超长条目名被拒绝`() {
        // 生产实现的上限是 128 字符；这里远超上限，避免测试与具体常量取值绑死。
        val tooLong = "photos/" + "a".repeat(200) + ".jpg"
        assertNull("超过长度上限的条目名应被拒绝", safePhotoEntryName(tooLong))
    }

    @Test
    fun `点段条目名被拒绝`() {
        // `photos/..` 压平后是上级目录引用：写不出暂存目录，但也没有任何合法用途，
        // 直接丢弃比让整次导入以 IO 失败告终更清楚。
        assertNull(safePhotoEntryName("photos/.."))
        assertNull(safePhotoEntryName("."))
        assertNull(safePhotoEntryName(".."))
    }

    @Test
    fun `空条目名被拒绝`() {
        assertNull("空条目名应被拒绝", safePhotoEntryName(""))
    }
}
