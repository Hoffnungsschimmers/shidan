package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
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
}
