package com.fanji.mealnote.data.webdav

import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
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

/** 调用生产上传执行器；网络与导出使用替身，临时包是实际文件，不执行远程请求。 */
class UploadExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val summary = WebDavStore.UploadSummary(2, 3, 4)

    @Test
    fun storageFullIsPreservedWithoutUploading() = runBlocking {
        assertBackupFailure(MealError.StorageFull)
    }

    @Test
    fun invalidBackupReasonIsPreservedWithoutUploading() = runBlocking {
        assertBackupFailure(MealError.InvalidInput("备份不满足限制"))
    }

    @Test
    fun databaseFailureAndItsCauseArePreservedWithoutUploading() = runBlocking {
        assertBackupFailure(MealError.DatabaseFailure(IllegalStateException("database failure")))
    }

    @Test
    fun photoReadFailureIsPreservedWithoutUploading() = runBlocking {
        assertBackupFailure(MealError.PhotoIoFailure)
    }

    @Test
    fun successfulBackupUploadsCompleteBytesBeforeDeletingItsFile() = runBlocking {
        val local = temporaryFolder.newFile()
        val events = mutableListOf<String>()
        val built = MealResult.Success(summary)
        val result = executeBackupUpload(local, { file ->
            assertSame(local, file)
            events += "build"
            file.writeText("complete backup bytes")
            built
        }, { file ->
            assertSame(local, file)
            events += "upload"
            assertEquals("complete backup bytes", file.readText())
        })

        assertSame(built, result)
        assertEquals(listOf("build", "upload"), events)
        assertFalse(local.exists())
    }

    @Test
    fun backupExceptionRemainsVisibleAndRemovesPartialFile() = runBlocking {
        val local = temporaryFolder.newFile()
        val failure = IOException("backup failed")
        val thrown = failureFrom {
            executeBackupUpload<WebDavStore.UploadSummary>(local, { file ->
                file.writeText("partial backup")
                throw failure
            }, { error("must not upload") })
        }

        assertSame(failure, thrown)
        assertFalse(local.exists())
    }

    @Test
    fun uploadExceptionRemainsVisibleAndRemovesLocalFile() = runBlocking {
        val local = temporaryFolder.newFile()
        val failure = IOException("HTTP upload failed")
        val thrown = failureFrom {
            executeBackupUpload(local, { MealResult.Success(summary) }, { throw failure })
        }

        assertSame(failure, thrown)
        assertFalse(local.exists())
    }

    @Test
    fun backupCancellationIsRethrownAndRemovesPartialFile() = runBlocking {
        val local = temporaryFolder.newFile()
        val cancelled = CancellationException("backup cancelled")
        val thrown = failureFrom {
            executeBackupUpload<WebDavStore.UploadSummary>(local, { file ->
                file.writeText("partial")
                throw cancelled
            }, { error("must not upload") })
        }

        assertSame(cancelled, thrown)
        assertFalse(local.exists())
    }

    @Test
    fun uploadCancellationIsRethrownAndRemovesLocalFile() = runBlocking {
        val local = temporaryFolder.newFile()
        val cancelled = CancellationException("upload cancelled")
        val thrown = failureFrom {
            executeBackupUpload(local, { MealResult.Success(summary) }, { throw cancelled })
        }

        assertSame(cancelled, thrown)
        assertFalse(local.exists())
    }

    @Test
    fun alreadyCancelledOperationNeverBuildsOrUploads() = runBlocking {
        val local = temporaryFolder.newFile()
        var built = false
        var uploaded = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            executeBackupUpload(local, {
                built = true
                MealResult.Success(summary)
            }, { uploaded = true })
        }
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(built)
        assertFalse(uploaded)
        assertFalse(local.exists())
    }

    @Test
    fun cancellationDuringBlockingBackupStopsBeforeUpload() = runBlocking {
        val local = temporaryFolder.newFile()
        var uploaded = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            executeBackupUpload(local, {
                currentCoroutineContext().cancel()
                MealResult.Success(summary)
            }, { uploaded = true })
        }
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(uploaded)
        assertFalse(local.exists())
    }

    @Test
    fun cancellationDuringBlockingUploadNeverReportsSuccess() = runBlocking {
        val local = temporaryFolder.newFile()
        var returnedSuccess = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            executeBackupUpload(local, { MealResult.Success(summary) }, {
                currentCoroutineContext().cancel()
            })
            returnedSuccess = true
        }
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(returnedSuccess)
        assertFalse(local.exists())
    }

    @Test
    fun overlappingOperationsOnlyDeleteTheirOwnTemporaryFile() = runBlocking {
        val first = File.createTempFile("webdav_upload_", ".zip", temporaryFolder.root)
        val second = File.createTempFile("webdav_upload_", ".zip", temporaryFolder.root)
        val firstReady = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val job = launch {
            executeBackupUpload(first, { file ->
                file.writeText("first backup")
                firstReady.complete(Unit)
                releaseFirst.await()
                MealResult.Success(summary)
            }, { file -> assertEquals("first backup", file.readText()) })
        }
        try {
            firstReady.await()
            executeBackupUpload(second, { file ->
                file.writeText("second partial backup")
                MealResult.Failure(MealError.StorageFull)
            }, { error("must not upload") })
            assertFalse(second.exists())
            assertTrue(first.exists())
            assertEquals("first backup", first.readText())
        } finally {
            releaseFirst.complete(Unit)
        }
        job.join()
        assertFalse(first.exists())
    }

    private suspend fun assertBackupFailure(error: MealError) {
        val local = temporaryFolder.newFile()
        val failure = MealResult.Failure(error)
        var uploaded = false
        val result = executeBackupUpload(local, { file ->
            file.writeText("partial backup")
            failure
        }, { uploaded = true })

        assertSame(failure, result)
        assertSame(error, (result as MealResult.Failure).error)
        assertFalse(uploaded)
        assertFalse(local.exists())
    }

    private suspend fun failureFrom(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (error: Throwable) {
            return error
        }
        error("Expected failure")
    }
}
