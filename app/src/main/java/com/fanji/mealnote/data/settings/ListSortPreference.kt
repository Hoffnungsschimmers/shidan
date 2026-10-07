package com.fanji.mealnote.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 清单排序方式。
 *
 * 常量名写进 `SharedPreferences`(名字即持久化编码,禁止直接改名,否则老用户设置回落默认)。
 */
enum class RestaurantSort(val label: String) {
    /** 最近有变动(新增/编辑/新记录)的店在前。默认,与旧行为一致。 */
    RECENT_UPDATED("最近更新"),

    /** 最近添加进清单的店在前。 */
    RECENT_ADDED("最近添加"),

    /** 按店名(中文按拼音/笔画由 Collator 决定,英文按字典序)。 */
    NAME("按名称"),
}

/**
 * 清单排序偏好的持久化。与其它偏好同一模式:`SharedPreferences` + `StateFlow`,零新依赖,
 * 复用 `mealnote_settings` 文件。
 */
@Singleton
class ListSortPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _sort = MutableStateFlow(readSort())

    val sort: StateFlow<RestaurantSort> = _sort.asStateFlow()

    fun setSort(value: RestaurantSort) {
        if (_sort.value == value) return
        preferences.edit { putString(KEY_SORT, value.name) }
        _sort.value = value
    }

    /** 未知常量名(手改存档/降级)回落默认档,不让设置坏掉。 */
    private fun readSort(): RestaurantSort =
        preferences.getString(KEY_SORT, null)
            ?.let { name -> RestaurantSort.entries.firstOrNull { it.name == name } }
            ?: RestaurantSort.RECENT_UPDATED

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_SORT = "list_sort"
    }
}
