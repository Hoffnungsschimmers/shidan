package com.fanji.mealnote.ui.detail

import com.fanji.mealnote.data.local.DiningRecordWithPhotos

/**
 * 详情页的账本小结(纯逻辑,便于单测)。
 *
 * 该店累计入账金额(分):把每条用餐记录的 `amountMinorUnits` 求和;一条都没入账时返回
 * `null`(与全局账本口径一致:`null` ≠ 0,「没记」不等于「没花」)。
 */
internal fun List<DiningRecordWithPhotos>.totalLedgerMinor(): Long? =
    mapNotNull { it.record.amountMinorUnits }
        .takeIf { it.isNotEmpty() }
        ?.sum()
