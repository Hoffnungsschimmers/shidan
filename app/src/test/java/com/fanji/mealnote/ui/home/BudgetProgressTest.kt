package com.fanji.mealnote.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定每月预算进度计算:未设预算返回 null、超支标记、剩余额(可负)、比例不裁剪。 */
class BudgetProgressTest {

    @Test
    fun noBudgetReturnsNull() {
        assertNull(monthlyBudgetProgress(12345, 0))
        assertNull(monthlyBudgetProgress(12345, -100))
    }

    @Test
    fun nullSpendTreatedAsZero() {
        val p = monthlyBudgetProgress(null, 200000)!!
        assertEquals(0L, p.spentMinor)
        assertEquals(0f, p.fraction, 0.0001f)
        assertEquals(200000L, p.remainingMinor)
        assertFalse(p.overBudget)
    }

    @Test
    fun halfSpent() {
        val p = monthlyBudgetProgress(100000, 200000)!!
        assertEquals(0.5f, p.fraction, 0.0001f)
        assertEquals(100000L, p.remainingMinor)
        assertFalse(p.overBudget)
    }

    @Test
    fun overBudgetHasNegativeRemainingAndFractionAboveOne() {
        val p = monthlyBudgetProgress(250000, 200000)!!
        assertTrue(p.overBudget)
        assertEquals(-50000L, p.remainingMinor)
        assertTrue(p.fraction > 1f)
    }

    @Test
    fun negativeSpendCoercedToZero() {
        val p = monthlyBudgetProgress(-500, 200000)!!
        assertEquals(0L, p.spentMinor)
    }
}
