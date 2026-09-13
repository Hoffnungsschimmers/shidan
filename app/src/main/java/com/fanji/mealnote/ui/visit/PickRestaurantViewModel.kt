package com.fanji.mealnote.ui.visit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.RestaurantEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 选店页状态。
 *
 * [restaurants] 已按「最近去过 / 修改过」排序（上游 `observeRestaurants` 按 `updatedAt` 倒序），
 * 这与真实使用习惯一致：想补记录的多半是刚吃完的那家。
 */
data class PickRestaurantUiState(
    val restaurants: List<RestaurantEntity> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
) {
    val isSearchMiss: Boolean get() = query.isNotBlank() && restaurants.isEmpty()
}

/**
 * 「已经吃过」流程的第一步：确定这次吃的是哪家店。
 *
 * 为什么需要这一页：上一版要记录一餐，必须先进入某家餐厅的详情页再点「新增用餐记录」。
 * 这意味着「刚吃完想马上记一笔」这个最高频的场景，要求用户先想起并找到那家店。
 * 把选店独立成一页后，主流程变成「加号 → 已经吃过 → 选店（或新建）→ 填表」，
 * 且选店页自带搜索，店多的时候也不需要先回列表翻。
 */
@HiltViewModel
class PickRestaurantViewModel @Inject constructor(
    repository: MealRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<PickRestaurantUiState> = combine(
        repository.observeRestaurants(),
        _query,
    ) { restaurants, query ->
        val term = query.trim()
        PickRestaurantUiState(
            restaurants = if (term.isEmpty()) {
                restaurants
            } else {
                restaurants.filter {
                    it.name.contains(term, ignoreCase = true) ||
                        it.address.contains(term, ignoreCase = true)
                }
            },
            query = query,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PickRestaurantUiState(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
