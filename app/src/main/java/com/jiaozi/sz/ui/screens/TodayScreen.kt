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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
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
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.theme.AppGradients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val streak by appVm.checkinStreak.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val themePack by appVm.themePack.collectAsStateWithLifecycle()
    val onboarded by appVm.onboarded.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    val repo = appVm.repo

    // 首开轻引导：meta 加载完成后且未引导过才弹，避免默认值 false 闪烁；去设置/稍后都会置已引导
    var showOnboard by remember { mutableStateOf(false) }
    var showMock by remember { mutableStateOf(false) }
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

    Crossfade(targetState = metaLoaded, animationSpec = tween(Motion.duration(rm, Motion.SLOW)), label = "todayLoad") { loaded ->
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

        // 升级版 Hero：放大比例 + 设计感（渐变 + 右上装饰圆 + 大数字 + 打卡胶囊）；配色走主题包渐变
        if (daysLeft != null) {
            val grad = AppGradients.hero(themePack, isSystemInDarkTheme())
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    Modifier
                        .background(grad, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    // 企鹅主题：右侧底部半透明企鹅剪影装饰（仅首页默认主题展示，matchParentSize 不撑开布局）
                    if (themePack == "企鹅") {
                        Canvas(
                            Modifier.matchParentSize().padding(end = 8.dp, bottom = 4.dp),
                            onDraw = {
                                val bodyR = size.height * 0.30f
                                val headR = bodyR * 0.6f
                                val px = size.width - bodyR * 1.6f
                                val py = size.height - bodyR * 1.2f
                                val fill = Color.White.copy(alpha = 0.15f)
                                drawOval(fill, topLeft = Offset(px - bodyR, py - bodyR * 0.7f), size = Size(bodyR * 2f, bodyR * 1.8f))
                                drawOval(Color.White.copy(alpha = 0.08f), topLeft = Offset(px - bodyR * 0.5f, py - bodyR * 0.3f), size = Size(bodyR * 0.8f, bodyR * 1.1f))
                                drawCircle(fill, headR, Offset(px, py - bodyR * 0.8f))
                                val beak = Path().apply {
                                    moveTo(px + headR * 0.4f, py - bodyR * 0.85f)
                                    lineTo(px + headR * 1.1f, py - bodyR * 0.8f)
                                    lineTo(px + headR * 0.4f, py - bodyR * 0.75f)
                                    close()
                                }
                                drawPath(beak, Color(0xFFE67E22).copy(alpha = 0.3f))
                                drawCircle(Color.White.copy(alpha = 0.4f), headR * 0.2f, Offset(px - headR * 0.25f, py - bodyR * 0.85f))
                                drawCircle(Color.White.copy(alpha = 0.4f), headR * 0.2f, Offset(px + headR * 0.3f, py - bodyR * 0.85f))
                                drawPath(Path().apply {
                                    moveTo(px - bodyR * 0.9f, py - bodyR * 0.2f)
                                    quadraticBezierTo(px - bodyR * 1.3f, py + bodyR * 0.1f, px - bodyR * 0.7f, py + bodyR * 0.3f)
                                    lineTo(px - bodyR * 0.5f, py + bodyR * 0.1f)
                                    close()
                                }, fill)
                                drawPath(Path().apply {
                                    moveTo(px + bodyR * 0.9f, py - bodyR * 0.2f)
                                    quadraticBezierTo(px + bodyR * 1.3f, py + bodyR * 0.1f, px + bodyR * 0.7f, py + bodyR * 0.3f)
                                    lineTo(px + bodyR * 0.5f, py + bodyR * 0.1f)
                                    close()
                                }, fill)
                            }
                        )
                    }
                    // 右上角半透明装饰圆（负偏移完全收进卡片内，杜绝右缘硬切）
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-12).dp, y = (-12).dp)
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.10f), CircleShape)
                    )
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("距教资考试", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        Text(
                            if (daysLeft >= 0) "$daysLeft 天" else "已结束",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            "目标日 $targetDay · 今日已练 $practicedCount 题",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("连续打卡 $streak 天", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
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

        // 行动区：开始练习（主）+ 复习 / 快速模考 快捷胶囊
        Button(
            onClick = {
                appVm.checkIn()
                practiceVm.start(PracticeConfig(mode = "随机全科", num = 20))
                nav.navigate(Screen.Practice.route)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(appPainter("play"), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始练习")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    appVm.checkIn()
                    practiceVm.start(PracticeConfig(mode = "仅复习", num = 20, disc = disc))
                    nav.navigate(Screen.Practice.route)
                },
                modifier = Modifier.weight(1f)
            ) { Text("复习 $due 题") }
            OutlinedButton(onClick = { showMock = true }, modifier = Modifier.weight(1f)) { Text("快速模考 ▾") }
        }

        // 学习概览（2x2 芯片；连续打卡 / 目标日已并入上方 Hero，避免重复）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("题库总量", "$totalQuestions", Modifier.weight(1f))
            StatCard("已练习", "$practicedCount", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("总正确率", if (overallAcc < 0f) "—" else "${(overallAcc * 100).toInt()}%", Modifier.weight(1f))
            StatCard("待复习", "$due 题", Modifier.weight(1f))
        }

        // 今日待复习入口已并入上方「复习 N 题」胶囊；开始练习与快捷胶囊已在 Hero 下方统一置顶

        // 薄弱章节：紧凑列表（整行点击即去练该章，去掉逐卡按钮，节省竖向空间）
        Text("薄弱章节", style = MaterialTheme.typography.titleMedium)
        if (weakTop3.isEmpty()) {
            Text("暂无数据，去做几道题吧～", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    weakTop3.forEach { (subj, ch, sc) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appVm.checkIn()
                                    practiceVm.startChapter(subj, ch, null, 20, if (subj == "科三") disc else null)
                                    nav.navigate(Screen.Practice.route)
                                }
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$subj · $ch", style = MaterialTheme.typography.bodyMedium)
                                Text("${(sc * 100).toInt()}% 薄弱", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = sc.toFloat().coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 备考工具：4 列等宽等高网格，图标与文字居中（保留首页快捷入口，不占额外竖向空间）
        Text("备考工具", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickCard("备课教案", appPainter("school"), onClick = { nav.navigate("lesson") }, modifier = Modifier.weight(1f))
            QuickCard("收集箱", appPainter("inbox"), onClick = { nav.navigate("inbox") }, modifier = Modifier.weight(1f))
            QuickCard("校订", appPainter("proof"), onClick = { nav.navigate("proof") }, modifier = Modifier.weight(1f))
            QuickCard("AI 帮手", appPainter("chat"), onClick = { nav.navigate("aichat") }, modifier = Modifier.weight(1f))
        }
        // 快速模考入口已并入上方「快速模考 ▾」胶囊，点击弹出三档选择
            }
        } else {
            SkeletonTodayScreen()
        }

        // 快速模考三档选择（由首页「快速模考 ▾」胶囊唤起）
        if (showMock) {
            AlertDialog(
                onDismissRequest = { showMock = false },
                confirmButton = {},
                dismissButton = {},
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
private fun QuickCard(title: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxHeight().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(painter = icon, contentDescription = title, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    // 系统「减少动态效果」开启时停掉无限微光，改为静态占位，避免持续闪烁扰人（§39 P3 待改进①）
    if (reduceMotionNow(LocalContext.current)) {
        Box(
            modifier
                .fillMaxWidth()
                .height(boxHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }
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
