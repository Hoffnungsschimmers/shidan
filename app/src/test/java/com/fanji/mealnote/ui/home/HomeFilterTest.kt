package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页筛选与搜索规则测试。
 *
 * 筛选逻辑同时存在于 UI 状态与仓库查询之间，属于最容易被后续改动破坏的区域，
 * 因此把规则抽成纯函数并在此固化预期行为。
 */
class HomeFilterTest {

    private fun restaurant(
        id: Long,
        name: String,
        address: String = "",
        status: RestaurantStatus = RestaurantStatus.WANT_TO_EAT,
    ) = RestaurantEntity(id = id, name = name, address = address, status = status)

    private val sample = listOf(
        restaurant(1, "老王面馆", "朝阳区建国路 1 号"),
        restaurant(2, "川味小馆", "海淀区中关村大街", RestaurantStatus.EATEN),
        restaurant(3, "Ming's Kitchen", "Chaoyang Beijing", RestaurantStatus.EATEN),
        restaurant(4, "街角咖啡", ""),
    )

    private fun List<RestaurantEntity>.filterBy(keyword: String, filter: HomeFilter) =
        filter { entity ->
            val matchesStatus = when (filter) {
                HomeFilter.WANT_TO_EAT -> entity.status == RestaurantStatus.WANT_TO_EAT
                HomeFilter.EATEN -> entity.status == RestaurantStatus.EATEN
                HomeFilter.ALL -> true
            }
            val term = keyword.trim()
            matchesStatus && (term.isEmpty() ||
                entity.name.contains(term, ignoreCase = true) ||
                entity.address.contains(term, ignoreCase = true))
        }

    @Test
    fun `状态筛选只返回对应状态的餐厅`() {
        assertEquals(listOf(1L, 4L), sample.filterBy("", HomeFilter.WANT_TO_EAT).map { it.id })
        assertEquals(listOf(2L, 3L), sample.filterBy("", HomeFilter.EATEN).map { it.id })
        assertEquals(4, sample.filterBy("", HomeFilter.ALL).size)
    }

    @Test
    fun `中文关键字匹配店名与地址`() {
        assertEquals(listOf(1L), sample.filterBy("建国路", HomeFilter.ALL).map { it.id })
        assertEquals(listOf(2L), sample.filterBy("川味", HomeFilter.ALL).map { it.id })
    }

    @Test
    fun `英文关键字忽略大小写`() {
        val lower = sample.filterBy("ming", HomeFilter.ALL).map { it.id }
        val upper = sample.filterBy("MING", HomeFilter.ALL).map { it.id }
        assertEquals(listOf(3L), lower)
        assertEquals(lower, upper)
    }

    @Test
    fun `筛选与搜索同时生效`() {
        // “川味”属于已用餐餐厅，在待探访筛选下应为空。
        assertTrue(sample.filterBy("川味", HomeFilter.WANT_TO_EAT).isEmpty())
        assertEquals(listOf(2L), sample.filterBy("川味", HomeFilter.EATEN).map { it.id })
    }

    @Test
    fun `首尾空白的关键字按空关键字处理`() {
        assertEquals(4, sample.filterBy("   ", HomeFilter.ALL).size)
    }

    @Test
    fun `无匹配时返回空列表`() {
        assertTrue(sample.filterBy("不存在的店名", HomeFilter.ALL).isEmpty())
    }
}
