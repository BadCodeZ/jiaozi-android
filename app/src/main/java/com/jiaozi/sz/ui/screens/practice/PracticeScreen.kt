@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 练习主屏入口：按状态分发到首页 / 答题会话 / 总结页。
 *
 * 从 PracticeScreen.kt 拆分而来（纯物理拆分，逻辑未改）。
 */

import kotlin.math.*
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.island.IslandState
import com.jiaozi.sz.xiaomi.FloatingIslandService
import android.provider.Settings

@Composable
fun PracticeScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val vm: PracticeViewModel = LocalPracticeVm.current
    val ctx = LocalContext.current
    val st by vm.state.collectAsStateWithLifecycle()
    val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()

    // 灵动岛（上岛）：练习中把进度推到全局悬浮胶囊；若用户已开启灵动岛开关则自动拉起服务。
    // 依赖键仅取「会改变化胶囊内容」的字段，避免草稿输入等无关 st 变更触发悬浮窗每秒/每键重绘（#77 减重组）。
    LaunchedEffect(st.results.size, st.total, st.current?.subject, st.finished, st.questions.isNotEmpty(), islandEnabled) {
        if (st.questions.isNotEmpty() && !st.finished) {
            val total = st.total
            val done = st.results.size
            var streak = 0
            for (r in st.results.values.reversed()) { if (r.correct) streak++ else break }
            val subj = st.current?.subject?.let { BankStore.shortName(it) } ?: "练习"
            val detail = "$subj 已练 $done/$total · 连对 $streak"
            if (islandEnabled && Settings.canDrawOverlays(ctx)) {
                ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
            }
            IslandBus.enter(
                "practice",
                IslandState(
                    kind = "practice",
                    title = "练习中",
                    detail = detail,
                    progress = if (total > 0) done.toFloat() / total else null
                )
            )
        } else {
            IslandBus.leave("practice")
        }
    }
    // 离开练习页（含切到非岛页面）时释放岛占用，避免残留「练习中」胶囊
    DisposableEffect(Unit) {
        onDispose { IslandBus.leave("practice") }
    }

    when {
        st.finished -> SummaryView(vm, st, nav)
        st.questions.isNotEmpty() -> SessionView(vm, st)
        else -> PracticeHome(vm, appVm, nav, st)
    }
}
