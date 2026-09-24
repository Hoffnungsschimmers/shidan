package com.fanji.mealnote.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * WebDAV 地址回显的脱敏测试。
 *
 * 错误提示现在会把**实际请求的完整地址**回显给用户 —— 这是区分「404 是因为没传上去」
 * 和「404 是因为路径拼错」唯一有效的信息。但有人习惯把凭据直接写进地址栏
 * （`https://user:pass@dav.example.com/dav`），回显时不能顺手把口令再显示一遍，
 * 运行日志更不行（`AppLog` 的隐私红线）。
 *
 * 这类「写的时候一行，泄露时没人会发现」的行为用测试锁住，比靠评审可靠。
 */
class WebDavUrlSafetyTest {

    private val secret = "TopSecret123"

    @Test
    fun `不含凭据的地址原样回显`() {
        val url = "https://dav.example.com/dav/mealnote-backup-latest.zip"
        assertEquals(url, redactUrlCredentials(url))
    }

    @Test
    fun `洗掉用户名与密码`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/mealnote.zip",
            redactUrlCredentials("https://someone:$secret@dav.jianguoyun.com/dav/mealnote.zip"),
        )
    }

    @Test
    fun `口令不会残留在结果的任何位置`() {
        val redacted = redactUrlCredentials("https://someone:$secret@dav.jianguoyun.com/dav/a.zip")
        assertFalse("脱敏结果里仍能搜到口令", redacted.contains(secret))
        assertFalse(redacted.contains("someone"))
    }

    @Test
    fun `端口与路径保留`() {
        val url = "http://192.168.1.10:5005/dav/backup.zip"
        assertEquals(url, redactUrlCredentials(url))
    }

    @Test
    fun `只有主机没有路径时也能洗掉凭据`() {
        assertEquals(
            "https://host.example",
            redactUrlCredentials("https://u:$secret@host.example"),
        )
    }

    @Test
    fun `路径里的 at 号不算凭据`() {
        // 目录名带 @ 是真实存在的（邮箱式命名）。只有 authority 段里的 @ 才触发洗除，
        // 否则会把这个用户的目录名裁掉一半 —— 那比泄露更糟：静默改掉了请求路径。
        val url = "https://dav.example.com/dav/team@lab/backup.zip"
        assertEquals(url, redactUrlCredentials(url))
    }

    @Test
    fun `authority 与路径同时含 at 时只洗凭据不动路径`() {
        assertEquals(
            "https://dav.example.com/dav/team@lab/backup.zip",
            redactUrlCredentials("https://u:$secret@dav.example.com/dav/team@lab/backup.zip"),
        )
    }

    @Test
    fun `没有协议前缀时不猜测原样返回`() {
        val url = "dav.example.com/dav/backup.zip"
        assertEquals(url, redactUrlCredentials(url))
    }
}
