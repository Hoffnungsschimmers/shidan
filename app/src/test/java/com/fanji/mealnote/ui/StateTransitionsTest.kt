package com.fanji.mealnote.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * [tryTransition] 的语义测试。
 *
 * 这类守卫一旦写错是**静默失效**的：并发点击会各自写一次数据库，界面却看不出异常。
 * 因此除了顺序语义，这里用真实线程验证「同一次跃迁只有一个赢家」。
 */
class StateTransitionsTest {

    @Test
    fun `首次跃迁写入并返回新状态`() {
        val state = MutableStateFlow(0)

        val result = state.tryTransition { if (it == 0) 1 else null }

        assertEquals(1, result)
        assertEquals(1, state.value)
    }

    @Test
    fun `transform 返回 null 时不写入且返回 null`() {
        val state = MutableStateFlow("idle")

        val result = state.tryTransition { null }

        assertNull(result)
        assertEquals("idle", state.value)
    }

    @Test
    fun `已在忙碌时的第二次跃迁被拒绝`() {
        // 与 ViewModel 里的 isBusy/isSaving 守卫同形：忙碌时 transform 返回 null。
        val state = MutableStateFlow(false)

        val first = state.tryTransition { busy -> if (busy) null else true }
        val second = state.tryTransition { busy -> if (busy) null else true }

        assertEquals(true, first)
        assertNull(second)
        assertEquals("第二次调用不得改写状态", true, state.value)
    }

    @Test
    fun `transform 可以只改状态由调用方决定是否继续`() {
        // 表单校验失败时用它只翻一个 showError 标志，并让调用方自行判断是否继续。
        val state = MutableStateFlow(Holder(busy = false, error = false))

        val result = state.tryTransition { it.copy(error = true) }

        assertEquals(true, result?.error)
        assertEquals(false, result?.busy)
        assertEquals(false, state.value.busy)
        assertEquals(true, state.value.error)
    }

    @Test
    fun `并发调用只有一个赢家`() {
        repeat(100) { round ->
            val state = MutableStateFlow(false)
            val winners = AtomicInteger(0)
            val start = CountDownLatch(1)
            val threads = (1..8).map {
                Thread {
                    start.await()
                    val won = state.tryTransition { busy -> if (busy) null else true }
                    if (won != null) winners.incrementAndGet()
                }.apply { start() }
            }

            start.countDown()
            threads.forEach(Thread::join)

            assertEquals("第 $round 轮应恰好一次成功", 1, winners.get())
            assertEquals(true, state.value)
        }
    }

    private data class Holder(val busy: Boolean, val error: Boolean)
}
