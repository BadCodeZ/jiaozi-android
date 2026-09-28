package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.AppColors
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.activity.compose.BackHandler
import com.jiaozi.sz.ui.components.appPainter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.PracticeEngine
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import com.jiaozi.sz.ui.components.StatBand
import com.jiaozi.sz.ui.components.StatBandItem
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.toArgb

/**
 * 章节健康度（chapters 视图，高级维护功能，对齐网页端章节健康）。
 * 按章节聚合：题数、已练数、正确率、错题数，并以热力色标识薄弱/一般/良好/未练。
 * 提供「去练该章」入口，一键进入该章节专项练习；章节可编辑（显示名/权重/数据自检）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val subjects = listOf("科一", "科二", "科三")
    var subj by remember { mutableStateOf("科一") }
    var sortBy by remember { mutableStateOf("掌握度") }
    // 章节配置（显示名 + 权重）：来自 AppViewModel，编辑后实时刷新
    val chapterConfig by appVm.chapterConfig.collectAsStateWithLifecycle()
    // 编辑中章节（null = 未打开抽屉）
    var editing by remember { mutableStateOf<ChapterHealth?>(null) }

    // 该科目全部题（科三按当前学科隔离）
    val qs = remember(repo.bank.exam, subj, disc) {
        repo.bank.exam.filter { it.subject == subj && (subj != "科三" || it.disc == disc) }
    }
    // 章节聚合（按题中出现过的章节名，保证覆盖未进大纲的题）
    val health = remember(qs, progress) {
        qs.groupBy { it.chapter.ifBlank { "未归类" } }.entries.map { (ch, list) ->
            var r = 0; var w = 0; var wrong = 0
            list.forEach { q ->
                progress[q.id]?.let { e: ProgressEntity ->
                    r += e.right; w += e.wrong
                    if (e.wrongBook) wrong++
                }
            }
            val done = r + w
            val acc = if (done > 0) r.toFloat() / done else -1f
            ChapterHealth(ch, list.size, done, acc, wrong)
        }.sortedWith(compareBy({ it.acc }, { -it.count }))
    }

    val goodCol = AppColors.success
    val warnCol = AppColors.warning
    val badCol = AppColors.danger
    val noneCol = MaterialTheme.colorScheme.outlineVariant

    val listState = rememberLazyListState()

    // 科目 + 排序 chips：原两行合并为一行横向滚动（2026-09-25 晚：原「两态互斥渲染」已随折叠退场取消）
    val filterChips: @Composable RowScope.() -> Unit = {
        subjects.forEach { s -> FilterChip(selected = subj == s, onClick = { subj = s }, label = { Text(BankStore.shortName(s)) }) }
        listOf("掌握度", "题数").forEach { k ->
            FilterChip(selected = sortBy == k, onClick = { sortBy = k }, label = { Text(if (k == "掌握度") "按掌握度" else "按题数") })
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ① Hero + 概况说明：常驻不滚，手势直通下方列表
        CollapsingTopBlocks(spacing = 14.dp, modifier = Modifier.hubDragToScroll(listState)) {
            HeroHeader(
                "章节", "按章练 · 专项突破薄弱点",
                // 🔴 2026-09-23 按 08 号 E1：Hero with_icon(school)，走独立装饰位
                decorIcon = appPainter("school"),
                // 🔴 2026-09-22 二级 Hero 统一沉浸通栏
                immersive = true, onBack = { nav.navigateUp() }
            )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = filterChips
        )
            // ── 章节统计带（08 号 E2 / F1）：3 列 —— 章节数 / 已练 / 错题 ──
            val totalDone = health.sumOf { it.done }
            val totalWrong = health.sumOf { it.wrong }
            StatBand(
                items = listOf(
                    StatBandItem(health.size.toString(), "章节数", "章"),
                    StatBandItem(totalDone.toString(), "已练", "题"),
                    StatBandItem(
                        totalWrong.toString(), "错题", "题",
                        valueColor = if (totalWrong > 0) AppColors.danger else null
                    )
                ),
                showDivider = true
            )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        // ── 数据自检横条（08 号 E3 / F5）：顶部通栏，三行各自可点跳到对应修复入口 ──
        val noAnalysisCount = qs.count { it.analysis.isNullOrBlank() || it.analysis.length < 6 }
        val unclassifiedCount = qs.count { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }
        val stoppedCount = health.count { (chapterConfig[PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, it.chapter)]?.weight ?: 1.0) <= 0.0 }
        if (health.isNotEmpty() && (noAnalysisCount > 0 || unclassifiedCount > 0 || stoppedCount > 0)) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppColors.warningBg),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("数据自检", style = MaterialTheme.typography.labelMedium, color = AppColors.warning, fontWeight = FontWeight.SemiBold)
                    if (noAnalysisCount > 0) {
                        SelfCheckRow("缺解析 $noAnalysisCount 题") { nav.navigate("proof") }
                    }
                    if (unclassifiedCount > 0) {
                        SelfCheckRow("未归类章节 $unclassifiedCount 题") { editing = health.firstOrNull { it.chapter == "未归类" } }
                    }
                    if (stoppedCount > 0) {
                        SelfCheckRow("权重已停用 $stoppedCount 章") { editing = health.firstOrNull { (chapterConfig[PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, it.chapter)]?.weight ?: 1.0) <= 0.0 } }
                    }
                }
            }
        }

            val sorted = remember(health, sortBy) {
            if (sortBy == "题数") health.sortedWith(compareBy({ -it.count }))
            else health.sortedWith(compareBy({ it.acc }, { -it.count }))
        }
        // 🔴 2026-09-23（#35）：章节卡按当前排序名次加序号（章节名唯一，作 key 稳妥）
        val rankByChapter = remember(sorted) { sorted.mapIndexed { i, c -> c.chapter to (i + 1) }.toMap() }

        if (health.isEmpty()) {
            EmptyHint("bars", "该科目暂无题目", "先去「练习」或收集箱转题，章节健康度会自动统计。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), state = listState, modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                items(sorted, contentType = { "chapter" }) { h ->
                    val col = when {
                        h.acc < 0f -> noneCol
                        h.acc >= 0.8f -> goodCol
                        h.acc >= 0.5f -> warnCol
                        else -> badCol
                    }
                    val status = when {
                        h.acc < 0f -> "未练"
                        h.acc >= 0.8f -> "良好"
                        h.acc >= 0.5f -> "一般"
                        else -> "薄弱"
                    }
                    // 本章权重（读态表达）：默认 1.0×，≤0 视为「已停用」
                    val cfgNow = chapterConfig[PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, h.chapter)]
                    val weightNow = (cfgNow?.weight ?: 1.0).toFloat()
                    // 本章缺解析题数（行内异常徽标的数据源）
                    val chQs = qs.filter {
                        if (h.chapter == "未归类") it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱"
                        else it.chapter == h.chapter
                    }
                    val chNoAnalysis = chQs.count { it.analysis.isNullOrBlank() || it.analysis.length < 6 }

                    Card(
                        // 🔴 2026-09-23（08 号 E5）：整卡可点 → 直接进该章练习
                        Modifier.fillMaxWidth().clickable {
                            appVm.setPendingChapterPractice(subj, h.chapter)
                            nav.navigate("practice")
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (h.acc in 0f..0.5f) {
                                        Canvas(Modifier.size(10.dp)) {
                                            drawContext.canvas.nativeCanvas.drawCircle(
                                                size.width / 2f, size.height / 2f, size.width / 2f,
                                                android.graphics.Paint().apply { color = badCol.toArgb(); isAntiAlias = true }
                                            )
                                        }
                                    }
                                    Canvas(Modifier.size(12.dp)) {
                                        drawContext.canvas.nativeCanvas.drawCircle(
                                            size.width / 2f, size.height / 2f, size.width / 2f,
                                            android.graphics.Paint().apply { color = col.toArgb(); isAntiAlias = true }
                                        )
                                    }
                                    // 🔴 2026-09-23（#35）：章节卡序号（按当前排序位的名次，1 起）
                                    Text(
                                        "${rankByChapter[h.chapter] ?: 0}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    val dispName = cfgNow?.name?.takeIf { it.isNotBlank() } ?: h.chapter
                                    // 🔴 2026-09-23（08 号 E5）：标题 16sp SemiBold
                                    Text(dispName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // ── 行内异常徽标（08 号 E6）：同一张卡只取其一，优先级 权重停用 > 缺解析 ──
                                    when {
                                        weightNow <= 0f -> StatusChip("已停用", AppColors.warningBg, AppColors.warning)
                                        chNoAnalysis > 0 -> StatusChip("缺解析 $chNoAnalysis", AppColors.redBg, AppColors.danger)
                                        else -> Text(status, style = MaterialTheme.typography.labelMedium, color = col, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Icon(appPainter("chevron"), contentDescription = null, Modifier.size(18.dp), tint = AppColors.textSecondary.copy(alpha = 0.4f))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("题数 ${h.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("已练 ${h.done}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("错题 ${h.wrong}", style = MaterialTheme.typography.bodySmall, color = if (h.wrong > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (h.acc < 0f) "未练" else "正确 ${(h.acc * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (h.acc >= 0f) {
                                LinearProgressIndicator(
                                    progress = { h.acc.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = col,
                                    trackColor = AppColors.trackGray
                                )
                            }
                            // ── 模考权重（08 号 E5）：读态必须表达，6dp primary 条 + 右对齐数值 ──
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("模考权重", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary)
                                Text(
                                    "${"%.1f".format(weightNow)}×",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (weightNow <= 0f) AppColors.danger else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (weightNow / 2f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = if (weightNow <= 0f) AppColors.danger else MaterialTheme.colorScheme.primary,
                                trackColor = AppColors.trackGray
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                                TextButton(onClick = { editing = h }) { Text("编辑", fontSize = 13.sp) }
                                Spacer(Modifier.width(8.dp))
                                if (h.acc < 0f) {
                                    Button(onClick = {
                                        appVm.setPendingChapterPractice(subj, h.chapter)
                                        nav.navigate("practice")
                                    }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)) { Text("优先练习", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                                } else {
                                    OutlinedButton(onClick = {
                                        appVm.setPendingChapterPractice(subj, h.chapter)
                                        nav.navigate("practice")
                                    }, shape = RoundedCornerShape(12.dp)) { Text("去练该章", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                                }
                            }
                        }
                    }
                }

            }

                // 章节编辑抽屉：显示名（改名）+ 模考权重 + 数据自检
                if (editing != null) {
                    val eh = editing!!
                    val key = PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, eh.chapter)
                    val cfg = chapterConfig[key]
                    var name by remember(eh.chapter) { mutableStateOf(cfg?.name ?: "") }
                    var weight by remember(eh.chapter) { mutableStateOf((cfg?.weight ?: 1.0).toFloat()) }
                    // 🔴 2026-09-23 脏态守卫（08 号 E10）：改了显示名/权重后，下滑关闭抽屉或按返回键
                    //    都会静默丢弃改动 ⇒ 一律拦截到二次确认。
                    val origName = cfg?.name ?: ""
                    val origWeight = (cfg?.weight ?: 1.0).toFloat()
                    val dirty = name != origName || kotlin.math.abs(weight - origWeight) > 0.001f
                    var confirmDiscard by remember { mutableStateOf(false) }
                    BackHandler(enabled = dirty) { confirmDiscard = true }
                    val chQs = if (eh.chapter == "未归类")
                        qs.filter { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }
                    else
                        qs.filter { it.chapter == eh.chapter }
                    val noAnalysis = chQs.count { it.analysis.isNullOrBlank() || it.analysis.length < 6 }
                    val unclassified = chQs.count { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }
                    if (confirmDiscard) {
                        AlertDialog(
                            onDismissRequest = { confirmDiscard = false },
                            title = { Text("放弃修改？") },
                            text = { Text("「${eh.chapter}」的显示名或权重已改动，关闭将不会保存。") },
                            confirmButton = {
                                TextButton(onClick = { confirmDiscard = false; editing = null }) { Text("放弃") }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
                            }
                        )
                    }
                    ModalBottomSheet(onDismissRequest = { if (dirty) confirmDiscard = true else editing = null }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("编辑章节 · ${eh.chapter}", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(name, { name = it }, label = { Text("显示名（改名）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("模考权重", style = MaterialTheme.typography.bodyMedium)
                                Text("${"%.1f".format(weight)}×", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            Slider(value = weight, onValueChange = { weight = it }, valueRange = 0f..2f, steps = 39)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("数据自检", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("题数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${chQs.size}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("缺解析", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$noAnalysis", style = MaterialTheme.typography.bodySmall, color = if (noAnalysis > 0) warnCol else goodCol)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("未归类章节", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$unclassified", style = MaterialTheme.typography.bodySmall, color = if (unclassified > 0) warnCol else goodCol)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("权重状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (weight <= 0f) "已停用" else "正常", style = MaterialTheme.typography.bodySmall, color = if (weight <= 0f) badCol else goodCol)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editing = null }, Modifier.weight(1f)) { Text("取消") }
                                Button(onClick = { appVm.saveChapterConfig(key, name.trim(), weight.toDouble()); editing = null }, Modifier.weight(1f)) { Text("保存") }
                            }
                        }
                    }
                }

        }
    }
}

private data class ChapterHealth(
    val chapter: String,
    val count: Int,
    val done: Int,
    val acc: Float,
    val wrong: Int
)

/** 行内状态 / 异常徽标（08 号 E6）：浅底胶囊 + 语义色文字，11sp。 */
@Composable
private fun StatusChip(text: String, bg: Color, fg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 数据自检横条的单行（08 号 E3 / E9）：**每行可点**，点击后跳到对应修复入口 ——
 * 规范 anti_patterns 明确禁止「只报数字不给去处」的死提示。
 */
@Composable
private fun SelfCheckRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = AppColors.textPrimary)
        Icon(
            appPainter("chevron"),
            contentDescription = null,
            Modifier.size(14.dp),
            tint = AppColors.warning
        )
    }
}
