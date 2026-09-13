package com.fanji.mealnote.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.PhotoEntity
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.formatMonthLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

/**
 * 「足迹」界面状态。
 *
 * 统计数字（`*Count`）基于**全量数据**而非筛选结果计算，
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
) {
    /** 用户输入了关键字但没有任何匹配。 */
    val isSearchMiss: Boolean get() = query.isNotBlank() && sections.isEmpty()

    /** 数据源本身为空。 */
    val isEmpty: Boolean get() = totalCount == 0
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

        FootprintUiState(
            sections = visible.toSections(),
            query = query,
            isLoading = false,
            totalCount = all.size,
            goodCount = all.count { it.record.verdict == Verdict.GOOD },
            mehCount = all.count { it.record.verdict == Verdict.MEH },
            badCount = all.count { it.record.verdict == Verdict.BAD },
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

    /**
     * 按月份分组。
     *
     * 依赖上游已按 `eatenAt DESC` 排序（见 `MealDao.observeAllDiningRecords`），
     * 因此这里用 `groupBy` 即可保持「新月份在前、月内新记录在前」的顺序，
     * 不需要二次排序。若上游排序被改动，此处顺序会一并失效。
     */
    private fun List<FootprintEntry>.toSections(): List<FootprintSection> =
        groupBy { it.record.eatenAt.formatMonthLabel() }
            .map { (title, entries) -> FootprintSection(title, entries) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
