package com.wiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wiss.ui.theme.ThemeSettings
import com.wiss.ui.theme.WissTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读取「主题色」设置（固定 / 动态配色）
        ThemeSettings.load(this)
        setContent {
            WissTheme {
                MainScaffold()
            }
        }
    }
}
