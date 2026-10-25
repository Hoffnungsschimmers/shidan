package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.MAX_DISHES_LENGTH
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.normalizeText

/** 只复用餐品；历史花费仅作参考，评价、金额、人数、日期、备注、照片不自动带入。 */
internal data class VisitPrefill(
    val dishes: String,
    val referencePriceText: String,
)

internal fun prefillFromRecord(
    source: DiningRecordEntity?,
    targetRestaurantId: Long,
): VisitPrefill? {
    if (source == null || source.restaurantId != targetRestaurantId) return null
    return VisitPrefill(source.dishes, source.priceText)
}

/** 迟到的预填不得覆盖已经编辑或提交的本次记录；日期保持打开表单时的值。 */
internal fun AddVisitUiState.applyVisitPrefill(prefill: VisitPrefill): AddVisitUiState =
    if (prefillTouched || isSaving || saved || isImportingPhotos) this
    else copy(dishes = prefill.dishes, referencePriceText = prefill.referencePriceText)

/** 最近记录优先；只把明确的餐品分隔符视为边界，不把空格切成不同餐品。 */
internal fun recentDishSuggestions(
    records: List<DiningRecordEntity>,
    restaurantId: Long,
): List<String> = records.asSequence()
    .filter { it.restaurantId == restaurantId }
    .sortedWith(compareByDescending<DiningRecordEntity> { it.eatenAt }.thenByDescending { it.id })
    .flatMap { dishParts(it.dishes).asSequence() }
    .distinct()
    .take(8)
    .toList()

internal fun appendRecentDish(current: String, dish: String): String {
    val selected = dish.normalizeText(MAX_DISHES_LENGTH)
    if (selected.isBlank() || selected in dishParts(current)) return current
    return (if (current.isBlank()) selected else "${current.trim()}、$selected")
        .normalizeText(MAX_DISHES_LENGTH)
}

private fun dishParts(text: String): List<String> = text.split('、', '，', ',', '\n', ';', '；')
    .map { it.normalizeText(MAX_DISHES_LENGTH) }
    .filter(String::isNotBlank)
