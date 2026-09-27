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
import com.jiaozi.sz.ui.CrashScreen
import com.jiaozi.sz.ui.theme.JiaoziTheme
import com.jiaozi.sz.xiaomi.StudyTimerService
import java.io.File

class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃诊断：上次运行若遗留 crash.log（App.kt 全局处理器写入），直接展示，无需连机 logcat 即可定位
        val crashFile = File(filesDir, "crash.log")
        if (crashFile.exists()) {
            val crashText = runCatching { crashFile.readText() }.getOrDefault("(崩溃日志读取失败)")
            runCatching { crashFile.delete() }
            // 🔴 2026-09-21 按 14 号稿：崩溃页不再内建「关闭并返回」按钮，
            //    退出交由系统返回键（本 Activity 无导航栈可回退 ⇒ 返回键必然 finish）。
            setContent { CrashScreen(crashText) }
            return
        }
        enableEdgeToEdge() // 状态栏/导航栏沉浸（HyperOS 风格）
        handleIntent(intent)
        setContent {
            val theme by appVm.theme.collectAsStateWithLifecycle()
            val dynamic by appVm.dynamicColor.collectAsStateWithLifecycle()
            val fontScale by appVm.fontScale.collectAsStateWithLifecycle()
            val dark = when (theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            JiaoziTheme(darkTheme = dark, dynamicColor = dynamic, fontScale = fontScale) {
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
     * 🔴 专注计时口径（2026-09-24 杰哥拍板「后台暂停 / 超时归零」）：
     * onStop（真正退到后台，含锁屏/切走 App）暂停读秒；onStart 回来时判断离开时长——
     * 未超 30 分钟则接着累计（后台停留不计入），超过则整段归零从零重计。
     * 注：切 Tab 不会触发 onStop（Activity 仍在 onStart），故站内切页不受影响。
     */
    override fun onStart() {
        super.onStart()
        StudyTimerService.resumeFromBackground()
    }

    override fun onStop() {
        super.onStop()
        StudyTimerService.pauseForBackground()
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
