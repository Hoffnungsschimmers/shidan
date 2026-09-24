package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.data.settings.RandomPickConfig
import com.fanji.mealnote.data.settings.RandomScope
import com.fanji.mealnote.data.settings.randomExcludeDaysLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 「随机选一家」的候选池计算。
 *
 * ## 为什么要重定义候选池
 *
 * 旧实现直接把「当前筛选后的待探访列表」当作候选——而待探访店按定义**没有任何评价**，
 * 所以「按评价范围随机」这个需求在原结构里根本无从下手。本版把候选池的定义下沉一层：
 *
 * - 候选来自**全量店铺**，不再受清单页的分段与搜索词影响（用户搜「火锅」时抽出来的
 *   应该是按范围挑的店，而不是搜索结果里随机一个，两件事混在一起只会让人困惑）；
 * - 每家店有一个「当前评价」= **最近一次**用餐记录的评价。常客口味会变，
 *   三年前的「推荐」不代表今天还想去，最新那条判断才代表「还想不想再去了」。
 *
 * ## 与「排除近期」的关系
 *
 * 两个维度正交：范围决定「哪些店有资格」，排除近期决定「刚去过的还要不要出现」。
 * 待探访店没有用餐记录，天然不受排除影响。
 *
 * ## 可测试性
 *
 * 与 [FootprintAggregation] 同一约定：纯函数、不读时钟，`today` 与 `zone` 作参数。
 * 排除的判定按**自然日**而不是 24 小时滚动窗口——「7 天内吃过」在界面上是这么读的，
 * 实现也该这么算；用 `LocalDate` 比较同时让跨年与闰月的行为可被测试固定。
 */

/** 一家店「最近一次用餐」留下的事实：评价与日期。 */
internal data class LatestVisit(val verdict: Verdict, val eatenAt: Long)

/**
 * 按餐厅归并出每家的最近一次用餐。
 *
 * 刻意**不依赖上游排序**：早期版本打算利用 `MealDao.observeAllDiningRecords` 的
 * `eatenAt DESC` 顺序「取每个餐厅的第一条」，但那会让正确性挂在一个远在上游的约定上——
 * 哪天有人改了 `ORDER BY`，这里会静默变成「取最早一次评价」，随机池于是稳定地推出
 * 用户三年前嫌弃的店，而且没有任何报错。自己比时间戳，代价只是多一次遍历。
 *
 * 没有用餐记录的店不会出现在结果里，调用方以「不在 map 中」判定为待探访。
 */
internal fun latestVisitByRestaurant(records: List<DiningRecordEntity>): Map<Long, LatestVisit> {
    val visits = LinkedHashMap<Long, LatestVisit>(records.size)
    records.forEach { record ->
        val current = visits[record.restaurantId]
        if (current == null || record.eatenAt > current.eatenAt) {
            visits[record.restaurantId] = LatestVisit(record.verdict, record.eatenAt)
        }
    }
    return visits
}

/**
 * 按配置算出随机候选池。
 *
 * 返回顺序沿用 [restaurants] 的入参顺序（即 `updatedAt DESC`），**不做二次排序**：
 * 抽选是等概率的，任何额外排序都不会让结果更「该被选中」，只会改变统计直觉。
 * 结果为空时界面应当隐藏随机按钮，而不是点了之后弹「没有可选的店」。
 */
internal fun buildRandomCandidatePool(
    restaurants: List<RestaurantEntity>,
    latestVisits: Map<Long, LatestVisit>,
    config: RandomPickConfig,
    today: LocalDate,
    zone: ZoneId,
): List<RestaurantEntity> {
    val cutoff = if (config.excludeRecentDays > 0) {
        today.minusDays(config.excludeRecentDays.toLong())
    } else {
        null
    }
    return restaurants.filter { restaurant ->
        val latest = latestVisits[restaurant.id]
        if (latest == null) {
            config.includeWantToList
        } else {
            config.scope.accepts(latest.verdict) &&
                (cutoff == null || !latest.eatenAt.isOnOrAfter(cutoff, zone))
        }
    }
}

/**
 * 结果对话框里对当前范围的一句话说明。
 *
 * 必须有：用户在「仅推荐」档下抽到一家待探访新店时，第一反应是「范围没生效」，
 * 说明写清楚「含待探访」就不用猜。
 */
internal fun RandomPickConfig.candidateScopeDescription(): String = buildString {
    append("范围：").append(scope.label)
    if (includeWantToList) append("，含待探访")
    if (excludeRecentDays > 0) append("，排除 ${randomExcludeDaysLabel(excludeRecentDays)}吃过的")
}

/**
 * 「换一家」用的抽选：候选多于一个时保证不与 [previous] 重复。
 *
 * 不做这个保证的话，连续点「换一家」有 1/N 的概率抽出同一家店，
 * 用户读到的是「按钮坏了」而不是「运气不好」。
 */
internal fun <T> List<T>.randomOtherThan(previous: T?): T? = when {
    isEmpty() -> null
    size == 1 -> first()
    else -> filterNot { it == previous }.randomOrNull()
}

private fun RandomScope.accepts(verdict: Verdict): Boolean = when (this) {
    RandomScope.ALL -> true
    RandomScope.RECOMMENDED_AND_OK -> verdict == Verdict.GOOD || verdict == Verdict.MEH
    RandomScope.RECOMMENDED -> verdict == Verdict.GOOD
}

/** 这条用餐时间戳落在 [date] 当天或之后（按 [zone] 判定自然日）。 */
private fun Long.isOnOrAfter(date: LocalDate, zone: ZoneId): Boolean =
    !Instant.ofEpochMilli(this).atZone(zone).toLocalDate().isBefore(date)
