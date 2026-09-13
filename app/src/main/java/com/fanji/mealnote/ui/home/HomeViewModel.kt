package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 首页列表的状态筛选维度。顺序即界面中标签的展示顺序。 */
enum class HomeFilter(val label: String) {
    WANT_TO_EAT("待探访"),
    EATEN("已用餐"),
    ALL("全部"),
}

/**
 * 首页界面状态。
 *
 * [restaurants] 为已应用筛选与搜索后的结果；`*Count` 系列始终基于**全量数据**计算，
 * 因此筛选标签上的数字不会随当前筛选条件抖动。
 */
data class HomeUiState(
    val restaurants: List<RestaurantEntity> = emptyList(),
    val query: String = "",
    val filter: HomeFilter = HomeFilter.WANT_TO_EAT,
    val totalCount: Int = 0,
    val wantCount: Int = 0,
    val eatenCount: Int = 0,
    val isLoading: Boolean = true,
) {
    /** 当前查询无匹配结果且用户确实输入了关键字，用于区分“空数据”与“搜不到”。 */
    val isSearchMiss: Boolean get() = query.isNotBlank() && restaurants.isEmpty()

    /** 数据源本身为空（尚未创建任何记录）。 */
    val isEmpty: Boolean get() = totalCount == 0
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: MealRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HomeFilter.WANT_TO_EAT)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRestaurants(),
        _query,
        _filter,
    ) { restaurants, query, filter ->
        val keyword = query.trim()
        HomeUiState(
            restaurants = restaurants.filter { it.matches(keyword, filter) },
            query = query,
            filter = filter,
            totalCount = restaurants.size,
            wantCount = restaurants.count { it.status == RestaurantStatus.WANT_TO_EAT },
            eatenCount = restaurants.count { it.status == RestaurantStatus.EATEN },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(filter: HomeFilter) {
        _filter.value = filter
    }

    /**
     * 判断某条餐厅是否同时满足状态筛选与关键字搜索。
     *
     * 关键字同时匹配店名与地址；空关键字视为全部匹配。抽成扩展函数便于单元测试覆盖，
     * 也避免在 combine 的 lambda 中堆叠条件分支。
     */
    private fun RestaurantEntity.matches(keyword: String, filter: HomeFilter): Boolean {
        val matchesStatus = when (filter) {
            HomeFilter.WANT_TO_EAT -> status == RestaurantStatus.WANT_TO_EAT
            HomeFilter.EATEN -> status == RestaurantStatus.EATEN
            HomeFilter.ALL -> true
        }
        if (!matchesStatus) return false
        if (keyword.isEmpty()) return true
        return name.contains(keyword, ignoreCase = true) ||
            address.contains(keyword, ignoreCase = true)
    }

    private companion object {
        /** 界面离开后延迟 5 秒再停止上游订阅，避免旋转屏幕等短暂重建时重新查询数据库。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
