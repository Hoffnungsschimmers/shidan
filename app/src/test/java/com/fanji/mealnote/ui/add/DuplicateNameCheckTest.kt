package com.fanji.mealnote.ui.add

import com.fanji.mealnote.data.local.RestaurantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 锁定重复店名检测:归一化(去空白/压空格/小写)与命中/未命中。 */
class DuplicateNameCheckTest {

    private fun stores(vararg names: String) =
        names.mapIndexed { i, n -> RestaurantEntity(id = (i + 1).toLong(), name = n) }

    @Test
    fun normalizeTrimsCollapsesAndLowercases() {
        assertEquals("kfc 三里屯", normalizeRestaurantName("  KFC   三里屯 "))
        assertEquals("老王面馆", normalizeRestaurantName("老王面馆"))
    }

    @Test
    fun findsExactAndCaseInsensitiveDuplicate() {
        val list = stores("老王面馆", "KFC")
        assertEquals("老王面馆", list.findDuplicateName("老王面馆")?.name)
        assertEquals("KFC", list.findDuplicateName("kfc")?.name)
    }

    @Test
    fun toleratesSurroundingAndRepeatedWhitespace() {
        val list = stores("老王 面馆")
        assertEquals("老王 面馆", list.findDuplicateName("  老王   面馆 ")?.name)
    }

    @Test
    fun noDuplicateReturnsNull() {
        val list = stores("老王面馆")
        assertNull(list.findDuplicateName("海底捞"))
    }

    @Test
    fun emptyInputOrEmptyListReturnsNull() {
        assertNull(stores("老王面馆").findDuplicateName(""))
        assertNull(stores("老王面馆").findDuplicateName("   "))
        assertNull(emptyList<RestaurantEntity>().findDuplicateName("老王面馆"))
    }

    @Test
    fun differentBranchesAreNotFalsePositives() {
        val list = stores("KFC 三里屯")
        assertNull(list.findDuplicateName("KFC 望京"))
    }
}
