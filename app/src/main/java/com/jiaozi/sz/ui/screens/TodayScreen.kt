package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.MiniBadge
import com.jiaozi.sz.ui.components.NavRowCard
import com.jiaozi.sz.ui.components.QuickActionCard
import com.jiaozi.sz.ui.components.SectionTitleDot
import com.jiaozi.sz.ui.components.ShimmerBox
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.hubDragToScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.WeaknessScorer
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.util.startOfDayMillis
import com.jiaozi.sz.util.toIsoDate
import com.jiaozi.sz.util.todayStartMillis

/**
 * 今日目标题数（03 号 E1 副标题 / E2 进度卡分母）。
 * 🔴 单一来源：今日页与练习页共用（练习页 Hero 副标题 + 「今日已练 x/50 题」分母），
 *    改这里两页同时生效，禁止在别处再写一份字面量。
 */
internal const val TODAY_GOAL = 50

/**
 * 一级 Tab · 今日（03 号 `today.main`，两段式布局）。
 *
 * 结构（🔴 不许回退）：
 * 非滚动外框 Column → `CollapsingTopBlocks[Hero 倒计时]`（2 列统计卡已下沉进滚动区首项）
 * → **唯一滚动容器** `LazyColumn(Modifier.fillMaxWidth().weight(1f))`。
 *
 * ⭐ 固定带挂 `hubDragToScroll(listState)` 做**手势直通**（固定带自身是非滚动
 * Column，无滚动节点 ⇒ 不挂则手指落上去既不滚列表也无任何反应）。
 * 🔴 折叠机制 2026-09-21 已整体停用、2026-09-25 死码清理 ⇒ 外框**不再挂**任何折叠监听。
 *
 * 缺 `weight(1f)` 会导致「整页滑不动 + 悬浮导航栏被空底衬成一块白色遮罩」（05 号 critical_history）。
 *
 * 合规说明：本页**不出现**任何「连续打卡 / streak」概念（06 / 01 号全工程禁令）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val onboarded by appVm.onboarded.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    val repo = appVm.repo

    var showOnboard by remember { mutableStateOf(false) }
    var showMock by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    LaunchedEffect(onboarded, metaLoaded) {
        if (metaLoaded && !onboarded) showOnboard = true
    }

    val now = System.currentTimeMillis()
    val due = remember(progress) { progress.values.count { it.due > 0 && it.due <= now } }
    val practicedCount = remember(progress) { progress.values.count { it.right + it.wrong > 0 } }
    val daysLeft = remember(targetDay) {
        if (targetDay.isBlank()) null else runCatching {
            val t = java.time.LocalDate.parse(targetDay).startOfDayMillis()
            ((t - now) / 86400000).toInt()
        }.getOrNull()
    }

    // ── 任务种子：按「科目 × 章节」聚合掌握度与错题量（纯数据，颜色在正文处再取，避免 remember 冻结主题色） ──
    val seeds = rememberKey(progress, disc) {
        repo.bank.exam
            .filter { it.subject != "科三" || it.disc == disc }
            .groupBy { it.subject to it.chapter }
            .map { (k, list) ->
                val practiced = list.count { q -> progress[q.id]?.let { it.right + it.wrong > 0 } == true }
                val wrong = list.sumOf { q -> progress[q.id]?.wrong ?: 0 }
                val avg = list.map { WeaknessScorer.score(progress[it.id]) }.average()
                TaskSeed(k.first, k.second, list.size, practiced, wrong, if (avg.isNaN()) 1f else avg.toFloat())
            }
    }
    val weakSeeds = seeds.filter { it.score < 1f }.sortedBy { it.score }   // 掌握度升序 ⇒ 最弱在前
    val wrongSeeds = seeds.filter { it.wrong > 0 }.sortedByDescending { it.wrong }

    val tasks = ArrayList<TodayTask>(3)
    weakSeeds.getOrNull(0)?.let {
        tasks += it.toTask("优先练习", "edit", AppColors.blue, AppColors.blueBg, planCount(it))
    }
    weakSeeds.getOrNull(1)?.let { s ->
        if (tasks.none { t -> t.subject == s.subject && t.chapter == s.chapter }) {
            tasks += s.toTask("章节补强", "target", AppColors.purple, AppColors.purpleBg, planCount(s))
        }
    }
    wrongSeeds.getOrNull(0)?.let { s ->
        if (tasks.none { t -> t.subject == s.subject && t.chapter == s.chapter }) {
            val n = s.wrong.coerceIn(5, 20)
            tasks += s.toTask("错题复习", "inbox", AppColors.danger, AppColors.redBg, n)
        }
    }

    val listState = rememberLazyListState()

    // ── E1 Hero 文案（2026-09-19：目标日已过时不再显示「距教资 -N 天」，改「已结束」并引导更新考期）──
    val heroTitle = when {
        daysLeft == null -> "设置考试日期"
        daysLeft > 0 -> "距教资 $daysLeft 天"
        daysLeft == 0 -> "今天考试"
        else -> "教资考试已结束"
    }
    val heroSub = when {
        daysLeft == null -> "点此选择目标日，开启备考倒计时"
        // 已过期：给出原目标日 + 出口（hero 此时恢复可点，见下方 modifier）
        daysLeft < 0 -> "目标日 $targetDay · 点此更新下一个考期"
        practicedCount > 0 -> "你的备考进度 · 已完成 $practicedCount/$TODAY_GOAL"
        else -> "今天开始第一组练习吧"
    }
    // 未设置目标日、或目标日已过期 ⇒ hero 可点，点开日期选择器（过期时是唯一「更新考期」入口）
    val heroClickable = daysLeft == null || daysLeft < 0

    Crossfade(targetState = metaLoaded, animationSpec = tween(Motion.duration(rm, Motion.SLOW)), label = "todayLoad") { loaded ->
        if (loaded) {
            // 🔴 2026-09-19 沉浸 Hero：外框**不再整体加横向 padding**（否则 Hero 无法通栏），
            //    改为 Hero 之外的元素各自补 sp.16 —— 统计行 / 列表内容边距与原状完全一致。
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.bg),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ══════════ 上半段（非滚动）══════════
                // ⚠️ 沉浸 Hero 背景向上溢出容器边界（原 clip=false 参数已随折叠退场移除）。
                CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
                    // ── E1 Hero 倒计时头（CMP-HERO with_action · 沉浸通栏）──
                    // 沉浸溢出与「少报高度」由 HeroHeader(immersive=true) 内部完成，调用方**不再**写 offset。
                    HeroHeader(
                        title = heroTitle,
                        subtitle = heroSub,
                        icon = appPainter("today"),
                        immersive = true,
                        statusBarInset = statusBarTop,
                        modifier = if (heroClickable) {
                            Modifier.clickable { showDayPicker = true }
                        } else {
                            Modifier
                        },
                        action = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { nav.navigate("settings") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(appPainter("gear"), contentDescription = "设置", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    )

                }

                // 2026-09-25 晚：原 E4 收起态常驻栏已删（折叠状态机退场，收起态不存在）。

                // ══════════ 下半段：唯一滚动容器 ══════════
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // 2026-09-19：外框已移除横向 padding，此处补回 sp.16 保持内容边距不变。
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 76.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
                    item(key = "hubBand") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // ── E2 今日目标进度卡 + E3 待复习卡（2 列，等高）──
                            // 2026-09-18 按高保真图对齐：实心圆徽章 + 标签同行 + 左对齐数值 + 通栏进度条，
                            // 卡片自带容器（surfaceContainer），不再依赖页面私有 shell。
                            // 2026-09-21：下沉进滚动区，横向边距由 LazyColumn contentPadding 提供。
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    icon = "target",
                                    value = "$practicedCount",
                                    unit = "/$TODAY_GOAL 题",
                                    label = "今日目标",
                                    modifier = Modifier.weight(1f),
                                    valueColor = AppColors.textPrimary,
                                    iconTint = Color.White,
                                    iconBg = AppColors.blue,
                                    iconShape = CircleShape,
                                    iconSize = 32.dp,
                                    labelInline = true,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    progress = practicedCount / TODAY_GOAL.toFloat(),
                                    progressColor = AppColors.blue,
                                    progressHeight = 4.dp
                                )
                                StatCard(
                                    icon = "bell",
                                    value = "$due",
                                    unit = "个",
                                    label = "待复习",
                                    modifier = Modifier.weight(1f),
                                    valueColor = AppColors.textPrimary,
                                    iconTint = Color.White,
                                    iconBg = if (due > 0) AppColors.warning else AppColors.success,
                                    iconShape = CircleShape,
                                    iconSize = 32.dp,
                                    labelInline = true,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    // 🔴 03 号 E3：与左卡等高 —— 左卡尾部有「8dp 间距 + 4dp 进度条」，
                                    //    本卡以 0% 进度条补齐同一段高度（语义＝今日复习尚未开始）。
                                    progress = 0f,
                                    progressColor = AppColors.success,
                                    progressHeight = 4.dp
                                )
                            }
                        }
                    }

                    if (tasks.isEmpty()) {
                        // ── E12 空态（CMP-EMPTYHINT）──
                        item(key = "empty") { TodayEmptyHint(practiced = practicedCount > 0, onGo = { nav.navigate(Screen.Practice.route) }) }
                    } else {
                        // ── E5 dot 分组标题（本页圆点标题上限 2 处之第 1 处）──
                        item(key = "taskHead") { SectionTitleDot("今日任务", trailing = "${tasks.size} 项") }
                        // ── E6 今日任务卡（CMP-LISTROW nav 变体 + E7 类型角标）──
                        items(tasks, key = { "${it.subject}/${it.chapter}" }, contentType = { "task" }) { t ->
                            NavRowCard(
                                icon = t.icon,
                                title = t.title,
                                subtitle = t.subtitle,
                                iconTint = t.fg,
                                iconBg = t.bg,
                                badge = { MiniBadge(t.badge, t.fg, t.bg) },
                                onClick = {
                                    appVm.checkIn()
                                    practiceVm.startChapter(t.subject, t.chapter, null, t.questions, if (t.subject == "科三") disc else null)
                                    nav.navigate(Screen.Practice.route)
                                }
                            )
                        }
                    }

                    // ── E8 快捷入口分组标题（plain 变体，本页第 2 处不用圆点）──
                    // 🔴 2026-09-27 间距双轨：本 item 位于 LazyColumn(spacedBy = 12.dp) 内，
                    //    区块头补前导 8dp ⇒ 与上一区块间距 12 + 8 = 20dp（卡片间距仍为 12dp，不动全局 spacedBy）。
                    item(key = "quickHead") { GroupTitle("快捷入口", Modifier.padding(top = 8.dp)) }
                    // ── E9 快捷入口卡（CMP-QUICKACTION 3col）──
                    item(key = "quickRow") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QuickActionCard(
                                "play", "继续练习", "12 题未完成", "practice",
                                onClick = { nav.navigate(Screen.Practice.route) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionCard(
                                "exam", "开始模考", "限时 120 分", "mock",
                                onClick = { showMock = true },
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionCard(
                                "inbox", "错题本", "34 道待清", "wrong",
                                onClick = { nav.navigate("proof") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ── E10 复习提醒条（无到期整条不渲染）──
                    if (due > 0) {
                        item(key = "dueBar") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(AppColors.greenBg)
                                    .clickable { nav.navigate("proof") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(16.dp).clip(CircleShape).background(AppColors.success.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.success))
                                }
                                Text(
                                    "$due 个知识点今日到期",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.success, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            SkeletonTodayScreen()
        }

        // ── 快速模考三档选择 ──
        if (showMock) {
            AlertDialog(
                onDismissRequest = { showMock = false },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showMock = false }) { Text("取消") }
                },
                title = { Text("快速模考（限时套卷）") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple(20, 40, "20 题 / 40 分"),
                            Triple(30, 60, "30 题 / 60 分"),
                            Triple(50, 90, "50 题 / 90 分")
                        ).forEach { (n, min, label) ->
                            Button(
                                onClick = {
                                    showMock = false
                                    appVm.checkIn()
                                    practiceVm.startBlueprint(disc, n, min * 60)
                                    nav.navigate(Screen.Practice.route)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(label) }
                        }
                    }
                }
            )
        }
    }

    // ── 首开轻引导 ──
    val todayMillis = todayStartMillis()
    val onboardPickerState = rememberDatePickerState(initialSelectedDateMillis = todayMillis)
    if (showOnboard) {
        AlertDialog(
            onDismissRequest = { showOnboard = false; appVm.setOnboarded(true) },
            confirmButton = {
                Button(onClick = { showOnboard = false; showDayPicker = true }) { Text("设置目标日") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOnboard = false; appVm.setOnboarded(true) }) { Text("稍后再说") }
            },
            title = { Text("欢迎使用综合教资备考平台") },
            text = {
                Text("建议先设置「教资考试目标日」，首页会出现倒计时，帮你感知备考节奏。")
            }
        )
    }
    if (showDayPicker) {
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                Button(onClick = {
                    onboardPickerState.selectedDateMillis?.let { ms -> appVm.setTargetDay(ms.toIsoDate()) }
                    showDayPicker = false
                    appVm.setOnboarded(true)
                }) { Text("保存") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDayPicker = false }) { Text("取消") }
            }
        ) { DatePicker(state = onboardPickerState) }
    }
}

// ============================================================
// 本页私有小件
// ============================================================

/** 空态提示（CMP-EMPTYHINT · no_data）：44dp 描边图标 → 标题 → 说明 → 主按钮 */
@Composable
private fun TodayEmptyHint(practiced: Boolean, onGo: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.blueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(appPainter(if (practiced) "check" else "today"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(24.dp))
            }
            Text(
                if (practiced) "今日任务已清空" else "还没有学习数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (practiced) "做得不错，想再练一组也可以随时开始" else "完成第一组练习后，这里会出现为你定制的今日任务",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.textSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(2.dp))
            Button(onClick = onGo) { Text("去练习") }
        }
    }
}

/** 任务种子（章节聚合态） */
private data class TaskSeed(
    val subject: String,
    val chapter: String,
    val total: Int,
    val practiced: Int,
    val wrong: Int,
    val score: Float
)

/** 今日任务（示例文案：`优先练习 · 第三章` / `12 题 · 错题 4`） */
private data class TodayTask(
    val icon: String,
    val badge: String,
    val title: String,
    val subtitle: String,
    val fg: Color,
    val bg: Color,
    val subject: String,
    val chapter: String,
    val questions: Int
)

private fun TaskSeed.toTask(badge: String, icon: String, fg: Color, bg: Color, questions: Int) =
    TodayTask(
        icon = icon,
        badge = badge,
        title = "$badge · $chapter",
        subtitle = "$questions 题 · 错题 $wrong",
        fg = fg,
        bg = bg,
        subject = subject,
        chapter = chapter,
        questions = questions
    )

/** 练习/补强题量：优先取「尚未练过」的余量，已练完则给一组 10 题复习 */
private fun planCount(s: TaskSeed): Int {
    val remain = (s.total - s.practiced).coerceAtLeast(0)
    return when {
        remain >= 20 -> 20
        remain > 0 -> remain
        else -> 10
    }
}

/** 简单的记忆键 */
@Composable
private fun <T> rememberKey(vararg keys: Any?, computation: () -> T): T {
    return remember(keys) { computation() }
}

/**
 * 冷启动骨架屏（E11）：占位高度与真实布局逐块对齐，避免加载完成瞬间的跳变。
 * 骨架刻意不复用折叠基建 —— 加载期不存在滚动行为。
 */
@Composable
private fun SkeletonTodayScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        ShimmerBox(Modifier.fillMaxWidth(), 100.dp, 20.dp)   // E1 Hero
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.weight(1f), 118.dp, 24.dp)   // E2 今日目标
            ShimmerBox(Modifier.weight(1f), 118.dp, 24.dp)   // E3 待复习
        }
        Spacer(Modifier.height(4.dp))
        ShimmerBox(Modifier.fillMaxWidth(), 20.dp, 8.dp)     // E5 dot 标题
        ShimmerBox(Modifier.fillMaxWidth(), 76.dp, 20.dp)    // E6 任务卡 ×3
        ShimmerBox(Modifier.fillMaxWidth(), 76.dp, 20.dp)
        ShimmerBox(Modifier.fillMaxWidth(), 76.dp, 20.dp)
        ShimmerBox(Modifier.fillMaxWidth(), 22.dp, 8.dp)     // E8 分组标题
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {   // E9 快捷入口 3 列
            ShimmerBox(Modifier.weight(1f), 112.dp, 20.dp)
            ShimmerBox(Modifier.weight(1f), 112.dp, 20.dp)
            ShimmerBox(Modifier.weight(1f), 112.dp, 20.dp)
        }
        ShimmerBox(Modifier.fillMaxWidth(), 48.dp, 20.dp)    // E10 复习提醒条
    }
}

// ShimmerBox 已收敛为公共骨架原子：ui/components/Skeleton.kt
// （依据 03 号 cross_screen_consistency「其他页补骨架必须复用同一 ShimmerBox」，2026-09-19）
