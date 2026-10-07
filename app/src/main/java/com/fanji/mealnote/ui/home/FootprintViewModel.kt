package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.ShareImageStore
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.PhotoEntity
import com.fanji.mealnote.data.local.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 时间线上的一条用餐记录。
 *
 * 把店名与地址**在 ViewModel 里摊平**，而不是在界面里再去查一次餐厅：
 * 界面层不应该持有「记录 -> 餐厅」的关联知识，否则每次改数据结构都要改 UI。
 */
@Immutable
data class FootprintEntry(
    val record: DiningRecordEntity,
    val restaurantName: String,
    val restaurantAddress: String,
    val photos: List<PhotoEntity>,
)

/** 按月份聚合的时间线分组。 */
@Immutable
data class FootprintSection(
    val title: String,
    val entries: List<FootprintEntry>,
)

/** 月度用餐次数，用于柱状图。 */
@Immutable
data class MonthlyCount(
    /** `YearMonth` 的字符串形式，用作列表 key（保证跨年时不重复）。 */
    val yearMonth: String,
    /** 轴标签，形如 `9月`。同一组 12 个月里不会重复，因此不必带年份。 */
    val label: String,
    val count: Int,
)

/** 「常去的店」排行项。 */
@Immutable
data class RestaurantRank(
    val restaurantId: Long,
    val name: String,
    val count: Int,
)

/** 足迹时间线的评价筛选维度。顺序即界面分段顺序。 */
enum class VerdictFilter(val label: String) {
    ALL("全部"),
    GOOD("推荐"),
    MEH("尚可"),
    BAD("不推荐"),
}

/**
 * 「足迹」界面状态。
 *
 * 统计数字（`*Count`、`monthlyCounts` 等）基于**全量数据**而非筛选结果计算，
 * 与「清单」页的统计口径保持一致：筛选不应让概览数字抖动。
 */
@Immutable
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
    /** 本月的用餐次数。 */
    val monthCount: Int = 0,
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
    // ------------------------------------------------------------ 账本（入账金额口径）
    /** 今年的入账总额（分）；没有任何入账记录时为 `null`（区别于 0）。 */
    val ledgerSpendThisYear: Long? = null,
    /** 本月的入账总额（分）；同上。 */
    val ledgerSpendThisMonth: Long? = null,
    /** 平均每笔入账金额（分）；无入账记录时为 `null`。 */
    val ledgerAverageAmount: Long? = null,
    /** 最近 12 个月的入账金额，从最早到最新。 */
    val ledgerMonthlyAmounts: List<MonthlyAmount> = emptyList(),
    /** 花钱最多的店，按累计入账金额倒序，最多 5 家。 */
    val topSpendRestaurants: List<RestaurantSpend> = emptyList(),
    /** 今年的入账覆盖情况（已入账几条 / 写了花费未入账几条）。 */
    val ledgerCoverage: LedgerCoverage = LedgerCoverage(0, 0),
    /** 每月预算（分）；0 表示未设置，界面不显示预算进度。 */
    val monthlyBudgetMinor: Long = 0L,
    /** 历年汇总（次数/入账/估算），按年份倒序；用于「历年」卡片。 */
    val yearlySummaries: List<YearSummary> = emptyList(),
    /** 每年的「年度回顾」分享数据，按年份倒序。 */
    val yearReviews: List<com.fanji.mealnote.ui.components.YearReviewCardData> = emptyList(),
    /** 时间线的评价筛选(仅影响时间线,不影响统计)。 */
    val verdictFilter: VerdictFilter = VerdictFilter.ALL,
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
    budgetPreference: com.fanji.mealnote.data.settings.BudgetPreference,
    private val shareImageStore: ShareImageStore,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _verdictFilter = MutableStateFlow(VerdictFilter.ALL)

    /** 年度回顾分享图写好后的 URI(一次性事件);界面消费后调用 [consumeShareUri] 复位。 */
    private val _pendingShareUri = MutableStateFlow<Uri?>(null)
    val pendingShareUri: StateFlow<Uri?> = _pendingShareUri.asStateFlow()

    /**
     * 全量条目 + 已算好的统计。
     *
     * 统计聚合(12 个月柱状图、排行榜、账本汇总等)只依赖数据库数据,与搜索词无关。
     * 把它们放在**不含 [_query]** 的上游 combine 里,搜索时每敲一个字只需重新过滤时间线,
     * 不再重复跑一整套聚合——记录多时这是搜索卡顿的主因。
     */
    private data class FootprintData(
        val all: List<FootprintEntry>,
        val base: FootprintUiState,
    )

    private val data: kotlinx.coroutines.flow.Flow<FootprintData> = combine(
        repository.observeRestaurants(),
        repository.observeAllDiningRecords(),
        repository.observeAllPhotos(),
        budgetPreference.monthlyBudgetMinor,
    ) { restaurants, records, photos, monthlyBudgetMinor ->
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

        // 时钟与时区在这里读取一次并向下传递，聚合逻辑本身保持纯函数（见 FootprintAggregation.kt）。
        // 每次数据变化都重新读时钟，因此应用跨年驻留后台后统计会自动切到新年度。
        val today = YearMonth.now()
        val zone = ZoneId.systemDefault()
        val currentYear = today.year

        FootprintData(
            all = all,
            base = FootprintUiState(
                sections = emptyList(),
                query = "",
                isLoading = false,
                totalCount = all.size,
                goodCount = all.count { it.record.verdict == Verdict.GOOD },
                mehCount = all.count { it.record.verdict == Verdict.MEH },
                badCount = all.count { it.record.verdict == Verdict.BAD },
                yearCount = all.count { it.isInYear(currentYear, zone) },
                monthCount = all.countInMonth(today, zone),
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
                ledgerSpendThisYear = all.ledgerSpendIn(currentYear, zone),
                ledgerSpendThisMonth = all.ledgerSpendInMonth(today, zone),
                ledgerAverageAmount = all.ledgerAverageAmount(),
                ledgerMonthlyAmounts = all.ledgerMonthlyAmounts(today, zone),
                topSpendRestaurants = all.toTopSpendRestaurants(),
                ledgerCoverage = all.ledgerCoverageIn(currentYear, zone),
                monthlyBudgetMinor = monthlyBudgetMinor,
                yearlySummaries = all.yearlySummaries(zone),
                yearReviews = all.yearReviews(zone),
            ),
        )
    }

    val uiState: StateFlow<FootprintUiState> = combine(data, _query, _verdictFilter) { d, query, verdictFilter ->
        val term = query.trim()
        val visible = d.all
            .let { if (term.isEmpty()) it else it.filter { entry -> entry.matchesQuery(term) } }
            .filterByVerdict(verdictFilter)
        d.base.copy(
            sections = visible.toSections(),
            query = query,
            verdictFilter = verdictFilter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FootprintUiState(),
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onVerdictFilterChange(filter: VerdictFilter) {
        _verdictFilter.value = filter
    }

    /** 把年度回顾卡片位图写盘并生成分享 URI。文件名带时间戳,避免覆盖仍被接收方读取的图。 */
    fun shareYearReview(bitmap: Bitmap) {
        viewModelScope.launch {
            val uri = shareImageStore.writeShareImage(
                bitmap,
                "mealnote-year-${System.currentTimeMillis()}.png",
            )
            _pendingShareUri.value = uri
        }
    }

    fun consumeShareUri() {
        _pendingShareUri.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
