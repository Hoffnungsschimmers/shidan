package com.fanji.mealnote.ui.edit

import com.fanji.mealnote.data.local.DiningRecordEntity

/** 加载只补齐原照片引用；有恢复草稿时不重写本次输入。 */
internal fun EditVisitUiState.applyExistingVisit(
    record: DiningRecordEntity,
    orderedPaths: List<String>,
    restaurantName: String,
    restored: Boolean,
): EditVisitUiState = if (restored) {
    copy(isLoading = false, restaurantName = restaurantName, originalPhotoPaths = orderedPaths)
} else {
    copy(
        isLoading = false,
        restaurantName = restaurantName,
        dateMillis = record.eatenAt,
        verdict = record.verdict,
        dishes = record.dishes,
        priceText = record.priceText,
        note = record.note,
        photoPaths = orderedPaths,
        originalPhotoPaths = orderedPaths,
        personCount = record.personCount,
        personCountTouched = record.personCount > 1,
        // NULL 同样是已保存的事实；修改其他字段不能让历史记录突然入账。
        amountMinorUnits = record.amountMinorUnits,
        amountOverridden = true,
    )
}
