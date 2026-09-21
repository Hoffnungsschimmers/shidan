package com.fanji.mealnote.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 从「花费」自由文本解析**入账金额**（单位：分），无法确定时返回 `null`。
 *
 * 与 `ui.Formatters.parseEstimatedAmount()`（展示用估算，取最大数字）不同，
 * 本函数是**账本口径**，规则更严格——账本宁可缺条目，也不要错条目：
 *
 * | 输入 | personCount | 结果 | 规则 |
 * | --- | --- | --- | --- |
 * | `128` | 1 | 12800 | 取最大数字（跳过人数样式的数字） |
 * | `人均60` | 3 | 18000 | **仅**「人均」单价 × 人数 |
 * | `3个人吃了240` | 3 | 24000 | 240 是整桌总额，**不再** × 人数；`3` 只用于探测人数 |
 * | `1,280` | 1 | 128000 | 千分位逗号先移除 |
 * | `人均60` | 1 | 6000 | 没多人时人均=总额 |
 * | `300积分` | 1 | 30000 | 同「最大数字」；识别不了语义是已知近似 |
 * | `3个人吃了` | 1 | null | 只有人数、没有金额 |
 * | 空白 / 无法解析 | — | null | 入账列为空 = 「未记金额」 |
 *
 * **只有「人均」是单价，需 × 人数换算成整桌总额；其余数字本身就是总额，绝不再乘人数**——
 * 否则「3个人吃了240」在表单自动探测出人数 3 后会被算成 ¥720（决策点 B，用测试锁死）。
 *
 * 已知近似（刻意不处理，与展示估算同源）：`30-40` 取 40；
 * 数字紧跟单位（积分/分钟）会被当成金额——真实输入里极为罕见。
 *
 * 计算全程用 [BigDecimal]，`×100` 后 `HALF_UP` 取整到分，避免浮点误差进账本。
 */
internal fun parseLedgerAmountMinor(text: String, personCount: Int): Long? {
    val cleaned = text.replace(THOUSAND_SEPARATOR_PATTERN, "")
    val people = personCount.coerceAtLeast(1)

    val total: BigDecimal = perCapitaAmount(cleaned)?.multiply(BigDecimal(people))
        ?: allNumbers(cleaned).maxOrNull()
        ?: return null

    return toMinorUnits(total)
}

/**
 * 从自由文本里探测就餐人数（`3个人`、`两人`、`2位`），探测不到返回 `null`。
 *
 * 只用于表单**预填**人数——错猜人数的代价是金额算错，所以：
 * 数字后必须紧跟人数单位；结果限定 1..20，越界视为误匹配。
 * 「人均」不在其列（人数在「人」之后，不是之前）。
 */
internal fun detectPersonCount(text: String): Int? =
    PERSON_COUNT_PATTERN.find(text.replace(THOUSAND_SEPARATOR_PATTERN, ""))
        ?.groupValues?.get(1)
        ?.let { token -> if (token == "两") 2 else token.toIntOrNull() }
        ?.takeIf { it in 1..20 }

/** 「人均 60」「人均：60」→ 单价 BigDecimal；否则 null。 */
private fun perCapitaAmount(text: String): BigDecimal? =
    PER_CAPITA_PATTERN.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()

/**
 * 所有金额样式的数字：跳过「3个人」里的人数、也跳过裸单位数字前的修饰。
 * 与 [PERSON_COUNT_PATTERN] 用同一套单位，保证两处判定一致。
 */
private fun allNumbers(text: String): List<BigDecimal> {
    val personRanges = PERSON_COUNT_PATTERN.findAll(text).map { it.range }.toList()
    return NUMBER_PATTERN.findAll(text)
        .filterNot { match -> personRanges.any { match.range.first in it || match.range.last in it } }
        .mapNotNull { it.value.toBigDecimalOrNull() }
        .toList()
}

/** 整桌总额（元）→ 分。上限 100 万元（1 亿分）以上视为误匹配返回 null。 */
private fun toMinorUnits(total: BigDecimal): Long? {
    val cents = total.multiply(HUNDRED)
        .setScale(0, RoundingMode.HALF_UP)
    return cents.longValueExact().takeIf { it in 1..MAX_LEDGER_MINOR }
}

private val HUNDRED = BigDecimal(100)

/** 分上限：¥1,000,000。超过基本是误匹配（手机号、订单号粘进来了）。 */
private const val MAX_LEDGER_MINOR = 100_000_000L

/** 千分位逗号：只在「数字 + 逗号 + 三位数字」处移除（与 Formatters 中同一规则）。 */
private val THOUSAND_SEPARATOR_PATTERN = Regex("""(?<=\d),(?=\d{3})""")

/** 金额数字：整数或小数。 */
private val NUMBER_PATTERN = Regex("""\d+(?:\.\d+)?""")

/** 人数：数字（含「两」）+ 可选「个/位」+「人/位」。 */
private val PERSON_COUNT_PATTERN = Regex("""([2两]|\d+)\s*(?:个\s*)?(?:人|位)""")

/** 人均单价：「人均」后紧跟的数字。 */
private val PER_CAPITA_PATTERN = Regex("""人均\s*[:：]?\s*(\d+(?:\.\d+)?)""")
