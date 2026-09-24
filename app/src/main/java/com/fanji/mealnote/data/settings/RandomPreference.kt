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
 * 随机选店的候选范围。
 *
 * 常量名会作为字符串写进 SharedPreferences（与数据库里的枚举同一套约定：
 * **名字即持久化编码**），重命名会让老用户的设置回落成默认值，禁止直接改名。
 *
 * 三档都只管**有评价的店**；没有评价的待探访店是否参与由
 * [RandomPickConfig.includeWantToList] 单独决定，与范围正交。
 */
enum class RandomScope(val label: String) {
    /** 只抽最近一次评价为「推荐」的店。 */
    RECOMMENDED("仅推荐"),

    /** 最近一次评价为「推荐」或「尚可」的店。 */
    RECOMMENDED_AND_OK("推荐 + 尚可"),

    /** 所有去过的店，含「不推荐」。 */
    ALL("全部"),
}

/** 「排除最近 N 天吃过的店」的可选档位，0 表示不排除。顺序即界面分段顺序。 */
val RANDOM_EXCLUDE_DAY_OPTIONS = listOf(0, 7, 14, 30)

/** 这些档位对应的中文标签，用于设置页与结果对话框。 */
fun randomExcludeDaysLabel(days: Int): String = if (days <= 0) "不排除" else "$days 天内"

/**
 * 随机选店的完整配置。
 *
 * 默认值是**推荐 + 尚可 + 待探访计入 + 不排除**：
 * 「不知道吃啥」时抽到一家曾经避雷的店，是这功能最招人烦的失败方式；
 * 而新用户只有待探访店，默认档下候选集与旧版完全一致，升级不会改变他们的体验。
 */
data class RandomPickConfig(
    val scope: RandomScope = RandomScope.RECOMMENDED_AND_OK,
    val includeWantToList: Boolean = true,
    val excludeRecentDays: Int = 0,
)

/**
 * 随机选店偏好的持久化。
 *
 * 与 [ThemePreference] / [MotionPreference] 同一模式：`SharedPreferences` + `StateFlow`，
 * 零新依赖（项目锁定依赖版本，不值得为三个设置项引入 DataStore）。
 * 存储在同一个 `mealnote_settings` 文件里，避免设置越加越多时碎片化。
 */
@Singleton
class RandomPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())

    val config: StateFlow<RandomPickConfig> = _config.asStateFlow()

    fun setScope(scope: RandomScope) = update { copy(scope = scope) }

    fun setIncludeWantToList(value: Boolean) = update { copy(includeWantToList = value) }

    /** 只接受 [RANDOM_EXCLUDE_DAY_OPTIONS] 中的档位，越界值回落为「不排除」。 */
    fun setExcludeRecentDays(days: Int) = update { copy(excludeRecentDays = days.normalize()) }

    private inline fun update(transform: RandomPickConfig.() -> RandomPickConfig) {
        val next = _config.value.transform()
        if (next == _config.value) return
        preferences.edit {
            putString(KEY_SCOPE, next.scope.name)
            putBoolean(KEY_INCLUDE_WANT, next.includeWantToList)
            putInt(KEY_EXCLUDE_DAYS, next.excludeRecentDays)
        }
        _config.value = next
    }

    /**
     * 读取配置时对两个字段做容错：
     * 未知的作用域名（手改过的存档、降级安装）回落默认档，越界的天数回落「不排除」。
     * 设置项坏了不该让整个页面崩。
     */
    private fun readConfig(): RandomPickConfig = RandomPickConfig(
        scope = preferences.getString(KEY_SCOPE, null)
            ?.let { name -> RandomScope.entries.firstOrNull { it.name == name } }
            ?: RandomScope.RECOMMENDED_AND_OK,
        includeWantToList = preferences.getBoolean(KEY_INCLUDE_WANT, true),
        excludeRecentDays = preferences.getInt(KEY_EXCLUDE_DAYS, 0).normalize(),
    )

    private fun Int.normalize(): Int = takeIf { it in RANDOM_EXCLUDE_DAY_OPTIONS } ?: 0

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_SCOPE = "random_scope"
        const val KEY_INCLUDE_WANT = "random_include_want"
        const val KEY_EXCLUDE_DAYS = "random_exclude_days"
    }
}
