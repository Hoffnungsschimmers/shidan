package com.fanji.mealnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.settings.ThemePreference
import com.fanji.mealnote.ui.MealNoteApp
import com.fanji.mealnote.ui.theme.MealNoteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var themePreference: ThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 主题模式来自持久化偏好，默认「跟随系统」。
            // 在这里读取而不是让 MealNoteApp 自己读：主题必须包住整个界面，
            // 包括后续可能加进来的对话框与弹窗。
            val themeMode by themePreference.mode.collectAsStateWithLifecycle()
            MealNoteTheme(darkTheme = themeMode.resolveDark()) {
                MealNoteApp()
            }
        }
    }
}
