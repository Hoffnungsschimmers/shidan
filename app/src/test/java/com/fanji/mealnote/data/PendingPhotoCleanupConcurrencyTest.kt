package com.fanji.mealnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 待回收图片暂存区的并发语义测试。
 *
 * 背景：`MealRepository` 是单例，其待回收图片暂存区会被多个 ViewModel 的协程并发读写。
 * 早期实现使用普通 `mutableMapOf`，存在两类真实风险：
 * 1. 并发修改导致 `ConcurrentModificationException`；
 * 2. 同一批文件被两个协程同时取走（`get` + `remove` 非原子），造成重复删除。
 *
 * 本测试直接对当前实现所依赖的数据结构（`ConcurrentHashMap`）与“取出即清空”协议
 * 做并发验证。之所以不直接实例化 `MealRepository`：它的构造函数要求 Android 框架依赖
 * （`AppDatabase`、`PhotoStore` 需要 `Context`），在纯 JVM 单元测试中无法构造；
 * 而待回收逻辑本身与框架无关，抽出协议单独验证更准确。
 */
class PendingPhotoCleanupConcurrencyTest {

    /** 复刻 `MealRepository` 中暂存区的实现方式。 */
    private val pending = ConcurrentHashMap<Long, List<String>>()

    private fun track(key: Long, paths: List<String>) {
        if (paths.isEmpty()) return
        pending[key] = paths
    }

    private fun release(key: Long): List<String> = pending.remove(key).orEmpty()

    @Test
    fun `并发取出同一表单的待回收图片只成功一次`() {
        val formKey = 42L
        track(formKey, listOf("/photos/a.jpg", "/photos/b.jpg"))

        val threadCount = 16
        val startGate = CountDownLatch(1)
        val successCount = AtomicInteger(0)
        val totalFetched = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            pool.execute {
                startGate.await()
                val fetched = release(formKey)
                if (fetched.isNotEmpty()) successCount.incrementAndGet()
                totalFetched.addAndGet(fetched.size)
            }
        }
        startGate.countDown()
        pool.shutdown()
        assertTrue("并发任务未在超时内完成", pool.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals("同一批图片只应被一个线程取走", 1, successCount.get())
        assertEquals("总取出数量应等于登记数量", 2, totalFetched.get())
        assertTrue("取出后暂存区应为空", release(formKey).isEmpty())
    }

    @Test
    fun `并发登记与取出不会抛异常`() {
        val pool = Executors.newFixedThreadPool(8)
        val startGate = CountDownLatch(1)
        val errors = AtomicInteger(0)

        repeat(64) { index ->
            pool.execute {
                try {
                    startGate.await()
                    val key = (index % 4).toLong()
                    track(key, listOf("/photos/$index.jpg"))
                    release(key)
                } catch (_: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }
        startGate.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))

        assertEquals("并发读写不应抛出任何异常", 0, errors.get())
    }

    @Test
    fun `登记空列表不会污染暂存区`() {
        track(1L, emptyList())
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `重复登记同一表单以最后一次为准`() {
        track(7L, listOf("/photos/first.jpg"))
        track(7L, listOf("/photos/second.jpg"))
        assertEquals(listOf("/photos/second.jpg"), release(7L))
    }

    @Test
    fun `并发登记不同表单不会丢失条目`() {
        val expectedKeys = 200
        val pool = Executors.newFixedThreadPool(8)
        val startGate = CountDownLatch(1)
        repeat(expectedKeys) { index ->
            pool.execute {
                startGate.await()
                track(index.toLong(), listOf("/photos/$index.jpg"))
            }
        }
        startGate.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals("并发写入后条目数应完整", expectedKeys, pending.size)
    }
}
