package com.fanji.mealnote.data

/**
 * 自由文本的统一入库处理。
 *
 * ## 为什么按 code point 截断而不是 `take(limit)`
 *
 * Kotlin 的 `String.take(n)` 数的是 **UTF-16 码元**，不是字符。
 * 增补平面字符（绝大多数 emoji）在 UTF-16 里占**两个**码元，因此在截断边界上会被
 * 切成半个代理对，产生一个**非法的 UTF-16 字符串**：
 *
 * ```
 * "很好吃😋".take(4)   // → "很好吃" + 半个代理对（孤立的高代理）
 * ```
 *
 * 后果不是「显示得不好看」，而是数据本身坏掉：
 * - 存入数据库后再读出，渲染成替换字符 `�`；
 * - 写进 JSON 备份时，孤立代理会被编码成非法转义，**备份文件可能直接无法解析**；
 * - 往返一次备份/恢复后，原文永久丢失。
 *
 * 按 code point 截断可以彻底避免这个问题。`offsetByCodePoints` 会自动跳过完整的
 * 代理对，保证结果始终是合法字符串。
 *
 * ## 已知的近似（刻意不处理）
 *
 * 截断级别是 **code point**，不是**字素簇**。因此由零宽连接符（ZWJ）拼成的
 * 复合 emoji 序列可能被拆开 —— 例如 `👨‍👩‍👧` 会被截成 `👨‍👩`（两个人），
 * 视觉上不理想，但**仍然是合法字符串**，不会造成数据损坏。
 *
 * 要做到字素簇级别需要 `java.text.BreakIterator`，其行为依赖默认 locale，
 * 而本项目这三处限制（花费 40 / 餐品 500 / 备注 2000）在真实输入里几乎不会
 * 正好落在 ZWJ 序列中间。为这点收益引入 locale 依赖不划算。
 *
 * ## 截断而非报错
 *
 * 超长输入绝大多数来自**粘贴**（整段聊天记录、整篇点评），此时静默截断比弹错误更好 ——
 * 用户想要的是「把内容记下来」，而不是被告知「你粘多了」。
 */
internal fun String.normalizeText(limit: Int): String {
    require(limit >= 0) { "limit must be non-negative, was $limit" }
    // trim() 覆盖 Unicode 空白，包含全角空格 U+3000 —— 中文输入法下很容易带出来。
    val trimmed = trim()
    val codePoints = trimmed.codePointCount(0, trimmed.length)
    if (codePoints <= limit) return trimmed
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, limit))
}
