@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 练习设置独立页（图 2-3）：学科范围 / 题量 / 穿插混合 / 限时模式 / 答案即时显示 / 选择章节。
 * 所有参数持久化进 MetaKeys（PRACTICE_*），与旧版本用户设置完全兼容。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.domain.PracticeConfig
import com.jiaozi.sz.domain.StatsCalculator
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.PrefChipGroup

@Composable
fun PracticeSetupScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val vm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val examStage by appVm.examStage.collectAsStateWithLifecycle()
    val cfg by vm.config.collectAsStateWithLifecycle()
    val progress by appVm.progressMap.collectAsStateWithLifecycle()

    var subj by remember { mutableStateOf(cfg.subj ?: "科一") }
    var num by remember { mutableStateOf(cfg.num) }
    var interleave by remember { mutableStateOf(cfg.interleave) }
    var timedMode by remember { mutableStateOf(cfg.timeLimitSec != null) }
    var showAnswer by remember { mutableStateOf(cfg.showAnswer) }
    var shuffleOptions by remember { mutableStateOf(cfg.shuffleOptions) }
    var includeWrong by remember { mutableStateOf(cfg.includeWrong) }
    var selected by remember { mutableStateOf(cfg.chapters.toSet()) }
    // 🔴 2026-09-28 题型分离：默认「全部选择题」，与全局 PracticeType 偏好同步
    var typeCombo by remember { mutableStateOf(vm.practiceType.value) }
    // 🔴 2026-09-28 学段筛题：本页只读展示（真源在 AppViewModel，改动即时落盘）
    val stageSel = examStage

    val subjects = listOf("科一", "科二", "科三")
    val subjDisc = if (subj == "科三") disc else null
    // 学段过滤值："" ⇒ null（不过滤）
    val stageFilter = stageSel.takeIf { it.isNotBlank() }

    // 当前科目的章节 + 掌握度（🔴 章节题量按学段口径统计，与抽题结果保持一致）
    val chapterStats = remember(progress, subj, subjDisc, stageFilter) {
        val syl = repo.syllabus.find { it.subject == subj } ?: return@remember emptyList<ChapterStat>()
        syl.chapters.map { ch ->
            val qs = repo.bank.exam.filter {
                it.subject == subj && it.chapter == ch.name && (subj != "科三" || it.disc == subjDisc) &&
                    BankStore.stageMatches(it, stageFilter)
            }
            val stat = StatsCalculator.attemptStat(qs, progress)
            val practiced = stat.practiced
            val acc = stat.accPercent
            val pct = if (qs.isNotEmpty()) practiced * 100 / qs.size else 0
            ChapterStat(ch.name, practiced, qs.size, acc, pct)
        }
    }

    // 🔴 学段空状态：本学段（+ 通用）在**当前科目**下无任何题目时给出可操作提示
    val stageSubjectEmpty = remember(subj, subjDisc, stageFilter) {
        stageFilter != null && repo.bank.exam.none {
            it.subject == subj && (subj != "科三" || it.disc == subjDisc) && BankStore.stageMatches(it, stageFilter)
        }
    }
    val stageTotal = remember(subj, subjDisc, stageFilter) {
        repo.bank.exam.count {
            it.subject == subj && (subj != "科三" || it.disc == subjDisc) && BankStore.stageMatches(it, stageFilter)
        }
    }

    val estMin = maxOf(1, num * 45 / 60)
    val selectedCount = selected.size
    val selectedQ = remember(chapterStats, selected) {
        chapterStats.filter { it.name in selected }.sumOf { it.total }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
    ) {
        // ── 顶栏 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(appPainter("close"), contentDescription = "关闭", tint = AppColors.textPrimary, modifier = Modifier.size(22.dp))
            }
            Text("练习设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(44.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            // 🔴 2026-09-25 修正 B1：本列装载的是「学科范围 / 题量 / 选择章节 / 开关组」等**区块**，
            //   间距按 `tokens.section_gap`「区块之间恒 20dp」（= `sp.20`）取值，原 14dp 与规范明文不符。
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 报考学段（🔴 2026-09-28 学段筛题）──
            SetupSection(
                title = "报考学段",
                extra = when {
                    stageSel.isBlank() -> "未设置 · 不筛选"
                    else -> "本学段 + 通用题"
                }
            ) {
                // 三枚等宽：初中 / 高中 / 不限（与「学科范围」三段式视觉一致）
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    listOf(
                        BankStore.STAGE_JUNIOR to "初中",
                        BankStore.STAGE_SENIOR to "高中",
                        "" to "不限"
                    ).forEach { (value, label) ->
                        val sel = stageSel == value
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) AppColors.blue else Color.White)
                                .clickable { appVm.setExamStage(value) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (sel) Color.White else AppColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
                Text(
                    when {
                        stageSel.isBlank() -> "未设置学段，全部题目可见。选定后仅出与该学段匹配的题目（初高中同卷的通用题不受影响）。"
                        else -> "将只出「$stageSel 专属题 + 通用题」；科一科二初高中同卷，不受影响。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
            }

            // ── 学科范围 ──
            SetupSection(
                title = "学科范围",
                extra = if (stageFilter != null) "本学段 $stageTotal 题" else null
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { s ->
                        val sel = subj == s
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) AppColors.blue else Color.White)
                                .clickable {
                                    subj = s
                                    if (s != "科三") selected = emptySet()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 展示名走大纲官方简写（综合素质 / 教育知识 / 学科知识，均 4 字，三等分不挤压）
                            Text(BankStore.shortName(s), color = if (sel) Color.White else AppColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
                // 🔴 2026-09-28 学段空状态：该学段在当前科目下无题时，给出可操作的切换指引
                if (stageSubjectEmpty) {
                    val other = if (stageSel == BankStore.STAGE_JUNIOR) BankStore.STAGE_SENIOR else BankStore.STAGE_JUNIOR
                    EmptyHint(
                        "bars",
                        "「$stageSel」暂无${BankStore.shortName(subj)}题目",
                        "该科目在当前学段下没有专属题；可切到「不限」或改选「$other」。"
                    )
                }
            }

            // ── 题量 ──
            SetupSection(title = "题量") {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("本次练习题目数量", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary)
                    Text("$num 题", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.blue)
                }
                Slider(
                    value = num.toFloat(),
                    onValueChange = { num = it.toInt() },
                    valueRange = 5f..100f,
                    steps = 18,
                    colors = SliderDefaults.colors(activeTrackColor = AppColors.blue, thumbColor = AppColors.blue)
                )
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 50, 100).forEach { q ->
                        // 🔴 2026-09-24 对齐 AppChips 契约：未选＝surfaceVariant（原为纯白 Color.White，
                        //    与全局 chip「未选＝surfaceVariant 无边框」不一致）。选中＝分段型 primaryContainer 浅蓝底 + primary 字。
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (num == q) AppColors.blueLight else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { num = q }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$q", color = if (num == q) AppColors.blue else AppColors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── 题型组合（2026-09-28 题型分离）──
            SetupSection(
                title = "题型组合",
                extra = when (typeCombo) {
                    "subjective" -> "仅主观题"
                    "choice" -> "仅选择题"
                    else -> "混合"
                }
            ) {
                PrefChipGroup(
                    title = "",
                    options = listOf(
                        "choice" to "仅选择题",
                        "subjective" to "仅主观题",
                        "all" to "混合"
                    ),
                    selected = typeCombo,
                    onSelect = { v ->
                        typeCombo = v
                        vm.setPracticeType(v)
                    }
                )
            }

            // ── 开关组（04 号 E4：乱序题目/乱序选项/开启限时/混入错题 + 既有穿插混合/答案即时显示）──
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow("穿插混合", "多章节混合出题，避免连续同类", interleave) { interleave = it }
                    SwitchRow("选项乱序", "每题选项顺序随机，避免背选项", shuffleOptions) { shuffleOptions = it }
                    SwitchRow("限时模式", "每题约 60 秒，模拟真实考试节奏", timedMode) { timedMode = it }
                    SwitchRow("混入错题", "把错题优先混入本轮（约占三分之一）", includeWrong) { includeWrong = it }
                    SwitchRow("答案即时显示", "提交后立即展示答案与解析", showAnswer) { showAnswer = it }
                }
            }

            // ── 选择章节 ──
            SetupSection(
                title = "选择章节",
                extra = if (selectedCount > 0) "已选 $selectedCount 章 · 约 $selectedQ 题" else "已选全部章节"
            ) {
                if (chapterStats.isEmpty() || (stageFilter != null && chapterStats.all { it.total == 0 })) {
                    if (stageFilter != null) {
                        // 🔴 学段空状态：章节列出来了，但本学段过滤后每章题量都是 0
                        val other = if (stageSel == BankStore.STAGE_JUNIOR) BankStore.STAGE_SENIOR else BankStore.STAGE_JUNIOR
                        EmptyHint(
                            "bars",
                            "「$stageSel」暂无章节题目",
                            "该科目在当前学段下没有专属题（通用题也未被本章目录收录）；切到「不限」或改选「$other」。"
                        )
                    } else {
                        EmptyHint("bars", "该科目暂无章节数据", "换一个学科，或先去题库录入该科题目。")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chapterStats.forEach { st ->
                            val sel = st.name in selected
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(st.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text("已练 ${st.practiced}/${st.total} · 正确率 ${st.acc}%", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp))
                                            .background(AppColors.trackGray)
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth(st.pct / 100f).height(5.dp).clip(RoundedCornerShape(999.dp))
                                                .background(AppColors.blue)
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = sel,
                                    onCheckedChange = {
                                        selected = if (it) selected + st.name else selected - st.name
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = AppColors.blue)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 底部 CTA ──
        Button(
            onClick = {
                val mode = if (selected.isNotEmpty()) "章节练习" else "随机全科"
                vm.start(
                    PracticeConfig(
                        mode = mode,
                        subj = subj,
                        num = num,
                        interleave = interleave,
                        timeLimitSec = if (timedMode) num * 60 else null,
                        showAnswer = showAnswer,
                        chapters = selected.toList(),
                        disc = subjDisc,
                        shuffleOptions = shuffleOptions,
                        includeWrong = includeWrong,
                        typeCombo = typeCombo,
                        // 🔴 2026-09-28 学段筛题：显式传本页所选学段（"" ⇒ 不过滤）
                        stage = stageFilter
                    )
                )
                nav.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
        ) {
            Icon(appPainter("play"), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("开始练习 · $num 题 · 约 ${estMin}分钟", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

private data class ChapterStat(
    val name: String,
    val practiced: Int,
    val total: Int,
    val acc: Int,
    val pct: Int
)

@Composable
private fun SetupSection(title: String, extra: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (extra != null) Text(extra, style = MaterialTheme.typography.bodySmall, color = AppColors.blue, fontWeight = FontWeight.Medium)
        }
        content()
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppColors.blue)
        )
    }
}
