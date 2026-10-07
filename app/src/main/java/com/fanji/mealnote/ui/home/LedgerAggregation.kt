package com.fanji.mealnote.ui.home

import androidx.compose.runtime.Immutable
import java.time.YearMonth
import java.time.ZoneId

/**
 * 「账本」聚合 —— 基于**结构化入账金额**（[com.fanji.mealnote.data.local.DiningRecordEntity.amountMinorUnits]）。
 *
 * 与 `FootprintAggregation` 的花费**估算**是两套口径，刻意分开：
 *
 * | 口径 | 数据源 | 用途 | 界面措辞 |
 * | --- | --- | --- | --- |
 * | 估算 | `priceText` 取最大数字 | 历史数据、随手写的场景 | 「估算」 |
 * | 账本 | `amountMinorUnits`（分） | v0.5 起逐笔确认的金额 | 不带「估算」二字 |
 *
 * 账本只认入账金额，**绝不**从文本估算里凑数——账本宁可少算，不可错算；
 * 覆盖不足的事实通过「N 条已入账」的说明如实展示，而不是悄悄用估算填充。
 *
 * 与 `FootprintAggregation` 同一约定：[today]/[zone] 作参数、纯函数、不读时钟。
 */

/** 月度金额柱的一根柱子。[amountMinor] 为 `null` = 该月没有任何入账记录。 */
@Immutable
data class MonthlyAmount(
    val yearMonth: String,
    val label: String,
    val amountMinor: Long?,
)

/** 花钱最多的店：按累计入账金额排行。 */
@Immutable
data class RestaurantSpend(
    val restaurantId: Long,
    val restaurantName: String,
    val amountMinor: Long,
    /** 该店的入账笔数（不含未入账的用餐）。 */
    val visitCount: Int,
)

/** 入账覆盖情况：有金额几条、写了花费但没入账几条。 */
@Immutable
data class LedgerCoverage(
    val recognizedCount: Int,
    val textOnlyCount: Int,
)

/** 本条记录的入账金额（分）；未入账为 null。 */
internal fun FootprintEntry.ledgerAmountMinor(): Long? = record.amountMinorUnits

/**
 * 指定年份的入账总额（分）。一笔都没有时返回 `null` 而非 `0`——
 * 「约 ¥0」会被读成「今年没花钱」，而事实是「还没有入账记录」（与估算口径同一原则）。
 */
internal fun List<FootprintEntry>.ledgerSpendIn(year: Int, zone: ZoneId): Long? =
    filter { it.isInYear(year, zone) }
        .mapNotNull { it.ledgerAmountMinor() }
        .takeIf { it.isNotEmpty() }
        ?.sum()

/** 最近 [months] 个月的入账总额，从最早到最新。横轴固定，与次数柱状图同一规则。 */
internal fun List<FootprintEntry>.ledgerMonthlyAmounts(
    today: YearMonth,
    zone: ZoneId,
    months: Int = MONTHS_IN_CHART,
): List<MonthlyAmount> {
    require(months > 0) { "months must be positive, was $months" }
    val totals = filter { it.ledgerAmountMinor() != null }
        .groupBy { it.record.eatenAt.toYearMonth(zone) }
        .mapValues { (_, entries) -> entries.sumOf { it.ledgerAmountMinor()!! } }
    return (months - 1 downTo 0).map { monthsAgo ->
        val month = today.minusMonths(monthsAgo.toLong())
        MonthlyAmount(
            yearMonth = month.toString(),
            label = "${month.monthValue}月",
            amountMinor = totals[month],
        )
    }
}

/** 平均每笔入账金额（分）；没有入账记录时返回 null。 */
internal fun List<FootprintEntry>.ledgerAverageAmount(): Long? {
    val amounts = mapNotNull { it.ledgerAmountMinor() }
    if (amounts.isEmpty()) return null
    return (amounts.sum() + amounts.size / 2L) / amounts.size // 四舍五入到分
}

/**
 * 花钱最多的店，按累计入账金额倒序。
 *
 * 金额相同时按 id 升序（与「常去的店」同一稳定性约定，避免无理由跳动）。
 * 未入账的用餐不计入该店金额，也不计入笔数。
 */
internal fun List<FootprintEntry>.toTopSpendRestaurants(
    limit: Int = MAX_TOP_RESTAURANTS,
): List<RestaurantSpend> {
    val paid = filter { it.ledgerAmountMinor() != null }
    return paid.groupBy { it.record.restaurantId }
        .map { (id, entries) ->
            RestaurantSpend(
                restaurantId = id,
                restaurantName = entries.first().restaurantName,
                amountMinor = entries.sumOf { it.ledgerAmountMinor()!! },
                visitCount = entries.size,
            )
        }
        .sortedWith(
            compareByDescending<RestaurantSpend> { it.amountMinor }.thenBy { it.restaurantId },
        )
        .take(limit)
}

/**
 * 入账覆盖情况（在 [year] 年内统计，按 [zone] 判定归属）。
 *
 * - `recognizedCount`：有结构化金额的条数；
 * - `textOnlyCount`：写了花费文本但没入账的条数 —— 界面据此提示
 *   「另有 N 条写的是文字，可在编辑时补录金额」，而不是假装它们不存在。
 */
internal fun List<FootprintEntry>.ledgerCoverageIn(year: Int, zone: ZoneId): LedgerCoverage {
    val inYear = filter { it.isInYear(year, zone) }
    return LedgerCoverage(
        recognizedCount = inYear.count { it.ledgerAmountMinor() != null },
        textOnlyCount = inYear.count {
            it.ledgerAmountMinor() == null && it.record.priceText.isNotBlank()
        },
    )
}

/**
 * 指定自然月的入账总额(分);该月没有任何入账记录时返回 `null`(与年度口径一致:null ≠ 0)。
 *
 * 从 `FootprintViewModel` 里内联的「本月入账」下沉为纯函数,以便按固定月份单测。
 */
internal fun List<FootprintEntry>.ledgerSpendInMonth(month: YearMonth, zone: ZoneId): Long? =
    filter { it.record.eatenAt.toYearMonth(zone) == month }
        .mapNotNull { it.ledgerAmountMinor() }
        .takeIf { it.isNotEmpty() }
        ?.sum()

/**
 * 每月预算进度。
 *
 * @param spentMinor 本月已入账金额(分),null 视为 0。
 * @param budgetMinor 每月预算(分)。<=0 表示未设预算,返回 null(界面不显示进度)。
 *
 * `fraction` **不做上限裁剪**(超支时 >1,交给界面决定进度条封顶与配色);
 * `remainingMinor` 可为负(超支额取负);`overBudget` 为是否超过预算。
 */
@Immutable
data class BudgetProgress(
    val spentMinor: Long,
    val budgetMinor: Long,
    val fraction: Float,
    val remainingMinor: Long,
    val overBudget: Boolean,
)

internal fun monthlyBudgetProgress(spentMinor: Long?, budgetMinor: Long): BudgetProgress? {
    if (budgetMinor <= 0L) return null
    val spent = (spentMinor ?: 0L).coerceAtLeast(0L)
    return BudgetProgress(
        spentMinor = spent,
        budgetMinor = budgetMinor,
        fraction = spent.toDouble().div(budgetMinor.toDouble()).toFloat(),
        remainingMinor = budgetMinor - spent,
        overBudget = spent > budgetMinor,
    )
}
