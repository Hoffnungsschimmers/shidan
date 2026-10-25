package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.data.settings.RestaurantSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页筛选与搜索规则测试。
 *
 * 这里调用的是生产函数 [matches] / [dishNamesByRestaurant]，**不是测试里照抄的副本**。
 * 上一版测试自带一份 `filterBy`，改了生产代码而没改副本时测试会照常全绿——
 * 那份绿毫无意义。逻辑已抽到 `ListSearch.kt`，测试直接依赖它。
 */
class HomeFilterTest {

    private fun restaurant(
        id: Long,
        name: String,
        address: String = "",
        status: RestaurantStatus = RestaurantStatus.WANT_TO_EAT,
    ) = RestaurantEntity(id = id, name = name, address = address, status = status)

    private fun dish(restaurantId: Long, dishes: String) =
        DiningRecordEntity(restaurantId = restaurantId, dishes = dishes, verdict = Verdict.GOOD)

    private val sample = listOf(
        restaurant(1, "老王面馆", "朝阳区建国路 1 号"),
        restaurant(2, "川味小馆", "海淀区中关村大街", RestaurantStatus.EATEN),
        restaurant(3, "Ming's Kitchen", "Chaoyang Beijing", RestaurantStatus.EATEN),
        restaurant(4, "街角咖啡", ""),
    )

    /** 店 2 记过小龙虾，店 3 记过英文菜名；店 1 与店 4 还没有用餐记录。 */
    private val records = listOf(
        dish(2, "麻辣小龙虾、冰镇酸梅汤"),
        dish(2, "宫保鸡丁"),
        dish(3, "Salted Pepper Crab"),
        dish(2, "   "),
    )

    private val dishNames = dishNamesByRestaurant(records)

    private fun List<RestaurantEntity>.filterBy(
        keyword: String,
        filter: HomeFilter,
        byRestaurant: Map<Long, List<String>> = dishNames,
    ) = filter { entity ->
        entity.matches(keyword.trim(), filter, byRestaurant[entity.id].orEmpty())
    }

    private fun idsIn(list: List<RestaurantEntity>) = list.map { it.id }

    // ------------------------------------------------------------------ 归并餐品

    @Test
    fun `按店归并餐品名并保留多条记录`() {
        assertEquals(
            listOf("麻辣小龙虾、冰镇酸梅汤", "宫保鸡丁"),
            dishNames.getValue(2L),
        )
        assertEquals(listOf("Salted Pepper Crab"), dishNames.getValue(3L))
    }

    @Test
    fun `空白餐品不进索引`() {
        // records 里那条全空格的餐品如果留下，每次搜索都要多比一个空串；
        // 更关键的是空串 contains 任意关键字的反向误判风险不该靠运气避开。
        assertEquals(2, dishNames.getValue(2L).size)
    }

    @Test
    fun `没有用餐记录时索引为空`() {
        assertTrue(dishNamesByRestaurant(emptyList()).isEmpty())
        assertFalse(dishNames.containsKey(1L))
    }

    // -------------------------------------------------------------- 状态与关键字

    @Test
    fun `状态筛选只返回对应状态的餐厅`() {
        assertEquals(listOf(1L, 4L), idsIn(sample.filterBy("", HomeFilter.WANT_TO_EAT)))
        assertEquals(listOf(2L, 3L), idsIn(sample.filterBy("", HomeFilter.EATEN)))
        assertEquals(4, sample.filterBy("", HomeFilter.ALL).size)
    }

    @Test
    fun `中文关键字匹配店名与地址`() {
        assertEquals(listOf(1L), idsIn(sample.filterBy("建国路", HomeFilter.ALL)))
        assertEquals(listOf(2L), idsIn(sample.filterBy("川味", HomeFilter.ALL)))
    }

    @Test
    fun `英文关键字忽略大小写`() {
        val lower = idsIn(sample.filterBy("ming", HomeFilter.ALL))
        val upper = idsIn(sample.filterBy("MING", HomeFilter.ALL))
        assertEquals(listOf(3L), lower)
        assertEquals(lower, upper)
    }

    @Test
    fun `筛选与搜索同时生效`() {
        // “川味”属于已用餐餐厅，在待探访筛选下应为空。
        assertTrue(sample.filterBy("川味", HomeFilter.WANT_TO_EAT).isEmpty())
        assertEquals(listOf(2L), idsIn(sample.filterBy("川味", HomeFilter.EATEN)))
    }

    @Test
    fun `首尾空白的关键字按空关键字处理`() {
        assertEquals(4, sample.filterBy("   ", HomeFilter.ALL).size)
    }

    @Test
    fun `无匹配时返回空列表`() {
        assertTrue(sample.filterBy("不存在的店名", HomeFilter.ALL).isEmpty())
    }

    // ------------------------------------------------------------ 餐品名可搜索

    @Test
    fun `搜餐品能命中店名里没有的店`() {
        // 「上次那个小龙虾」——店叫「川味小馆」，不含「小龙虾」三个字。
        assertEquals(listOf(2L), idsIn(sample.filterBy("小龙虾", HomeFilter.ALL)))
    }

    @Test
    fun `餐品关键字同样忽略大小写`() {
        assertEquals(listOf(3L), idsIn(sample.filterBy("crab", HomeFilter.ALL)))
        assertEquals(listOf(3L), idsIn(sample.filterBy("CRAB", HomeFilter.ALL)))
    }

    @Test
    fun `餐品搜索受状态筛选约束`() {
        // 待探访视图下搜「小龙虾」应为空：那家店已经去过了，不该出现在「还没去」的列表里。
        assertTrue(sample.filterBy("小龙虾", HomeFilter.WANT_TO_EAT).isEmpty())
        assertEquals(listOf(2L), idsIn(sample.filterBy("小龙虾", HomeFilter.EATEN)))
    }

    @Test
    fun `没有餐品的店不会因为别人记过菜而被搜到`() {
        val only = listOf(restaurant(1, "老王面馆", "朝阳区建国路 1 号", RestaurantStatus.EATEN))
        assertTrue(only.filterBy("小龙虾", HomeFilter.ALL).isEmpty())
    }

    // ------------------------------------------------------------ 可见命中解释

    @Test
    fun `餐品匹配返回可见店铺和真实命中摘要`() {
        val result = sample.searchForList("小龙虾", HomeFilter.ALL, dishNames, RestaurantSort.RECENT_UPDATED)
        assertEquals(listOf(2L), idsIn(result.restaurants))
        assertEquals(mapOf(2L to "麻辣小龙虾、冰镇酸梅汤"), result.dishMatchSummaries)
    }

    @Test
    fun `摘要取第一条真正命中的餐品且保持记录顺序`() {
        val dishes = dishNamesByRestaurant(listOf(
            dish(2, "宫保鸡丁"),
            dish(2, "蒜香小龙虾"),
            dish(2, "麻辣小龙虾"),
        ))
        val result = sample.searchForList("小龙虾", HomeFilter.ALL, dishes, RestaurantSort.RECENT_UPDATED)
        assertEquals(mapOf(2L to "蒜香小龙虾"), result.dishMatchSummaries)
    }

    @Test
    fun `店名匹配优先不额外展示餐品`() {
        val stores = listOf(restaurant(2, "小龙虾之家"))
        val result = stores.searchForList("小龙虾", HomeFilter.ALL, dishNames, RestaurantSort.RECENT_UPDATED)
        assertEquals(listOf(2L), idsIn(result.restaurants))
        assertTrue(result.dishMatchSummaries.isEmpty())
    }

    @Test
    fun `地址匹配优先不额外展示餐品`() {
        val stores = listOf(restaurant(2, "川味小馆", "小龙虾街 1 号"))
        val result = stores.searchForList("小龙虾", HomeFilter.ALL, dishNames, RestaurantSort.RECENT_UPDATED)
        assertEquals(listOf(2L), idsIn(result.restaurants))
        assertTrue(result.dishMatchSummaries.isEmpty())
    }

    @Test
    fun `空查询和清除后的空白查询都没有摘要`() {
        for (query in listOf("", "   ", "　")) {
            val result = sample.searchForList(query, HomeFilter.ALL, dishNames, RestaurantSort.RECENT_UPDATED)
            assertEquals(4, result.restaurants.size)
            assertTrue(result.dishMatchSummaries.isEmpty())
        }
    }

    @Test
    fun `无命中和被状态筛选排除都不留下摘要`() {
        for ((query, filter) in listOf("不存在" to HomeFilter.ALL, "小龙虾" to HomeFilter.WANT_TO_EAT)) {
            val result = sample.searchForList(query, filter, dishNames, RestaurantSort.RECENT_UPDATED)
            assertTrue(result.restaurants.isEmpty())
            assertTrue(result.dishMatchSummaries.isEmpty())
        }
    }

    @Test
    fun `摘要和搜索共用首尾去空白及英文忽略大小写规则`() {
        val result = sample.searchForList("  CRAB　", HomeFilter.EATEN, dishNames, RestaurantSort.RECENT_UPDATED)
        assertEquals(listOf(3L), idsIn(result.restaurants))
        assertEquals(mapOf(3L to "Salted Pepper Crab"), result.dishMatchSummaries)
    }

    @Test
    fun `数据更新后摘要重新派生且删除后不残留`() {
        val updated = sample.searchForList("小龙虾", HomeFilter.ALL,
            dishNamesByRestaurant(listOf(dish(2, "蒜香小龙虾"))), RestaurantSort.RECENT_UPDATED)
        assertEquals(mapOf(2L to "蒜香小龙虾"), updated.dishMatchSummaries)
        val removedDish = sample.searchForList("小龙虾", HomeFilter.ALL, emptyMap(), RestaurantSort.RECENT_UPDATED)
        assertTrue(removedDish.restaurants.isEmpty())
        assertTrue(removedDish.dishMatchSummaries.isEmpty())
        val removedStore = sample.filter { it.id != 2L }
            .searchForList("小龙虾", HomeFilter.ALL, dishNames, RestaurantSort.RECENT_UPDATED)
        assertTrue(removedStore.dishMatchSummaries.isEmpty())
    }

    private fun summaryFor(dishes: String, query: String): String =
        sample.searchForList(query, HomeFilter.ALL, mapOf(2L to listOf(dishes)), RestaurantSort.RECENT_UPDATED)
            .dishMatchSummaries.getValue(2L)

    private fun assertBoundedSummary(summary: String) {
        assertTrue(summary.codePointCount(0, summary.length) <= 40)
        assertTrue(summary.codePoints().noneMatch { it in 0xD800..0xDFFF })
    }

    @Test
    fun `摘要截取命中处前后文且不切碎emoji`() {
        val summary = summaryFor("😋".repeat(50) + "小龙虾" + "🦞".repeat(50), "小龙虾")
        assertTrue(summary.contains("小龙虾"))
        assertTrue(summary.startsWith("…"))
        assertTrue(summary.endsWith("…"))
        assertEquals(40, summary.codePointCount(0, summary.length))
        assertBoundedSummary(summary)
    }

    @Test
    fun `长餐品末尾命中仍出现在摘要且没有虚假尾省略号`() {
        val summary = summaryFor("😋".repeat(80) + "麻辣小龙虾", "小龙虾")
        assertTrue(summary.startsWith("…"))
        assertTrue(summary.endsWith("麻辣小龙虾"))
        assertBoundedSummary(summary)
    }

    @Test
    fun `长餐品开头命中不加虚假首省略号`() {
        val summary = summaryFor("小龙虾" + "😋".repeat(80), "小龙虾")
        assertTrue(summary.startsWith("小龙虾"))
        assertTrue(summary.endsWith("…"))
        assertBoundedSummary(summary)
    }

    @Test
    fun `超长emoji查询的摘要仍有界且是合法UTF16`() {
        val query = "😋".repeat(60)
        val summary = summaryFor(query + "冰镇酸梅汤", query)
        assertTrue(summary.startsWith("😋"))
        assertBoundedSummary(summary)
    }

    @Test
    fun `多行餐品摘要折叠空白但不改变原文搜索谓词`() {
        val dishes = mapOf(2L to listOf("麻辣\n\n　小龙虾\t酸梅汤"))
        val result = sample.searchForList("小龙虾", HomeFilter.ALL, dishes, RestaurantSort.RECENT_UPDATED)
        assertEquals(mapOf(2L to "麻辣 小龙虾 酸梅汤"), result.dishMatchSummaries)
        val joined = sample.searchForList("麻辣小龙虾", HomeFilter.ALL, dishes, RestaurantSort.RECENT_UPDATED)
        assertTrue(joined.restaurants.isEmpty())
        assertTrue(joined.dishMatchSummaries.isEmpty())
    }

    @Test
    fun `刚好四十个codepoint的餐品不截断`() {
        val dishes = "😋".repeat(37) + "小龙虾"
        assertEquals(dishes, summaryFor(dishes, "小龙虾"))
    }

    @Test
    fun `新搜索入口保持原搜索筛选和全部排序模式结果`() {
        val stores = listOf(
            RestaurantEntity(id = 1, name = "C", address = "Crab Road", status = RestaurantStatus.EATEN,
                createdAt = 300, updatedAt = 100),
            RestaurantEntity(id = 2, name = "A", status = RestaurantStatus.WANT_TO_EAT,
                createdAt = 100, updatedAt = 300),
            RestaurantEntity(id = 3, name = "B", status = RestaurantStatus.EATEN,
                createdAt = 200, updatedAt = 200),
        )
        val dishes = mapOf(2L to listOf("Pepper Crab"), 3L to listOf("Salted Crab"))
        for (filter in HomeFilter.entries) {
            for (sort in RestaurantSort.entries) {
                for (query in listOf("", "  CRAB ", "不存在")) {
                    val expected = stores.filterBy(query, filter, dishes).sortedForList(sort)
                    val actual = stores.searchForList(query, filter, dishes, sort)
                    assertEquals(expected, actual.restaurants)
                    assertTrue(actual.dishMatchSummaries.keys.all { id -> actual.restaurants.any { it.id == id } })
                }
            }
        }
        val all = stores.searchForList("crab", HomeFilter.ALL, dishes, RestaurantSort.RECENT_UPDATED)
        assertEquals(listOf(2L, 3L, 1L), idsIn(all.restaurants))
        assertEquals(mapOf(2L to "Pepper Crab", 3L to "Salted Crab"), all.dishMatchSummaries)
    }
}
