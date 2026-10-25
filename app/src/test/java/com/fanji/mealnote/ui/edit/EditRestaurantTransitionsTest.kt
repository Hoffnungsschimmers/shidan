package com.fanji.mealnote.ui.edit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EditRestaurantTransitionsTest {
    private val imported = "/photos/imported.jpg"
    private val original = "/photos/original.jpg"
    private val record = "/photos/record.jpg"

    private fun state() = EditRestaurantUiState(
        isLoading = false,
        name = "示例",
        photoPaths = listOf(imported),
        originalCoverPath = original,
        recordPhotoPaths = listOf(record),
    )

    @Test
    fun `保存快照取得后所有换图及移除入口拒绝且不返回回收路径`() {
        val flow = MutableStateFlow(state())
        val snapshot = flow.beginRestaurantSave()!!
        assertNull(flow.changeCover(record, fromRecord = true))
        assertNull(flow.changeCover("/photos/camera.jpg"))
        assertNull(flow.removeCover(0))
        assertFalse(flow.beginCoverImport())
        assertEquals(snapshot, flow.value)
    }

    @Test
    fun `用餐照片先替换时只回收导入图且保存取得新封面`() {
        val flow = MutableStateFlow(state())
        assertEquals(listOf(imported), flow.changeCover(record, fromRecord = true))
        assertEquals(listOf(record), flow.beginRestaurantSave()!!.photoPaths)
    }

    @Test
    fun `相机先替换时保存不再引用回收图`() {
        val flow = MutableStateFlow(state())
        val camera = "/photos/camera.jpg"
        assertEquals(listOf(imported), flow.changeCover(camera))
        assertEquals(listOf(camera), flow.beginRestaurantSave()!!.photoPaths)
    }

    @Test
    fun `移除先完成时保存空封面`() {
        val flow = MutableStateFlow(state())
        assertEquals(listOf(imported), flow.removeCover(0))
        assertTrue(flow.beginRestaurantSave()!!.photoPaths.isEmpty())
    }

    @Test
    fun `原封面和用餐照片不进入回收集合`() {
        for (path in listOf(original, record)) {
            val flow = MutableStateFlow(state().copy(photoPaths = listOf(path)))
            assertTrue(flow.changeCover("/photos/new.jpg")!!.isEmpty())
        }
        val flow = MutableStateFlow(state().copy(photoPaths = listOf(original)))
        assertTrue(flow.removeCover(0)!!.isEmpty())
    }

    @Test
    fun `已保存和加载或导入状态拒绝外部图片变更`() {
        for (busy in listOf(
            state().copy(saved = true),
            state().copy(isLoading = true),
            state().copy(notFound = true),
            state().copy(isImportingPhotos = true),
        )) {
            val flow = MutableStateFlow(busy)
            assertNull(flow.changeCover(record, fromRecord = true))
            assertNull(flow.changeCover("/photos/new.jpg"))
            assertNull(flow.removeCover(0))
            assertFalse(flow.beginCoverImport())
            assertNull(flow.beginRestaurantSave())
            assertEquals(busy, flow.value)
        }
    }

    @Test
    fun `候选必须属于用餐照片且无效索引不修改状态`() {
        val initial = state()
        val flow = MutableStateFlow(initial)
        assertNull(flow.changeCover(imported, fromRecord = true))
        assertNull(flow.removeCover(-1))
        assertNull(flow.removeCover(1))
        assertEquals(initial, flow.value)
    }

    @Test
    fun `导入先占用状态阻止保存和重复导入完成后新封面可保存`() {
        val flow = MutableStateFlow(state())
        assertTrue(flow.beginCoverImport())
        assertFalse(flow.beginCoverImport())
        assertNull(flow.beginRestaurantSave())
        assertNull(flow.changeCover(record, fromRecord = true))
        assertNull(flow.changeCover("/photos/camera.jpg"))
        assertNull(flow.removeCover(0))
        val newCover = "/photos/new.jpg"
        assertEquals(listOf(imported), flow.changeCover(newCover, finishImport = true))
        assertFalse(flow.value.isImportingPhotos)
        assertEquals(listOf(newCover), flow.beginRestaurantSave()!!.photoPaths)
    }

    @Test
    fun `迟到的导入完成不能改保存中或已保存的封面`() {
        for (busy in listOf(state().copy(isSaving = true), state().copy(saved = true))) {
            val flow = MutableStateFlow(busy)
            assertNull(flow.changeCover("/photos/new.jpg", finishImport = true))
            assertEquals(busy, flow.value)
        }
        assertNull(MutableStateFlow(state()).changeCover("/photos/new.jpg", finishImport = true))
    }

    @Test
    fun `名称校验不取得保存权且重复保存只有一次成功`() {
        val empty = MutableStateFlow(state().copy(name = " "))
        val rejected = empty.beginRestaurantSave()!!
        assertTrue(rejected.showNameError)
        assertFalse(rejected.isSaving)
        val flow = MutableStateFlow(state())
        assertNotNull(flow.beginRestaurantSave())
        assertNull(flow.beginRestaurantSave())
    }

    @Test
    fun `保存暂停期间尝试候选相机移除后提交快照文件仍存在`() = runBlocking {
        val directory = Files.createTempDirectory("mealnote-cover-test").toFile()
        try {
            val image = directory.resolve("imported.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val existingPhoto = directory.resolve("record.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
            val flow = MutableStateFlow(state().copy(
                photoPaths = listOf(image.absolutePath),
                recordPhotoPaths = listOf(existingPhoto.absolutePath),
            ))
            val snapshot = flow.beginRestaurantSave()!!
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var committed: String? = null
            val save = launch {
                entered.complete(Unit)
                resume.await()
                committed = snapshot.photoPaths.single()
                assertTrue(image.isFile)
            }
            entered.await()
            assertNull(flow.changeCover(existingPhoto.absolutePath, fromRecord = true))
            assertNull(flow.changeCover(directory.resolve("camera.jpg").absolutePath))
            assertNull(flow.removeCover(0))
            assertFalse(flow.beginCoverImport())
            resume.complete(Unit)
            save.join()
            assertEquals(image.absolutePath, committed)
            assertTrue(image.readBytes().contentEquals(byteArrayOf(1, 2, 3)))
            assertTrue(existingPhoto.isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `保存和候选多线程竞争时回收集合不包含提交快照`() {
        val workers = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val flow = MutableStateFlow(state())
                val barrier = CyclicBarrier(2)
                val save = workers.submit(Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    flow.beginRestaurantSave()!!
                })
                val change = workers.submit(Callable {
                    barrier.await(5, TimeUnit.SECONDS)
                    flow.changeCover(record, fromRecord = true)
                })
                val snapshot = save.get(5, TimeUnit.SECONDS)
                val obsolete = change.get(5, TimeUnit.SECONDS).orEmpty()
                assertTrue(snapshot.photoPaths.none { it in obsolete })
                assertEquals(snapshot.photoPaths, flow.value.photoPaths)
            }
        } finally {
            workers.shutdownNow()
        }
    }
}
