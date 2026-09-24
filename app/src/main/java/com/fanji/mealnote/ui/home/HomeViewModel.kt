package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.settings.RandomPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
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
 *
 * [randomCandidates] 与 [restaurants] **刻意不是同一批数据**：随机池来自全量店铺并按
 * 用户在设置页配置的评价范围筛选，不受当前分段与搜索词影响（见 `RandomPick.kt`）。
 * 为空时界面隐藏随机按钮。
 */
data class HomeUiState(
    val restaurants: List<RestaurantEntity> = emptyList(),
    val query: String = "",
    val filter: HomeFilter = HomeFilter.WANT_TO_EAT,
    val totalCount: Int = 0,
    val wantCount: Int = 0,
    val eatenCount: Int = 0,
    val randomCandidates: List<RestaurantEntity> = emptyList(),
    val randomScopeDescription: String = "",
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
    randomPreference: RandomPreference,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HomeFilter.WANT_TO_EAT)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRestaurants(),
        repository.observeAllDiningRecords(),
        _query,
        _filter,
        randomPreference.config,
    ) { restaurants, records, query, filter, randomConfig ->
        val keyword = query.trim()
        // 时钟每次发射只读一次并作为参数传给纯函数：既避免同一帧内两次读到不同的日期
        // 让池子和列表自相矛盾，也让纯函数本身可在测试里固定到任意日期。
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 餐品名与随机池用的是同一条记录流，这次扩展没有增加任何数据库查询。
        val dishNames = dishNamesByRestaurant(records)
        HomeUiState(
            restaurants = restaurants.filter { it.matches(keyword, filter, dishNames[it.id].orEmpty()) },
            query = query,
            filter = filter,
            totalCount = restaurants.size,
            wantCount = restaurants.count { it.status == RestaurantStatus.WANT_TO_EAT },
            eatenCount = restaurants.count { it.status == RestaurantStatus.EATEN },
            randomCandidates = buildRandomCandidatePool(
                restaurants = restaurants,
                latestVisits = latestVisitByRestaurant(records),
                config = randomConfig,
                today = today,
                zone = zone,
            ),
            randomScopeDescription = randomConfig.candidateScopeDescription(),
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

    private companion object {
        /** 界面离开后延迟 5 秒再停止上游订阅，避免旋转屏幕等短暂重建时重新查询数据库。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
