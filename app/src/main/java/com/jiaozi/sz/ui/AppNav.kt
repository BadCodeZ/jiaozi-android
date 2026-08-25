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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.GlassSurface
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jiaozi.sz.ui.screens.AiChatScreen
import com.jiaozi.sz.ui.screens.BankScreen
import com.jiaozi.sz.ui.screens.BookScreen
import com.jiaozi.sz.ui.screens.ChaptersScreen
import com.jiaozi.sz.ui.screens.CurricScreen
import com.jiaozi.sz.ui.screens.GraphScreen
import com.jiaozi.sz.ui.screens.InboxScreen
import com.jiaozi.sz.ui.screens.KnowledgeScreen
import com.jiaozi.sz.ui.screens.LessonScreen
import com.jiaozi.sz.ui.screens.MineScreen
import com.jiaozi.sz.ui.screens.SearchScreen
import com.jiaozi.sz.ui.screens.PracticeScreen
import com.jiaozi.sz.ui.screens.ProofScreen
import com.jiaozi.sz.ui.screens.SettingsScreen
import com.jiaozi.sz.ui.screens.AboutScreen
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
    "curric" to "课标库",
    "books" to "教材库",
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

/**
 * 集中动效 token + 系统「减少动态效果」跟随。
 * - 时长统一为三档（fast/base/slow），消除此前散落在各文件的 160/180/200/220/250ms 不一致。
 * - reduce：跟随 Android 系统「设置 → 无障碍 → 减少动态效果」(ANIMATOR_DURATION_SCALE==0)。
 *   开启后三档时长全部压到 0（瞬时切换），骨架屏 shimmer 停闪，照顾前庭敏感与低端机用户（§39 P3 待改进①）。
 * 用法：在 @Composable 作用域内算一次 `val rm = reduceMotionNow(ctx)`，再在非组合回调（如 enterTransition）里
 * 捕获这个普通 Boolean 传进 `Motion.duration(rm, base)`；不应在 CompositionLocal 里读（enterTransition 非组合上下文）。
 */
object Motion {
    const val FAST = 150
    const val BASE = 200
    const val SLOW = 250
    /** 跟随系统「减少动态效果」：开启返回 0，否则原值 */
    fun duration(reduce: Boolean, base: Int): Int = if (reduce) 0 else base

    /**
     * 物理弹簧动画（参考椒盐笔记大量使用 spring 而非常规 tween 的"丝滑感"来源）。
     * - 选中态位移/显隐、卡片回弹用中等刚度+低阻尼（自然回弹，不发飘）。
     * - reduce（系统「减少动态效果」开启）时退化为瞬时 tween(0)，照顾前庭敏感/低端机。
     */
    fun <T> springSpec(reduce: Boolean): AnimationSpec<T> =
        if (reduce) tween(0) else spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioLowBouncy
        )

    /** 较稳的弹簧（tab 选中、导航显隐用，低回弹避免抖动） */
    fun <T> springSteady(reduce: Boolean): AnimationSpec<T> =
        if (reduce) tween(0) else spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioNoBouncy
        )
}

/** 读取系统「减少动态效果」开关（ANIMATOR_DURATION_SCALE==0）。仅在 @Composable 内调用。 */
@Composable
fun reduceMotionNow(ctx: android.content.Context): Boolean = remember(ctx) {
    android.provider.Settings.Global.getFloat(
        ctx.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    // 活动级共享实例（AppRoot 在 NavHost 之外，viewModel() 默认即 activity 作用域）
    val appVm: AppViewModel = viewModel()
    val practiceVm: PracticeViewModel = viewModel()
    val loadError by appVm.loadError.collectAsStateWithLifecycle()
    // 导航栏显示规则：
    //  - 一级 Tab（今日/练习/题库/统计/我的）= 显示导航栏；
    //  - 二级界面（设置/图谱/章节/备课/课标/教材/收集箱/校订/AI 帮手/知识库/搜索等）= 隐藏导航栏；
    //  - 练习页内「正在答题、未完成」时仍额外隐藏（沉浸式，不挡提交/下一步按钮），完成后结算页恢复。
    // 说明：此前误将「二级界面隐藏导航栏」当成 bug 改反了——这里按预期实现：二级界面隐藏、一级 Tab 显示。
    val practiceState by practiceVm.state.collectAsStateWithLifecycle()
    // 命令式维护当前路由：AppRoot 位于 NavHost 之外，用 currentBackStackEntryAsState 在父级组合树
    // 常不随子图路由变化重组（Compose Navigation 已知边缘情况），导致 currentRoute 卡旧值、导航栏不随
    // 二级界面隐藏。改用 OnDestinationChangedListener 监听目标变化，100% 可靠。
    var currentRoute by remember { mutableStateOf(nav.currentDestination?.route ?: Screen.Today.route) }
    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentRoute = destination.route ?: currentRoute
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    // 底部一级 Tab 集合，命中则导航栏显示
    val isPrimaryTab = currentRoute in listOf(
        Screen.Today.route, Screen.Practice.route, Screen.Bank.route,
        Screen.Stats.route, Screen.Mine.route
    )
    // 练习答题会话（仅限练习页内、未完成），与「二级界面」共同决定是否隐藏
    val inPracticeAnswering = currentRoute == Screen.Practice.route
        && practiceState.questions.isNotEmpty() && !practiceState.finished
    // 隐藏 = 非一级 Tab（即二级界面），或（练习页内正在答题）
    val navHidden = !isPrimaryTab || inPracticeAnswering

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

    // 系统「减少动态效果」开关：仅在 @Composable 作用域算一次，普通 Boolean 可安全捕获进非组合回调
    val rm = reduceMotionNow(LocalContext.current)
    // 启动遮罩：冷启动时先显示品牌 logo 居中，首帧组合完成后短暂停留再 scale+alpha 退场，
    // 消除 Android 冷启动白屏断层（参考椒盐 SplashScreen 退出动画思路，但零新依赖、纯 Compose 自绘）。
    // 减少动态效果时直接跳过遮罩（不闪）。
    var showSplash by remember { mutableStateOf(!rm) }
    LaunchedEffect(Unit) {
        if (rm) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        showSplash = false
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // 固定顶栏：状态栏占位 + （仅二级页）返回键与标题，滚动不影响其位置
            Column(Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars.union(WindowInsets.displayCutout)))
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
                // 平板/横屏适配：宽屏（sw>=600，含平板与手机横屏）约束内容最大宽度并居中，避免单栏拉得过宽
                val isWide = LocalConfiguration.current.screenWidthDp >= 600
                // 内容区：顶部留出顶栏高度；内容延展到导航之下（导航为半透明玻璃，内容从身后透出 = 四周透明）
                Box(Modifier.fillMaxSize().padding(inner).then(if (isWide) Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter) else Modifier)) {
                    // 内容区微质感：极淡 surface→surfaceVariant 垂直渐变（仅内容区；顶栏/底栏仍为纯 surface）
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            )
                        )
                    )
                    NavHost(
                        nav,
                        startDestination = Screen.Today.route,
                        // 页面切换：fade + 轻微 scale（缩放营造"推入/推出"纵深感，参考椒盐丝滑观感）
                        enterTransition = {
                            fadeIn(tween(Motion.duration(rm, Motion.FAST))) +
                                scaleIn(initialScale = 0.97f, animationSpec = tween(Motion.duration(rm, Motion.BASE)))
                        },
                        exitTransition = {
                            fadeOut(tween(Motion.duration(rm, Motion.FAST))) +
                                scaleOut(targetScale = 1.03f, animationSpec = tween(Motion.duration(rm, Motion.BASE)))
                        },
                        popEnterTransition = {
                            fadeIn(tween(Motion.duration(rm, Motion.FAST))) +
                                scaleIn(initialScale = 1.03f, animationSpec = tween(Motion.duration(rm, Motion.BASE)))
                        },
                        popExitTransition = {
                            fadeOut(tween(Motion.duration(rm, Motion.FAST))) +
                                scaleOut(targetScale = 0.97f, animationSpec = tween(Motion.duration(rm, Motion.BASE)))
                        }
                    ) {
                        composable(Screen.Today.route) { TodayScreen(nav) }
                        composable(Screen.Practice.route) { PracticeScreen(nav) }
                        composable(Screen.Bank.route) { BankScreen(nav) }
                        composable(Screen.Stats.route) { StatsScreen(nav) }
                        composable(Screen.Mine.route) { MineScreen(nav) }
                        // 我的页内的二级入口（不在底部显示）
                        composable("settings") { SettingsScreen(nav) }
                        composable("about") { AboutScreen(nav) }
                        composable("graph") { GraphScreen(nav) }
                        composable("chapters") { ChaptersScreen(nav) }
                        composable("lesson") { LessonScreen(nav) }
                        composable("curric") { CurricScreen(nav) }
                        composable("books") { BookScreen(nav) }
                        composable("inbox") { InboxScreen(nav) }
                        composable("proof") { ProofScreen(nav) }
                        composable("aichat") { AiChatScreen(nav) }
                        composable("knowledge") { KnowledgeScreen(nav) }
                        composable("search") { SearchScreen(nav) }
                    }
                }
                // 悬浮导航层：进入练习答题会话时下移淡出（沉浸式），避免遮挡提交/下一步按钮。
                // 关键：导航栏【始终保留在组合树中】，仅用 offset/alpha 做显隐——不依赖 AnimatedVisibility 的
                // 挂载/卸载，否则重新添加后点击通道与 nav.currentBackStackEntryAsState 存在边缘态窗口，
                // 表现为「退出练习后点导航栏无反应」（V2.35.3 引入的回归）。隐藏时整体下移出屏，点击通道仍注册但不挡内容。
                val navOffsetY by animateDpAsState(
                    targetValue = if (navHidden) 160.dp else 0.dp,
                    animationSpec = Motion.springSteady(rm),
                    label = "navOffset"
                )
                val navAlpha by animateFloatAsState(
                    targetValue = if (navHidden) 0f else 1f,
                    animationSpec = Motion.springSteady(rm),
                    label = "navAlpha"
                )
                Box(
                    Modifier.fillMaxWidth()
                        .then(if (isWide) Modifier.widthIn(max = 720.dp) else Modifier)
                        .align(Alignment.BottomCenter)
                        .offset(y = navOffsetY)
                        .alpha(navAlpha)
                ) {
                    GlassNavBar(nav)
                }
                // 启动遮罩（纯 Compose 自绘，零新依赖）：首帧渲染后短暂停留，logo 缩放+淡出退场
                AnimatedVisibility(
                    visible = showSplash,
                    enter = fadeIn(tween(0)),
                    exit = fadeOut(tween(Motion.duration(rm, Motion.SLOW))) +
                        scaleOut(targetScale = 1.12f, animationSpec = tween(Motion.duration(rm, Motion.SLOW))),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        val logoScale by animateFloatAsState(
                            targetValue = if (showSplash) 1f else 0.8f,
                            animationSpec = Motion.springSteady(rm),
                            label = "splashLogo"
                        )
                        Icon(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp).graphicsLayer {
                                scaleX = logoScale
                                scaleY = logoScale
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 悬浮底部导航：轻量 NavSurface（实色 surfaceContainerLow + 柔和投影）绝对定位于屏幕底部，
 * 透出底层主背景，发丝边框 + 柔和投影，内容列表可从其下方滚过。
 * 选中项高亮态沿用 primaryContainer 低透明底色，未选中为纯透明。
 */
@Composable
private fun GlassNavBar(nav: NavHostController, modifier: Modifier = Modifier) {
    val rm = reduceMotionNow(LocalContext.current)
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
                        animationSpec = Motion.springSteady(rm),
                        label = "navBg"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = if (selected) 24.dp else 22.dp,
                        animationSpec = Motion.springSteady(rm),
                        label = "navIcon"
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clickable {
                                Haptic.tick(nav.context)
                                nav.navigate(s.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
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
