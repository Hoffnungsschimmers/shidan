package com.fanji.mealnote.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定 WebDAV 连通性检查的状态码归类。 */
class WebDavProbeTest {

    @Test
    fun successCodesMeanFileExists() {
        listOf(200, 201, 204, 207).forEach {
            assertEquals("code=$it", WebDavProbe.FILE_EXISTS, webDavProbeFor(it))
        }
    }

    @Test
    fun notFoundMeansFileAbsent() {
        assertEquals(WebDavProbe.FILE_ABSENT, webDavProbeFor(404))
    }

    @Test
    fun unauthorizedOrForbiddenMeansAuthFailed() {
        assertEquals(WebDavProbe.AUTH_FAILED, webDavProbeFor(401))
        assertEquals(WebDavProbe.AUTH_FAILED, webDavProbeFor(403))
    }

    @Test
    fun threeXxMeansRedirect() {
        listOf(301, 302, 307, 308).forEach {
            assertEquals("code=$it", WebDavProbe.REDIRECT, webDavProbeFor(it))        }
    }

    @Test
    fun otherCodesMeanUnreachable() {
        listOf(400, 405, 500, 503, 0, -1).forEach {
            assertEquals("code=$it", WebDavProbe.UNREACHABLE, webDavProbeFor(it))
        }
    }

    /** 文件与目录同时 404：真正的问题是目录不存在，不是「还没传过」。 */
    @Test
    fun fileAndCollectionBothMissingMeansCollectionAbsent() {
        assertEquals(WebDavProbe.COLLECTION_ABSENT, classifyWebDavProbe(404, 404))
    }

    @Test
    fun fileMissingButCollectionAliveStaysFileAbsent() {
        listOf(200, 207, 401).forEach {
            assertEquals(
                "collection=$it",
                WebDavProbe.FILE_ABSENT,
                classifyWebDavProbe(404, it),
            )
        }
    }

    /** 没发第二次请求（null）时必须与单状态码判定完全一致，不改变既有行为。 */
    @Test
    fun absentCollectionProbeNeverChangesTheVerdict() {
        listOf(200, 204, 207, 400, 401, 403, 404, 405, 500).forEach {
            assertEquals(
                "code=$it",
                webDavProbeFor(it),
                classifyWebDavProbe(it, null),
            )
        }
    }

    /** 文件侧已经给出确定答案时，目录侧的 404 不得反过来覆盖它。 */
    @Test
    fun definitiveFileCodeOutranksCollectionStatus() {
        assertEquals(WebDavProbe.AUTH_FAILED, classifyWebDavProbe(401, 404))
        assertEquals(WebDavProbe.FILE_EXISTS, classifyWebDavProbe(200, 404))
        assertEquals(WebDavProbe.REDIRECT, classifyWebDavProbe(302, 404))
    }
}
