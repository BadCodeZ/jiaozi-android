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
import com.jiaozi.sz.ui.components.FloatingBackButton
import com.jiaozi.sz.ui.components.GlassSurface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.jiaozi.sz.ui.screens.AiGenScreen
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
import com.jiaozi.sz.ui.screens.PracticeSetupScreen
import com.jiaozi.sz.ui.screens.ProofScreen
import com.jiaozi.sz.ui.screens.SettingsScreen
import com.jiaozi.sz.ui.screens.SettingsAppearanceScreen
import com.jiaozi.sz.ui.screens.SettingsIslandScreen
import com.jiaozi.sz.ui.screens.SettingsGoalScreen
import com.jiaozi.sz.ui.screens.SettingsSubjectScreen
import com.jiaozi.sz.ui.screens.SettingsStageScreen
import com.jiaozi.sz.ui.screens.SettingsBackupScreen
import com.jiaozi.sz.ui.screens.SettingsWebDavScreen
import com.jiaozi.sz.ui.screens.SettingsAiScreen
import com.jiaozi.sz.ui.screens.SettingsOnboardingScreen
import com.jiaozi.sz.ui.screens.AboutScreen
import com.jiaozi.sz.ui.screens.StatsScreen
import com.jiaozi.sz.ui.screens.TodayScreen
import com.jiaozi.sz.ui.screens.BankDownloadScreen
import com.jiaozi.sz.ui.screens.BankManageScreen
import com.jiaozi.sz.xiaomi.Haptic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed class Screen(val route: String, val label: String, val iconRes: Int) {
    object Today : Screen("today", "今日", R.drawable.ic_today)
    object Practice : Screen("practice", "练习", R.drawable.ic_edit)
    object Bank : Screen("bank", "题库", R.drawable.ic_book)
    object Stats : Screen("stats", "统计", R.drawable.ic_bars)
    object Mine : Screen("mine", "我的", R.drawable.ic_person)
}

/**
 * 底部主导航：5 个入口，避免窄屏 6 tab 拥挤；图谱/设置收入"我的"。
 *
 * 🔴 2026-09-20 订正：原注释已如此宣称，但「我的」页四组入口**实际均无 graph** ⇒ 名不副实
 * （用户报「找不到图谱页入口」）。已按 07 号 E4 + overlap_policy 在『内容』组补齐
 * `MineEntry("tree","知识图谱",…,"graph")`，注释与实现自此一致。
 */
val bottomItems = listOf(
    Screen.Today, Screen.Practice, Screen.Bank, Screen.Stats, Screen.Mine
)

/** 二级页面路由 → 顶栏标题（用于固定顶栏返回键，仅二级页显示，底部 5 页不显示） */
val secondaryTitles = mapOf(
    "settings" to "设置",
    "about" to "关于",
    "graph" to "知识图谱",
    "chapters" to "章节管理",
    "lesson" to "备课组",
    "curric" to "课标库",
    "books" to "教材管理",
    "inbox" to "收集箱",
    "proof" to "校订",
    "aichat" to "AI 助手",
    "knowledge" to "知识库",
    "search" to "搜索",
    "practicesetup" to "练习设置",
    "aigen" to "AI 生成",
    // 🔴 2026-09-26 新增：设置组 8 个二级页（方案 B「独立二级页统一跳转」）
    //    见 11 号规范 `revision_20260926` —— 原「行内就地展开手风琴」整体退场。
    "settings_appearance" to "外观主题",
    "settings_island" to "灵动岛",
    "settings_goal" to "学习目标",
    "settings_subject" to "科目三 · 学科",
    // 🔴 2026-09-28 学段筛题：报考学段二级页（与「科目三 · 学科」并列，同属偏好设置组）
    "settings_stage" to "报考学段",
    "settings_backup" to "本地备份",
    "settings_webdav" to "WebDAV 同步",
    "settings_ai" to "AI 配置",
    "settings_onboarding" to "新手引导",
    // 🔴 2026-09-28 题库外置：首启下载引导 + 题库管理二级页
    "bankdownload" to "下载题库",
    "bankmanage" to "题库管理"
)

/**
 * 已自带 HeroHeader 的二级屏：标题由 HeroHeader 承载，悬浮返回条只留返回箭头（showTitle=false），避免标题重复。
 * 其余二级屏仍由悬浮返回条显示标题。
 *
 * 🔴 2026-09-21 增补 `aichat`：AI 助手页**页内自带标题行**（「AI 助手」+ 新建对话 + 菜单，见 `AiChatScreen`），
 * 而悬浮返回条默认还会再画一枚「AI 助手」标题胶囊 ⇒ **同屏出现两个标题**（真机实测）。
 * 按稿（页内只有一条标题行）登记本集合：返回条只留返回圆，标题交给页内。
 */
val heroHeaderRoutes = setOf(
    "settings", "proof", "chapters", "graph", "aichat", "search",
    // 2026-09-26：设置组 8 个二级页 —— 标题由页内 Hero 承载，悬浮条只留返回箭头
    "settings_appearance", "settings_island", "settings_goal", "settings_subject", "settings_stage",
    "settings_backup", "settings_webdav", "settings_ai", "settings_onboarding",
    // 🔴 2026-09-28 题库外置：两页均自带 immersive HeroHeader，标题由 Hero 承载
    "bankdownload", "bankmanage"
)

/**
 * 🔴🔴 **沉浸 Hero 二级页**（2026-09-19 新增；**2026-09-22 扩面**）：这些二级页自带
 * `HeroHeader(immersive = true)`，Hero 背景要**铺到屏幕顶**（状态栏白字直接压在渐变上），
 * 因此**必须豁免下方 56dp 返回件占位**，且**不再叠加全局悬浮返回件**。
 *
 * 不豁免的后果（2026-09-19 实测）：`AppNav` 给二级页统一留 56dp 顶部内边距给悬浮返回键，
 * 而 `HeroHeader` 内部只向上溢出 `statusBarInset`（24dp）⇒ 屏幕顶部露出 **56dp 页面底色**
 * （实测灰带 y=0..223px ÷ 4 = 56dp，Hero 蓝从 y=224 才开始），沉浸观感全废。
 *
 * ⚠️ 判定口径：一级 Tab（`isPrimaryTab`）本就不留 56dp，无需入此集合；
 * 本集合只登记「二级页 + 沉浸 Hero」的组合。新增此类页面时**必须同步登记**。
 *
 * 🔴 2026-09-22 按杰哥裁定「二级界面 Hero 统一沉浸通栏」扩面：原仅 `inbox`/`proof`，
 * 现并入原「卡片派」6 页（教材/课标库/章节/知识库/图谱/设置）+ 备课组/教案模板，
 * 全站二级页 Hero 收敛为**同一形态**。
 */
val immersiveHeroRoutes = setOf(
    // 原始两页（2026-09-19）
    "inbox", "proof",
    // 2026-09-22 扩面：原卡片派二级页
    "books", "curric", "chapters", "knowledge", "graph", "settings",
    // 2026-09-22 扩面：备课组与教案模板（此前已沉浸，此处补登记口径一致性）
    "lesson",
    // 2026-09-26：设置组 8 个二级页（外壳统一走 SettingsDetailPage，Hero 均 immersive = true）
    "settings_appearance", "settings_island", "settings_goal", "settings_subject", "settings_stage",
    "settings_backup", "settings_webdav", "settings_ai", "settings_onboarding",
    // 🔴 2026-09-28 题库外置：下载引导 / 题库管理均 immersive Hero
    "bankdownload", "bankmanage"
)

/**
 * 🔴🔴 **沉浸 Hero 页自带返回入口**（2026-09-22 新增）：`HeroHeader(onBack = …)` 把返回键
 * **内联在 Hero 首行左端槽位**，故全局悬浮返回件与「收起态磨砂返回条」都**不应再出现**
 *（后者所属的折叠机制已于 2026-09-21 停用、2026-09-25 彻底退场）。
 *
 * 与 [immersiveHeroRoutes] 的区别：
 * - `immersiveHeroRoutes` = 只豁免 56dp 顶距（返回件仍可由 AppNav 叠加，如收集箱/校订曾靠
 *   `onBack` 内联 + 本集合豁免来避免重叠）；
 * - `selfBackRoutes`（本集合）= 页面**完全自理**返回入口。若某页外框有自己的一级子页
 *   （如教材/课标库的 Detail 态自带 `GlassBackButton`），登记本集合即可让全局件彻底让位。
 *
 * ⚠️ 只登记「确实已内联 `onBack`」的页面，避免用户失去返回入口。
 */
val selfBackRoutes = setOf(
    "inbox", "proof", "lesson",
    "books", "curric", "chapters", "knowledge", "graph", "settings",
    // 2026-09-26：设置组 9 个二级页 —— 返回键内联在 Hero 首行左端槽，全局悬浮件彻底让位
    // 🔴 2026-09-28 补漏：settings_stage（报考学段）此前漏登记 ⇒ 与全局悬浮返回件同屏出现「双返回键」
    "settings_appearance", "settings_island", "settings_goal", "settings_subject", "settings_stage",
    "settings_backup", "settings_webdav", "settings_ai", "settings_onboarding",
    // 🔴 2026-09-28 题库外置：两页均自带返回入口（BankManage 内联 onBack；BankDownload 为强制首启页，无返回）
    "bankdownload", "bankmanage"
)

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
    // 悬浮返回条：二级界面显示（练习是一级 Tab，天然排除；lesson 为内部多视图状态机，
    // 由其自身组件（LessonHub/LessonEditor/LessonTemplateLibrary）渲染状态感知的返回键，避免绕过未保存保护）
    val showFloating = !isPrimaryTab && currentRoute != "lesson"
    // 练习答题会话（仅限练习页内、未完成）。所有会话入口（练习首页 / 题库章节 / 今日推荐 /
    // 搜索单题）都会先 navigate 到 practice，故该判据完整覆盖各种题型。
    // 🔴 2026-09-19：答题中【一律隐藏】底栏（原仅隐藏限时模考）——普通练习保留底栏会与
    // 「上/提交/下一题」按钮行贴边，且浪费约 76dp 纵向空间。代价：答题中不能直接切去
    // 知识库/统计，需先点左上「× 退出」（退出不保留当前会话，已答仍记入错题本）。
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

    // 🔴 2026-09-28 题库外置：首启引导闭环。仅当【meta 已就绪】且【bank_init_done=false】才跳转，
    // 避免默认值 false 在 init 读 meta 前误触发（导致老用户闪现下载页且无法退出）。
    // 跳转用 popUpTo(start) inclusive，使下载页成为唯一栈底；完成后 BankDownloadScreen 自行跳 today。
    val bankInitDone by appVm.bankInitDone.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    LaunchedEffect(metaLoaded, bankInitDone) {
        if (metaLoaded && !bankInitDone && currentRoute != "bankdownload") {
            nav.navigate("bankdownload") {
                popUpTo(nav.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    // 系统「减少动态效果」开关：仅在 @Composable 作用域算一次，普通 Boolean 可安全捕获进非组合回调
    val rm = reduceMotionNow(LocalContext.current)
    // 启动遮罩：冷启动时先显示品牌 logo 居中，首帧组合完成后短暂停留再 scale+alpha 退场，
    // 消除 Android 冷启动白屏断层（参考椒盐 SplashScreen 退出动画思路，但零新依赖、纯 Compose 自绘）。
    // 减少动态效果时直接跳过遮罩（不闪）。
    //
    // 🔴 2026-09-19：退场时机与 `metaLoaded` 联动（原为固定 delay(450)，导致骨架屏被遮挡而永不可见）。
    //   时序 = 最短停留 450ms（保证 logo 可辨识）→ 等首屏 meta 就绪 → 最长兜底 1500ms。
    //   · meta 快（常见 <100ms）⇒ 450ms 即退场，观感与旧版一致
    //   · meta 慢 ⇒ 退场后由页面自身骨架屏接管，用户看到结构占位而非白屏
    var showSplash by remember { mutableStateOf(!rm) }
    LaunchedEffect(Unit) {
        if (rm) return@LaunchedEffect
        kotlinx.coroutines.delay(450)                                  // 最短停留：品牌可见性下限
        withTimeoutOrNull(1050) { appVm.metaLoaded.first { it } }      // 上限 450+1050=1500ms，防无限等待
        showSplash = false
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // 固定顶栏仅保留状态栏占位（含挖孔）；返回键与标题改为圆形磨砂悬浮件 + 标题胶囊叠加在内容之上，
            // 不再占用 Scaffold 顶栏高度，减少屏幕纵向占用（详见下方 FloatingBackButton overlay）。
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars.union(WindowInsets.displayCutout)))
        },
    ) { inner ->
        CompositionLocalProvider(LocalAppVm provides appVm, LocalPracticeVm provides practiceVm) {
            Box(Modifier.fillMaxSize()) {
                // 平板/横屏适配：宽屏约束内容最大宽度并居中，避免单栏拉得过宽。
                // 🔴 2026-09-21 按 14 号高保真稿把阈值 600 → **720dp**：稿面响应式区明确标注
                //    「竖屏（<720dp）」不居中 /「平屏·横屏（≥720dp）居中」，且 responsive.tokens 记
                //    `sz.navbar.maxWidth = 720dp`。原 600 沿用 Android 通用 sw600dp 平板判定，与稿不符。
                //    ⚠️ 影响面：600~719dp 设备（小平板 / 折叠屏半开）由「居中限宽」变回「通栏」。
                val isWide = LocalConfiguration.current.screenWidthDp >= 720
                // 内容区：顶部留出顶栏高度；内容延展到导航之下（导航为半透明玻璃，内容从身后透出 = 四周透明）
                Box(Modifier.fillMaxSize().padding(inner).then(if (isWide) Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter) else Modifier)) {
                    // 内容区微质感：极淡 surface→surfaceVariant 垂直渐变（仅内容区；顶栏/底栏仍为纯 surface）
                    // 二级界面顶部留出 56dp（44dp 圆钮 + 上下 6dp）给悬浮返回键，避免正文被遮挡
                    Box(
                        Modifier
                            .fillMaxSize()
                            // ⚠️ 沉浸 Hero 二级页豁免这 56dp：Hero 自己负责顶部（背景铺到屏幕顶），
                            // 再留占位会让屏幕顶部露出 56dp 页面底色（见 immersiveHeroRoutes 注释）。
                            .then(if (showFloating && currentRoute !in immersiveHeroRoutes) Modifier.padding(top = 56.dp) else Modifier)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    ) {
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
                            composable("about") { AboutScreen() }
                            // 2026-09-26：设置组 8 个二级页（原「行内就地展开」改为独立路由）
                            composable("settings_appearance") { SettingsAppearanceScreen(nav) }
                            composable("settings_island") { SettingsIslandScreen(nav) }
                            composable("settings_goal") { SettingsGoalScreen(nav) }
                            composable("settings_subject") { SettingsSubjectScreen(nav) }
                            composable("settings_stage") { SettingsStageScreen(nav) }
                            composable("settings_backup") { SettingsBackupScreen(nav) }
                            composable("settings_webdav") { SettingsWebDavScreen(nav) }
                            composable("settings_ai") { SettingsAiScreen(nav) }
                            composable("settings_onboarding") { SettingsOnboardingScreen(nav) }
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
                            composable("practicesetup") { PracticeSetupScreen(nav) }
                            composable("aigen") { AiGenScreen(nav) }
                            // 🔴 2026-09-28 题库外置：首启下载引导 + 题库管理
                            composable("bankdownload") { BankDownloadScreen(nav) }
                            composable("bankmanage") { BankManageScreen(nav) }
                        }
                    }
                    // 悬浮返回键（仅二级界面）：常驻【左上角圆形磨砂悬浮件】+ 右侧标题胶囊，
                    // 覆盖在内容之上、不占 Scaffold 顶栏高度（2026-09-18 P2 统一，见 GlassBackButton）
                    //
                    // 🔴🔴 沉浸 Hero 二级页（immersiveHeroRoutes）**不再叠加本返回件**（2026-09-19）：
                    //   本件是 `Alignment.TopCenter` 的最上层 overlay，与 Hero 内容完全独立 ⇒ 必然压住
                    //   Hero 的 46dp 图标徽章（实测收集箱/校订两页「磨砂白圆 + 标题胶囊」正落在图标上）。
                    //   这两页改为：返回键**内联在 Hero 首行**（`HeroHeader(onBack = …)`），
                    //   （原「收起态由 CollapsedHubBar(leading = …) 承载」已随折叠退场删除，)
                    //   仍满足 02 号全局规则第 5 条「返回入口全局唯一」的精神（唯一 = 每个页面只一个返回入口）。
                    if (showFloating && currentRoute !in selfBackRoutes) {
                        Box(Modifier.align(Alignment.TopCenter)) {
                            FloatingBackButton(nav, secondaryTitles[currentRoute] ?: "", showTitle = currentRoute !in heroHeaderRoutes)
                        }
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
                        // 🔴 2026-09-21 按 14 号高保真稿：开屏补 slogan「让学习更简单」
                        //    （稿面 logo 下方一行；开屏是本 App 允许承载品牌/IP 文案的合法节点之一）
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).graphicsLayer {
                                    scaleX = logoScale
                                    scaleY = logoScale
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "让学习更简单",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 2.sp
                            )
                        }
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
