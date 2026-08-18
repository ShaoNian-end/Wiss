package com.wiss

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wiss.ui.theme.ThemeSettings
import com.wiss.ui.theme.WissTheme

/**
 * 卡片浏览页 Activity：通过 Intent 接收链接参数（EXTRA_URL），
 * 展示 ShowCardScreen（顶栏筛选/排序、中部卡片网格、底栏翻页）。
 */
class ShowCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏沉浸：透明系统栏，内容延伸到状态栏/导航栏后；
        // 顶栏为深色主色 → 状态栏图标用浅色；底栏为浅色 surface → 导航栏图标用深色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        // 应用「主题色」设置（固定 / 动态配色）
        ThemeSettings.load(this)
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        setContent {
            WissTheme {
                ShowCardScreen(
                    url = url,
                    title = "浏览",
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "show_card_url"
    }
}
