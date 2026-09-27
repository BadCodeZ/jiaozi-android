@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 练习首页（04 号 `practice.home`，两段式布局）。
 *
 * 结构（🔴 不许回退）：
 * 非滚动外框 `Column` → `CollapsingTopBlocks[Hero 练习]`（2 列统计卡已下沉进滚动区首项）
 * → **唯一滚动容器** `LazyColumn(Modifier.fillMaxWidth().weight(1f))`。
 *
 * ⭐ 固定带挂 `hubDragToScroll(listState)` 做**手势直通**（固定带自身是非滚动
 * Column，无滚动节点 ⇒ 不挂则手指落上去既不滚列表也无任何反应）。
 * 🔴 折叠机制 2026-09-21 已整体停用、2026-09-25 死码清理 ⇒ 外框**不再挂**任何折叠监听。
 *
 * 缺 `weight(1f)` 会导致「整页滑不动 + 悬浮导航栏被空底衬成一块白色遮罩」
 * （05 号 `critical_history`，题库页曾实测复现）。
 *
 * 形态依据：**高保真图 #4 第 1 屏**（Hero + 2 列统计 + 5 个整行入口）优先于 04 号 JSON 线框
 * 的「3 列统计 + 2×2 宫格」——线框为早期稿，交付以高保真图为准。
 * 唯一刻意偏离：AI 生成入口按 04 号 `practice.home.anti_patterns`「AI 入口禁用紫/炫彩底」，
 * 走企鹅蓝族（blueLight 底 + blue 徽章）；其余四行配色与图中一致。
 *
 * 历史沿革：本文件由 `PracticeScreen.kt` 拆分而来；2026-09-18 由旧式「单 verticalScroll 列」
 * 重写为两段式，并清掉四个零调用点的旧组件（PracticeFeatureCard / QuickStartCard /
 * MockExamButton / ResumeCard）——其形态已被 `NavRowCard` 取代。
 */

import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.ChapterRow
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.MiniBadge
import com.jiaozi.sz.ui.components.NavRowCard
import com.jiaozi.sz.ui.components.QuickActionCard
import com.jiaozi.sz.ui.components.SectionTitleDot
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.PracticeState
import com.jiaozi.sz.util.todayIso

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.PracticeConfig
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.PracticeViewModel

@Composable
internal fun PracticeHome(vm: PracticeViewModel, appVm: AppViewModel, nav: NavHostController, st: PracticeState) {
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
    // 🔴 2026-09-28 学段筛题：本页三处快捷入口（快速开始 / 薄弱优先 / 待复习）直调 vm.start()，
    //    显式带上报考学段，与 practicesetup 传参口径一致（vm.start 内亦有 currentStage() 兜底）。
    val examStage by appVm.examStage.collectAsStateWithLifecycle()
    val stageFilter = examStage.takeIf { it.isNotBlank() }

    // ── 练习偏好：本页**只读展示**（编辑一律进 practicesetup，杜绝两处可改同一份配置）──
    val num = cfg.num
    val interleave = cfg.interleave
    val showAnswer = cfg.showAnswer
    val timedMode = cfg.timeLimitSec != null
    val subj = cfg.subj
    val subjLabel = subj?.takeIf { it.isNotBlank() } ?: "随机全科"
    // 🔴 2026-09-25 补（G2 配套）：单章通道此前无章名可显，副标题只能落到「科一 · 章节练习」。
    //    补出 PRACTICE_CHAPTER 键后，这里能显示具体章名，用户才知「继续练习」会练哪一章。
    val lastScope = when {
        cfg.chapters.isNotEmpty() -> "上次：$subjLabel · 已选 ${cfg.chapters.size} 章"
        !cfg.chapter.isNullOrBlank() -> "上次：$subjLabel · ${cfg.chapter}"
        else -> "上次：$subjLabel · ${cfg.mode.ifBlank { "随机全科" }}"
    }

    var showCausePicker by remember { mutableStateOf(false) }
    var showMock by remember { mutableStateOf(false) }

    val pendingChapter by appVm.pendingChapterPractice.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChapter) {
        pendingChapter?.let { (s, c) ->
            vm.startChapter(s, c, disc = if (s == "科三") disc else null)
            appVm.clearPendingChapterPractice()
        }
    }

    // ── E2 今日已练 / 正确率：数据源 daily_stat 当日行（与今日页共用 TODAY_GOAL 单一来源）──
    val daily by repo.recentDailyStat(7).collectAsStateWithLifecycle(initialValue = emptyList())
    val todayKey = remember { todayIso() }
    val todayStat = remember(daily, todayKey) { daily.firstOrNull { it.date == todayKey } }
    val todayDone = (todayStat?.right ?: 0) + (todayStat?.wrong ?: 0)
    // 🔴 红线「未知值一律显示「—」，不得显示 0」：今天一题未练时正确率是**未知量**，
    //    此前 else 分支兜底为 0，真机呈现「正确率 0 %」（把「没练」误报成「全错」）。
    //    改为可空 Int?，无数据落「—」；注意区分：练过但全错 ⇒ todayDone>0 ⇒ 0% 是真值，仍显示 0。
    val todayAcc: Int? = if (todayDone > 0) (todayStat?.right ?: 0) * 100 / todayDone else null
    // 04 号 E2 阈值：≥80% success / 60~80% warning / <60% danger；
    // 无数据用中性灰，避免「今天还没练」被渲染成一片红。
    val accColor = when {
        todayAcc == null -> AppColors.textSecondary
        todayAcc >= 80 -> AppColors.success
        todayAcc >= 60 -> AppColors.warning
        else -> AppColors.danger
    }

    val wrongIds = remember(progress) { progress.values.filter { it.wrongBook }.map { it.qid }.toSet() }
    val dueCount = remember(progress) {
        val now = System.currentTimeMillis()
        progress.values.count { it.due > 0 && it.due <= now }
    }
    val hasHistory = progress.isNotEmpty()

    val causeDist = remember(progress) {
        progress.values.mapNotNull { it.cause }
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount()
            .filter { it.value > 0 }
    }

    val chapterList = remember(progress, disc) {
        repo.syllabus.flatMap { syl ->
            syl.chapters.map { ch ->
                val qs = repo.bank.exam.filter { it.subject == syl.subject && it.chapter == ch.name && (it.subject != "科三" || it.disc == disc) }
                val practiced = qs.count { q -> progress[q.id]?.let { it.right + it.wrong > 0 } == true }
                val right = qs.sumOf { q -> progress[q.id]?.right ?: 0 }
                val acc = if (practiced > 0) right * 100 / practiced else 0
                val pct = if (qs.isNotEmpty()) practiced * 100 / qs.size else 0
                Triple(ch.name, practiced, (qs.size to acc) to pct)
            }
        }.filter { it.second > 0 }.take(4)
    }

    val listState = rememberLazyListState()

    // 🔴 2026-09-19 沉浸 Hero（对齐高保真稿）：本页各元素本就自带 sp.16
    //（统计行 / 列表 contentPadding），故只需让 Hero 通栏并上穿状态栏占位。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
    ) {
        // ══════════ 上半段（非滚动）：Hero + 统计卡，常驻 ══════════
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
            // ── E1 Hero（plain，**无 action 槽**）──
            // 文案逐字取高保真图 #4 第 1 屏；不用 04 号 E1 的「今日已练 N 题 · 正确率 X%」——
            // 该数据已由下方 E2 两张统计卡承载，写进 Hero 会同一屏重复三遍。
            // 另：全 App 其余 Hero（章节/知识图谱/校订/设置…）副标题一律 slogan，
            // 此处跟随全局约定 —— Hero 是「情绪位」，数据归统计卡。
            // ⚠️ 不挂 action（学科胶囊）：高保真图里 Hero 右侧本就没有胶囊，04 号 E1 也明确「无 action 槽」；
            //    挂上后正文可用宽度被吃掉，副标题会被压成两行（实测「今日已练 0 题 · 正确/率 —」）。
            //    学科切换在『按范围练习 / 练习设置』页内均有，无需在 Hero 重复。
            HeroHeader(
                title = "练习 让进步看得见",
                subtitle = "每天多练一点 · 成绩更进一步",
                icon = appPainter("play"),
                immersive = true,
                statusBarInset = statusBarTop
            )
        }

        // 2026-09-25 晚：原 E10 收起态常驻栏已整块删除（折叠状态机退场，收起态不存在）。

        // ══════════ 下半段：唯一滚动容器（🔴 weight(1f) 禁止回退）══════════
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ── E2 两列统计卡（今日已练 / 正确率）——与今日页 E2 同规格：圆徽章 + 标签同行 + 通栏进度条 ──
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = "target",
                            value = "$todayDone",
                            unit = "/$TODAY_GOAL 题",
                            label = "今日已练",
                            modifier = Modifier.weight(1f),
                            valueColor = AppColors.textPrimary,
                            iconTint = Color.White,
                            iconBg = AppColors.blue,
                            iconShape = CircleShape,
                            iconSize = 32.dp,
                            labelInline = true,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            progress = todayDone / TODAY_GOAL.toFloat(),
                            progressColor = AppColors.blue,
                            progressHeight = 4.dp
                        )
                        StatCard(
                            icon = "check",
                            // 未知（今天未练）⇒ 数值「—」且不挂「%」单位，避免渲染成「— %」
                            value = todayAcc?.toString() ?: "—",
                            unit = if (todayAcc != null) "%" else "",
                            label = "正确率",
                            modifier = Modifier.weight(1f),
                            valueColor = accColor,
                            iconTint = Color.White,
                            iconBg = accColor,
                            iconShape = CircleShape,
                            iconSize = 32.dp,
                            labelInline = true,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            progress = (todayAcc ?: 0) / 100f,
                            progressColor = accColor,
                            progressHeight = 4.dp
                        )
                    }
                }
            }

            // ── E3 继续练习（只在有练习历史时渲染；无历史不渲染灰化卡）──
            if (hasHistory) {
                item(key = "entryContinue") {
                    NavRowCard(
                        icon = "play",
                        title = "继续练习",
                        subtitle = lastScope,
                        subtitle2 = "按上次设置继续 · $num 题",
                        iconTint = Color.White,
                        iconBg = AppColors.blue,
                        containerColor = AppColors.blueBg,
                        onClick = { vm.resumeLast() }
                    )
                }
            }

            // ── E5 快速开始（随机全科，沿用当前题量 / 穿插 / 答案偏好）──
            item(key = "entryQuick") {
                NavRowCard(
                    icon = "star",
                    title = "快速开始",
                    subtitle = "随机抽题 · 立即练习",
                    iconTint = Color.White,
                    iconBg = AppColors.purple,
                    containerColor = AppColors.purpleBg,
                    onClick = {
                        appVm.checkIn()
                        vm.start(
                            PracticeConfig(
                                mode = "随机全科",
                                num = num,
                                interleave = interleave,
                                showAnswer = showAnswer,
                                disc = disc,
                                stage = stageFilter
                            )
                        )
                    }
                )
            }

            // ── E5 按范围练习（章节 / 知识点多选 → practicesetup 已含「选择章节」区）──
            item(key = "entryScope") {
                NavRowCard(
                    icon = "target",
                    title = "按范围练习",
                    subtitle = "选择章节或知识点",
                    iconTint = Color.White,
                    iconBg = AppColors.success,
                    containerColor = AppColors.greenBg,
                    onClick = { nav.navigate("practicesetup") }
                )
            }

            // ── E6 AI 生成（🔴 04 号 anti_pattern：不用紫/炫彩底，走企鹅蓝族）──
            item(key = "entryAi") {
                NavRowCard(
                    icon = "lesson",
                    title = "AI 生成",
                    subtitle = "智能出题 · 个性化练习",
                    iconTint = Color.White,
                    iconBg = AppColors.blue,
                    containerColor = AppColors.blueLight,
                    onClick = { nav.navigate("aigen") }
                )
            }

            // ── E9 模考入口（通栏；暖底 + 红 chevron，与图中一致。进入限时会话后底部导航自动隐藏）──
            item(key = "entryMock") {
                NavRowCard(
                    icon = "clock",
                    title = "限时模考",
                    subtitle = "全真模拟 · 检验水平",
                    iconTint = Color.White,
                    iconBg = AppColors.warning,
                    containerColor = AppColors.redBg,
                    chevronTint = AppColors.danger,
                    onClick = { showMock = true }
                )
            }

            // ── E8 复习巩固（本页唯一圆点标题）──
            item(key = "reviewHead") { SectionTitleDot("复习巩固", trailing = "错题 ${wrongIds.size} 道") }
            item(key = "reviewQuick") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(
                        "inbox", "错题本", "${wrongIds.size} 道待清", "wrong",
                        onClick = { vm.startWrong(disc) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        "target", "薄弱优先", "智能暴露薄弱点", "weak",
                        onClick = { vm.start(PracticeConfig(mode = "薄弱优先", num = num, interleave = interleave, disc = disc, stage = stageFilter)) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionCard(
                        "clock", "待复习", "$dueCount 题到期", "mock",
                        onClick = {
                            appVm.checkIn()
                            vm.start(PracticeConfig(mode = "仅复习", num = 20, disc = disc, stage = stageFilter))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(key = "reviewCause") {
                NavRowCard(
                    icon = "brain",
                    title = "错因强化",
                    subtitle = "按错因精准补强 · 概念不清 / 审题偏差 / 记忆模糊…",
                    iconTint = Color.White,
                    iconBg = AppColors.purple,
                    containerColor = AppColors.purpleBg,
                    onClick = { showCausePicker = true }
                )
            }

            // ── E7 练习设置（整行入口，摘要随偏好实时刷新）──
            // 🔴 2026-09-27 间距双轨：区块头补前导 8dp ⇒ 区块间距 12 + 8 = 20dp（不动全局 spacedBy）。
            item(key = "setupHead") { GroupTitle("练习设置", Modifier.padding(top = 8.dp)) }
            item(key = "setup") {
                NavRowCard(
                    icon = "settings",
                    title = "练习设置",
                    subtitle = "题量 $num · ${if (interleave) "穿插混合" else "单章"} · ${if (timedMode) "限时" else "不限时"} · ${if (showAnswer) "即时答案" else "交后答案"}",
                    onClick = { nav.navigate("practicesetup") }
                )
            }

            // ── 最近练习（空态给 EmptyHint，不留白板）──
            item(key = "recentHead") { GroupTitle("最近练习", Modifier.padding(top = 8.dp)) }
            if (chapterList.isEmpty()) {
                item(key = "recentEmpty") {
                    EmptyHint("book", "还没有练习记录", "开始练习后，这里会显示最近练习的章节与进度", Modifier.fillMaxWidth())
                }
            } else {
                items(chapterList, key = { it.first }, contentType = { "chapter" }) { row ->
                    val (ch, practiced, data) = row
                    val (totalAcc, pct) = data
                    val (total, acc) = totalAcc
                    ChapterRow(
                        rank = null,
                        title = ch,
                        detail = "已练 $practiced/$total · 正确率 $acc%",
                        progress = pct / 100f,
                        percent = "$pct%",
                        onClick = { vm.startChapter(subj ?: "科一", ch, null, num, if (subj == "科三") disc else null) }
                    )
                }
            }
        }
    }

    // 错因选择对话框
    if (showCausePicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCausePicker = false },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCausePicker = false }) { Text("取消") }
            },
            title = { Text("错因强化", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (causeDist.isEmpty()) {
                        Text("暂无错因数据，练习时标记错因后这里会显示可选错因。", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary)
                    } else {
                        Text("选择要强化的错因：", style = MaterialTheme.typography.bodyMedium)
                        causeDist.entries.sortedByDescending { it.value }.forEach { (cause, count) ->
                            Card(
                                onClick = {
                                    showCausePicker = false
                                    vm.startCause(cause, disc)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = AppColors.purpleBg),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Text(cause, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("$count 次", style = MaterialTheme.typography.bodySmall, color = AppColors.purple, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // 快速模考三档选择
    if (showMock) {
        androidx.compose.material3.AlertDialog(
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
                                vm.startBlueprint(disc, n, min * 60)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            }
        )
    }
}
