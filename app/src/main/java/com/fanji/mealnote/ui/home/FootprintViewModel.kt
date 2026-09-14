package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.PhotoEntity
import com.fanji.mealnote.data.local.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 时间线上的一条用餐记录。
 *
 * 把店名与地址**在 ViewModel 里摊平**，而不是在界面里再去查一次餐厅：
 * 界面层不应该持有「记录 -> 餐厅」的关联知识，否则每次改数据结构都要改 UI。
 */
data class FootprintEntry(
    val record: DiningRecordEntity,
    val restaurantName: String,
    val restaurantAddress: String,
    val photos: List<PhotoEntity>,
)

/** 按月份聚合的时间线分组。 */
data class FootprintSection(
    val title: String,
    val entries: List<FootprintEntry>,
)

/** 月度用餐次数，用于柱状图。 */
data class MonthlyCount(
    /** `YearMonth` 的字符串形式，用作列表 key（保证跨年时不重复）。 */
    val yearMonth: String,
    /** 轴标签，形如 `9月`。同一组 12 个月里不会重复，因此不必带年份。 */
    val label: String,
    val count: Int,
)

/** 「常去的店」排行项。 */
data class RestaurantRank(
    val restaurantId: Long,
    val name: String,
    val count: Int,
)

/**
 * 「足迹」界面状态。
 *
 * 统计数字（`*Count`、`monthlyCounts` 等）基于**全量数据**而非筛选结果计算，
 * 与「清单」页的统计口径保持一致：筛选不应让概览数字抖动。
 */
data class FootprintUiState(
    val sections: List<FootprintSection> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val goodCount: Int = 0,
    val mehCount: Int = 0,
    val badCount: Int = 0,
    // ------------------------------------------------------------ 统计
    /** 今年的用餐次数。 */
    val yearCount: Int = 0,
    /** 去过的店铺数量（按 restaurantId 去重）。 */
    val visitedCount: Int = 0,
    /** 今年可识别的花费合计；一条都识别不出来时为 `null`。 */
    val estimatedSpendThisYear: Double? = null,
    /** 今年填写了花费且能识别出金额的记录数。 */
    val amountRecognizedCount: Int = 0,
    /** 今年填写了花费但识别不出金额的记录数（如「忘了」「很贵」）。 */
    val amountUnrecognizedCount: Int = 0,
    /** 最近 12 个月的用餐次数，从最早到最新。 */
    val monthlyCounts: List<MonthlyCount> = emptyList(),
    /** 常去的店，按次数倒序，最多 5 家。 */
    val topRestaurants: List<RestaurantRank> = emptyList(),
) {
    /** 用户输入了关键字但没有任何匹配。 */
    val isSearchMiss: Boolean get() = query.isNotBlank() && sections.isEmpty()

    /** 数据源本身为空。 */
    val isEmpty: Boolean get() = totalCount == 0

    /** 统计页是否有任何可展示的内容。 */
    val hasStats: Boolean get() = totalCount > 0
}

/**
 * 「足迹」标签页：以用餐记录为维度的时间线。
 *
 * 与「清单」的分工：本页只关心**每一次吃饭**这件事本身 —— 哪天、哪家店、评价如何、
 * 吃了什么、花了多少。餐厅的静态资料（地址、封面）不在这里编辑。
 */
@HiltViewModel
class FootprintViewModel @Inject constructor(
    repository: MealRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<FootprintUiState> = combine(
        repository.observeRestaurants(),
        repository.observeAllDiningRecords(),
        repository.observeAllPhotos(),
        _query,
    ) { restaurants, records, photos, query ->
        val restaurantById = restaurants.associateBy { it.id }
        val photosByRecord = photos.groupBy { it.diningRecordId }

        // 记录所属餐厅可能已被删除（外键级联本应避免，但导入的备份包不保证一致），
        // 这类记录直接跳过而不是显示成「未知餐厅」。
        val all = records.mapNotNull { record ->
            val restaurant = restaurantById[record.restaurantId] ?: return@mapNotNull null
            FootprintEntry(
                record = record,
                restaurantName = restaurant.name,
                restaurantAddress = restaurant.address,
                photos = photosByRecord[record.id].orEmpty().sortedBy { it.sortOrder },
            )
        }

        val term = query.trim()
        val visible = if (term.isEmpty()) all else all.filter { it.matches(term) }

        // 时钟与时区在这里读取一次并向下传递，聚合逻辑本身保持纯函数（见 FootprintAggregation.kt）。
        // 每次数据变化都重新读时钟，因此应用跨年驻留后台后统计会自动切到新年度。
        val today = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val currentYear = today.year

        FootprintUiState(
            sections = visible.toSections(),
            query = query,
            isLoading = false,
            totalCount = all.size,
            goodCount = all.count { it.record.verdict == Verdict.GOOD },
            mehCount = all.count { it.record.verdict == Verdict.MEH },
            badCount = all.count { it.record.verdict == Verdict.BAD },
            yearCount = all.count { it.isInYear(currentYear, zone) },
            visitedCount = all.map { it.record.restaurantId }.distinct().size,
            estimatedSpendThisYear = all.estimatedSpendIn(currentYear, zone),
            amountRecognizedCount = all.count {
                it.isInYear(currentYear, zone) && it.estimatedAmount() != null
            },
            amountUnrecognizedCount = all.count {
                it.isInYear(currentYear, zone) &&
                    it.record.priceText.isNotBlank() &&
                    it.estimatedAmount() == null
            },
            monthlyCounts = all.toMonthlyCounts(today, zone),
            topRestaurants = all.toTopRestaurants(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FootprintUiState(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    /**
     * 关键字匹配范围。
     *
     * 除店名外还覆盖餐品、花费与备注 —— 用户找一条旧记录时，记得住的往往
     * 是「那次吃了什么」或「花了多少钱」，而不是店名。
     */
    private fun FootprintEntry.matches(keyword: String): Boolean =
        restaurantName.contains(keyword, ignoreCase = true) ||
            restaurantAddress.contains(keyword, ignoreCase = true) ||
            record.dishes.contains(keyword, ignoreCase = true) ||
            record.priceText.contains(keyword, ignoreCase = true) ||
            record.note.contains(keyword, ignoreCase = true)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
