package com.fanji.mealnote.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑餐厅表单的图片回收规则测试。
 *
 * 这是本项目**最容易造成数据丢失**的一处逻辑：判错一个路径，用户取消编辑后
 * 数据库仍指向一个已被删除的文件，封面变成永久空白。
 *
 * 规则：只有「本次导入、且当前未被选中」的图片可以回收。
 * 原封面与用餐照片无论何时都必须保留。
 */
class ReclaimableFormPhotosTest {

    private val originalCover = "/photos/cover_original.jpg"
    private val importedA = "/photos/imported_a.jpg"
    private val importedB = "/photos/imported_b.jpg"
    private val recordPhoto = "/photos/record_1.jpg"

    @Test
    fun `换新图时原封面不能被回收`() {
        // 回归用例：早期版本在导入的瞬间就把上一张删掉了，
        // 而首次加载时「上一张」正是数据库当前的封面。
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(originalCover),
            originalCoverPath = originalCover,
            recordPhotoPaths = emptyList(),
            keep = importedA,
        )
        assertTrue("原封面仍在被数据库引用，不能回收", reclaimable.isEmpty())
    }

    @Test
    fun `本次导入的图片在被替换时可以回收`() {
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(importedA),
            originalCoverPath = originalCover,
            recordPhotoPaths = emptyList(),
            keep = importedB,
        )
        assertEquals(listOf(importedA), reclaimable)
    }

    @Test
    fun `被设为封面的用餐照片不能被回收`() {
        // 用户把某张用餐照片设成封面后，该文件同时被 photos 与 restaurants 引用。
        // 若在此处回收，那条用餐记录的照片就没了。
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(recordPhoto),
            originalCoverPath = originalCover,
            recordPhotoPaths = listOf(recordPhoto),
            keep = importedA,
        )
        assertTrue(reclaimable.isEmpty())
    }

    @Test
    fun `当前选中的那张始终保留`() {
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(importedA),
            originalCoverPath = originalCover,
            recordPhotoPaths = emptyList(),
            keep = importedA,
        )
        assertTrue(reclaimable.isEmpty())
    }

    @Test
    fun `取消编辑时回收本次导入的图`() {
        // onCleared 用 keep = "" 调用：没有「当前选中」的概念，
        // 表单里除原封面与用餐照片之外的都是本次导入的，可以回收。
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(importedA),
            originalCoverPath = originalCover,
            recordPhotoPaths = listOf(recordPhoto),
            keep = "",
        )
        assertEquals(listOf(importedA), reclaimable)
    }

    @Test
    fun `取消编辑时保留原封面与用餐照片`() {
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(originalCover, recordPhoto),
            originalCoverPath = originalCover,
            recordPhotoPaths = listOf(recordPhoto),
            keep = "",
        )
        assertTrue(reclaimable.isEmpty())
    }

    @Test
    fun `多种路径混在一起时只回收本次导入的`() {
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(originalCover, recordPhoto, importedA, importedB),
            originalCoverPath = originalCover,
            recordPhotoPaths = listOf(recordPhoto),
            keep = importedB,
        )
        assertEquals(listOf(importedA), reclaimable)
    }

    @Test
    fun `没有原封面时不影响其余判定`() {
        // 新店铺还没有封面，originalCoverPath 为空串。
        val reclaimable = reclaimableFormPhotos(
            formPhotoPaths = listOf(importedA),
            originalCoverPath = "",
            recordPhotoPaths = emptyList(),
            keep = importedB,
        )
        assertEquals(listOf(importedA), reclaimable)
    }

    @Test
    fun `空表单返回空结果`() {
        assertTrue(
            reclaimableFormPhotos(
                formPhotoPaths = emptyList(),
                originalCoverPath = originalCover,
                recordPhotoPaths = listOf(recordPhoto),
                keep = "",
            ).isEmpty(),
        )
    }
}
