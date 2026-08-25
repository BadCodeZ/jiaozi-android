@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.PracticeState
import kotlin.math.*

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.AiExplainEngine
import com.jiaozi.sz.domain.PracticeConfig
import com.jiaozi.sz.domain.answerIndex
import com.jiaozi.sz.domain.parseOptions
import com.jiaozi.sz.ui.AiExplainViewModel
import com.jiaozi.sz.ui.AiGenViewModel
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.CAUSE_OPTIONS
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.island.IslandState
import com.jiaozi.sz.xiaomi.FloatingIslandService
import android.provider.Settings
import com.jiaozi.sz.xiaomi.Haptic
import com.jiaozi.sz.xiaomi.StudyTimerService
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.ContextWrapper

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
            val subj = st.current?.subject ?: "练习"
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

@Composable
private fun PracticeHome(vm: PracticeViewModel, appVm: AppViewModel, nav: NavHostController, st: PracticeState) {
    // 错题本等异步入口的加载态：显示覆盖层，避免「点击无反应」的空白感
    if (st.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("正在准备练习…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
        return
    }
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val cfg by vm.config.collectAsStateWithLifecycle()
    val repo = appVm.repo
    val progress by appVm.progressMap.collectAsStateWithLifecycle()

    // 读取持久化偏好初始化本地编辑态
    var mode by remember { mutableStateOf(cfg.mode.ifBlank { "随机全科" }) }
    var subj by remember { mutableStateOf(cfg.subj) }
    var num by remember { mutableStateOf(cfg.num) }
    var interleave by remember { mutableStateOf(cfg.interleave) }
    LaunchedEffect(cfg) {
        if (cfg.mode.isNotBlank()) {
            mode = cfg.mode; subj = cfg.subj; num = cfg.num; interleave = cfg.interleave
        }
    }

    val subjects = listOf("科一", "科二", "科三")
    val modes = listOf("随机全科", "按科目", "章节练习", "薄弱优先", "仅复习")
    val nums = listOf(10, 20, 30, 50)

    // 章节健康度「去练该章」：进入练习页即按携带的 (科目, 章节) 开练，消费后清空
    val pendingChapter by appVm.pendingChapterPractice.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChapter) {
        pendingChapter?.let { (s, c) ->
            vm.startChapter(s, c, disc = if (s == "科三") disc else null)
            appVm.clearPendingChapterPractice()
        }
    }

    // 错题/错因数据
    val wrongIds = remember(progress) { progress.values.filter { it.wrongBook }.map { it.qid }.toSet() }
    val causeDist = remember(progress) {
        progress.values.mapNotNull { it.cause }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .filter { it.value > 0 }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("练习模式", style = MaterialTheme.typography.headlineSmall)

        // 快速开始（新手默认入口）：一键随机全科 20 题
        QuickStartCard(onQuick = { vm.start(PracticeConfig(mode = "随机全科", num = 20, disc = disc)) })

        // 继续上次
        val lastSubj = cfg.subj
        if (!lastSubj.isNullOrBlank()) {
            ResumeCard(lastSubj, onResume = { vm.resumeLast() }, onClear = { vm.clearPrefs() })
        }

        // 模式
        Text("模式", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            modes.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m; if (m != "按科目") subj = null },
                    label = { Text(m) }
                )
            }
        }

        // 科目
        if (mode in listOf("随机全科", "按科目", "薄弱优先", "仅复习")) {
            Text("科目（按科目 / 随机全科时生效）", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                subjects.forEach { s ->
                    val cnt = repo.countBySubject(s, if (s == "科三") disc else null)
                    FilterChip(
                        selected = subj == s,
                        onClick = { subj = if (subj == s) null else s },
                        label = { Text("$s (${cnt})") }
                    )
                }
            }
            Text("当前科三学科：$disc · ${repo.countBySubject("科三", disc)} 题",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        // 章节选择
        if (mode == "章节练习") {
            ChapterPicker(repo, disc) { su, ch, sec ->
                vm.startChapter(su, ch, sec, num, if (su == "科三") disc else null)
            }
        }

        // 题量与穿插
        if (mode != "章节练习") {
            Text("题量", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                nums.forEach { n ->
                    FilterChip(selected = num == n, onClick = { num = n }, label = { Text("$n") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = interleave,
                    onClick = { interleave = !interleave },
                    label = { Text("穿插混合") }
                )
                Spacer(Modifier.width(8.dp))
                Text("不同科目打乱顺序，提升区分力", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        // 开始按钮
        val canStart = when (mode) {
            "按科目" -> subj != null
            "章节练习" -> false // 章节选择内直接启动
            else -> true
        }
        Button(
            onClick = {
                vm.start(PracticeConfig(mode = mode, subj = subj, num = num, interleave = interleave, disc = disc))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canStart
        ) {
            Icon(appPainter("play"), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始练习")
        }

        HorizontalDivider()

        // 错因强化
        if (causeDist.isNotEmpty()) {
            Text("错题强化（按错因组卷）", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                causeDist.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                    OutlinedButton(onClick = { vm.startCause(c, disc) }) {
                        Text("$c · $n")
                    }
                }
            }
        }

        HorizontalDivider()

        // 模考（限时套卷，支持短模考）
        Text("模考（限时套卷）", style = MaterialTheme.typography.titleMedium)
        Text("限时套卷模拟笔试节奏：20 题≈40 分钟 / 30 题≈60 分钟 / 50 题≈90 分钟",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockExamButton("20 题", "40 分钟", onClick = { vm.startBlueprint(disc, 20, 40 * 60) }, modifier = Modifier.weight(1f))
            MockExamButton("30 题", "60 分钟", onClick = { vm.startBlueprint(disc, 30, 60 * 60) }, modifier = Modifier.weight(1f))
            MockExamButton("50 题", "90 分钟", onClick = { vm.startBlueprint(disc, 50, 90 * 60) }, modifier = Modifier.weight(1f))
        }
        OutlinedButton(onClick = { vm.startWrong(disc) }, modifier = Modifier.fillMaxWidth()) { Text("错题本") }

        HorizontalDivider()

        // AI 题库
        Text("AI 题库", style = MaterialTheme.typography.titleMedium)
        AiGenPanel(appVm, vm)
    }
}

@Composable
private fun QuickStartCard(onQuick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("快速开始", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
            Text("随机全科 20 题 · 不挑科目直接练", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            Button(
                onClick = onQuick,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(appPainter("play"), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("开始练习")
            }
        }
    }
}

@Composable
private fun MockExamButton(label: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ResumeCard(lastSubj: String, onResume: () -> Unit, onClear: () -> Unit) {    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("继续上次练习", style = MaterialTheme.typography.titleMedium)
            Text("上次：$lastSubj", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("继续") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("清除记录") }
            }
        }
    }
}

@Composable
private fun ChapterPicker(repo: com.jiaozi.sz.data.Repository, disc: String, onStart: (String, String, String?) -> Unit) {
    var expandedSubj by remember { mutableStateOf<String?>(null) }
    var expandedChap by remember { mutableStateOf<String?>(null) }
    val subjects = listOf("科一", "科二", "科三")

    Text("选择章节", style = MaterialTheme.typography.titleMedium)
    subjects.forEach { su ->
        val chapters = repo.syllabus.find { it.subject == su }?.chapters ?: emptyList()
        if (chapters.isEmpty()) return@forEach
        val isExpanded = expandedSubj == su
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { expandedSubj = if (isExpanded) null else su }.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$su · ${chapters.size} 章", style = MaterialTheme.typography.bodyMedium)
                    Text(if (isExpanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (isExpanded) {
                    chapters.forEach { ch ->
                        val chExpanded = expandedChap == "${su}|${ch.name}"
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { expandedChap = if (chExpanded) null else "${su}|${ch.name}" }.padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(ch.name, style = MaterialTheme.typography.bodyMedium)
                                val cnt = repo.countChapter(su, ch.name, null, if (su == "科三") disc else null)
                                Text("$cnt 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (chExpanded) {
                                ch.sections.forEach { sec ->
                                    val secCnt = repo.countChapter(su, ch.name, sec, if (su == "科三") disc else null)
                                    if (secCnt > 0) {
                                        Row(
                                            Modifier.fillMaxWidth().clickable { onStart(su, ch.name, sec) }.padding(start = 44.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(sec, style = MaterialTheme.typography.bodySmall)
                                            Text("$secCnt 题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** AI 出题面板：选科目/学科/数量，生成后预览审阅，确认再入库（对齐网页端「AI 生题后先预览」） */
@Composable
private fun AiGenPanel(appVm: AppViewModel, vm: PracticeViewModel) {
    val aiVm: AiGenViewModel = viewModel()
    val aiState by aiVm.state.collectAsStateWithLifecycle()
    val aiKey by appVm.aiKey.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val subjects = listOf("科一", "科二", "科三")
    var subject by remember { mutableStateOf("科三") }
    var count by remember { mutableStateOf("10") }
    // 预览选中态：包含集合（默认全选），用 included id 集合表示
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(aiState.preview) {
        selected = aiState.preview.map { it.id }.toSet()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            subjects.forEach { s ->
                FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(s) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = count, onValueChange = { count = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("数量") }, modifier = Modifier.width(100.dp), singleLine = true
            )
            Button(
                onClick = { aiVm.preview(appVm.aiProvider.value, aiKey, subject, if (subject == "科三") disc else "", count.toIntOrNull() ?: 10, appVm.aiModel.value) },
                enabled = !aiState.generating
            ) { Text(if (aiState.generating) "生成中…" else "生成题目") }
        }
        if (aiKey.isBlank()) {
            Text("未配置 AI Key：将生成内置「离线样例」预览，仅供试用（不会污染同步）。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        aiState.error?.let { Text("出错：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }

        // 预览 / 审阅（默认全选，可逐题取消或整体取消/全选）
        if (aiState.preview.isNotEmpty()) {
            val allIds = aiState.preview.map { it.id }
            val allSelected = selected.size == allIds.size
            Text(
                "AI 已生成 ${aiState.preview.size} 题，请审阅后入库（默认全选）：",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(if (allSelected) "取消全选" else "全选") {
                    selected = if (allSelected) emptySet() else allIds.toSet()
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { aiVm.commit(aiState.preview.filter { it.id in selected }) },
                    enabled = selected.isNotEmpty()
                ) { Text("确认入库（${selected.size}）") }
                ActionButton("放弃") { aiVm.discard() }
            }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aiState.preview.size, contentType = { "aiprev" }) { i ->
                    val q = aiState.preview[i]
                    val checked = q.id in selected
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = if (it) selected + q.id else selected - q.id
                                })
                                Column(Modifier.weight(1f)) {
                                    Text("${q.subject} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(q.q, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (q.opt.isNotBlank()) {
                                Text(q.opt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("答案：${if (q.answer.isBlank()) "（主观题，自判）" else q.answer}", style = MaterialTheme.typography.labelSmall)
                            if (!q.analysis.isNullOrBlank()) {
                                Text("解析：${q.analysis}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        } else if (aiState.committed > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已入库 ${aiState.committed} 题", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ActionButton("去练习") { vm.startUserBank(subject, if (subject == "科三") disc else "") }
            }
        }
    }
}

@Composable
private fun SessionView(vm: PracticeViewModel, st: com.jiaozi.sz.ui.PracticeState) {
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)
    val q = st.current ?: return
    val isLast = st.isLast

    // 模考·全屏考场沉浸：限时套卷期间隐藏状态栏+导航栏，进入纯考场视野；
    // 系统返回手势拦截为「二次确认退出」，防手滑中断模考。
    val isMock = st.timeLimitSec != null && st.timeLimitSec!! > 0
    var showExitConfirm by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { showExitConfirm = true }
    DisposableEffect(isMock) {
        if (isMock) {
            ctx.findActivity()?.window?.let { w ->
                val ctrl = WindowInsetsControllerCompat(w, w.decorView)
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            ctx.findActivity()?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(if (isMock) "离开模考？" else "退出练习？") },
            text = {
                Text(
                    if (isMock) "本场模考尚未完成，离开将按当前进度交卷并结束。"
                    else "当前练习进度不会自动保存，退出后将回到练习首页（已答进度仍会记入错题本）。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; vm.exitSession() }) { Text(if (isMock) "交卷离开" else "退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(if (isMock) "继续答题" else "继续练习") }
            }
        )
    }

    // 专注计时由练习「会话」生命周期管理（begin 开 / exitSession、末题、超时、ViewModel 销毁 关），
    // 不再绑定本视图：切到其它 tab 不会停表归零，回来继续累计。

    // 练习页内顶部计时：专注时长与模考倒计时各自收进独立小 Composable，
    // 每秒的读秒只重绘那一小块，不再触发整个 SessionView（含 AnimatedContent + 选项列表）重组，消除卡顿。

    Column(Modifier.fillMaxSize().displayCutoutPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showExitConfirm = true }) {
                    Icon(appPainter("close"), contentDescription = "退出练习", modifier = Modifier.size(22.dp))
                }
                Text("${st.mode} · 第 ${st.index + 1}/${st.total} 题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            if (st.timeLimitSec == null) PracticeTimer()
            IconButton(onClick = { showCard = true }) {
                Icon(Icons.Rounded.GridOn, contentDescription = "答题卡", modifier = Modifier.size(22.dp))
            }
        }

        LinearProgressIndicator(
            progress = { if (st.total == 0) 0f else (st.index + 1).toFloat() / st.total },
            modifier = Modifier.fillMaxWidth()
        )
        // 模考倒计时进度条（红色越界告警）——独立 Composable，每秒仅重绘自身
        if (st.timeLimitSec != null && st.timeLimitSec!! > 0) {
            MockCountdown(st.timeLimitSec!!) { vm.onTimeout() }
        }

        // 切题平滑过渡：按题目 id 取快照，避免题目瞬间硬切打断心流（低端机也不显突兀）
        AnimatedContent(
            targetState = q.id,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(tween(Motion.duration(rm, Motion.BASE))))
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(tween(Motion.duration(rm, Motion.BASE))))
            },
            label = "questionSwap"
        ) { id ->
            val qq = st.questions.firstOrNull { it.id == id } ?: q
            val opts = parseOptions(qq.opt)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${qq.subject} · ${qq.chapter}${if (!qq.section.isNullOrBlank()) " · " + qq.section!! else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(qq.q, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // 选项/主观题使用 LazyColumn 避免长选项列表卡顿
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (qq.isSubjective) {
                        if (!st.showAnswer) {
                            // 第一步：写草稿
                            item {
                                OutlinedTextField(
                                    value = st.draft,
                                    onValueChange = { vm.setDraft(it) },
                                    label = { Text("在此写下你的答案（草稿）") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    maxLines = 8
                                )
                            }
                            item {
                                Button(
                                    onClick = { vm.revealAnswer() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("对答案") }
                            }
                        } else {
                            // 第二步：展示参考答案，自评
                            item {
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("参考答案 / 解析", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(qq.analysis ?: "暂无解析", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            // 错题本重练主观题时，展示当初作答（与当前草稿不同才提示，避免和预填重复）
                            if (!st.historyDraft.isNullOrBlank() && st.historyDraft != st.draft) {
                                item {
                                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("你上次作答", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(st.historyDraft ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                Text("对照后自评：", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ActionButton(
                                        if (st.subjectiveResult == "right") "✓ 我答对了" else "我答对了",
                                        Modifier.weight(1f)
                                    ) { vm.markSubjective("right") }
                                    ActionButton(
                                        if (st.subjectiveResult == "wrong") "✓ 我还不会" else "我还不会",
                                        Modifier.weight(1f)
                                    ) { vm.markSubjective("wrong") }
                                }
                            }
                        }
                    } else {
                        val correctIdx = answerIndex(qq.answer)
                        items(opts.size, contentType = { "opt" }) { i ->
                            val letter = ('A' + i).toString()
                            val isCorrect = i == correctIdx
                            // 答错后：用户选错的那项标红✗，正确项标绿✓；未作答前只显示选中态
                            val wrongSel = st.answered && (st.selected == i) && !isCorrect
                            OptionRow(
                                letter = letter,
                                text = opts[i],
                                selected = st.selected == i,
                                answered = st.answered,
                                correct = isCorrect,
                                wrongSelected = wrongSel
                            ) { vm.selectOption(i) }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !st.answered,
            enter = fadeIn(tween(Motion.duration(rm, Motion.FAST))),
            exit = fadeOut(tween(Motion.duration(rm, Motion.FAST)))
        ) {
            val canSubmit = if (q.isSubjective) (st.showAnswer && st.subjectiveResult != null) else st.selected >= 0
            Button(
                onClick = { vm.submit(); Haptic.tick(ctx) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSubmit
            ) { Text("提交") }
        }
        AnimatedVisibility(
            visible = st.answered,
            enter = fadeIn(tween(Motion.duration(rm, Motion.BASE))) + slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(Motion.duration(rm, Motion.BASE))),
            exit = fadeOut(tween(Motion.duration(rm, Motion.FAST))) + slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(Motion.duration(rm, Motion.FAST)))
        ) {
            // 反馈：结果判定 + 解析
            val ok = st.correct
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            )) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (ok) "答对了 ✓" else "答错了", style = MaterialTheme.typography.titleMedium,
                        color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                    Text(q.analysis ?: "暂无解析", style = MaterialTheme.typography.bodyMedium)
                }
            }
            // 错因判断：单独成卡，与结果判定拉开层次，避免"答错了"和错因选择贴成一团
            if (!ok) {
                Spacer(Modifier.height(16.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("为什么错了？", style = MaterialTheme.typography.titleSmall)
                        Text("选一下错因（至少 1 项才能继续）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            CAUSE_OPTIONS.forEach { cause ->
                                FilterChip(
                                    selected = cause in st.causeSelected,
                                    onClick = { vm.toggleCause(cause) },
                                    label = { Text(cause) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val canNext = ok || st.causeSelected.isNotEmpty()
            Button(
                onClick = { vm.next() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canNext
            ) { Text(if (isLast) "查看结果" else "下一步") }
        }
    }

    // 答题卡：题号宫格，按作答态着色，点击跳题；当前题描边高亮 + 图例
    if (showCard) {
        ModalBottomSheet(onDismissRequest = { showCard = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("答题卡", style = MaterialTheme.typography.titleLarge)
                    Text("${st.results.size}/${st.total} 已答", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                LazyVerticalGrid(columns = GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(st.total) { i ->
                        val itemQ = st.questions[i]
                        val r = st.results[itemQ.id]
                        val isCurrent = i == st.index
                        val (bg, fg) = when {
                            r?.correct == true -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            r != null -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
                        }
                        val statusColor = when {
                            r?.correct == true -> MaterialTheme.colorScheme.primary
                            r != null -> MaterialTheme.colorScheme.error
                            else -> null
                        }
                        val border = when {
                            isCurrent -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            statusColor != null -> BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                            else -> null
                        }
                        Card(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clickable { vm.goto(i); showCard = false },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            border = border
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = fg)
                                    if (isCurrent) Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                // 图例
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnswerCardLegend(MaterialTheme.colorScheme.primaryContainer, "答对")
                    AnswerCardLegend(MaterialTheme.colorScheme.errorContainer, "答错")
                    AnswerCardLegend(MaterialTheme.colorScheme.surfaceVariant, "未答")
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    letter: String,
    text: String,
    selected: Boolean,
    answered: Boolean,
    correct: Boolean,
    wrongSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    // 主题感知的「正确绿」：深色用深底亮绿字，浅色用浅绿底深绿字
    val correctContainer = if (isDark) Color(0xFF0F3D2B) else Color(0xFFD8F2E2)
    val correctOn = if (isDark) Color(0xFF86E8B4) else Color(0xFF0E7A45)
    val correctBorder = Color(0xFF2E9E5B)
    val bg = when {
        answered && correct -> correctContainer
        answered && wrongSelected -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        answered && correct -> correctOn
        answered && wrongSelected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        answered && correct -> correctBorder
        answered && wrongSelected -> MaterialTheme.colorScheme.error
        else -> null
    }
    val mark = when {
        answered && correct -> "✓"   // 正确答案
        answered && wrongSelected -> "✗" // 用户错选
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, RoundedCornerShape(16.dp)) else Modifier)
            .background(bg)
            .clickable(enabled = !answered) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mark != null) {
            Text(mark, style = MaterialTheme.typography.labelLarge, color = fg)
            Spacer(Modifier.width(8.dp))
        } else {
            Text(letter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
        if (answered && correct) {
            Spacer(Modifier.width(8.dp))
            Text("正确答案", style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

/** 专注计时：独立状态，每秒只重绘本 Composable，不触发整页重组 */
@Composable
private fun PracticeTimer() {
    var elapsedSec by remember { mutableStateOf(StudyTimerService.elapsedSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSec = StudyTimerService.elapsedSeconds()
        }
    }
    val mm = elapsedSec / 60
    val ss = elapsedSec % 60
    Text("专注 %02d:%02d".format(mm, ss), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

/** 模考倒计时：独立状态，每秒重绘本 Composable；时间到回调交卷 */
@Composable
private fun MockCountdown(timeLimitSec: Int, onTimeout: () -> Unit) {
    var remainSec by remember(timeLimitSec) { mutableStateOf(timeLimitSec) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remainSec = (remainSec - 1).coerceAtLeast(0)
            // 最后 10 秒每读秒连续轻微震动（紧迫感）；30 秒时单下提醒半程
            if (remainSec in 1..10) Haptic.tick(ctx)
            else if (remainSec == 30) Haptic.tick(ctx)
            if (remainSec == 0) { onTimeout(); break }
        }
    }
    val mm = remainSec / 60
    val ss = remainSec % 60
    val urgent = remainSec <= 60
    val ratio = if (timeLimitSec > 0) remainSec.toFloat() / timeLimitSec.toFloat() else 0f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("⏱ 剩余 %02d:%02d".format(mm, ss),
            style = MaterialTheme.typography.labelMedium,
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (remainSec <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/** 结算页错题回顾条目 */
private data class WrongView(
    val q: com.jiaozi.sz.data.model.Question,
    val cause: String,
    val draft: String?,
    val selected: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryView(vm: PracticeViewModel, st: com.jiaozi.sz.ui.PracticeState, nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val aiVm: AiExplainViewModel = viewModel()
    val aiState by aiVm.state.collectAsStateWithLifecycle()
    val aiKey by appVm.aiKey.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val (acc, cause) = vm.summary()
    val isMock = st.mode == "全科模考"
    val bySubject = if (isMock) vm.summaryBySubject() else emptyMap()
    val score = vm.scoreEstimate()

    // 错题清单（含主观题草稿、客观题所选）
    val wrongs = remember(st) {
        st.questions.mapNotNull { q ->
            val r = st.results[q.id] ?: return@mapNotNull null
            if (r.correct) null else WrongView(q, r.cause.joinToString("、").ifBlank { "未标错因" }, r.draft, r.selected)
        }
    }

    val scope = rememberCoroutineScope()
    var showExplain by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("本次练习", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { nav.navigateUp() }) {
                Icon(appPainter("close"), contentDescription = "关闭")
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(acc * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("正确率", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // 能力雷达（按科目正确率）
        val subjAcc = remember(st) {
            st.questions.groupBy { it.subject }.mapValues { (_, qs) ->
                val rs = qs.mapNotNull { st.results[it.id] }
                if (rs.isEmpty()) 0f else rs.count { it.correct }.toFloat() / rs.size
            }
        }
        if (subjAcc.isNotEmpty()) {
            Text("能力雷达", style = MaterialTheme.typography.titleMedium)
            RadarChart(subjAcc)
        }

        // 模考：分科报告 + 分数预估
        if (isMock && bySubject.isNotEmpty()) {
            Text("模考分科报告", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bySubject.forEach { (subj, pair) ->
                        val (rt, tot) = pair
                        val a = if (tot == 0) 0f else rt.toFloat() / tot
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(subj, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                            LinearProgressIndicator(progress = { a }, modifier = Modifier.weight(1f).height(6.dp))
                            Text("$rt/$tot · ${(a * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("分数预估（百分制）", style = MaterialTheme.typography.bodyMedium)
                        Text("$score 分", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("注：百分制估算，非教资官方分数线（官方为 150 分制，合格线约卷面 70 分）",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 错因诊断
        if (cause.isNotEmpty()) {
            Text("错因诊断", style = MaterialTheme.typography.titleMedium)
            cause.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                Text("· $c：$n 次", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 错题回顾（含主观题草稿）
        if (wrongs.isNotEmpty()) {
            Text("错题回顾", style = MaterialTheme.typography.titleMedium)
            wrongs.forEach { w ->
                val q = w.q
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${q.subject} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(q.q, style = MaterialTheme.typography.bodyMedium)
                        if (!q.isSubjective) {
                            val opts = parseOptions(q.opt)
                            val mySel = if (w.selected in opts.indices) ('A' + w.selected).toString() else "—"
                            val corr = answerIndex(q.answer)
                            val corrLetter = if (corr in opts.indices) ('A' + corr).toString() else "—"
                            Text("你的选择：$mySel　正确答案：$corrLetter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("错因：${w.cause}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (!w.draft.isNullOrBlank()) {
                            Text("我的作答：${w.draft}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.End) {
                            IconButton(onClick = { appVm.setPendingAiContext(q.q); nav.navigate("aichat") }) {
                                Icon(appPainter("chat"), contentDescription = "问 AI", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // AI 讲评入口（对齐网页端 v5.14）
            Button(
                onClick = {
                    aiVm.clear()
                    aiVm.explain(appVm.aiProvider.value, aiKey, wrongs.map { AiExplainEngine.WrongItem(it.q.q, it.q.analysis, it.cause) }, appVm.aiModel.value)
                    showExplain = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = aiKey.isNotBlank() && !aiState.explaining
            ) {
                Text(if (aiState.explaining) "AI 讲评生成中…" else "AI 讲评（错因 + 纵向回顾）")
            }
            if (aiKey.isBlank()) {
                Text("未配置 AI Key，AI 讲评暂不可用（请到「设置」填写 DeepSeek Key）",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        Button(onClick = { vm.restart() }, modifier = Modifier.fillMaxWidth()) { Text("再来一组") }
    }

    // AI 讲评结果弹层
    if (showExplain) {
        ModalBottomSheet(
            onDismissRequest = { showExplain = false; aiVm.clear() },
            sheetState = sheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 讲评", style = MaterialTheme.typography.titleLarge)
                Text("覆盖 ${wrongs.size} 道错题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                when {
                    aiState.explaining -> Text("生成中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    aiState.error != null -> Text(aiState.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    aiState.text != null -> Text(aiState.text ?: "", style = MaterialTheme.typography.bodyMedium)
                }
                if (aiState.text != null) {
                    OutlinedButton(
                        onClick = {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("AI讲评", aiState.text ?: ""))
                            Toast.makeText(ctx, "已复制讲评", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("复制讲评") }
                }
                Button(
                    onClick = { scope.launch { sheetState.hide(); showExplain = false } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

/** 答题卡图例：色块 + 文案 */
@Composable
private fun AnswerCardLegend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 能力雷达：按科目正确率绘制多边形（自绘 Canvas） */
@Composable
private fun RadarChart(data: Map<String, Float>) {
    if (data.isEmpty()) return
    val axes = data.keys.toList()
    val n = axes.size
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val R = (minOf(size.width, size.height) / 2 - 18.dp.toPx())
        // 网格环
        for (ring in 1..4) {
            val rr = R * ring / 4f
            val path = Path()
            for (k in 0..n) {
                val a = -PI / 2 + 2 * PI * k / n
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Color.Gray.copy(alpha = 0.25f), style = Stroke(width = 1.dp.toPx()))
        }
        // 数据多边形
        val dpath = Path()
        data.values.forEachIndexed { i, v ->
            val a = -PI / 2 + 2 * PI * i / n
            val rr = R * v.coerceIn(0f, 1f)
            val x = cx + rr * cos(a).toFloat()
            val y = cy + rr * sin(a).toFloat()
            if (i == 0) dpath.moveTo(x, y) else dpath.lineTo(x, y)
        }
        dpath.close()
        drawPath(dpath, color = Color(0xFF7F77DD).copy(alpha = 0.3f))
        drawPath(dpath, color = Color(0xFF7F77DD), style = Stroke(width = 2.dp.toPx()))
    }
}

/** 简易 FlowRow 实现（避免引入额外依赖） */
@Composable
private fun FlowRow(
    horizontalGap: androidx.compose.ui.unit.Dp = 8.dp,
    verticalGap: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content) { measurables, constraints ->
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentWidth = 0
        var currentHeight = 0

        for (m in measurables) {
            val p = m.measure(constraints.copy(minWidth = 0))
            if (currentRow.isNotEmpty() && currentWidth + hGapPx + p.width > constraints.maxWidth) {
                rows.add(currentRow)
                rowWidths.add(currentWidth)
                rowHeights.add(currentHeight)
                currentRow = mutableListOf()
                currentWidth = 0
                currentHeight = 0
            }
            currentRow.add(p)
            currentWidth += if (currentRow.size == 1) p.width else hGapPx + p.width
            currentHeight = maxOf(currentHeight, p.height)
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentWidth)
            rowHeights.add(currentHeight)
        }

        val totalHeight = rowHeights.sum() + (rowHeights.size - 1).coerceAtLeast(0) * vGapPx
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            for (i in rows.indices) {
                var x = 0
                for (p in rows[i]) {
                    p.placeRelative(x, y + (rowHeights[i] - p.height) / 2)
                    x += p.width + hGapPx
                }
                y += rowHeights[i] + vGapPx
            }
        }
    }
}

/** 从 Compose 上下文回溯 Activity（ContextWrapper 链） */
private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
