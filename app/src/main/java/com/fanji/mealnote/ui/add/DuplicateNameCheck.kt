package com.fanji.mealnote.ui.add

import com.fanji.mealnote.data.local.RestaurantEntity

/**
 * 新建店铺时的**重复店名检测**(纯逻辑,便于单测)。
 *
 * 只做「提醒」不做「拦截」:同名分店(「沙县小吃」)是真实存在的,用户仍可执意保存;
 * 但绝大多数重复是「忘了已经记过」,一句提示能省掉一次翻清单。
 *
 * 归一化:去首尾空白 + 把连续空白压成一个空格 + 转小写(英文忽略大小写、容忍多敲的空格),
 * 不做更激进的处理(如删全部空格),以免把「KFC 三里屯」「KFC 望京」这类不同店误判为同名。
 */
internal fun normalizeRestaurantName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").lowercase()

/**
 * 在现有餐厅里找一家与 [input] 同名的(归一化后相等);没有则返回 null。
 * 空输入返回 null(还没输入时不提示)。
 */
internal fun List<RestaurantEntity>.findDuplicateName(input: String): RestaurantEntity? {
    val key = normalizeRestaurantName(input)
    if (key.isEmpty()) return null
    return firstOrNull { normalizeRestaurantName(it.name) == key }
}
