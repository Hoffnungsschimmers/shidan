package com.fanji.mealnote.data.backup

import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.ReplaceSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** 直接对生产恢复执行器注入复制、提交、回收故障，并验证真实临时文件与引用状态。 */
class RestoreExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val summary = ReplaceSummary(1, 1, 2)

    @Test
    fun `第二张复制返回null不替换旧内容且回收第一张新图`() = runBlocking {
        val fixture = Fixture()
        val outcome = executeRestore(
            fixture.staged,
            importPhoto = { if (it == fixture.staged[1]) null else fixture.copy(it) },
            replace = fixture::replace,
            reclaimUnreferenced = fixture::reclaim,
        )

        assertEquals(MealResult.Failure(MealError.PhotoIoFailure), outcome)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
        assertEquals(listOf(fixture.imported.single().absolutePath), fixture.reclaimCandidates.single())
    }

    @Test
    fun `全部复制完成才替换且回收守卫保留新引用文件`() = runBlocking {
        val fixture = Fixture()
        val outcome = executeRestore(fixture.staged, fixture::copy, fixture::replace, fixture::reclaim)

        assertEquals(MealResult.Success(summary), outcome)
        assertEquals(listOf("copy:first.jpg", "copy:second.jpg", "replace", "reclaim"), fixture.events)
        assertEquals("restored", fixture.rows)
        assertFalse(fixture.oldPhoto.exists())
        assertTrue(fixture.imported.all(File::exists))
        assertEquals(listOf("first bytes", "second bytes"), fixture.imported.map { it.readText() })
        assertEquals(fixture.imported.map(File::getAbsolutePath), fixture.reclaimCandidates.single())
    }

    @Test
    fun `全部成功时回收未被有效行引用的新图但保留封面引用`() = runBlocking {
        val fixture = Fixture()
        val outcome = executeRestore(
            fixture.staged,
            fixture::copy,
            replace = { paths ->
                fixture.commit(paths, referencedNames = setOf("first.jpg"))
                MealResult.Success(summary.copy(photoCount = 0))
            },
            reclaimUnreferenced = fixture::reclaim,
        )

        assertEquals(MealResult.Success(summary.copy(photoCount = 0)), outcome)
        // 引用集合包括封面，即使没有用餐照片行也必须保留 first.jpg。
        assertTrue(fixture.imported[0].exists())
        assertFalse(fixture.imported[1].exists())
    }

    @Test
    fun `replace返回失败且未提交时清理所有新图`() = runBlocking {
        val fixture = Fixture()
        val rejected = MealResult.Failure(MealError.StorageFull)
        val outcome = executeRestore(fixture.staged, fixture::copy, { rejected }, fixture::reclaim)

        assertSame(rejected, outcome)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `第二张复制IOException回收第一张且原异常继续抛出`() = runBlocking {
        val fixture = Fixture()
        val failure = IOException("copy failed")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                { if (it == fixture.staged[1]) throw failure else fixture.copy(it) },
                fixture::replace,
                fixture::reclaim,
            )
        }

        assertOriginalFailure(failure, thrown)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `第二张复制非IO异常也不能触发替换`() = runBlocking {
        val fixture = Fixture()
        val failure = IllegalStateException("unexpected copy failure")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                { if (it == fixture.staged[1]) throw failure else fixture.copy(it) },
                fixture::replace,
                fixture::reclaim,
            )
        }

        assertOriginalFailure(failure, thrown)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `第二张复制抛取消原样重抛且回收第一张`() = runBlocking {
        val fixture = Fixture()
        val cancelled = CancellationException("copy cancelled")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                { if (it == fixture.staged[1]) throw cancelled else fixture.copy(it) },
                fixture::replace,
                fixture::reclaim,
            )
        }

        assertOriginalFailure(cancelled, thrown)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `复制完成时父协程取消仍登记并回收刚返回的新图`() = runBlocking {
        val fixture = Fixture()
        var cancellationObserved = false
        val child = launch {
            val ownerJob = currentCoroutineContext().job
            try {
                executeRestore(
                    fixture.staged,
                    importPhoto = {
                        val path = fixture.copy(it)
                        ownerJob.cancel(CancellationException("cancel during copy"))
                        path
                    },
                    replace = fixture::replace,
                    reclaimUnreferenced = fixture::reclaim,
                )
            } catch (cancelled: CancellationException) {
                cancellationObserved = true
                throw cancelled
            }
        }
        child.join()

        assertTrue(child.isCancelled)
        assertTrue(cancellationObserved)
        assertEquals(1, fixture.imported.size)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `开始前取消不复制也不替换`() = runBlocking {
        val fixture = Fixture()
        val child = launch {
            currentCoroutineContext().job.cancel()
            executeRestore(fixture.staged, fixture::copy, fixture::replace, fixture::reclaim)
        }
        child.join()

        assertTrue(child.isCancelled)
        assertTrue(fixture.imported.isEmpty())
        assertTrue(fixture.reclaimCandidates.isEmpty())
        fixture.assertOldUntouched()
    }

    @Test
    fun `无照片备份取消也不替换`() = runBlocking {
        val fixture = Fixture()
        val child = launch {
            currentCoroutineContext().job.cancel()
            executeRestore(emptyList(), fixture::copy, fixture::replace, fixture::reclaim)
        }
        child.join()

        assertTrue(child.isCancelled)
        fixture.assertOldUntouched()
        assertTrue(fixture.reclaimCandidates.isEmpty())
    }

    @Test
    fun `replace未提交就抛错回收新图并保留旧文件`() = runBlocking {
        val fixture = Fixture()
        val failure = IllegalStateException("transaction rolled back")
        val thrown = failureFrom {
            executeRestore(fixture.staged, fixture::copy, { throw failure }, fixture::reclaim)
        }

        assertOriginalFailure(failure, thrown)
        fixture.assertOldUntouched()
        fixture.assertAllNewRemoved()
    }

    @Test
    fun `replace先提交新引用再抛错只能删未引用新图`() = runBlocking {
        val fixture = Fixture()
        val failure = IOException("old file cleanup failed after commit")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                fixture::copy,
                replace = { paths ->
                    fixture.commit(paths, referencedNames = setOf("first.jpg"))
                    throw failure
                },
                reclaimUnreferenced = fixture::reclaim,
            )
        }

        assertOriginalFailure(failure, thrown)
        assertEquals("restored", fixture.rows)
        assertTrue(fixture.imported[0].exists())
        assertFalse(fixture.imported[1].exists())
    }

    @Test
    fun `replace提交后返回失败仍保留所有被引用新图`() = runBlocking {
        val fixture = Fixture()
        val rejected = MealResult.Failure(MealError.PhotoIoFailure)
        val outcome = executeRestore(
            fixture.staged,
            fixture::copy,
            replace = { paths ->
                fixture.commit(paths)
                rejected
            },
            reclaimUnreferenced = fixture::reclaim,
        )

        assertSame(rejected, outcome)
        assertEquals("restored", fixture.rows)
        assertTrue(fixture.imported.all(File::exists))
    }

    @Test
    fun `replace提交后取消安全回收保留引用新图且重抛取消`() = runBlocking {
        val fixture = Fixture()
        val cancelled = CancellationException("cancel after commit")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                fixture::copy,
                replace = { paths ->
                    fixture.commit(paths, referencedNames = setOf("first.jpg"))
                    throw cancelled
                },
                reclaimUnreferenced = fixture::reclaim,
            )
        }

        assertOriginalFailure(cancelled, thrown)
        assertTrue(fixture.imported[0].exists())
        assertFalse(fixture.imported[1].exists())
    }

    @Test
    fun `提交后引用查询失败不能退回直接删除新图`() = runBlocking {
        val fixture = Fixture()
        val failure = IOException("reference query failed")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                fixture::copy,
                fixture::replace,
                reclaimUnreferenced = { throw failure },
            )
        }

        assertOriginalFailure(failure, thrown)
        assertEquals("restored", fixture.rows)
        assertTrue(fixture.imported.all(File::exists))
    }

    @Test
    fun `回收抛错不能覆盖复制取消`() = runBlocking {
        val fixture = Fixture()
        val cancelled = CancellationException("copy cancelled")
        val cleanupFailure = IOException("cleanup failed")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                { if (it == fixture.staged[1]) throw cancelled else fixture.copy(it) },
                fixture::replace,
                reclaimUnreferenced = { throw cleanupFailure },
            )
        }

        assertOriginalFailure(cancelled, thrown)
        assertSuppressedFailure(thrown, cleanupFailure)
        fixture.assertOldUntouched()
        assertTrue(fixture.imported.single().exists())
    }

    @Test
    fun `回收失败不覆盖已有replace失败结果`() = runBlocking {
        val fixture = Fixture()
        val rejected = MealResult.Failure(MealError.PhotoIoFailure)
        val outcome = executeRestore(
            fixture.staged,
            fixture::copy,
            { rejected },
            reclaimUnreferenced = { throw IOException("cleanup failed") },
        )

        assertSame(rejected, outcome)
        fixture.assertOldUntouched()
        assertTrue(fixture.imported.all(File::exists))
    }

    @Test
    fun `回收自身取消不能被已有失败结果吞掉`() = runBlocking {
        val fixture = Fixture()
        val cancelled = CancellationException("cleanup cancelled")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                fixture::copy,
                { MealResult.Failure(MealError.StorageFull) },
                reclaimUnreferenced = { throw cancelled },
            )
        }

        assertOriginalFailure(cancelled, thrown)
        fixture.assertOldUntouched()
        assertTrue(fixture.imported.all(File::exists))
    }

    @Test
    fun `复制抛错后回收自身取消仍重抛取消且保留原异常`() = runBlocking {
        val fixture = Fixture()
        val copyFailure = IOException("copy failed")
        val cancelled = CancellationException("cleanup cancelled")
        val thrown = failureFrom {
            executeRestore(
                fixture.staged,
                { if (it == fixture.staged[1]) throw copyFailure else fixture.copy(it) },
                fixture::replace,
                reclaimUnreferenced = { throw cancelled },
            )
        }

        assertOriginalFailure(cancelled, thrown)
        assertSuppressedFailure(thrown, copyFailure)
        fixture.assertOldUntouched()
        assertTrue(fixture.imported.single().exists())
    }

    @Test
    fun `replace期间父协程真正取消仍执行非取消回收`() = runBlocking {
        val fixture = Fixture()
        val child = launch {
            executeRestore(
                fixture.staged,
                fixture::copy,
                replace = { paths ->
                    fixture.commit(paths, referencedNames = setOf("first.jpg"))
                    currentCoroutineContext().job.cancel()
                    currentCoroutineContext().ensureActive()
                    MealResult.Success(summary)
                },
                reclaimUnreferenced = fixture::reclaim,
            )
        }
        child.join()

        assertTrue(child.isCancelled)
        assertEquals(1, fixture.reclaimCandidates.size)
        assertTrue(fixture.imported[0].exists())
        assertFalse(fixture.imported[1].exists())
    }

    @Test
    fun `无照片正常恢复仍替换且不发起无用回收`() = runBlocking {
        val fixture = Fixture()
        val outcome = executeRestore(emptyList(), fixture::copy, fixture::replace, fixture::reclaim)

        assertEquals(MealResult.Success(summary), outcome)
        assertEquals(listOf("replace"), fixture.events)
        assertTrue(fixture.reclaimCandidates.isEmpty())
    }

    // Gradle 的协程调试模式会为堆栈恢复复制异常；类型/消息和原始因果链不能变。
    private fun assertOriginalFailure(expected: Throwable, actual: Throwable) {
        assertEquals(expected.javaClass, actual.javaClass)
        assertEquals(expected.message, actual.message)
        assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
    }

    private fun assertSuppressedFailure(actual: Throwable, expected: Throwable) {
        val suppressed = generateSequence(actual) { it.cause }.flatMap { it.suppressed.asSequence() }
        val match = suppressed.firstOrNull { candidate ->
            generateSequence(candidate) { it.cause }.any { it === expected }
        }
        assertTrue("Original suppressed failure must be retained", match != null)
        assertOriginalFailure(expected, match!!)
    }

    private suspend fun failureFrom(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        throw AssertionError("Expected operation to throw")
    }

    private inner class Fixture {
        private val root = temporaryFolder.newFolder()
        private val staging = File(root, "staging").apply { check(mkdir()) }
        private val owned = File(root, "photos").apply { check(mkdir()) }
        val staged = listOf(
            File(staging, "first.jpg").apply { writeText("first bytes") },
            File(staging, "second.jpg").apply { writeText("second bytes") },
        )
        val oldPhoto = File(owned, "old.jpg").apply { writeText("old bytes") }
        val imported = mutableListOf<File>()
        val events = mutableListOf<String>()
        val reclaimCandidates = mutableListOf<List<String>>()
        private val referenced = mutableSetOf(oldPhoto.absolutePath)
        var rows = "old rows"
            private set

        fun copy(source: File): String {
            events += "copy:${source.name}"
            return File(owned, "new-${source.name}").also {
                source.copyTo(it)
                imported += it
            }.absolutePath
        }

        fun commit(paths: Map<String, String>, referencedNames: Set<String> = paths.keys) {
            assertEquals(staged.size, imported.size)
            assertTrue(imported.all(File::exists))
            events += "replace"
            rows = "restored"
            referenced.clear()
            referenced += paths.filterKeys(referencedNames::contains).values
            // 模拟 Repository 在提交后清理已无引用的旧图。
            check(oldPhoto.delete())
        }

        fun replace(paths: Map<String, String>): MealResult<ReplaceSummary> {
            if (paths.isEmpty()) {
                events += "replace"
                rows = "restored"
            } else {
                commit(paths)
            }
            return MealResult.Success(summary)
        }

        suspend fun reclaim(paths: Collection<String>) {
            // 真正取消的测试也要求这个挂起点能执行，证明回收处于 NonCancellable。
            currentCoroutineContext().ensureActive()
            events += "reclaim"
            reclaimCandidates += paths.toList()
            assertTrue(paths.all { candidate -> imported.any { it.absolutePath == candidate } })
            paths.filterNot(referenced::contains).forEach { check(File(it).delete()) }
        }

        fun assertOldUntouched() {
            assertEquals("old rows", rows)
            assertTrue(oldPhoto.exists())
            assertEquals("old bytes", oldPhoto.readText())
            assertEquals(setOf(oldPhoto.absolutePath), referenced)
            assertFalse(events.contains("replace"))
        }

        fun assertAllNewRemoved() {
            assertTrue(imported.isNotEmpty())
            assertTrue(imported.none(File::exists))
        }
    }
}
