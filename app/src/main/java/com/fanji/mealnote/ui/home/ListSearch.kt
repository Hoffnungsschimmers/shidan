package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.settings.RestaurantSort
import java.text.Collator
import java.util.Locale

/**
 * 「清单」页的筛选与搜索规则。
 *
 * ## 为什么从 ViewModel 里抽出来
 *
 * 这段逻辑原先是 `HomeViewModel` 的私有扩展函数，测试结果很糟：
 * `HomeFilterTest` 无法调用它，只好在测试里**照抄一份**同样的 `when` 分支。
 * 那份副本跑得通不证明生产代码是对的——改了 `matches` 而没改副本，
 * 测试会照常全绿，而这正是最坏的一种「有测试」的错觉。
 *
 * ## 为什么搜索要覆盖餐品名
 *
 * 「上次那个小龙虾」是真实的查找入口，但店名可能是「老王面馆」——用户记得的是**吃了什么**，
 * 不是店叫什么。清单页此前只搜店名与地址，这类回忆只能翻「足迹」。
 * 餐品名与备注都在用餐记录里，清单页本来就已经订阅了记录流（随机池要用），
 * 因此这次扩展**不增加任何数据库查询**。
 *
 * 刻意只到餐品名为止，不含备注：备注里什么都会写，一有店就搜不到的观感会变成
 * 「清单和足迹到底差在哪」。足迹页才是「按任意内容找我吃过的东西」的地方。
 */

/**
 * 按餐厅归并每家店的餐品名（保留记录顺序，不去重）。
 *
 * 空白餐品直接丢弃：老数据里大量记录没填餐品，留在列表里只是让每次搜索多绕几个空串。
 */
internal fun dishNamesByRestaurant(records: List<DiningRecordEntity>): Map<Long, List<String>> {
    if (records.isEmpty()) return emptyMap()
    val dishes = HashMap<Long, MutableList<String>>(records.size)
    records.forEach { record ->
        val text = record.dishes.trim()
        if (text.isNotEmpty()) {
            dishes.getOrPut(record.restaurantId) { mutableListOf() }.add(text)
        }
    }
    return dishes
}

/** 按餐厅统计用餐次数(记录条数)。用于清单里「去过 N 次」的展示,与餐品索引共用同一条记录流。 */
internal fun visitCountsByRestaurant(records: List<DiningRecordEntity>): Map<Long, Int> =
    records.groupingBy { it.restaurantId }.eachCount()

/** 筛选后的店铺与餐品命中解释来自同一次匹配；摘要仅属于当前可见结果。 */
internal data class ListSearchResult(
    val restaurants: List<RestaurantEntity>,
    val dishMatchSummaries: Map<Long, String>,
)

/** 搜索、状态筛选和排序的生产入口；不改变原有排序规则与餐品索引顺序。 */
internal fun List<RestaurantEntity>.searchForList(
    query: String,
    filter: HomeFilter,
    dishNames: Map<Long, List<String>>,
    sort: RestaurantSort,
): ListSearchResult {
    val keyword = query.trim()
    val summaries = mutableMapOf<Long, String>()
    val restaurants = filter { restaurant ->
        val match = restaurant.searchMatch(keyword, filter, dishNames[restaurant.id].orEmpty())
        match?.dishSummary?.let { summaries[restaurant.id] = it }
        match != null
    }.sortedForList(sort)
    return ListSearchResult(restaurants, summaries)
}

/**
 * 这家店是否同时满足状态筛选与关键字搜索。
 *
 * [keyword] 为 `trim` 后的原文，空关键字视为全部匹配；英文忽略大小写。
 * 与可见摘要复用 [searchMatch]，避免「筛选搜到了，解释却用了另一套规则」。
 */
internal fun RestaurantEntity.matches(
    keyword: String,
    filter: HomeFilter,
    dishNames: List<String>,
): Boolean = searchMatch(keyword, filter, dishNames) != null

private data class ListSearchMatch(val dishSummary: String? = null)
private val RestaurantOnlyMatch = ListSearchMatch()

private fun RestaurantEntity.searchMatch(
    keyword: String,
    filter: HomeFilter,
    dishNames: List<String>,
): ListSearchMatch? {
    val matchesStatus = when (filter) {
        HomeFilter.WANT_TO_EAT -> status == RestaurantStatus.WANT_TO_EAT
        HomeFilter.EATEN -> status == RestaurantStatus.EATEN
        HomeFilter.ALL -> true
    }
    if (!matchesStatus) return null
    if (keyword.isEmpty() || name.contains(keyword, ignoreCase = true) ||
        address.contains(keyword, ignoreCase = true)
    ) return RestaurantOnlyMatch
    // 只解释真正命中的第一条餐品记录，店名/地址已经匹配时不展示无关餐品。
    for (dish in dishNames) {
        val matchIndex = dish.indexOf(keyword, ignoreCase = true)
        if (matchIndex >= 0) return ListSearchMatch(dishMatchSummary(dish, matchIndex, keyword))
    }
    return null
}

private val DishSummaryWhitespace = Regex("(?U)\\s+")

/** 有界餐品片段以命中处为中心；UTF-16 下标仅用于定位，截断边界全部按 code point。 */
private fun dishMatchSummary(dish: String, matchIndex: Int, keyword: String): String {
    val limit = 40
    val total = dish.codePointCount(0, dish.length)
    if (total <= limit) return dish.replace(DishSummaryWhitespace, " ").trim()
    val matchStart = dish.codePointCount(0, matchIndex)
    val matchLength = keyword.codePointCount(0, keyword.length)
    // 预留首尾省略号；长关键字尽量保留其开头，短关键字同时保留前后文。
    val contentLimit = limit - 2
    val context = ((contentLimit - matchLength) / 2).coerceAtLeast(0)
    val start = (matchStart - context).coerceIn(0, total - contentLimit)
    val end = start + contentLimit
    val excerpt = dish.substring(
        dish.offsetByCodePoints(0, start),
        dish.offsetByCodePoints(0, end),
    ).replace(DishSummaryWhitespace, " ").trim()
    return (if (start > 0) "…" else "") + excerpt + (if (end < total) "…" else "")
}

/**
 * 按 [mode] 对清单排序。
 *
 * - [RestaurantSort.RECENT_UPDATED]:按 `updatedAt` 倒序(与数据库默认顺序一致);
 * - [RestaurantSort.RECENT_ADDED]:按 `createdAt` 倒序;
 * - [RestaurantSort.NAME]:用 [Collator](中文按本地化顺序,英文按字典序),同名再按 `updatedAt` 倒序稳定。
 *
 * 抽成纯函数便于单测;时间戳相等时都以 `id` 兜底,保证结果稳定不抖动。
 */
internal fun List<RestaurantEntity>.sortedForList(mode: RestaurantSort): List<RestaurantEntity> =
    when (mode) {
        RestaurantSort.RECENT_UPDATED ->
            sortedWith(compareByDescending<RestaurantEntity> { it.updatedAt }.thenByDescending { it.id })
        RestaurantSort.RECENT_ADDED ->
            sortedWith(compareByDescending<RestaurantEntity> { it.createdAt }.thenByDescending { it.id })
        RestaurantSort.NAME -> {
            val collator = Collator.getInstance(Locale.CHINA)
            sortedWith(
                compareBy(collator) { r: RestaurantEntity -> r.name }
                    .thenByDescending { it.updatedAt },
            )
        }
    }

