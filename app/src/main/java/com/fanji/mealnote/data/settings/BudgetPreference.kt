package com.fanji.mealnote.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/** 预算上限:¥1,000,000(与账本单笔上限同量级),防止误输入天文数字撑坏进度显示。 */
internal const val MAX_BUDGET_MINOR = 100_000_000L

/**
 * 把用户输入的「元」文本解析为整数分。
 *
 * 空 / 非数字 / 非正数一律返回 `0`(视为「清除预算」);去掉 `¥` 与千分位逗号;
 * 四舍五入到分;并封顶到 [MAX_BUDGET_MINOR]。纯函数,便于单测。
 */
internal fun parseBudgetYuanToMinor(text: String): Long {
    val cleaned = text.trim()
        .removePrefix("¥")
        .replace(",", "")
        .replace("，", "")
        .trim()
    val value = cleaned.toBigDecimalOrNull() ?: return 0L
    if (value <= BigDecimal.ZERO) return 0L
    val minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    return minor.coerceIn(0L, MAX_BUDGET_MINOR)
}

/**
 * 每月吃饭预算的持久化。
 *
 * 与 [RandomPreference] / [MotionPreference] 同一模式：`SharedPreferences` + `StateFlow`，
 * 零新依赖,存在同一个 `mealnote_settings` 文件里。
 *
 * ## 口径
 *
 * - 金额一律存**整数分**(与账本 `amountMinorUnits` 同口径),避免浮点误差进入设置;
 * - `0` 表示**未设置预算**(不显示进度条),与「预算为 0 元」不做区分——预算 0 元没有意义;
 * - 预算只在**应用内**展示(足迹·账本卡的进度条),**刻意不做系统通知**:通知需要
 *   `POST_NOTIFICATIONS` 权限,与本应用「唯一权限 INTERNET」的边界冲突,不值得为一个
 *   提醒引入通知权限与后台调度。
 */
@Singleton
class BudgetPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _monthlyBudgetMinor = MutableStateFlow(readBudget())

    /** 每月预算(分)。0 表示未设置。 */
    val monthlyBudgetMinor: StateFlow<Long> = _monthlyBudgetMinor.asStateFlow()

    /** 设置每月预算(分)。负数归零(视为清除);无变化则跳过写入。 */
    fun setMonthlyBudgetMinor(value: Long) {
        val next = value.coerceAtLeast(0L)
        if (next == _monthlyBudgetMinor.value) return
        preferences.edit { putLong(KEY_MONTHLY_BUDGET, next) }
        _monthlyBudgetMinor.value = next
    }

    private fun readBudget(): Long =
        preferences.getLong(KEY_MONTHLY_BUDGET, 0L).coerceAtLeast(0L)

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_MONTHLY_BUDGET = "monthly_budget_minor"
    }
}
