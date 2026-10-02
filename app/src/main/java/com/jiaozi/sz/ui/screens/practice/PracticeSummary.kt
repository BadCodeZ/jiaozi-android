@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 答题总结：总结页、操作按钮、图例、雷达图、流式布局。
 *
 * 从 PracticeScreen.kt 拆分而来（纯物理拆分，逻辑未改）。
 */

import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.ui.PendingWeaknessSeed
import com.jiaozi.sz.ui.components.GlassBackButton
import com.jiaozi.sz.ui.components.HeroStatCell
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.toRichText
import com.jiaozi.sz.ui.PracticeState
import kotlin.math.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.AiExplainEngine
import com.jiaozi.sz.domain.answerIndex
import com.jiaozi.sz.domain.parseOptions
import com.jiaozi.sz.ui.AiExplainViewModel
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.theme.AppGradients
import com.jiaozi.sz.ui.theme.LocalAppDark
import android.widget.Toast
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.ContextWrapper
import com.jiaozi.sz.xiaomi.StudyTimerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummaryView(vm: PracticeViewModel, st: com.jiaozi.sz.ui.PracticeState, nav: NavHostController) {
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
            // 错因＝可标可不标（E6b）：未标时留空串 ⇒ 卡片内该行整行不渲染（对齐错题本 ProofScreen 的「未标记不占位」）
            if (r.correct) null else WrongView(q, r.cause.joinToString("、"), r.draft, r.selected)
        }
    }

    val scope = rememberCoroutineScope()
    var showExplain by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Column(Modifier.fillMaxSize().background(AppColors.bg).verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 关闭键统一为左上角圆形磨砂件形态（P2）。注：练习【答题】界面 PracticeSession 不在本次统一范围，保持原样。
            GlassBackButton(onClick = { nav.navigateUp() }, icon = "close", contentDescription = "关闭")
            Text("本次练习", style = MaterialTheme.typography.headlineSmall)
        }
        // ── 结果总览（04 号「结果复盘页」）：正确率大字 + **答对 / 答错 / 用时 / 总分** 四格 ──
        val correctN = st.results.values.count { it.correct }
        val wrongN = st.results.values.count { !it.correct }
        val elapsedSec = StudyTimerService.elapsedSeconds()
        val timeText = if (elapsedSec > 0) "${elapsedSec / 60}分${elapsedSec % 60}秒" else "—"
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Box(Modifier.fillMaxWidth().background(AppGradients.hero(LocalAppDark.current), RoundedCornerShape(20.dp))) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${(acc * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 48.sp)
                            Text("正确率", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                        }
                        Icon(appPainter("target"), contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(72.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // 🔴 2026-09-27：原页内私有 `SummaryStatCell`（值 17sp / 标签 12sp，形参序 (label, value)）
                        //    收敛为全局 CMP-STATCARD 的 onHero 变体 `HeroStatCell`（形参序 (value, label)），私有副本删除。
                        //    ⚠️ 两处必须注意：① 形参顺序**相反**，务必显式命名入参；② 本页四格并排、单格约 80dp，
                        //    「用时」为长文本（「12分34秒」）⇒ 覆写字号为 17sp / 12sp（原取值，视觉零变化），勿用默认 20sp。
                        HeroStatCell(value = correctN.toString(), label = "答对", valueFontSize = 17.sp, labelFontSize = 12.sp)
                        HeroStatCell(value = wrongN.toString(), label = "答错", valueFontSize = 17.sp, labelFontSize = 12.sp)
                        HeroStatCell(value = timeText, label = "用时", valueFontSize = 17.sp, labelFontSize = 12.sp)
                        HeroStatCell(value = "$score", label = "总分", valueFontSize = 17.sp, labelFontSize = 12.sp)
                    }
                }
            }
        }

        // 能力雷达（科目>=2 按科目，否则按章节；不足 3 轴时兜底补维度，避免退化成孤点/线段）
        // 科目轴名走大纲官方简写（综合素质 / 教育知识 / 学科知识）
        val subjAcc = remember(st) {
            st.questions.groupBy { BankStore.shortName(it.subject) }.mapValues { (_, qs) ->
                val rs = qs.mapNotNull { st.results[it.id] }
                if (rs.isEmpty()) 0f else rs.count { it.correct }.toFloat() / rs.size
            }
        }
        val chapterAcc = remember(st) {
            st.questions.groupBy { it.chapter }.mapValues { (_, qs) ->
                val rs = qs.mapNotNull { st.results[it.id] }
                if (rs.isEmpty()) 0f else rs.count { it.correct }.toFloat() / rs.size
            }
        }
        val base = if (subjAcc.size >= 3) subjAcc else chapterAcc.toList().take(6).toMap()
        val radarData = if (base.size >= 3) base else {
            val total = st.results.size
            val acc = if (total == 0) 0f else st.results.values.count { it.correct }.toFloat() / total
            val done = if (st.questions.isEmpty()) 0f else total.toFloat() / st.questions.size
            linkedMapOf(
                "正确率" to acc,
                "完成率" to done,
                "掌握度" to (acc * 0.6f + done * 0.4f)
            )
        }
        if (radarData.isNotEmpty()) {
            Text("能力雷达", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            RadarChart(radarData)
        }

        // 模考：分科报告 + 分数预估
        if (isMock && bySubject.isNotEmpty()) {
            Text("模考分科报告", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bySubject.forEach { (subj, pair) ->
                        val (rt, tot) = pair
                        val a = if (tot == 0) 0f else rt.toFloat() / tot
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(BankStore.shortName(subj), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                            LinearProgressIndicator(progress = { a }, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(4.dp)), color = AppColors.blue, trackColor = AppColors.trackGray)
                            Text("$rt/$tot · ${(a * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("分数预估（百分制）", style = MaterialTheme.typography.bodyMedium)
                        Text("$score 分", style = MaterialTheme.typography.titleMedium, color = AppColors.blue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text("百分制估算，非官方分数线（150 分制，合格约卷面 70 分）",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 错因诊断（2026-09-24 对齐 E6b：错因＝辅助信息，配色由 purpleBg 降为中性底，
        // 不再与正确率/解析争夺视觉权重；顶部「更多」死链一并移除）
        if (cause.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("错因诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                    cause.entries.sortedByDescending { it.value }.take(4).forEach { (c, n) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c, style = MaterialTheme.typography.bodySmall, color = AppColors.textPrimary, fontSize = 13.sp)
                            Text("$n 次", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 错题回顾（含主观题草稿）
        if (wrongs.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("错题回顾", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                // 「更多」＝跳校订页错题本 Tab（2026-09-24 修死链；复用 StatsScreen 既有入口模式）
                Text(
                    "更多",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.blue,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        appVm.setPendingProofTab("错题本")
                        nav.navigate("proof")
                    }
                )
            }
            wrongs.forEachIndexed { idx, w ->
                val q = w.q
                // 🔴 2026-09-25 补 A5（#406 报告 A 类）：04 号 practice.summary E5 规定错题列表为
                //    「CMP-LISTROW / **metric**：24dp 序号圆位 → 题干摘要（maxLines=2）→ chevron → 展开反馈区」，
                //    hf 亦明写「错题卡复用 CMP-LISTROW，序号用 24dp 圆形位」。此前为无序号位的普通 Card
                //    ⇒ 现按 metric 变体补齐：前导 24dp 圆形序号位（danger 底白字）+ 题干 maxLines=2 +
                //    容器 surfaceContainer / r20 / padding 16dp（与 NavRowCard 同规格）。解析块等反馈区原样保留。
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(AppColors.danger),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${idx + 1}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${BankStore.shortName(q.subject)} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = AppColors.blue, fontSize = 13.sp)
                            Text(q.q, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!q.isSubjective) {
                                val opts = parseOptions(q.opt)
                                val mySel = if (w.selected in opts.indices) ('A' + w.selected).toString() else "—"
                                val corr = answerIndex(q.answer)
                                val corrLetter = if (corr in opts.indices) ('A' + corr).toString() else "—"
                                Text("你的选择：$mySel　正确答案：$corrLetter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // ── 解析块（2026-09-24 对齐练习会话 E6b：解析是反馈主内容，错因不得压过）──
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.blueBg).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(appPainter("bulb"), contentDescription = null, Modifier.size(15.dp), tint = AppColors.blue)
                                    Text("解析", style = MaterialTheme.typography.labelMedium, color = AppColors.blue, fontWeight = FontWeight.SemiBold)
                                }
                                Text(q.analysis?.takeIf { it.isNotBlank() } ?: "暂无解析", style = MaterialTheme.typography.bodyMedium, color = AppColors.textPrimary)
                            }
                            // 错因降级为中性辅助小字（2026-09-24 对齐 E6b：原 danger 红字与解析争夺注意力）
                            // 条件渲染：未标错因时整行不渲染，避免「错因：未标错因」占位噪声（对齐 ProofScreen L539）
                            w.cause.takeIf { it.isNotBlank() }?.let { c ->
                                Text("错因：$c", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp)
                            }
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
            }

            // 🔴 2026-09-30（薄弱点攻坚独立模块）：错题一键转攻坚笔记
            //    种子经 AppViewModel.pendingWeaknessSeed 注入 WeaknessScreen 编辑器（与备课分家）。
            Button(
                onClick = {
                    val seed = buildWeaknessSeedFromWrongs(wrongs)
                    appVm.setPendingWeaknessSeed(seed)
                    nav.navigate("weakness")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.danger)
            ) {
                Icon(appPainter("target"), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" 错题生成攻坚笔记", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                Text("未配置 AI Key，AI 讲评不可用（去「设置」填 DeepSeek Key）",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        // ── 底部双行动（04 号 E7 / SP-BOTTOM-ACTION）：56dp + r20，主键通栏左、次键右 ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { vm.restart() },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(20.dp)
            ) { Text("再练一组", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            Button(
                onClick = { vm.startWrong(st.questions.firstOrNull { it.subject == "科三" }?.disc ?: "") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) { Text("攻克错题", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        }
    }

    // AI 讲评结果弹层
    if (showExplain) {
        ModalBottomSheet(
            onDismissRequest = { showExplain = false; aiVm.clear() },
            sheetState = sheetState
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI 讲评", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("覆盖 ${wrongs.size} 道错题", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary, fontSize = 13.sp)
                when {
                    aiState.explaining -> Text("生成中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    aiState.error != null -> Text(aiState.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    // 🔴 2026-10-02 同 AI 对话气泡：讲评由模型直出、常带 `**加粗**`，
                    //    经 toRichText 渲染成真粗体，界面不再露星号（复制到剪贴板仍保留原文）
                    aiState.text != null -> Text((aiState.text ?: "").toRichText(), style = MaterialTheme.typography.bodyMedium)
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
internal fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

/**
 * 薄弱点攻坚独立模块：把本次练习错题归集为攻坚笔记种子。
 * - 标题：错题补强 · {主导科目简称} · {月.日}
 * - subject/disc：主导科目（科三 取 disc）
 * - body：按章节归并的错题原文 + 你的选择 / 正确答案 / 我的作答 / 错因 / 解析，供补强复盘直接引用
 * - fromExamId：标记来源为练习（"practice:N题"），可溯源
 */
private fun buildWeaknessSeedFromWrongs(wrongs: List<WrongView>): PendingWeaknessSeed {
    val dom = wrongs.groupBy { it.q.subject }.maxByOrNull { it.value.size }?.key ?: "科一"
    val domShort = BankStore.shortName(dom)
    val disc = wrongs.firstOrNull { it.q.subject == "科三" }?.q?.disc?.takeIf { it.isNotBlank() } ?: ""
    val cal = java.util.Calendar.getInstance()
    val title = "错题补强 · $domShort · ${cal.get(java.util.Calendar.MONTH) + 1}.${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    val sb = StringBuilder()
    sb.appendLine("本次练习错题归集（共 ${wrongs.size} 道），据此补强复盘：")
    wrongs.groupBy { it.q.chapter.ifBlank { domShort } }.toSortedMap().forEach { (ch, ws) ->
        sb.appendLine("\n【$ch】")
        ws.forEachIndexed { i, w ->
            val q = w.q
            sb.appendLine("${i + 1}. ${q.q}")
            if (!q.isSubjective) {
                val opts = parseOptions(q.opt)
                val mySel = if (w.selected in opts.indices) ('A' + w.selected).toString() else "—"
                val corr = answerIndex(q.answer)
                val corrLetter = if (corr in opts.indices) ('A' + corr).toString() else "—"
                sb.appendLine("   你的选择：$mySel　正确答案：$corrLetter")
            }
            if (!w.draft.isNullOrBlank()) sb.appendLine("   我的作答：${w.draft}")
            if (w.cause.isNotBlank()) sb.appendLine("   错因：${w.cause}")
            sb.appendLine("   解析：${q.analysis?.takeIf { it.isNotBlank() } ?: "暂无解析"}")
        }
    }
    return PendingWeaknessSeed(
        title = title,
        subject = dom,
        disc = disc,
        body = sb.toString(),
        fromExamId = "practice:${wrongs.size}题"
    )
}

// 🔴 2026-09-27 死代码清理：原页内私有 `SummaryStatCell(label, value)`（值 17sp / 标签 12sp）已删除，
//    改调全局原子 `HeroStatCell(value, label, valueFontSize = 17.sp, labelFontSize = 12.sp)`（见上方调用点）。
//    （14 号 `pending_normalization_rollup` P3「私有统计卡副本」项据此闭环 —— 私有副本全站归零。）

/** 答题卡图例：色块 + 文案 */
@Composable
internal fun AnswerCardLegend(color: androidx.compose.ui.graphics.Color, label: String, textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // 🔴 2026-09-30 实测修正：图例色块原为**纯色无描边**，而「答对/答错/未答」三色
        //   都是浅容器色（greenBg / redBg / surfaceVariant），在浅色主题的 sheet 底
        //   （surfaceContainer #FFFFFF）上几乎与底融合 —— **图例自身不可见**，
        //   用户拿它当"色卡"去对答题卡，反而对不上。
        // ⇒ 统一补 1dp outlineVariant 描边，四种样例在任何主题下都有明确边界。
        Box(
            Modifier.size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

/** 能力雷达：按科目正确率绘制多边形（自绘 Canvas） */
@Composable
internal fun RadarChart(data: Map<String, Float>) {
    if (data.isEmpty()) return
    val axes = data.keys.toList()
    val n = axes.size
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val R = (minOf(size.width, size.height) / 2 - 24.dp.toPx())
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
                drawPath(path, color = AppColors.trackGray, style = Stroke(width = 1.dp.toPx()))
            }
            // 轴线
            for (i in 0 until n) {
                val a = -PI / 2 + 2 * PI * i / n
                val x = cx + R * cos(a).toFloat()
                val y = cy + R * sin(a).toFloat()
                drawLine(AppColors.trackGray, Offset(cx, cy), Offset(x, y), strokeWidth = 1.dp.toPx())
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
            drawPath(dpath, color = AppColors.blue.copy(alpha = 0.25f))
            drawPath(dpath, color = AppColors.blue, style = Stroke(width = 2.dp.toPx()))
            // 数据点
            data.values.forEachIndexed { i, v ->
                val a = -PI / 2 + 2 * PI * i / n
                val rr = R * v.coerceIn(0f, 1f)
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                drawCircle(AppColors.blue, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
        // 轴标签（简单显示在雷达图下方）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            axes.take(4).forEach { label ->
                Text(label.take(4), style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

/** 从 Compose 上下文回溯 Activity（ContextWrapper 链） */


/** 简易 FlowRow 实现（避免引入额外依赖） */
@Composable
internal fun FlowRow(
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

        val totalHeight = rowHeights.sum() + vGapPx * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIdx, row ->
                var x = 0
                row.forEach { p ->
                    p.placeRelative(x, y)
                    x += p.width + hGapPx
                }
                y += rowHeights[rowIdx] + vGapPx
            }
        }
    }
}
