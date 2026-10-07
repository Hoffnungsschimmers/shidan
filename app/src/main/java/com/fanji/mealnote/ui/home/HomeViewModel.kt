package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
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
@Immutable
data class HomeUiState(
    val restaurants: List<RestaurantEntity> = emptyList(),
    val query: String = "",
    val filter: HomeFilter = HomeFilter.WANT_TO_EAT,
    val sortMode: com.fanji.mealnote.data.settings.RestaurantSort =
        com.fanji.mealnote.data.settings.RestaurantSort.RECENT_UPDATED,
    /** 各餐厅用餐次数(restaurantId → 次数),用于清单「去过 N 次」。 */
    val visitCounts: Map<Long, Int> = emptyMap(),
    /**
     * 各餐厅**最近一次**的评价(restaurantId → 评价)。
     *
     * 清单卡片用它回答「这家上次吃得怎么样」——这一眼决定了用户要不要再去,
     * 而原来卡片上只有「待探访 / 已用餐」,去过 8 次的店和去过 1 次的店长得一样。
     */
    val latestVerdicts: Map<Long, Verdict> = emptyMap(),
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

    /** 全部用餐记录条数（各店次数之和），hero 的「一共几餐」。 */
    val totalVisitCount: Int get() = visitCounts.values.sum()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: MealRepository,
    randomPreference: RandomPreference,
    private val listSortPreference: com.fanji.mealnote.data.settings.ListSortPreference,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(HomeFilter.WANT_TO_EAT)

    /**
     * 与搜索/筛选无关的部分:全量店铺、餐品名索引、各状态计数、随机候选池。
     *
     * 这些只依赖数据库数据与随机设置,放在**不含 [_query]/[_filter]** 的上游 combine 里。
     * 搜索时每敲一个字只重新过滤店铺列表,不再重算随机池与餐品名索引——记录多时这是搜索卡顿的来源。
     */
    private data class HomeData(
        val restaurants: List<RestaurantEntity>,
        val dishNames: Map<Long, List<String>>,
        val visitCounts: Map<Long, Int>,
        val latestVerdicts: Map<Long, Verdict>,
        val totalCount: Int,
        val wantCount: Int,
        val eatenCount: Int,
        val randomCandidates: List<RestaurantEntity>,
        val randomScopeDescription: String,
    )

    private val data: kotlinx.coroutines.flow.Flow<HomeData> = combine(
        repository.observeRestaurants(),
        repository.observeAllDiningRecords(),
        randomPreference.config,
    ) { restaurants, records, randomConfig ->
        // 时钟每次发射只读一次并作为参数传给纯函数：既避免同一帧内两次读到不同的日期
        // 让池子自相矛盾，也让纯函数本身可在测试里固定到任意日期。
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // 随机池与卡片上的「最近评价」共用这一份归并结果，不再各算一遍。
        val latestVisits = latestVisitByRestaurant(records)
        HomeData(
            restaurants = restaurants,
            // 餐品名与随机池用的是同一条记录流，这次扩展没有增加任何数据库查询。
            dishNames = dishNamesByRestaurant(records),
            visitCounts = visitCountsByRestaurant(records),
            latestVerdicts = latestVisits.mapValues { it.value.verdict },
            totalCount = restaurants.size,
            wantCount = restaurants.count { it.status == RestaurantStatus.WANT_TO_EAT },
            eatenCount = restaurants.count { it.status == RestaurantStatus.EATEN },
            randomCandidates = buildRandomCandidatePool(
                restaurants = restaurants,
                latestVisits = latestVisits,
                config = randomConfig,
                today = today,
                zone = zone,
            ),
            randomScopeDescription = randomConfig.candidateScopeDescription(),
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        data,
        _query,
        _filter,
        listSortPreference.sort,
    ) { d, query, filter, sort ->
        val keyword = query.trim()
        HomeUiState(
            restaurants = d.restaurants
                .filter { it.matches(keyword, filter, d.dishNames[it.id].orEmpty()) }
                .sortedForList(sort),
            query = query,
            filter = filter,
            sortMode = sort,
            visitCounts = d.visitCounts,
            latestVerdicts = d.latestVerdicts,
            totalCount = d.totalCount,
            wantCount = d.wantCount,
            eatenCount = d.eatenCount,
            randomCandidates = d.randomCandidates,
            randomScopeDescription = d.randomScopeDescription,
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

    fun onSortChange(mode: com.fanji.mealnote.data.settings.RestaurantSort) =
        listSortPreference.setSort(mode)

    private companion object {
        /** 界面离开后延迟 5 秒再停止上游订阅，避免旋转屏幕等短暂重建时重新查询数据库。 */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
