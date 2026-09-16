package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict

/**
 * 「照上次再来一份」要带入表单的字段。
 *
 * 这个类型**刻意只有三个字段** —— 它是一个结构性约束，不是随手省略：
 * 日期、备注、照片都不在其中，因此不可能被误带入。
 *
 * ## 为什么只带这三个
 *
 * | 字段 | 带入 | 理由 |
 * | --- | --- | --- |
 * | 评价 | ✅ | 常客对同一家店的判断通常稳定，改一下比从头选快 |
 * | 餐品 | ✅ | 「还是那几样」是最常见的复购形态 |
 * | 花费 | ✅ | 同一家店同几样菜，价格基本一致 |
 * | 日期 | ❌ | 这是**新的一次**用餐，必须用「现在」而不是上次那天 |
 * | 备注 | ❌ | 多为当次的具体感受（「今天排队很久」），带过来是误导 |
 * | 照片 | ❌ | 属于上一次的现场；带入会让两处引用同一批文件 |
 */
internal data class VisitPrefill(
    val verdict: Verdict,
    val dishes: String,
    val priceText: String,
)

/**
 * 从来源记录提取预填内容。
 *
 * @param source 来源记录；为 null（已被删除）时返回 null。
 * @param targetRestaurantId 表单当前所属的餐厅。
 * @return null 表示来源不可用，调用方应保持表单原样。
 *
 * **必须校验餐厅归属**：`recordId` 来自路由参数，若与目标餐厅不一致
 * （例如用户先打开了 A 店的表单、又快速切到 B 店），把 A 店的餐品写进
 * B 店的表单会产出一条「看着正常、内容却是错的」记录。
 */
internal fun prefillFromRecord(
    source: DiningRecordEntity?,
    targetRestaurantId: Long,
): VisitPrefill? {
    if (source == null) return null
    if (source.restaurantId != targetRestaurantId) return null
    return VisitPrefill(
        verdict = source.verdict,
        dishes = source.dishes,
        priceText = source.priceText,
    )
}
