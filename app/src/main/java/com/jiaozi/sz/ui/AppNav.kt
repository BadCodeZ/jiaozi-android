package com.jiaozi.sz.ui

import com.jiaozi.sz.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.GlassSurface
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jiaozi.sz.ui.screens.AiChatScreen
import com.jiaozi.sz.ui.screens.BankScreen
import com.jiaozi.sz.ui.screens.ChaptersScreen
import com.jiaozi.sz.ui.screens.GraphScreen
import com.jiaozi.sz.ui.screens.InboxScreen
import com.jiaozi.sz.ui.screens.KnowledgeScreen
import com.jiaozi.sz.ui.screens.LessonScreen
import com.jiaozi.sz.ui.screens.MineScreen
import com.jiaozi.sz.ui.screens.SearchScreen
import com.jiaozi.sz.ui.screens.PracticeScreen
import com.jiaozi.sz.ui.screens.ProofScreen
import com.jiaozi.sz.ui.screens.SettingsScreen
import com.jiaozi.sz.ui.screens.StatsScreen
import com.jiaozi.sz.ui.screens.TodayScreen
import com.jiaozi.sz.xiaomi.Haptic

sealed class Screen(val route: String, val label: String, val iconRes: Int) {
    object Today : Screen("today", "今日", R.drawable.ic_today)
    object Practice : Screen("practice", "练习", R.drawable.ic_edit)
    object Bank : Screen("bank", "题库", R.drawable.ic_book)
    object Stats : Screen("stats", "统计", R.drawable.ic_bars)
    object Mine : Screen("mine", "我的", R.drawable.ic_person)
}

/** 底部主导航：5 个入口，避免窄屏 6 tab 拥挤；图谱/设置收入"我的" */
val bottomItems = listOf(
    Screen.Today, Screen.Practice, Screen.Bank, Screen.Stats, Screen.Mine
)

/** 二级页面路由 → 顶栏标题（用于固定顶栏返回键，仅二级页显示，底部 5 页不显示） */
val secondaryTitles = mapOf(
    "settings" to "设置",
    "graph" to "知识关联图谱",
    "chapters" to "章节健康度",
    "lesson" to "备课教案",
    "inbox" to "收集箱",
    "proof" to "校订",
    "aichat" to "AI 帮手",
    "knowledge" to "知识库",
    "search" to "搜索"
)

/**
 * 二级页面统一顶栏：左上角返回按钮 + 标题。
 * 由「我的」进入的设置/图谱/备课/收集箱/校订/AI帮手/知识库/全局搜索等页面复用，
 * 用户可一键返回，不再卡在二级页。
 */
@Composable
fun ScreenHeader(title: String, nav: NavHostController, showBack: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (showBack) {
            IconButton(onClick = { nav.navigateUp() }) {
                Icon(appPainter("back"), contentDescription = "返回", modifier = Modifier.size(22.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    // 活动级共享实例（AppRoot 在 NavHost 之外，viewModel() 默认即 activity 作用域）
    val appVm: AppViewModel = viewModel()
    val practiceVm: PracticeViewModel = viewModel()
    val loadError by appVm.loadError.collectAsStateWithLifecycle()

    // 小米传送门：长按文本唤起时，携带关键词跳全局搜索
    val pendingSearch by appVm.pendingSearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        if (pendingSearch.isNotBlank()) {
            nav.navigate("search") {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            // 不清空：交由 SearchScreen 读取 initial 后再清空，避免竞态吞掉关键词
        }
    }

    // 小米桌面组件点击：直接进入练习页
    val pendingPractice by appVm.pendingPractice.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPractice) {
        if (pendingPractice) {
            nav.navigate(Screen.Practice.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            appVm.setPendingPractice(false)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // 固定顶栏：状态栏占位 + （仅二级页）返回键与标题，滚动不影响其位置
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
                val route = nav.currentBackStackEntryAsState().value?.destination?.route
                val title = secondaryTitles[route]
                if (title != null) {
                    ScreenHeader(title, nav)
                }
            }
        },
    ) { inner ->
        CompositionLocalProvider(LocalAppVm provides appVm, LocalPracticeVm provides practiceVm) {
            Box(Modifier.fillMaxSize()) {
                // 内容区：顶部留出顶栏高度；内容延展到导航之下（导航为半透明玻璃，内容从身后透出 = 四周透明）
                Box(Modifier.fillMaxSize().padding(inner)) {
                    NavHost(
                        nav,
                        startDestination = Screen.Today.route,
                        enterTransition = { fadeIn(tween(160)) },
                        exitTransition = { fadeOut(tween(160)) },
                        popEnterTransition = { fadeIn(tween(160)) },
                        popExitTransition = { fadeOut(tween(160)) }
                    ) {
                        composable(Screen.Today.route) { TodayScreen(nav) }
                        composable(Screen.Practice.route) { PracticeScreen(nav) }
                        composable(Screen.Bank.route) { BankScreen(nav) }
                        composable(Screen.Stats.route) { StatsScreen(nav) }
                        composable(Screen.Mine.route) { MineScreen(nav) }
                        // 我的页内的二级入口（不在底部显示）
                        composable("settings") { SettingsScreen(nav) }
                        composable("graph") { GraphScreen(nav) }
                        composable("chapters") { ChaptersScreen(nav) }
                        composable("lesson") { LessonScreen(nav) }
                        composable("inbox") { InboxScreen(nav) }
                        composable("proof") { ProofScreen(nav) }
                        composable("aichat") { AiChatScreen(nav) }
                        composable("knowledge") { KnowledgeScreen(nav) }
                        composable("search") { SearchScreen(nav) }
                    }
                }
                // 悬浮玻璃导航层（绝对定位于底部，半透明透出底层主背景）
                GlassNavBar(nav, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/**
 * 悬浮玻璃底部导航：半透明玻璃药丸（GlassSurface）绝对定位于屏幕底部，
 * 透出底层主背景，发丝边框 + 柔和投影，内容列表可从其下方滚过。
 * 选中项高亮态沿用 primaryContainer 低透明底色，未选中为纯透明。
 */
@Composable
private fun GlassNavBar(nav: NavHostController, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 4.dp)
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val current = nav.currentBackStackEntryAsState().value?.destination
                bottomItems.forEach { s ->
                    val selected = current?.hierarchy?.any { it.route == s.route } == true
                    val bgAlpha by animateFloatAsState(
                        targetValue = if (selected) 0.6f else 0f,
                        animationSpec = tween(160),
                        label = "navBg"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = if (selected) 24.dp else 22.dp,
                        animationSpec = tween(160),
                        label = "navIcon"
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable {
                                Haptic.tick(nav.context)
                                nav.navigate(s.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = 7.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = bgAlpha),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(s.iconRes),
                                    contentDescription = s.label,
                                    modifier = Modifier.size(iconSize),
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
