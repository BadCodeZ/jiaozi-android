package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import androidx.compose.ui.graphics.painter.Painter

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.PracticeConfig
import java.time.format.DateTimeFormatter
import com.jiaozi.sz.domain.WeaknessScorer
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.Motivation
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.island.IslandState
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.theme.AppGradients
import com.jiaozi.sz.xiaomi.FloatingIslandService
import android.provider.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val ctx = LocalContext.current
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val streak by appVm.checkinStreak.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()
    val themePack by appVm.themePack.collectAsStateWithLifecycle()
    val onboarded by appVm.onboarded.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    val repo = appVm.repo

    // 首开轻引导：meta 加载完成后且未引导过才弹，避免默认值 false 闪烁；去设置/稍后都会置已引导
    var showOnboard by remember { mutableStateOf(false) }
    LaunchedEffect(onboarded, metaLoaded) {
        if (metaLoaded && !onboarded) showOnboard = true
    }

    val loadError by appVm.loadError.collectAsStateWithLifecycle()
    var showLoadError by remember { mutableStateOf(true) }

    val now = System.currentTimeMillis()
    val due = remember(progress) { progress.values.count { it.due > 0 && it.due <= now } }
    // 顶部四统计：题库总量 / 已练习 / 总正确率 / 连续打卡
    val totalQuestions = remember(repo) { repo.bank.exam.size }
    val practicedCount = remember(progress) { progress.values.count { it.right + it.wrong > 0 } }
    val overallAcc = remember(progress) {
        val r = progress.values.sumOf { it.right }; val w = progress.values.sumOf { it.wrong }
        if (r + w == 0) -1f else r.toFloat() / (r + w)
    }
    // 目标日倒计时（天），未设置则不显示
    val daysLeft = remember(targetDay) {
        if (targetDay.isBlank()) null else runCatching {
            val t = java.time.LocalDate.parse(targetDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            ((t - now) / 86400000).toInt()
        }.getOrNull()
    }

    val weakTop3 = rememberKey(progress, disc) {
        val byChapter = repo.bank.exam
            .filter { it.subject != "科三" || it.disc == disc }
            .groupBy { it.subject to it.chapter }
        byChapter.mapNotNull { (k, list) ->
            val score = list.map { WeaknessScorer.score(progress[it.id]) }.average()
            if (score.isNaN()) null else Triple(k.first, k.second, score)
        }.sortedByDescending { it.third }.take(3)
    }

    // 灵动岛（上岛）：首页场景，距考天数 + 今日已练；开关开启时自动拉起服务
    LaunchedEffect(daysLeft, practicedCount, islandEnabled) {
        val d = if (daysLeft != null && daysLeft >= 0) "距考 $daysLeft 天 · " else ""
        if (islandEnabled && Settings.canDrawOverlays(ctx)) {
            ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
        }
        IslandBus.enter("today", IslandState(kind = "today", title = "备考中", detail = "${d}今日已练 $practicedCount 题"))
    }
    DisposableEffect(Unit) {
        onDispose { IslandBus.leave("today") }
    }

    Crossfade(targetState = metaLoaded, animationSpec = tween(250), label = "todayLoad") { loaded ->
        if (loaded) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
        if (loadError != null && showLoadError) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "数据加载异常（已降级运行）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        loadError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "点击关闭",
                        modifier = Modifier.clickable { showLoadError = false }
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Text("今日", style = MaterialTheme.typography.headlineMedium)
        Text(
            Motivation.todayPhrase(streak, daysLeft, overallAcc, practicedCount),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // G3 目标日倒计时 Hero（对齐网页端 targetDay 倒计时；渐变背景，未设置时提示设置）
        if (daysLeft != null) {
            val grad = AppGradients.hero(themePack, isSystemInDarkTheme())
            Card(
                Modifier.fillMaxWidth().background(grad, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("距离教资考试还有", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Text(
                        if (daysLeft >= 0) "$daysLeft 天" else "已结束",
                        style = MaterialTheme.typography.headlineLarge, color = Color.White
                    )
                    Text("目标日 $targetDay", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                }
            }
        } else if (targetDay.isBlank()) {
            OutlinedButton(onClick = { nav.navigate("settings") }, Modifier.fillMaxWidth()) { Text("设置目标考试日（倒计时）") }
        } else {
            // targetDay 非空但解析失败：显式提示，避免静默不显示
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("目标日格式异常", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("当前值：$targetDay（应为 yyyy-MM-dd）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(onClick = { nav.navigate("settings") }, Modifier.fillMaxWidth()) { Text("去重新设置") }
                }
            }
        }

        // G3 顶部四统计（对齐网页端今日概览）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("题库总量", "$totalQuestions", Modifier.weight(1f))
            StatCard("已练习", "$practicedCount", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("总正确率", if (overallAcc < 0f) "—" else "${(overallAcc * 100).toInt()}%", Modifier.weight(1f))
            StatCard("连续打卡", "$streak 天", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("待复习", "$due 题", Modifier.weight(1f))
            StatCard("目标日", if (daysLeft == null) "未设" else if (daysLeft >= 0) "$daysLeft 天" else "已结束", Modifier.weight(1f))
        }

        // G3 今日待复习 + 今日建议（对齐网页端 dueCards / 学习计划提示）
        if (due > 0) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("今日待复习 $due 题", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { appVm.checkIn(); practiceVm.start(PracticeConfig(mode = "仅复习", num = 20, disc = disc)); nav.navigate(Screen.Practice.route) }) {
                        Text("去复习")
                    }
                }
            }
        }
        val suggestion = when {
            practicedCount == 0 -> "还没有练习记录，从「开始练习」做 20 题，今天就能看到成长曲线。"
            due == 0 -> "今日复习已清空，建议再做一组薄弱章节巩固。"
            else -> "今日待复习 $due 题，优先完成它们比做新题更高效。"
        }
        Text(suggestion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

        // 开始练习主 CTA：按用户建议上移到「章节」TOC（薄弱章节 Top3）上方，缩短开始练习路径
        Button(
            onClick = {
                appVm.checkIn()
                practiceVm.start(com.jiaozi.sz.domain.PracticeConfig(mode = "随机全科", num = 20))
                nav.navigate(Screen.Practice.route)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(appPainter("play"), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始练习")
        }

        Text("薄弱章节 Top3", style = MaterialTheme.typography.titleMedium)
        if (weakTop3.isEmpty()) {
            Text("暂无数据，去做几道题吧～", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            weakTop3.forEach { (subj, ch, sc) ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("$subj · $ch", style = MaterialTheme.typography.bodyMedium)
                            Text("${(sc * 100).toInt()}% 薄弱", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = sc.toFloat().coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                appVm.checkIn()
                                practiceVm.startChapter(subj, ch, null, 20)
                                nav.navigate(Screen.Practice.route)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(appPainter("play"), contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("去练这章")
                        }
                    }
                }
            }
        }

        // 首页快捷卡：把「我的」页内的 4 个新模块前置到首页（不动底部导航，避免拥挤）
        // R5：极小屏（如 320dp）2×2 固定权重会换行拥挤 → 改为横向滚动卡带（固定宽度，不随屏宽挤压换行）
        Text("备考工具", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickCard("备课教案", appPainter("school")) { nav.navigate("lesson") }
            QuickCard("收集箱", appPainter("inbox")) { nav.navigate("inbox") }
            QuickCard("校订", appPainter("proof")) { nav.navigate("proof") }
            QuickCard("AI 帮手", appPainter("chat")) { nav.navigate("aichat") }
        }

        // 首页快速模考入口（通勤碎片时间也能做套卷，支持三档）
        Text("快速模考（限时套卷）", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { appVm.checkIn(); practiceVm.startBlueprint(disc, 20, 40 * 60); nav.navigate(Screen.Practice.route) },
                modifier = Modifier.weight(1f)
            ) { Text("20 题 / 40 分") }
            OutlinedButton(
                onClick = { appVm.checkIn(); practiceVm.startBlueprint(disc, 30, 60 * 60); nav.navigate(Screen.Practice.route) },
                modifier = Modifier.weight(1f)
            ) { Text("30 题 / 60 分") }
            OutlinedButton(
                onClick = { appVm.checkIn(); practiceVm.startBlueprint(disc, 50, 90 * 60); nav.navigate(Screen.Practice.route) },
                modifier = Modifier.weight(1f)
                ) { Text("50 题 / 90 分") }
        }
            }
        } else {
            SkeletonTodayScreen()
        }
    }

    // 首开轻引导：欢迎语 + 内联设置目标日（不跳转设置页；灵动岛为测试功能，不在此引导）
    val todayMillis = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val onboardPickerState = rememberDatePickerState(initialSelectedDateMillis = todayMillis)
    var showOnboardPicker by remember { mutableStateOf(false) }
    if (showOnboard) {
        AlertDialog(
            onDismissRequest = { showOnboard = false; appVm.setOnboarded(true) },
            confirmButton = {
                Button(onClick = {
                    showOnboard = false
                    showOnboardPicker = true
                }) { Text("设置目标日") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showOnboard = false
                    appVm.setOnboarded(true)
                }) { Text("稍后再说") }
            },
            title = { Text("欢迎使用综合教资备考平台") },
            text = {
                Text("建议先设置「教资考试目标日」，首页会出现倒计时，帮你感知备考节奏。设置后即可从「开始练习」做 20 题，今天就看到成长曲线。")
            }
        )
    }
    if (showOnboardPicker) {
        DatePickerDialog(
            onDismissRequest = { showOnboardPicker = false },
            confirmButton = {
                Button(onClick = {
                    onboardPickerState.selectedDateMillis?.let { ms ->
                        val iso = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                        appVm.setTargetDay(iso)
                    }
                    showOnboardPicker = false
                    appVm.setOnboarded(true)
                }) { Text("保存") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showOnboardPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = onboardPickerState)
        }
    }
}

@Composable
private fun QuickCard(title: String, icon: Painter, onClick: () -> Unit) {
    Card(
        Modifier.width(96.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painter = icon, contentDescription = title, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 冷启动骨架屏：meta 加载完成前显示占位，消除白屏/空内容闪烁。
 * 用无限平移的浅色渐变模拟「加载中」微光，轻量无额外依赖。
 */
@Composable
private fun ShimmerBox(modifier: Modifier, boxHeight: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "shimmerX"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(boxHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                    startX = x - 200f, endX = x
                )
            )
    )
}

@Composable
private fun SkeletonTodayScreen() {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(Modifier, 30.dp)
        ShimmerBox(Modifier, 18.dp)
        ShimmerBox(Modifier, 96.dp) // 倒计时 Hero
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.weight(1f), 72.dp)
            ShimmerBox(Modifier.weight(1f), 72.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.weight(1f), 72.dp)
            ShimmerBox(Modifier.weight(1f), 72.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.weight(1f), 72.dp)
            ShimmerBox(Modifier.weight(1f), 72.dp)
        }
        ShimmerBox(Modifier, 22.dp) // 小标题
        ShimmerBox(Modifier, 120.dp) // 薄弱章节卡
        ShimmerBox(Modifier, 48.dp) // 开始练习按钮
    }
}

/** 简单的记忆键（避免额外依赖） */
@Composable
private fun <T> rememberKey(vararg keys: Any?, computation: () -> T): T {
    return androidx.compose.runtime.remember(keys) { computation() }
}
