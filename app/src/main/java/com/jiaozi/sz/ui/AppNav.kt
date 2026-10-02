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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.FloatingBackButton
import com.jiaozi.sz.ui.components.GlassPanel
import com.jiaozi.sz.ui.components.LiquidGlass
import com.jiaozi.sz.ui.components.AppColors
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import com.jiaozi.sz.ui.screens.WeaknessScreen
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
    "weakness" to "薄弱点攻坚",
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
    // 2026-09-29：薄弱点攻坚（独立二级页，Hero 沉浸式通栏，标题由 Hero 承载）
    "weakness",
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
    // 🔴 2026-10-01 九校补漏：练习设置（练习设置页自带「✕ 练习设置」顶栏）。
    //   此前漏登记 ⇒ 全局悬浮返回件与页面顶栏**同屏并存**，杰哥实测截图里
    //   「← 练习设置」（全局件）与「✕ 练习设置」（页面顶栏）上下两行双返回入口。
    "practicesetup",
    // 2026-09-29：薄弱点攻坚自带 Hero 内联 onBack，全局悬浮返回件彻底让位
    "weakness",
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
    val showFloating = !isPrimaryTab && currentRoute != "lesson" && currentRoute != "weakness"

    // 🔴🔴 2026-10-01 九校：全局悬浮返回件的**唯一判据**（修「双返回入口」类缺陷的根因）。
    //
    //  缺陷形态：顶部留白（56dp）原先只看 [showFloating]，而**渲染**悬浮件还额外排除
    //  [selfBackRoutes] —— 两个判据不一致。于是任何「已登记 selfBackRoutes、但不在
    //  [immersiveHeroRoutes]」的页面会同时踩两个坑：
    //    ① 页面自己的返回入口 + 全局悬浮件**同屏并存**（双返回键）；
    //    ② 悬浮件已让位，却仍空出 56dp 死区。
    //  `practicesetup` 正是这样漏登记的（杰哥实测：「← 练习设置」与「✕ 练习设置」上下两行）。
    //  ⇒ 收敛为**一个** `globalBack` 判据，渲染与留白同源，从结构上杜绝再次漂移。
    val globalBack = showFloating && currentRoute !in selfBackRoutes
    // 练习答题会话（仅限练习页内、未完成）。所有会话入口（练习首页 / 题库章节 / 今日推荐 /
    // 搜索单题）都会先 navigate 到 practice，故该判据完整覆盖各种题型。
    // 🔴 2026-09-19：答题中【一律隐藏】底栏（原仅隐藏限时模考）——普通练习保留底栏会与
    // 「上/提交/下一题」按钮行贴边，且浪费约 76dp 纵向空间。代价：答题中不能直接切去
    // 知识库/统计，需先点左上「× 退出」（退出不保留当前会话，已答仍记入错题本）。
    val inPracticeAnswering = currentRoute == Screen.Practice.route
        && practiceState.questions.isNotEmpty() && !practiceState.finished
    // 隐藏 = 非一级 Tab（即二级界面），或（练习页内正在答题）
    val navHidden = !isPrimaryTab || inPracticeAnswering

    // 🔴 2026-09-30 晚 杰哥裁定「**去掉滚动收缩**」：导航栏固定保持展开形态（62dp + 文字标签），
    //   不再随内容滚动缩成仅图标。原先的 navCollapse 状态机（`onPostScroll` 驱动）连同内容区的
    //   `Modifier.nestedScroll` 挂载点一并删除，避免残留「一上滑就缩」的行为。

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
                // 🔴🔴 2026-10-01 背景捕获层（kyant Liquid Glass 的前提）：
                //   内容树必须挂 `Modifier.layerBackdrop(backdrop)` 把自己录进 backdrop 的
                //   GraphicsLayer，底部玻璃导航的 `drawBackdrop(backdrop, …)` 才采得到像素。
                //   此前导航栏自建空 backdrop ⇒ 无 blur / 无 lens ⇒ 真机表现为「内容原样穿透」。
                //   本 backdrop 挂在最外层 Box，两个消费点共享：内容 Box（录）+ 导航栏（采样）。
                val navBackdrop = rememberLayerBackdrop()
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
                            // 内容录进共享 backdrop（供底部玻璃导航实时采样/模糊/折射）
                            .layerBackdrop(navBackdrop)
                            // ⚠️ 沉浸 Hero 二级页豁免这 56dp：Hero 自己负责顶部（背景铺到屏幕顶），
                            // 再留占位会让屏幕顶部露出 56dp 页面底色（见 immersiveHeroRoutes 注释）。
                            .then(if (globalBack && currentRoute !in immersiveHeroRoutes) Modifier.padding(top = 56.dp) else Modifier)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    )
                                )
                            )
                            // 🔴 2026-10-02 键盘统一让位（配合 Manifest 的 adjustResize）：
                            //   系统不再平移窗口，改由这里**一次性**把 IME 高度从内容区扣掉 ——
                            //   所有输入页（共 16 个）无需各自写 imePadding 即可在键盘下正确可见。
                            //   放在 `.background()` **之后**：渐变背景仍铺满整屏，只有子内容被内缩。
                            //   ⚠️ 各屏**不要**再自行加 imePadding，否则又变成双重让位（ai 对话页已移除）。
                            .imePadding()
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
                            composable("weakness") { WeaknessScreen(nav) }
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
                    if (globalBack) {
                        Box(Modifier.align(Alignment.TopCenter)) {
                            // 🔴 2026-10-01 九校：传入共享背景捕获层 ⇒ 返回件与标题胶囊也获得**真折射**。
                            //   合法性的前提是本 overlay **不在** `layerBackdrop` 节点的子树内 ——
                            //   它挂在内容层 Box 上（与挂 `layerBackdrop` 的 NavHost 内层 Box 平级），
                            //   因此不会把自己的上一帧录进 backdrop（否则会自采样、越叠越糊）。
                            //   各屏**内联**的返回/关闭件位于 NavHost 内部（在录制范围内）⇒ 一律不传。
                            FloatingBackButton(nav, secondaryTitles[currentRoute] ?: "", showTitle = currentRoute !in heroHeaderRoutes, backdrop = navBackdrop)
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
                // 2026-09-30：原「内容淡出遮罩」独立浮层已删除——GlassPanel 现走 kyant drawBackdrop
                // 真折射，导航栏背后的滚动内容被实时模糊"化开"，无需再罩一层渐变遮字。
                Box(
                    Modifier.fillMaxWidth()
                        .then(if (isWide) Modifier.widthIn(max = 720.dp) else Modifier)
                        .align(Alignment.BottomCenter)
                        .offset(y = navOffsetY)
                        .alpha(navAlpha)
                ) {
                    GlassNavBar(nav, navBackdrop)
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
 * 底部主导航（iOS 26 Liquid Glass，2026-09-30 五次重做 · 严格对齐 UFIPanel）。
 *
 * 形态逐项对齐 UFIPanel.apk（com.minikano.ufitools.client）底部导航的**实测像素**（1080×2400 @440dpi，1dp=2.75px）：
 *  - **悬浮胶囊（近满宽）**：左右各离屏 [LiquidGlass.SideInset]（**23dp**）⇒ 393dp 屏上容器
 *    **≈347.4dp**（参考 UFI 实测 954px = 347dp）；高 [LiquidGlass.Height]（64dp）、圆角 = 高 / 2；
 *  - **容器内壁留白** [LiquidGlass.ContentInset]（**4dp**）：内容带比容器窄 ⇒ 选中胶囊外弧与
 *    容器边缘之间留出 ≈4dp 呼吸位（参考实测 10.75px/侧）。**这两项必须联动**，见 token 注释；
 *  - **无描边**：参考图胶囊边缘仅靠「白色填充对比 + 投影」成形，**不画**发丝边；
 *  - **五等分**：每格 **67.9dp ≈ 186.7px**（参考 186.5px）；图标在上 / `labelSmall` 在下，居中；
 *  - **选中指示器**：一枚**中性色**大圆角底，**同时托住图标与文字**——既不是主色药丸，
 *    也不是只在图标后的圆点。2026-10-01 三校后的几何与材质：
 *      · 高 [LiquidGlass.IndicatorHeight] 58dp（胶囊 64dp ⇒ 上下各留 3dp）；
 *      · 宽 = 格宽 − 2×[LiquidGlass.IndicatorInset]（**0**）⇒ **贴满格宽**（≈186.7px，
 *        参考实测 185px）；左右各留 4dp 由上面的容器内壁留白提供；
 *      · 圆角 [LiquidGlass.IndicatorRadius] 29dp（= 半高）；
 *      · 材质＝**第二层真折射玻璃**（[LiquidGlass.IndicatorSpec]）+ 10% 中性黑（叠白玻璃实测 #E5E5E5）；
 *  - **选中着色**：图标 + 文字整体染主色；未选中 = `onSurface`（参考图实测纯黑，非灰）；
 *  - **固定展开**：不随滚动收缩（2026-09-30 晚 杰哥裁定去掉 scroll-to-minimize）。
 *
 *  ## 动效（2026-10-01 八校定稿）
 * 两个**互相独立**的物理量，刻意分工（见 [LiquidGlass.IndicatorSpring] / [LiquidGlass.JellySpring]）：
 *  1. `progress`（胶囊中心位置）：ζ 0.78 ⇒ 过冲仅 2%，**稳、准、快**（≈0.23s）。
 *     「惯性减小」就是这一步 —— 位移不再冲过头再荡回来。
 *  2. `jelly`（胶囊自身宽高）：ζ 0.38 ⇒ 过冲 27.5%，**拉长 → 反向收窄 → 回弹 → 落定**（≈0.58s）。
 *     「果冻/气泡弹性形变与回弹」全部来自它，且幅度**随跨格距离变化**（近邻 0.70 / 远端 1.0）。
 *  ⇒ 位移负责「稳」，形变负责「弹」；位移停了形变还在晃两下，这就是落地的余韵。
 *
 * 交互（⑤-1）：按压/悬停在**非选中项**上时，该格浮现一枚主色描边「预览轮廓」
 *  （[LiquidGlass.PreviewFillAlpha] / [LiquidGlass.PreviewStrokeAlpha]），并让图标放大到 1.10。
 *  行为响应 = **按下只预览、抬起才导航** ⇒ 误触不会切页。
 *  触屏上没有真 hover，手指按住未松就是它的对应物（走 `pressed`）；鼠标/触控笔走 `hovered`。
 */
@Composable
private fun GlassNavBar(
    nav: NavHostController,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    val rm = reduceMotionNow(LocalContext.current)
    val height = LiquidGlass.Height
    val shape = RoundedCornerShape(height / 2)
    val indicator = if (AppColors.isDark) LiquidGlass.IndicatorDark else LiquidGlass.IndicatorLight

    // 当前一级 Tab 下标；-1 = 不在任何一级 Tab（二级页 / 答题中 ⇒ 导航栏整体隐藏）
    val current = nav.currentBackStackEntryAsState().value?.destination
    val targetIndex = bottomItems.indexOfFirst { s ->
        current?.hierarchy?.any { it.route == s.route } == true
    }

    // 🔴🔴 2026-10-01 新增「浮动索引」progress ∈ [0, n-1]。
    //   选中胶囊的横坐标、每一项的着色与图标缩放**全部由它派生** ⇒ 三者天然严格联动，
    //   不会出现「胶囊已滑到、颜色还没跟上」的割裂（旧实现是 5 组各自独立的 animateFloatAsState，
    //   耦合靠肉眼对齐）。现在只有 1 个动画在跑，开销反而更低。
    val progress = remember { Animatable(0f) }
    var navProgressInited by remember { mutableStateOf(false) }

    // 🔴🔴 2026-10-01 八校新增「果冻形变量」jelly —— **独立于位移的第二个物理量**。
    //   语义：0 = 原始尺寸；1 = 被甩到最大变形（宽 +7.5% / 高 −5%）；
    //        **负值 = 回弹期的反向形变**（宽收窄 / 高回涨），由 [LiquidGlass.JellySpring] 的
    //        27.5% 过冲天然产生 —— 这一帧就是「果冻被弹回来」的来源。
    //
    //   🔴🔴 为什么必须与 progress **分离**（本轮最关键的一条）：
    //     七校把形变量写成 `速度的即时函数`（`bubbleW = indW × (1 + k·intensity)`）——
    //     它**结构上不可能有回弹**：速度是单峰的，速度归零形变就归零，全程单调。
    //     八校改为让形变有自己的二阶动力学（[LiquidGlass.JellySpring]），于是能
    //     `拉满 → 反向过冲 → 回弹 → 落定`，而且**位移停了它还在晃**（0.23s vs 0.58s）。
    val jelly = remember { Animatable(0f) }

    // 🔴🔴 2026-10-01 七校新增「运动中」标志。
    //   为什么需要它：速度驱动的量（拖尾 alpha / 拖尾滞后 / 速度门控部分）必须在动画结束后
    //   **精确归零**，而 `Animatable` 停止时**不会清零** `velocity` —— 它保留最后一次迭代的速度
    //   （受 `visibilityThreshold` 约束，残值可达 ~2.1 格/秒）。若直接拿它算 intensity，拖尾会
    //   长期停在「淡影」状态上，既不干净也不可预测。
    //   ⇒ 用本标志把非运动期的 intensity 强制归零。
    //   ⚠️ 它同时是**绘制层的失效信号**：`navAnimating` 是 `mutableStateOf`，由 true→false 的那次
    //   重组会把 intensity=0 写进 `graphicsLayer` 的 lambda ⇒ layer 真正被刷新一次。
    //   若改用 `progress.isRunning`（**非 State**）就收不到这次刷新，拖尾会停在最后一帧。
    var navAnimating by remember { mutableStateOf(false) }
    LaunchedEffect(targetIndex) {
        if (targetIndex < 0) return@LaunchedEffect          // 隐藏态：保持原位，不做无意义位移
        val t = targetIndex.toFloat()
        if (!navProgressInited || rm) {
            progress.snapTo(t)                              // 首帧落位 / 减少动态效果：瞬时
            navProgressInited = true
        } else {
            // 形变幅度**随跨格距离动态变化**（杰哥需求「随尺寸大小动态变化」）：
            //   跨 1 格 ⇒ 0.70、跨 2 格 ⇒ 0.86、≥3 格 ⇒ 饱和到 1.0。
            //   近邻切换本来就"轻"，若也拉满 7.5% 会显得夸张；远距离跃迁才值得一整下果冻。
            val dist = abs(t - progress.value)
            val kick = (0.54f + 0.16f * dist).coerceAtMost(1f)
            navAnimating = true
            try {
                coroutineScope {
                    // ① 形变支线：被甩变形（120ms 上升）→ 松手自由衰减
                    //    （欠阻尼 ⇒ 反向过冲 27.5% → 回弹 → 落定，共 ≈0.58s）
                    launch {
                        jelly.animateTo(
                            kick,
                            tween(LiquidGlass.JellyKickMs, easing = LinearOutSlowInEasing)
                        )
                        jelly.animateTo(0f, LiquidGlass.JellySpring)
                    }
                    // ② 位移主线：干脆利落（ζ 0.78 ⇒ 过冲仅 2%）
                    progress.animateTo(t, LiquidGlass.IndicatorSpring)
                    // 🔴 收尾清残值：`animateTo` 结束后 velocity 不清零（见上方 navAnimating 注释），
                    //   而拖尾的 alpha / 滞后量都门控在 `abs(velocity)/ref`。这里 snapTo 到同一值
                    //   ⇒ 下一次 `updateState` 把 velocity 写成 0 ⇒ 拖尾当帧彻底消失，不留淡影。
                    //   （不会打断 jelly 支线：两者是独立 Animatable。）
                    progress.snapTo(t)
                }
            } finally {
                navAnimating = false
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(
                start = LiquidGlass.SideInset,
                end = LiquidGlass.SideInset,
                bottom = LiquidGlass.BottomInset
            )
    ) {
        // 🔴🔴🔴 2026-10-01 七校 · 层级重构（杰哥本轮需求 ②：「超出导航栏范围时叠加在导航栏
        //   上层渲染，避免被导航栏边缘直接裁剪或切割」）。
        //
        //   旧结构：导航玻璃 `GlassPanel` **包着** BoxWithConstraints —— 而 `GlassPanel` 内部有
        //     `.clip(shape)`（见 Glass.kt），于是胶囊与拖尾被**双重裁剪**（导航圆角 + 容器边界）：
        //     一旦因惯性滞后或尺寸变化冲出栏边就立刻被切平。实测拖尾满强度时滞后 0.57 格
        //     ≈ 38dp，**必然**冲出左/右边缘。
        //
        //   新结构：把坐标系容器提到与导航玻璃**平级**，玻璃退化为「只画材质、不承载内容」的底层：
        //
        //     BoxWithConstraints（统一坐标系，导航栏全宽）
        //       ├ ① GlassPanel（导航玻璃底）        ← 自带 clip，但只裁自己
        //       └ ② Box 内容带（4dp 内壁留白，**不裁剪**）
        //            ├ ③ 拖尾层 ×2   ← 可溢出栏外
        //            ├ ④ 主胶囊      ← 可溢出栏外 + 可「果冻」形变（jelly 驱动）
        //            └ ⑤ 图标 Row    ← **最后声明** ⇒ 永远压在最上层
        //                 ├ ⑤-1 预览轮廓（仅按压/悬停且非选中项时进树）
        //                 └ ⑤-2 图标 + 文字
        //
        //   ⇒ 胶囊/拖尾自此「叠加在导航栏上层渲染」，溢出部分正常画在页面内容之上，不再被切。
        //   ⚠️ 层级顺序不可调换：图标必须在胶囊之后（沉到玻璃层下会被折射糊掉）；玻璃必须最先。
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            // 5 等分格宽（与下方 Row 的 weight(1f) 严格同宽 ⇒ 胶囊中心天然对准格中心）。
            // 🔴 2026-10-01 七校：内壁留白不再加在本 BoxWithConstraints 上（否则 maxWidth
            //   已被吃掉、这里会**重复内缩**），改由下方 ② 内容带承担 ⇒ 公式补上 2×ContentInset。
            val cellW = (maxWidth - LiquidGlass.ContentInset * 2) / bottomItems.size
            val indW = cellW - LiquidGlass.IndicatorInset * 2
            val indShape = RoundedCornerShape(LiquidGlass.IndicatorRadius)
            // 读浮动索引 ⇒ 本作用域逐帧重组（与旧的 5 组独立动画等价，未增加开销）
            val p = progress.value

            // 速度强度 ∈ [0,1] —— 八校后**只驱动拖尾**（alpha / 滞后量）。
            //   （七校时它还兼顾形变与上浮，八校已把这两者移交给 `jelly`。）
            // 🔴 量纲：`progress.velocity` 的单位是 **「格/秒」**，须先换算成真实像素速度才能与
            //   dp/s 口径的 [LiquidGlass.BubbleSpeedRef] 相比。两边同除 density ⇒ **密度直接约掉**，
            //   用 Dp 的数值比即可，天然跨密度安全：
            //     speedRefCells = 1200dp ÷ 67.9dp ≈ 17.7 格/秒（= 满强度对应的速度）
            //   （旧写法漏了 × 格宽，在 440dpi 真机上把强度低估 186.7 倍 —— 详见 Glass.kt 红线。）
            val speedRefCells = LiquidGlass.BubbleSpeedRef.value / cellW.value
            val intensity = if (navAnimating) {
                abs(progress.velocity).coerceAtMost(speedRefCells) / speedRefCells
            } else 0f

            // 果冻形变量（带符号）：正向 = 被甩长，负向 = 回弹期的收窄。
            //   · jelly < 0 时额外放大 [LiquidGlass.JellyReboundGain] 倍 —— 正向拉伸受「不能盖到
            //     邻格图标」的硬约束（+7.5% 已到顶），而**收窄没有这个约束**，可以放心放大，
            //     让「被弹回来」那一下真的看得见（27.5% 过冲 × 1.6 ⇒ 宽 −3.3% ≈ 6px）。
            val jellyRaw = jelly.value
            val jellyShaped =
                if (jellyRaw >= 0f) jellyRaw else jellyRaw * LiquidGlass.JellyReboundGain

            // 「果冻」形变后的实际尺寸：jelly 峰值 **宽 +7.5% / 高 −5%**（面积近似守恒），
            //   反向过冲时 **宽 −3.3% / 高 +2.2%**（就是"果冻被弹回来"的那一帧）。
            // 🔴🔴 必须走**真实布局尺寸**（width/height），**不能**用 `graphicsLayer` 缩放：
            //   `drawBackdrop` 按屏幕坐标采样 backdrop，graphicsLayer 的 scale 会把**已捕获的
            //   背景一起缩放** ⇒ 折射内容与玻璃块尺寸错配（边缘 lens 的弯折位置也随之外移）。
            //   改尺寸则每次都以**实际 bounds** 重新采样，光学效果永远正确。
            //   代价：动画期每帧一次 relayout —— 本子树只有 1 块玻璃 + 2 条尾迹，可接受。
            val bubbleW = indW * (1f + LiquidGlass.BubbleStretchX * jellyShaped)
            val bubbleH = LiquidGlass.IndicatorHeight * (1f - LiquidGlass.BubbleSquashY * jellyShaped)

            // ① 导航玻璃底：纯材质、**空内容**。不再包任何东西 ⇒ 它的 `clip(shape)` 只裁自己。
            GlassPanel(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                // 共享内容层：真折射（blur + lens + vibrancy）依赖它才有像素可采
                backdrop = backdrop,
                spec = LiquidGlass.NavSpec
            ) {}

            // ② 内容带：容器**内壁**水平留白 [LiquidGlass.ContentInset]（4dp）。
            //   参考（UFI）的 tab 内容带比容器窄 10.75px/侧，选中底贴满格宽 ⇒ 选中胶囊外弧
            //   与容器边缘之间自然留出 ≈4dp 呼吸位（这正是杰哥此前指出的「导航栏边缘与左右两端
            //   选中胶囊外弧没有间距」）。
            //   🔴🔴 本层**刻意不加任何 clip** —— 胶囊的拉伸、拖尾的滞后位移都可能冲出导航栏
            //   边界，在这里才能叠加在导航栏上层正常渲染（需求 ②）。
            Box(Modifier.fillMaxSize().padding(horizontal = LiquidGlass.ContentInset)) {

                // ③ 轨迹层（气泡拖尾）：两层纯色低透明胶囊，位移 = 当前位置 − 速度 × 滞后系数，
                //   ⇒ 移动时拖出残影、停下即消失（alpha ∝ 速度²，静止时完全不可见）。
                //   **不挂 drawBackdrop**（不新增 RenderEffect），成本≈0；
                //   也正因为是纯色层，才可以放心用 graphicsLayer 做缩放（玻璃层不行，见 ④）。
                //   🔴 溢出：满强度时首层落后 0.28 格 ≈53px、次层 0.57 格 ≈**107px** ⇒ 选中首/末格
                //     时尾迹会明显**冲出导航栏左右边缘**。旧结构下这部分被导航玻璃的 clip(shape)
                //     直接切平（一条硬边）；七校重构后它叠加在导航栏上层渲染，自然溢出（需求 ②）。
                for (k in 1..2) {
                    // 滞后时间（秒）。`v(格/秒) × lag(秒) = 滞后距离(格)`，再 × 格宽转 px。
                    val lag = 0.016f * k
                    val maxAlpha = 0.45f / k
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(indW)
                            .height(LiquidGlass.IndicatorHeight)
                            // 🔴🔴 2026-10-01 关键修复：lambda 内**必须直接读 `progress.value`（State）**，
                            //   不能读组合期捕获的普通 Float（如外层的 `val p`）。捕获普通值会让编译器
                            //   记忆化该 lambda ⇒ `GraphicsLayerElement.equals` 判等成立 ⇒ layer 永不更新
                            //   ⇒ `translationX/Y` 全部失效（实测：胶囊能移动是因为外层还靠重组换了新
                            //   lambda，而依赖速度的 `translationY`/拖尾 alpha 直接恒为 0、日志一条不出）。
                            //   读 State 后由快照系统驱动 layer 逐帧失效，反而**不再依赖重组**，更高效。
                            .graphicsLayer {
                                val pv = progress.value
                                val v = progress.velocity
                                // 🔴 量纲：velocity 是「格/秒」，除以 speedRefCells（同为「格/秒」）才无量纲。
                                //   两边都写成 Dp.value 相除 ⇒ density 约掉，跨密度天然安全（见 Glass.kt 红线）。
                                val refCells = LiquidGlass.BubbleSpeedRef.value / cellW.value
                                val vc = v.coerceIn(-refCells, refCells)
                                val inten = if (navAnimating) abs(vc) / refCells else 0f
                                // 🔴 溢出（需求 ② 的现场）：`- vc * lag` 让拖尾滞后于胶囊，
                                //   满强度时首层 0.28 格 ≈53px、次层 0.57 格 ≈**107px**。
                                //   选中首/末格时它会**冲出导航栏左右边缘** —— 这一层的父级刻意不加
                                //   clip(shape)，所以溢出部分正常叠加渲染（旧结构会被切平）。
                                translationX = (pv - vc * lag) * cellW.toPx()
                                alpha = maxAlpha * inten * inten
                                // 尾迹形变：越靠后越收窄（+ 越下沉）⇒ 收束成一条拖在主胶囊身后的「尾」；
                                // 再与主胶囊**同相**拉伸、并额外压扁一倍 ⇒ 像被空气拉细的一缕尾。
                                val s = 1f - 0.06f * k
                                scaleX = s * (1f + LiquidGlass.BubbleStretchX * inten)
                                scaleY = s * (1f - LiquidGlass.BubbleSquashY * 2f * inten)
                                translationY = 1.dp.toPx() * k * inten
                            }
                            .clip(indShape)
                            .background(indicator)
                    )
                }

                // ④ 选中胶囊 = 叠在导航胶囊上层的【第二层真折射玻璃】。
                //   · 常驻**单块**：静止时全屏仍只有 1 块第二层玻璃，与旧实现（选中格画 1 块）等价，
                //     不额外吃 RenderEffect；
                //   · 位移走 [LiquidGlass.IndicatorSpring]（欠阻尼）⇒ 抵达目标后有一次过冲回弹；
                //   · **「软糖」形变**（本轮新增）：速度驱动，横向拉伸 + 纵向压扁（见 [LiquidGlass.BubbleStretchX]）。
                //     ⚠️ 形变走 **width/height 真实尺寸**，**不是** graphicsLayer 缩放 —— 原因见上方
                //     `bubbleW/bubbleH` 的注释（drawBackdrop 按屏幕坐标采样，缩放会让折射内容错配）。
                //   · 🔴 层级：现在与导航玻璃**平级**、且声明在其后 ⇒ 叠加在导航栏上层渲染，
                //     拉伸/上浮溢出栏边的部分**不再被 `clip(shape)` 切平**（七校重构，需求 ②）。
                //   🔴 2026-10-01 五校：`graphicsLayer` 放在**外层 Box**、而非直接挂在 `GlassPanel` 的
                //     modifier 上 —— `GlassPanel` 内部还有 `drawBackdrop`（自建 layer）与 `clip`
                //     （再建一层 layer），多层 GraphicsLayerElement 合并时属性可能互相覆盖，故显式隔离。
                //     ⚠️ 六校更正：当初记的「translationY 上浮在其中失效」**是误判** —— 真正原因是
                //     量纲 bug 把 intensity 压到了 0.0035（见 Glass.kt 的 [LiquidGlass.BubbleSpeedRef]），
                //     与 layer 层数无关。隔离写法本身无害，保留。
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(bubbleW)
                        .height(bubbleH)
                        .graphicsLayer {
                            // 🔴 必须在 lambda 内读 State（见上方拖尾层注释），否则属性恒为初值。
                            val pv = progress.value
                            // 🔴 形变把宽度撑大了 `(size.width − indW)`，而 `align(Alignment.CenterStart)`
                            //   固定的是**左缘** ⇒ 必须左移半个增量，胶囊中心才仍严格压在格中心上。
                            //   `size.width` 取的是**本帧实测宽度**（GraphicsLayerScope.size），与上面的
                            //   `bubbleW` 必然同帧一致 ⇒ 既省一次重算，也**杜绝了组合期与绘制期
                            //   读到不同快照**导致的「中心忽左忽右」抖动。
                            translationX = pv * cellW.toPx() - (size.width - indW.toPx()) / 2f
                            // 🔴🔴 八校：上浮的驱动量从「速度」换成 **jelly**（与形变同源）。
                            //   为什么：速度驱动的上浮在位移结束（0.23s）就归零，而 jelly 要晃到 ≈0.58s
                            //   ⇒ 改用它之后，**位移停了胶囊还会上下浮两下**，正是"气泡落地的余韵"。
                            //   而且它与宽高形变天然同相，不会出现"已经压扁了还没落下"的割裂。
                            //   · jelly 反向（−0.275）时 translationY 变正 = 轻微**下沉**，读起来像
                            //     "落下来还被压了一下"，比单纯回到 0 更有重量感。
                            //   ⚠️ 峰值仍取 3dp（**设计选择**，不再是"会被 clip 切平"的硬约束 —— 七校
                            //     重构后胶囊已与导航玻璃平级、不受其 clip 约束，调大也不会被切）。
                            translationY = -3.dp.toPx() * jelly.value
                        }
                ) {
                    GlassPanel(
                        modifier = Modifier.fillMaxSize(),
                        shape = indShape,
                        // 🔴 2026-10-01 三校：与主胶囊同配方 = 白填充(ALPHA_LIGHT) **叠** 10% 中性黑
                        //   （`compositeOver`：白在下、黑在上），既有玻璃的提亮/均匀，又保住「选中灰」。
                        //   旧实现只传 `indicator`（10% 黑）⇒ 没有玻璃白填充层，比主胶囊「薄一层」，
                        //   真机实测灰底仅 208~222 且带明显背景梯度，而 UFI 是 229 均匀。
                        // 🔴🔴 2026-10-01 六校：基底同样必须**深浅同向**。旧写法恒取
                        //   `surfaceContainerLowest`（浅色＝白、深色＝**纯黑**）⇒ 深色下选中底
                        //   被黑 60% 压暗，再叠 10% 白也提不起来，与主胶囊一起融进 #121212。
                        //   深色改用低 alpha 白（[LiquidGlass.ALPHA_DARK]），与主胶囊的配方保持一致。
                        // 🔴🔴 2026-10-01 十四校（杰哥：「深色模式下被选中态的胶囊透光只是比导航栏灰一些，
                        //   而不是现在闷闷的不透」）：**选中态的叠加量必须远小于基底**，否则两层一叠
                        //   总不透明度翻倍（0.10 ⊕ 0.10 = 0.19）⇒ 透光从 90% 掉到 81%、内部灰度 42→63，
                        //   读起来是"一块实心浅灰板"而非玻璃。深色侧改用 [LiquidGlass.ALPHA_DARK_SELECT]
                        //   （0.03 ⇒ 总 0.127、透光 87.3%、内部 48），与导航栏**同档薄度**、只留一档色偏。
                        //   ⚠️ 浅色侧维持不变（`IndicatorLight` 10% 黑叠白玻璃，实测 226 vs 导航 237）。
                        containerColor = indicator.copy(
                            alpha = if (AppColors.isDark) LiquidGlass.ALPHA_DARK_SELECT else indicator.alpha
                        ).compositeOver(
                            if (AppColors.isDark) Color.White.copy(alpha = LiquidGlass.ALPHA_DARK)
                            else MaterialTheme.colorScheme.surfaceContainerLowest
                                .copy(alpha = LiquidGlass.ALPHA_LIGHT)
                        ),
                        backdrop = backdrop,
                        spec = LiquidGlass.IndicatorSpec
                    ) {}
                }

                // 预览轮廓的圆角：比真胶囊小一圈（按 [LiquidGlass.PreviewInset] 内缩半径），
                //   视觉上明确是「轮廓/占位」而不是一枚实心胶囊。
                val previewShape =
                    RoundedCornerShape(LiquidGlass.IndicatorRadius - LiquidGlass.PreviewInset)

                // ⑤ 图标 + 文字：永远在最上层（沉到玻璃层下会被折射糊掉）
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomItems.forEachIndexed { i, s ->
                        // 🔴 与胶囊位置**严格联动**：胶囊中心压在本项正上方时 sel=1；
                        //   压在相邻两项中点时各 0.5 ⇒ 颜色/缩放随胶囊实时推移，不需要各自开动画。
                        val sel = (1f - abs(p - i)).coerceIn(0f, 1f)
                        val fg = lerp(
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.primary,
                            sel
                        )

                        // 🔴🔴 2026-10-01 八校新增：悬停 / 按压交互。
                        //   为什么两路都要：`hovered` 覆盖**鼠标与触控笔**（桌面模式 / DeX / 平板上才非零），
                        //   `pressed` 覆盖**手指按住未松**（手机上没有真 hover，手指的"悬停"体感就是它）。
                        val iSrc = remember { MutableInteractionSource() }
                        val hovered by iSrc.collectIsHoveredAsState()
                        val pressed by iSrc.collectIsPressedAsState()

                        // 交互强度 inter ∈ [0,1]：按压 = 1.0 > 悬停 = [hoverLevel] > 无 = 0。
                        //   用**同一个** float 同时驱动「图标放大」与「预览轮廓显现」⇒ 两者天然同步，
                        //   不会出现「图标已经放大但轮廓还没出来」的割裂。
                        //   🔴 悬停档位由两个 token **反解**得出，而不是写死一个魔数 ——
                        //     目的是让 [LiquidGlass.HoverIconScale] / [LiquidGlass.PressIconScale]
                        //     这两个 token 真正生效（否则它们只是注释里的摆设）：
                        //       图标缩放 = 1 + (PressIconScale − 1) × inter
                        //     令 inter = hoverLevel 时恰为 HoverIconScale ⇒
                        //       hoverLevel = (1.08 − 1) / (1.10 − 1) = 0.8
                        val hoverLevel = (LiquidGlass.HoverIconScale - 1f) /
                            (LiquidGlass.PressIconScale - 1f)
                        val inter by animateFloatAsState(
                            targetValue = when {
                                pressed -> 1f
                                hovered -> hoverLevel
                                else -> 0f
                            },
                            // 110ms 短过渡：按压反馈必须**跟手**，超过 150ms 就有滞后感。
                            animationSpec = tween(durationMillis = 110),
                            label = "navInteract"
                        )
                        // 预览只对**非当前选中项**生效 —— 已经选中的格再提示"胶囊将移动到这里"是噪音。
                        //   用 `inter > 0.001f` 短路：静止态下这个 Box 根本不进树，零额外绘制节点。
                        val previewing = i != targetIndex && inter > 0.001f

                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable(
                                    interactionSource = iSrc,
                                    // 🔴 关掉 Material ripple：默认涟漪是一层灰色圆形扩散，画在玻璃上
                                    //   会破坏通透感，而且与本项目自研的「预览轮廓」语义重复。
                                    //   反馈全部交给下层的预览轮廓 + 图标放大。
                                    indication = null
                                ) {
                                    // 🔴 行为响应：**按下只做预览、抬起才导航**。
                                    //   于是误触的代价只是"看了一眼轮廓"，不会把页面切走 ——
                                    //   这也是"预览轮廓"存在的意义：把「将要移动到这里」提前告诉用户。
                                    Haptic.tick(nav.context)
                                    nav.navigate(s.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // ⑤-1 预览轮廓：声明在图标**之前** ⇒ 压在图标层之下、玻璃层之上。
                            if (previewing) {
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .padding(LiquidGlass.PreviewInset)
                                        .graphicsLayer {
                                            alpha = inter
                                            // 轻微「弹入」：0.92 → 1.0，读起来像轮廓被手指召出来。
                                            val sc = 0.92f + 0.08f * inter
                                            scaleX = sc
                                            scaleY = sc
                                        }
                                        .clip(previewShape)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(
                                                alpha = LiquidGlass.PreviewFillAlpha
                                            )
                                        )
                                        .border(
                                            width = LiquidGlass.PreviewStrokeWidth,
                                            color = MaterialTheme.colorScheme.primary.copy(
                                                alpha = LiquidGlass.PreviewStrokeAlpha
                                            ),
                                            shape = previewShape
                                        )
                                )
                            }

                            // ⑤-2 图标 + 文字
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(s.iconRes),
                                    contentDescription = s.label,
                                    // 22dp ↔ 24dp 改用 graphicsLayer 缩放：避免逐帧 remeasure
                                    //（旧实现用 animateDpAsState 改 size ⇒ 每帧重新测量图标布局）
                                    modifier = Modifier.size(24.dp).graphicsLayer {
                                        // 基线缩放（与胶囊位置联动）× 交互放大（悬停/按压）。
                                        //   交互项取到 [LiquidGlass.PressIconScale] = 1.10，
                                        //   **略大于选中态的 1.0** ⇒ 指针停在某格时，"我要点这个"的
                                        //   提示强度会盖过"这格当前被选中"的静态大小，指向性明确。
                                        val sc = (0.916f + 0.084f * sel) *
                                            (1f + (LiquidGlass.PressIconScale - 1f) * inter)
                                        scaleX = sc
                                        scaleY = sc
                                    },
                                    tint = fg
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}