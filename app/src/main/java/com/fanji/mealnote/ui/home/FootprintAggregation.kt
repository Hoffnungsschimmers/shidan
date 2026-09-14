package com.fanji.mealnote.ui.home

import com.fanji.mealnote.ui.formatMonthLabel
import com.fanji.mealnote.ui.parseEstimatedAmount
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 「足迹」的统计聚合。
 *
 * ## 为什么从 ViewModel 里抽出来
 *
 * 这些函数原本是 [FootprintViewModel] 内部的私有扩展函数，直接读
 * `YearMonth.now()` 与 `ZoneId.systemDefault()`。**读取系统时钟让它们无法被测试** ——
 * 同一个输入在不同日期会得到不同结果，测试只能断言「跑得通」，断言不了正确性。
 *
 * 把 `today` 与 `zone` 提升为参数之后：
 * - 测试可以固定任意日期（包括跨年、月末、闰年）；
 * - 时区行为变成显式契约而不是隐藏依赖；
 * - ViewModel 只剩「取当前时钟 + 组装状态」，不再混杂计算。
 *
 * 这些都是纯函数，不依赖 Android 框架，可直接在 JVM 单元测试里覆盖。
 */

/** 月度柱状图展示的月份数。 */
internal const val MONTHS_IN_CHART = 12

/** 「常去的店」最多展示几家。 */
internal const val MAX_TOP_RESTAURANTS = 5

/**
 * 时间戳落在哪个自然月（按 [zone] 判定）。
 *
 * 必须显式传时区：同一条记录在 UTC 的 12-31 23:00 与东八区的 1-1 07:00
 * 属于**不同的月份分组**，用错时区会让用户看到记录出现在「上个月」。
 */
internal fun Long.toYearMonth(zone: ZoneId): YearMonth =
    Instant.ofEpochMilli(this).atZone(zone).let(YearMonth::from)

/** 这条记录是否落在 [year] 年（按 [zone] 判定）。 */
internal fun FootprintEntry.isInYear(year: Int, zone: ZoneId): Boolean =
    record.eatenAt.toYearMonth(zone).year == year

/**
 * 本条记录的花费估算；未填写或识别不出数字时为 `null`。
 *
 * 注意区分「没填」与「填了但识别不出」：前者不该计入分母，
 * 后者要在界面上如实告诉用户（见 [FootprintViewModel] 的 `amountUnrecognizedCount`）。
 */
internal fun FootprintEntry.estimatedAmount(): Double? =
    record.priceText.takeIf(String::isNotBlank)?.parseEstimatedAmount()

/**
 * [year] 年的花费估算合计。
 *
 * 一条都识别不出来时返回 `null` 而不是 `0.0`：界面上「约 ¥0」会被读成
 * 「今年没花钱」，而事实是「没有可识别的金额」。
 */
internal fun List<FootprintEntry>.estimatedSpendIn(year: Int, zone: ZoneId): Double? =
    filter { it.isInYear(year, zone) }
        .mapNotNull { it.estimatedAmount() }
        .takeIf { it.isNotEmpty() }
        ?.sum()

/**
 * 最近 [months] 个月的用餐次数，从最早到最新。
 *
 * 以 [today] 为基准**向前推固定月数**，而不是只取数据中出现过的月份：
 * 柱状图的横轴必须固定，否则某个月没有记录时该柱会消失、整张图的月份间距错乱。
 *
 * @param today 基准月。传入参数而不是读时钟，测试才能固定到任意月份。
 */
internal fun List<FootprintEntry>.toMonthlyCounts(
    today: YearMonth,
    zone: ZoneId,
    months: Int = MONTHS_IN_CHART,
): List<MonthlyCount> {
    require(months > 0) { "months must be positive, was $months" }
    val counts = groupingBy { it.record.eatenAt.toYearMonth(zone) }.eachCount()
    return (months - 1 downTo 0).map { monthsAgo ->
        val month = today.minusMonths(monthsAgo.toLong())
        MonthlyCount(
            yearMonth = month.toString(),
            label = "${month.monthValue}月",
            count = counts[month] ?: 0,
        )
    }
}

/**
 * 常去的店，按次数倒序。
 *
 * 次数相同时按 id 升序 —— 排序必须**稳定**，否则数据库返回顺序变化时
 * 排行榜会无理由地跳动，用户会以为数据变了。
 *
 * @param limit 最多返回几家；不足时返回全部。
 */
internal fun List<FootprintEntry>.toTopRestaurants(limit: Int = MAX_TOP_RESTAURANTS): List<RestaurantRank> =
    groupingBy { it.record.restaurantId }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .mapNotNull { (id, count) ->
            // 从已有条目里取店名，避免再查一次数据库。
            firstOrNull { it.record.restaurantId == id }
                ?.let { RestaurantRank(id, it.restaurantName, count) }
        }

/**
 * 按月份分组。
 *
 * 依赖上游已按 `eatenAt DESC` 排序（见 `MealDao.observeAllDiningRecords`），
 * 因此这里用 `groupBy` 即可保持「新月份在前、月内新记录在前」的顺序，
 * 不需要二次排序。**若上游排序被改动，此处顺序会一并失效。**
 */
internal fun List<FootprintEntry>.toSections(): List<FootprintSection> =
    groupBy { it.record.eatenAt.formatMonthLabel() }
        .map { (title, entries) -> FootprintSection(title, entries) }
