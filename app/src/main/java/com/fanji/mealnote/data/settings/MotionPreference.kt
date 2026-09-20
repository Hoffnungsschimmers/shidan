package com.fanji.mealnote.data.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 流畅模式偏好。
 *
 * ## 为什么需要它
 *
 * 玻璃拟态的实时模糊（`GraphicsLayer + RenderEffect`）与列表的弹簧入场动画
 * 在低端机上会明显掉帧。用户反馈“新版动画很卡”，最直接的解法是给一个总开关：
 * 关闭后玻璃退化为不透明材质、列表取消入场动画，换取稳定 60 帧。
 *
 * ## 默认值策略
 *
 * - 首次安装：按设备自动判断。低内存设备（`isLowRamDevice`）或
 *   Android 10 及以下默认关闭，否则默认开启；
 * - 用户一旦手动切换过，以手动选择为准，不再自动变更。
 *
 * 与 [ThemePreference] 一样用 `SharedPreferences` 零依赖持久化。
 */
@Singleton
class MotionPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _fluid = MutableStateFlow(readStoredFluid())

    /** true = 完整动效与实时模糊；false = 流畅优先（静态材质、无入场动画）。 */
    val fluid: StateFlow<Boolean> = _fluid.asStateFlow()

    fun setFluid(value: Boolean) {
        if (_fluid.value == value) return
        preferences.edit {
            putBoolean(KEY_FLUID, value)
            putBoolean(KEY_MANUAL, true)
        }
        _fluid.value = value
    }

    private fun readStoredFluid(): Boolean {
        if (preferences.contains(KEY_FLUID)) {
            return preferences.getBoolean(KEY_FLUID, true)
        }
        // 首次安装：低端机默认关闭。isLowRamDevice 由系统按内存分级判定，
        // 比应用自己按型号黑名单更可靠；API 29 及以下再叠一层保守默认。
        val lowRam = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.isLowRamDevice
        }.getOrDefault(false)
        val default = !lowRam && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        preferences.edit { putBoolean(KEY_FLUID, default) }
        return default
    }

    private companion object {
        const val PREFERENCES_NAME = "mealnote_settings"
        const val KEY_FLUID = "fluid_motion"
        const val KEY_MANUAL = "fluid_motion_manual"
    }
}
