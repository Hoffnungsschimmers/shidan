package com.fanji.mealnote.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定预算金额文本→分的解析:清除语义、千分位/全角逗号、四舍五入、封顶。 */
class BudgetPreferenceParseTest {

    @Test
    fun blankOrZeroOrNegativeClearsBudget() {
        assertEquals(0L, parseBudgetYuanToMinor(""))
        assertEquals(0L, parseBudgetYuanToMinor("   "))
        assertEquals(0L, parseBudgetYuanToMinor("0"))
        assertEquals(0L, parseBudgetYuanToMinor("-5"))
        assertEquals(0L, parseBudgetYuanToMinor("abc"))
    }

    @Test
    fun parsesPlainYuanToMinor() {
        assertEquals(200000L, parseBudgetYuanToMinor("2000"))
        assertEquals(200050L, parseBudgetYuanToMinor("2000.5"))
        assertEquals(1L, parseBudgetYuanToMinor("0.01"))
    }

    @Test
    fun stripsCurrencySymbolAndThousandsSeparators() {
        assertEquals(123400L, parseBudgetYuanToMinor("¥1,234"))
        assertEquals(123400L, parseBudgetYuanToMinor("1，234")) // 全角逗号
    }

    @Test
    fun roundsHalfUpToFen() {
        assertEquals(1L, parseBudgetYuanToMinor("0.005"))
    }

    @Test
    fun cappedAtMaximum() {
        assertEquals(MAX_BUDGET_MINOR, parseBudgetYuanToMinor("99999999"))
    }
}
