package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus

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

/**
 * 这家店是否同时满足状态筛选与关键字搜索。
 *
 * 关键字按 `trim` 后的原文做子串匹配，**空关键字视为全部匹配**；
 * 英文忽略大小写。[dishNames] 是该店历次记录里填过的餐品名。
 */
internal fun RestaurantEntity.matches(
    keyword: String,
    filter: HomeFilter,
    dishNames: List<String>,
): Boolean {
    val matchesStatus = when (filter) {
        HomeFilter.WANT_TO_EAT -> status == RestaurantStatus.WANT_TO_EAT
        HomeFilter.EATEN -> status == RestaurantStatus.EATEN
        HomeFilter.ALL -> true
    }
    if (!matchesStatus) return false
    if (keyword.isEmpty()) return true
    if (name.contains(keyword, ignoreCase = true)) return true
    if (address.contains(keyword, ignoreCase = true)) return true
    return dishNames.any { it.contains(keyword, ignoreCase = true) }
}
