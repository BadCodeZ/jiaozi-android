package com.jiaozi.sz

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiaozi.sz.ui.AppRoot
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.theme.JiaoziTheme

class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // 状态栏/导航栏沉浸（HyperOS 风格）
        handleIntent(intent)
        setContent {
            val theme by appVm.theme.collectAsStateWithLifecycle()
            val dynamic by appVm.dynamicColor.collectAsStateWithLifecycle()
            val fontScale by appVm.fontScale.collectAsStateWithLifecycle()
            val themePack by appVm.themePack.collectAsStateWithLifecycle()
            val dark = when (theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            JiaoziTheme(darkTheme = dark, dynamicColor = dynamic, fontScale = fontScale, themePack = themePack) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 统一处理两类外部唤起：
     * 1) 小米传送门 / 侧边栏：长按文本选词后本应用被唤起（ACTION_PROCESS_TEXT）→ 跳全局搜索；
     * 2) 桌面组件点击：action="com.jiaozi.sz.START_PRACTICE" → 进练习页。
     * 均通过 AppViewModel 的待消费流驱动，AppRoot 负责路由，避免传参竞态。
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.takeIf { it.isNotBlank() }
                if (text != null) appVm.setPendingSearch(text)
            }
            "com.jiaozi.sz.START_PRACTICE" -> {
                appVm.setPendingPractice(true)
            }
        }
    }
}
