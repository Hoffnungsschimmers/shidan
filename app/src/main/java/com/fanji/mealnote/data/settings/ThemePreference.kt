package com.fanji.mealnote.data.settings

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题模式。
 *
 * 默认 [SYSTEM]：绝大多数用户希望应用跟随系统的深色设置，
 * 提供手动开关只是给「系统是浅色但我就想用深色」这类少数场景兜底。
 */
enum class ThemeMode {
    /** 跟随系统。 */
    SYSTEM,

    /** 始终浅色。 */
    LIGHT,

    /** 始终深色。 */
    DARK;

    /** 结合系统当前状态，得出实际是否使用深色。 */
    @Composable
    fun resolveDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }
}

/**
 * 主题偏好的持久化。
 *
 * 用 `SharedPreferences` 而不是 DataStore：这里只有一个枚举值，
 * 而 DataStore 需要新增依赖 —— 本项目锁定依赖版本（见 `README.md`「开发约定」），
 * 为单个设置项引入新依赖不划算。`SharedPreferences` 是平台自带的，零依赖、同步读取，
 * 正好适合「启动时必须立刻拿到」这类场景。
 *
 * 用 [StateFlow] 而非让界面直接读 `SharedPreferences`：偏好变化要能立刻反映到主题上，
 * 而 `SharedPreferences` 的监听器是回调式的，接到 Compose 里需要额外适配。
 */
@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readStoredMode())

    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(value: ThemeMode) {
        if (_mode.value == value) return
        // 先落盘再更新状态：万一写入失败，界面不会显示一个下次启动就消失的假状态。
        // 使用 core-ktx 的 edit {} 扩展，它默认走 apply()（异步写，不阻塞主线程）。
        preferences.edit { putString(KEY_THEME_MODE, value.name) }
        _mode.value = value
    }

    /**
     * 读取已保存的模式。
     *
     * 对无法识别的值回退到 [ThemeMode.SYSTEM] 而不是抛异常：
     * 该值可能来自更早或更新的版本，用户不该因此打不开应用。
     */
    private fun readStoredMode(): ThemeMode {
        val stored = preferences.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
