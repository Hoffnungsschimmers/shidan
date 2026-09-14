package com.fanji.mealnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自由文本入库处理（[normalizeText]）的测试。
 *
 * 重点是**截断不能损坏字符串**。早期实现用 `trim().take(limit)`，
 * 而 `take` 数的是 UTF-16 码元 —— 增补平面字符（emoji）占两个码元，
 * 在截断边界上会被切成半个代理对，产生非法字符串。
 */
class MealTextTest {

    // ─────────────────────────── 空白处理 ───────────────────────────

    @Test
    fun `去掉首尾空白`() {
        assertEquals("牛肉面", "  牛肉面  ".normalizeText(100))
        assertEquals("牛肉面", "\n\t牛肉面\r\n".normalizeText(100))
    }

    @Test
    fun `全角空格也会被去掉`() {
        // 中文输入法下很容易带出全角空格，只 trim 半角空格是不够的。
        assertEquals("牛肉面", "\u3000牛肉面\u3000".normalizeText(100))
    }

    @Test
    fun `中间的空格保留`() {
        assertEquals("牛肉 面", " 牛肉 面 ".normalizeText(100))
    }

    @Test
    fun `纯空白变成空串`() {
        assertEquals("", "   ".normalizeText(100))
        assertEquals("", "\u3000\n".normalizeText(100))
        assertEquals("", "".normalizeText(100))
    }

    // ─────────────────────────── 截断 ───────────────────────────

    @Test
    fun `未超限时原样返回`() {
        assertEquals("牛肉面", "牛肉面".normalizeText(3))
        assertEquals("牛肉面", "牛肉面".normalizeText(100))
    }

    @Test
    fun `超限时截断到指定长度`() {
        assertEquals("牛肉", "牛肉面加蛋".normalizeText(2))
    }

    @Test
    fun `中文按一个字算一个长度`() {
        val result = "牛肉面".normalizeText(3)
        assertEquals(3, result.length)
        assertEquals(3, result.codePointCount(0, result.length))
    }

    @Test
    fun `limit 为零时返回空串`() {
        assertEquals("", "牛肉面".normalizeText(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `limit 为负时抛出异常`() {
        "牛肉面".normalizeText(-1)
    }

    // ─────────────────────── emoji 与代理对（回归重点） ───────────────────────

    @Test
    fun `emoji 不会被切成半个代理对`() {
        // 回归用例：`"很好吃😋".take(3)` 会返回「很好吃」+ 半个代理对，
        // 那是一个非法字符串，入库后渲染成 �，写进 JSON 备份可能直接损坏文件。
        val result = "很好吃😋".normalizeText(3)

        assertEquals("很好吃", result)
        assertFalse("结果含孤立代理，说明代理对被切断了", result.hasLoneSurrogate())
    }

    @Test
    fun `截断点正好落在 emoji 上时整体丢弃`() {
        // 「很好吃😋」= 4 个 code point。limit=3 只能保留前三个，
        // emoji 必须**整体丢弃**，不能留半个。
        val result = "很好吃😋".normalizeText(3)
        assertFalse(result.contains("\uD83D"))
        assertFalse(result.hasLoneSurrogate())
    }

    @Test
    fun `limit 刚好容纳 emoji 时完整保留`() {
        val result = "很好吃😋".normalizeText(4)
        assertEquals("很好吃😋", result)
        assertEquals(4, result.codePointCount(0, result.length))
        // 4 个 code point 占 5 个 UTF-16 码元，这正是 take() 会出错的根源。
        assertEquals(5, result.length)
    }

    @Test
    fun `全是 emoji 时按个数截断`() {
        // 用 take(1) 会得到孤立的高代理；按 code point 截断应得到完整的第一个 emoji。
        val result = "😋😋😋".normalizeText(1)
        assertEquals("😋", result)
        assertFalse(result.hasLoneSurrogate())
    }

    @Test
    fun `emoji 按一个字符计数而不是两个`() {
        // 若按 UTF-16 码元计数，limit=2 只能放下 1 个 emoji。
        assertEquals("😋😋", "😋😋😋".normalizeText(2))
    }

    @Test
    fun `各种长度的混合文本都不会产生孤立代理`() {
        val source = "a😋牛🍜肉🥢面🎉好吃👍"
        val total = source.codePointCount(0, source.length)
        for (limit in 0..total + 2) {
            val result = source.normalizeText(limit)
            assertFalse("limit=$limit 产生了孤立代理", result.hasLoneSurrogate())
            assertTrue(
                "limit=$limit 的结果超出了上限",
                result.codePointCount(0, result.length) <= limit,
            )
        }
    }

    @Test
    fun `组合 emoji 序列被拆开时仍是合法字符串`() {
        // 已知近似：截断级别是 code point 而非字素簇，ZWJ 序列可能被拆开
        // （👨‍👩‍👧 → 👨‍👩）。视觉上不理想，但不会损坏数据。
        val family = "👨\u200D👩\u200D👧"
        val result = family.normalizeText(3)
        assertFalse(result.hasLoneSurrogate())
    }

    // ─────────────────────────── 真实约束 ───────────────────────────

    @Test
    fun `实际使用的三个长度上限都能正确工作`() {
        val long = "牛".repeat(3_000)
        assertEquals(MAX_PRICE_TEXT_LENGTH, long.normalizeText(MAX_PRICE_TEXT_LENGTH).length)
        assertEquals(MAX_DISHES_LENGTH, long.normalizeText(MAX_DISHES_LENGTH).length)
        assertEquals(MAX_NOTE_LENGTH, long.normalizeText(MAX_NOTE_LENGTH).length)
    }

    @Test
    fun `粘贴整段聊天记录时静默截断而不报错`() {
        // 超长输入绝大多数来自粘贴，用户想要的是「把内容记下来」，
        // 而不是被告知「你粘多了」。
        val pasted = "  刚才那家店不错，下次还去。".repeat(500)
        val result = pasted.normalizeText(MAX_PRICE_TEXT_LENGTH)
        assertEquals(MAX_PRICE_TEXT_LENGTH, result.length)
    }
}

/**
 * 判断字符串里是否存在**孤立代理**（非法的 UTF-16 序列）。
 *
 * 合法的高代理后面必须紧跟低代理；单独出现任何一个都是损坏的标志。
 */
private fun String.hasLoneSurrogate(): Boolean {
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c.isHighSurrogate() -> {
                if (i + 1 >= length || !this[i + 1].isLowSurrogate()) return true
                i += 2
            }

            c.isLowSurrogate() -> return true
            else -> i++
        }
    }
    return false
}
