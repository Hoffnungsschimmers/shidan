package com.fanji.mealnote.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PhotoReclamationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val cutoffMillis = 1_700_000_000_000L

    @Test
    fun orphanCleanupPreservesRecordAndCoverReferences() {
        val directory = temporaryFolder.newFolder("photos")
        val record = photo(directory, "record.jpg")
        val cover = photo(directory, "cover.jpg")
        val orphan = photo(directory, "orphan.jpg")

        assertEquals(1, deleteOrphanPhotoFiles(directory, listOf(record.path, cover.path), cutoffMillis))
        assertTrue(record.exists())
        assertTrue(cover.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun orphanCleanupPreservesSharedPhotoReferencedByEitherTable() {
        val directory = temporaryFolder.newFolder("photos")
        val shared = photo(directory, "shared.jpg")

        assertEquals(0, deleteOrphanPhotoFiles(directory, listOf(shared.path, shared.path), cutoffMillis))
        assertEquals(0, deleteOrphanPhotoFiles(directory, listOf(shared.path), cutoffMillis))
        assertTrue(shared.exists())
    }

    @Test
    fun orphanCleanupNormalizesDotSegmentReferences() {
        val directory = temporaryFolder.newFolder("photos")
        val shared = photo(directory, "shared.jpg")
        val alias = File(directory, "../photos/shared.jpg")

        assertEquals(0, deleteOrphanPhotoFiles(directory, listOf(alias.path), cutoffMillis))
        assertTrue(shared.exists())
    }

    @Test
    fun orphanCleanupAbortsBeforeAnyDeletionWhenReferenceResolutionFails() {
        val directory = temporaryFolder.newFolder("photos")
        val referenced = photo(directory, "referenced.jpg")
        val orphan = photo(directory, "orphan.jpg")
        val invalidPath = File(directory, "unresolved.jpg").path

        val error = assertThrows(IOException::class.java) {
            deleteOrphanPhotoFiles(directory, listOf(referenced.path, invalidPath), cutoffMillis) { path ->
                if (path == invalidPath) throw IOException("private path must not escape")
                File(path).canonicalPath
            }
        }
        assertFalse(error.message.orEmpty().contains("private path"))
        assertTrue(referenced.exists())
        assertTrue(orphan.exists())
    }

    @Test
    fun orphanCleanupAbortsWhenReferenceAccessIsDenied() {
        val directory = temporaryFolder.newFolder("photos")
        val orphan = photo(directory, "orphan.jpg")

        assertThrows(IOException::class.java) {
            deleteOrphanPhotoFiles(directory, listOf(orphan.path), cutoffMillis) {
                throw SecurityException("denied")
            }
        }
        assertTrue(orphan.exists())
    }

    @Test
    fun orphanCleanupAbortsWhenDirectoryCannotBeResolved() {
        val directory = temporaryFolder.newFolder("photos")
        val orphan = photo(directory, "orphan.jpg")

        assertThrows(IOException::class.java) {
            deleteOrphanPhotoFiles(directory, emptyList(), cutoffMillis) {
                throw IOException("unresolved")
            }
        }
        assertTrue(orphan.exists())
    }

    @Test
    fun orphanCleanupSkipsUnresolvableCandidateButReclaimsKnownOrphan() {
        val directory = temporaryFolder.newFolder("photos")
        val unresolved = photo(directory, "unresolved.jpg")
        val orphan = photo(directory, "orphan.jpg")

        assertEquals(1, deleteOrphanPhotoFiles(directory, emptyList(), cutoffMillis) { path ->
            if (path == unresolved.path) throw IOException("unresolved")
            File(path).canonicalPath
        })
        assertTrue(unresolved.exists())
        assertFalse(orphan.exists())
    }

    @Test
    fun orphanCleanupSkipsRecentFilesAndIncludesExactCutoff() {
        val directory = temporaryFolder.newFolder("photos")
        val recent = photo(directory, "recent.jpg", cutoffMillis + 1)
        val atCutoff = photo(directory, "cutoff.jpg", cutoffMillis)

        assertEquals(1, deleteOrphanPhotoFiles(directory, emptyList(), cutoffMillis))
        assertTrue(recent.exists())
        assertFalse(atCutoff.exists())
    }

    @Test
    fun orphanCleanupNeverTraversesSubdirectories() {
        val directory = temporaryFolder.newFolder("photos")
        val nested = File(directory, "nested").apply { assertTrue(mkdir()) }
        val child = photo(nested, "child.jpg")

        assertEquals(0, deleteOrphanPhotoFiles(directory, emptyList(), cutoffMillis))
        assertTrue(nested.exists())
        assertTrue(child.exists())
    }

    @Test
    fun orphanCleanupRejectsCandidateResolvingOutsideOwnedDirectory() {
        val directory = temporaryFolder.newFolder("photos")
        val candidate = photo(directory, "linked.jpg")
        val outside = temporaryFolder.newFile("outside.jpg")

        // 注入链接的解析结果：不依赖 Windows 创建符号链接的管理员权限。
        assertEquals(0, deleteOrphanPhotoFiles(directory, emptyList(), cutoffMillis) { path ->
            if (path == candidate.path) outside.canonicalPath else File(path).canonicalPath
        })
        assertTrue(candidate.exists())
        assertTrue(outside.exists())
    }

    @Test
    fun orphanCleanupIgnoresEmptyOptionalCoverPaths() {
        val directory = temporaryFolder.newFolder("photos")
        val orphan = photo(directory, "orphan.jpg")

        assertEquals(1, deleteOrphanPhotoFiles(directory, listOf("", "  "), cutoffMillis))
        assertFalse(orphan.exists())
    }

    @Test
    fun orphanCleanupPropagatesCancellationWithoutDeletingFiles() {
        val directory = temporaryFolder.newFolder("photos")
        val orphan = photo(directory, "orphan.jpg")
        val cancelled = CancellationException("cancel")

        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            deleteOrphanPhotoFiles(directory, listOf(orphan.path), cutoffMillis) { throw cancelled }
        })
        assertTrue(orphan.exists())
    }

    @Test
    fun targetedCleanupNormalizesBothCandidateAndReference() {
        val directory = temporaryFolder.newFolder("photos")
        val shared = photo(directory, "shared.jpg")
        val alias = File(directory, "../photos/shared.jpg").path

        assertTrue(unreferencedPhotoCandidates(listOf(shared.path), setOf(alias)) { false }.isEmpty())
        assertTrue(unreferencedPhotoCandidates(listOf(alias), setOf(shared.path)) { false }.isEmpty())
    }

    @Test
    fun targetedCleanupChecksLiveProtectionAfterResolvingReferences() {
        var selected = false
        val result = unreferencedPhotoCandidates(
            candidates = listOf("candidate"),
            referenced = setOf("database-photo"),
            canonicalize = { path ->
                if (path == "database-photo") selected = true
                path
            },
            isProtected = { selected },
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun targetedCleanupAbortsOnIncompleteReferenceSet() {
        assertThrows(IOException::class.java) {
            unreferencedPhotoCandidates(
                listOf("orphan"), setOf("invalid"),
                canonicalize = { throw IOException("unresolved") },
                isProtected = { false },
            )
        }
    }

    @Test
    fun targetedCleanupPreservesUnresolvableCandidate() {
        val result = unreferencedPhotoCandidates(
            listOf("invalid", "orphan"), emptySet(),
            canonicalize = { path ->
                if (path == "invalid") throw SecurityException("denied")
                path
            },
            isProtected = { false },
        )

        assertEquals(listOf("orphan"), result)
    }

    @Test
    fun targetedCleanupPropagatesCancellation() {
        val cancelled = CancellationException("cancel")
        assertSame(cancelled, assertThrows(CancellationException::class.java) {
            unreferencedPhotoCandidates(
                listOf("orphan"), setOf("referenced"),
                canonicalize = { throw cancelled },
                isProtected = { false },
            )
        })
    }

    private fun photo(directory: File, name: String, modifiedAt: Long = cutoffMillis - 1): File =
        File(directory, name).apply {
            writeBytes(byteArrayOf(1, 2, 3))
            assertTrue("fixture timestamp", setLastModified(modifiedAt))
        }
}
