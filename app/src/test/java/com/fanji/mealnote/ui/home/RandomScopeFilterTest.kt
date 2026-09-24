package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.data.settings.RANDOM_EXCLUDE_DAY_OPTIONS
import com.fanji.mealnote.data.settings.RandomPickConfig
import com.fanji.mealnote.data.settings.RandomScope
import com.fanji.mealnote.data.settings.randomExcludeDaysLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 随机选店候选池测试。
 *
 * 这里固化的是**产品决策**而不只是计算结果，改动了预期行为会被这些用例拦下来：
 *
 * - 「当前评价」= 最近一次用餐的评价，不是历史上最好/最差的一次；
 * - 范围与「待探访是否计入」正交，三档都只管有评价的店；
 * - 排除近期按**自然日**（按传入时区判定），不是 24 小时滚动窗口；
 * - 无候选时返回空列表，由界面隐藏按钮，而不是抛异常或回退到「全部」。
 *
 * 与 [FootprintAggregationTest] 同一约定：调用真实生产函数，`today` 与 `zone` 作参数，
 * 不读系统时钟。
 */
class RandomScopeFilterTest {

    private val utc: ZoneId = ZoneId.of("UTC")
    private val east8: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun restaurant(id: Long, status: RestaurantStatus) =
        RestaurantEntity(id = id, name = "店$id", status = status)

    private fun record(
        restaurantId: Long,
        verdict: Verdict,
        at: Long,
    ) = DiningRecordEntity(restaurantId = restaurantId, verdict = verdict, eatenAt = at)

    /** 用东八区的某个正午构造时间戳：避开「正好在时区边界」带来的偶然性。 */
    private fun noonEast8(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(east8).toInstant().toEpochMilli()

    private fun config(
        scope: RandomScope = RandomScope.ALL,
        includeWant: Boolean = true,
        excludeDays: Int = 0,
    ) = RandomPickConfig(scope, includeWant, excludeDays)

    private fun idsIn(pool: List<RestaurantEntity>): List<Long> = pool.map { it.id }

    // ------------------------------------------------------------------ 评价范围

    @Test
    fun `仅推荐档只留下最近一次评为推荐的店`() {
        val stores = listOf(
            restaurant(1, RestaurantStatus.EATEN),
            restaurant(2, RestaurantStatus.EATEN),
            restaurant(3, RestaurantStatus.EATEN),
        )
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, 100L),
            2L to LatestVisit(Verdict.MEH, 100L),
            3L to LatestVisit(Verdict.BAD, 100L),
        )
        val pool = buildRandomCandidatePool(
            restaurants = stores,
            latestVisits = visits,
            config = config(scope = RandomScope.RECOMMENDED, includeWant = false),
            today = LocalDate.of(2026, 9, 22),
            zone = east8,
        )
        assertEquals(listOf(1L), idsIn(pool))
    }

    @Test
    fun `推荐加尚可档收下推荐与尚可，仍排除不推荐`() {
        val stores = (1L..3L).map { restaurant(it, RestaurantStatus.EATEN) }
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, 1L),
            2L to LatestVisit(Verdict.MEH, 1L),
            3L to LatestVisit(Verdict.BAD, 1L),
        )
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.RECOMMENDED_AND_OK, includeWant = false),
            LocalDate.of(2026, 9, 22), east8,
        )
        assertEquals(listOf(1L, 2L), idsIn(pool))
    }

    @Test
    fun `全部档包含不推荐的店`() {
        val stores = (1L..3L).map { restaurant(it, RestaurantStatus.EATEN) }
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, 1L),
            2L to LatestVisit(Verdict.MEH, 1L),
            3L to LatestVisit(Verdict.BAD, 1L),
        )
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.ALL, includeWant = false),
            LocalDate.of(2026, 9, 22), east8,
        )
        assertEquals(listOf(1L, 2L, 3L), idsIn(pool))
    }

    // -------------------------------------------------------------- 最近一次优先

    @Test
    fun `当前评价取最近一次，早先的差评不连坐`() {
        // 同一家店：2024 年避雷，2026 年 recommission 成推荐。常客口味会变，以最新为准。
        val records = listOf(
            record(1, Verdict.GOOD, noonEast8(2026, 9, 1)),
            record(1, Verdict.BAD, noonEast8(2024, 5, 5)),
        )
        val visits = latestVisitByRestaurant(records)
        assertEquals(Verdict.GOOD, visits.getValue(1L).verdict)
    }

    @Test
    fun `最新一条是不推荐时，该店不再出现在仅推荐档`() {
        val records = listOf(
            record(1, Verdict.GOOD, noonEast8(2024, 5, 5)),
            record(1, Verdict.BAD, noonEast8(2026, 9, 1)),
        )
        val pool = buildRandomCandidatePool(
            restaurants = listOf(restaurant(1, RestaurantStatus.EATEN)),
            latestVisits = latestVisitByRestaurant(records),
            config = config(scope = RandomScope.RECOMMENDED, includeWant = false),
            today = LocalDate.of(2026, 9, 22),
            zone = east8,
        )
        assertTrue(pool.isEmpty())
    }

    @Test
    fun `候选池计算与上游记录顺序无关`() {
        // 生产查询是 eatenAt DESC，但这里刻意喂乱序：正确性不该挂在一个远在上游的 ORDER BY 上。
        val ascending = listOf(
            record(7, Verdict.BAD, noonEast8(2025, 1, 1)),
            record(7, Verdict.GOOD, noonEast8(2026, 8, 8)),
        )
        val descending = ascending.reversed()
        assertEquals(
            latestVisitByRestaurant(ascending),
            latestVisitByRestaurant(descending),
        )
        assertEquals(Verdict.GOOD, latestVisitByRestaurant(ascending).getValue(7L).verdict)
    }

    @Test
    fun `没有用餐记录的店不进映射`() {
        val visits = latestVisitByRestaurant(listOf(record(1, Verdict.GOOD, 1L)))
        assertFalse(visits.containsKey(2L))
    }

    // ---------------------------------------------------------------- 待探访开关

    @Test
    fun `开开关时待探访店在仅推荐档也参与随机`() {
        val stores = listOf(
            restaurant(1, RestaurantStatus.WANT_TO_EAT),
            restaurant(2, RestaurantStatus.EATEN),
        )
        val visits = mapOf(2L to LatestVisit(Verdict.BAD, 1L))
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.RECOMMENDED, includeWant = true),
            LocalDate.of(2026, 9, 22), east8,
        )
        assertEquals(listOf(1L), idsIn(pool))
    }

    @Test
    fun `关开关后仅保留有评价且符合范围的店`() {
        val stores = listOf(
            restaurant(1, RestaurantStatus.WANT_TO_EAT),
            restaurant(2, RestaurantStatus.EATEN),
        )
        val visits = mapOf(2L to LatestVisit(Verdict.GOOD, 1L))
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.RECOMMENDED, includeWant = false),
            LocalDate.of(2026, 9, 22), east8,
        )
        assertEquals(listOf(2L), idsIn(pool))
    }

    @Test
    fun `状态为已用餐但记录已被清空时按待探访处理`() {
        // 极端情况：删掉最后一条记录会让状态回退，但两者由不同事务写入，读到不一致时
        // 以「有没有记录」为准——这决定它是走范围筛选还是走待探访开关。
        val pool = buildRandomCandidatePool(
            restaurants = listOf(restaurant(1, RestaurantStatus.EATEN)),
            latestVisits = emptyMap(),
            config = config(scope = RandomScope.RECOMMENDED, includeWant = false),
            today = LocalDate.of(2026, 9, 22),
            zone = east8,
        )
        assertTrue(pool.isEmpty())
    }

    // ------------------------------------------------------------- 排除近期吃过

    @Test
    fun `排除七天内吃过的店，第八天之前的保留`() {
        val today = LocalDate.of(2026, 9, 22)
        val stores = listOf(
            restaurant(1, RestaurantStatus.EATEN),
            restaurant(2, RestaurantStatus.EATEN),
        )
        val visits = mapOf(
            // 正好 7 天前：按自然日语义属于「7 天内」，应被排除。
            1L to LatestVisit(Verdict.GOOD, noonEast8(2026, 9, 15)),
            2L to LatestVisit(Verdict.GOOD, noonEast8(2026, 9, 14)),
        )
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.ALL, includeWant = false, excludeDays = 7),
            today, east8,
        )
        assertEquals(listOf(2L), idsIn(pool))
    }

    @Test
    fun `不排除档下所有符合范围的店都在池里`() {
        val today = LocalDate.of(2026, 9, 22)
        val visits = mapOf(1L to LatestVisit(Verdict.GOOD, noonEast8(2026, 9, 21)))
        val pool = buildRandomCandidatePool(
            restaurants = listOf(restaurant(1, RestaurantStatus.EATEN)),
            latestVisits = visits,
            config = config(excludeDays = 0),
            today = today,
            zone = east8,
        )
        assertEquals(listOf(1L), idsIn(pool))
    }

    @Test
    fun `三十天档保留三十一天前那餐的店`() {
        val today = LocalDate.of(2026, 9, 22)
        val stores = listOf(
            restaurant(1, RestaurantStatus.EATEN),
            restaurant(2, RestaurantStatus.EATEN),
        )
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, noonEast8(2026, 8, 23)),
            2L to LatestVisit(Verdict.GOOD, noonEast8(2026, 8, 22)),
        )
        val pool = buildRandomCandidatePool(
            stores, visits,
            config(scope = RandomScope.ALL, includeWant = false, excludeDays = 30),
            today, east8,
        )
        assertEquals(listOf(2L), idsIn(pool))
    }

    @Test
    fun `排除近期跨年均按自然日成立`() {
        val today = LocalDate.of(2027, 1, 3)
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, noonEast8(2026, 12, 30)),
            2L to LatestVisit(Verdict.GOOD, noonEast8(2026, 12, 20)),
        )
        val pool = buildRandomCandidatePool(
            restaurants = listOf(
                restaurant(1, RestaurantStatus.EATEN),
                restaurant(2, RestaurantStatus.EATEN),
            ),
            latestVisits = visits,
            config = config(scope = RandomScope.ALL, includeWant = false, excludeDays = 7),
            today = today,
            zone = east8,
        )
        assertEquals(listOf(2L), idsIn(pool))
    }

    @Test
    fun `同一时刻在不同时区下归属不同自然日`() {
        // 2026-12-31T20:00Z：UTC 看是 12-31，东八区看是 2027-01-01。
        val instant = LocalDate.of(2026, 12, 31).atTime(20, 0)
            .atZone(utc).toInstant().toEpochMilli()
        val visits = mapOf(1L to LatestVisit(Verdict.GOOD, instant))
        val stores = listOf(restaurant(1, RestaurantStatus.EATEN))
        val cfg = config(scope = RandomScope.ALL, includeWant = false, excludeDays = 2)

        // 以 2027-01-03 为今天、排除 2 天内（cutoff = 01-01）：
        // UTC 归属 12-31 早于 cutoff → 保留；东八区归属 01-01 → 排除。
        val inUtc = buildRandomCandidatePool(stores, visits, cfg, LocalDate.of(2027, 1, 3), utc)
        val inEast8 = buildRandomCandidatePool(stores, visits, cfg, LocalDate.of(2027, 1, 3), east8)
        assertEquals(listOf(1L), idsIn(inUtc))
        assertTrue(inEast8.isEmpty())
    }

    @Test
    fun `待探访店不受排除近期影响`() {
        val pool = buildRandomCandidatePool(
            restaurants = listOf(restaurant(1, RestaurantStatus.WANT_TO_EAT)),
            latestVisits = emptyMap(),
            config = config(scope = RandomScope.RECOMMENDED, includeWant = true, excludeDays = 30),
            today = LocalDate.of(2026, 9, 22),
            zone = east8,
        )
        assertEquals(listOf(1L), idsIn(pool))
    }

    // -------------------------------------------------------------- 池的形态约定

    @Test
    fun `池顺序沿用入参顺序不做二次排序`() {
        val stores = listOf(
            restaurant(3, RestaurantStatus.EATEN),
            restaurant(1, RestaurantStatus.EATEN),
            restaurant(2, RestaurantStatus.EATEN),
        )
        val visits = mapOf(
            1L to LatestVisit(Verdict.GOOD, 1L),
            2L to LatestVisit(Verdict.GOOD, 1L),
            3L to LatestVisit(Verdict.GOOD, 1L),
        )
        val pool = buildRandomCandidatePool(
            stores, visits, config(), LocalDate.of(2026, 9, 22), east8,
        )
        assertEquals(listOf(3L, 1L, 2L), idsIn(pool))
    }

    @Test
    fun `没有任何候选时返回空而不是回退到全部`() {
        // 「仅推荐 + 不含待探访 + 全部店都是避雷」——此时界面应隐藏按钮，
        // 绝不能悄悄放宽范围把不推荐的店塞进来。
        val visits = mapOf(1L to LatestVisit(Verdict.BAD, 1L))
        val pool = buildRandomCandidatePool(
            restaurants = listOf(restaurant(1, RestaurantStatus.EATEN)),
            latestVisits = visits,
            config = config(scope = RandomScope.RECOMMENDED, includeWant = false),
            today = LocalDate.of(2026, 9, 22),
            zone = east8,
        )
        assertTrue(pool.isEmpty())
    }

    @Test
    fun `排除天数只接受预设档位`() {
        // 偏好读取侧的容错：越界值回落「不排除」，而不是拿一个没人设计的数字去算 cutoff。
        assertEquals(listOf(0, 7, 14, 30), RANDOM_EXCLUDE_DAY_OPTIONS)
        assertEquals("不排除", randomExcludeDaysLabel(0))
        assertEquals("14 天内", randomExcludeDaysLabel(14))
    }

    // ---------------------------------------------------------------- 范围说明

    @Test
    fun `默认档的说明同时点出评价范围与待探访`() {
        // 默认「仅推荐之外的第二档 + 含待探访」：抽到没吃过的店时必须能解释为什么。
        assertEquals(
            "范围：推荐 + 尚可，含待探访",
            RandomPickConfig().candidateScopeDescription(),
        )
    }

    @Test
    fun `说明覆盖关闭待探访与排除近期两种情形`() {
        assertEquals(
            "范围：仅推荐",
            config(scope = RandomScope.RECOMMENDED, includeWant = false).candidateScopeDescription(),
        )
        assertEquals(
            "范围：全部，排除 30 天内吃过的",
            config(scope = RandomScope.ALL, includeWant = false, excludeDays = 30)
                .candidateScopeDescription(),
        )
    }

    // ---------------------------------------------------------------- 「换一家」

    @Test
    fun `候选多于一家时换一家必定换店`() {
        val pool = (1L..5L).map { restaurant(it, RestaurantStatus.EATEN) }
        repeat(50) {
            val first = pool.randomOtherThan(null)
            val second = pool.randomOtherThan(first)
            assertTrue(pool.contains(first))
            assertTrue("换一家抽出了同一家店：$first", first != second)
        }
    }

    @Test
    fun `首次抽取对全部候选等概率`() {
        // 「换一家才排除上一次」，第一次不能排除任何一家。
        val pool = (1L..3L).map { restaurant(it, RestaurantStatus.EATEN) }
        val seen = (1..300).mapNotNull { pool.randomOtherThan(null)?.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), seen)
    }

    @Test
    fun `只剩一家候选时换一家仍返回该店`() {
        val pool = listOf(restaurant(1, RestaurantStatus.EATEN))
        assertEquals(pool, listOf(pool.randomOtherThan(pool.first())))
    }

    @Test
    fun `候选为空时返回空而不是抛异常`() {
        // List.random() 对空集合会抛 NoSuchElementException；按钮虽已隐藏，
        // 但发射与点击之间池子可能刚被清空（删掉了最后一家店）。
        assertNull(emptyList<RestaurantEntity>().randomOtherThan(null))
    }
}
