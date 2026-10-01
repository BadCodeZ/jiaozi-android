package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.QuickActionCard
import com.jiaozi.sz.ui.components.SectionTitleDot
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.HubBar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.domain.StatsCalculator
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.reduceMotionNow

// ============================================================
// 页面数据模型（06 号 E5 / E7 的行模型）
// ============================================================

/** 科目分布行（06 号 E5）：科目 / 正确率 / 该科已练题量 */
private data class SubjectStat(val subject: String, val acc: Float, val trained: Int)

/** 需要加强行（06 号 E7）：科目 / 章节 / 正确率 / 该章已练题量 */
private data class WeakChapter(val subject: String, val chapter: String, val acc: Float, val trained: Int)

/** 周期分段项（06 号 E3：7 天 / 30 天 / 全部） */
private val STATS_RANGES = listOf("7天", "30天", "全部")

/**
 * 正确率 → 语义色（06 号：**以高保真实测阈值为准**）。
 * ≥70% success / 60%~70% warning / <60% danger。普通函数 ⇒ DrawScope / 行内均可直接调用。
 */
private fun accColor(acc: Float): Color = when {
    acc >= 0.70f -> AppColors.success
    acc >= 0.60f -> AppColors.warning
    else -> AppColors.danger
}

/** 日期 → 星期中文（趋势图 x 轴 一~日） */
private fun weekdayLabel(iso: String): String = runCatching {
    when (java.time.LocalDate.parse(iso).dayOfWeek.value) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
    }
}.getOrDefault(iso.takeLast(2))

/**
 * 一级 Tab · 统计（06 号 `stats.main`，两段式布局）。
 *
 * 结构（🔴 不许回退）：
 * 非滚动外框 Column → `CollapsingTopBlocks[Hero]`（四列核心指标 + 周期分段已下沉进滚动区首项）
 * → **唯一滚动容器** `LazyColumn(Modifier.fillMaxWidth().weight(1f))`。
 *
 * ⭐ 固定带挂 `hubDragToScroll(listState)` 做**手势直通**（固定带自身是非滚动
 * Column，无滚动节点 ⇒ 不挂则手指落上去既不滚列表也无任何反应）。
 * 🔴 折叠机制 2026-09-21 已整体停用、2026-09-25 死码清理 ⇒ 外框**不再挂**任何折叠监听。
 *
 * 缺 `weight(1f)` 会导致「整页滑不动 + 悬浮导航栏被空底衬成一块白色遮罩」（05 号 critical_history）。
 *
 * 合规说明（01 / 06 号全工程禁令）：
 *  - 本页**不出现**「连续打卡 / streak」概念；第二格口径改为「近 7 天有练习的天数」（纯区间统计）。
 *  - **无数据时渲染整页空态**（[EmptyHint] + 「去练习」），**不得渲染全 0 图表**。
 *  - 页面**不得**再定义私有 StatCard（P1 归一后唯一的 [StatCard] 在 components 内）。
 */
@Composable
fun StatsScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val rm = reduceMotionNow(LocalContext.current)
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val daily by appVm.repo.recentDailyStat(90).collectAsStateWithLifecycle(initialValue = emptyList())
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    val questions = appVm.repo.bank.exam

    var timeRange by remember { mutableStateOf("30天") }

    // ── E2 四列核心指标 ──
    val practiced = remember(progress) { StatsCalculator.totalPracticed(progress) }
    val overall = remember(progress) { StatsCalculator.overallAccuracy(progress) }
    val dueCount = remember(progress) {
        val now = System.currentTimeMillis()
        progress.values.count { it.due > 0 && it.due <= now }
    }
    // 🔴 禁 streak：以「近 7 天有练习的天数」侧写（区间统计，不依赖连续打卡）
    val activeDays7 = remember(daily) { daily.takeLast(7).count { it.right + it.wrong > 0 } }

    // ── E3 周期过滤 ──
    val filteredDaily = remember(daily, timeRange) {
        when (timeRange) {
            "7天" -> daily.takeLast(7)
            "30天" -> daily.takeLast(30)
            else -> daily
        }
    }
    val rangeTotal = remember(filteredDaily) { filteredDaily.sumOf { it.right + it.wrong } }

    // ── E5 科目分布 ──（口径下沉 StatsCalculator.attemptStat：分母＝作答次数，非已练题数）
    val subjectStats = remember(progress, questions, disc) {
        listOf("科一", "科二", "科三").mapNotNull { subj ->
            val qs = questions.filter { it.subject == subj && (subj != "科三" || it.disc == disc) }
            if (qs.isEmpty()) return@mapNotNull null
            val stat = StatsCalculator.attemptStat(qs, progress)
            SubjectStat(subj, stat.acc, stat.practiced)
        }
    }

    // ── E7 需要加强：正确率升序；仅「正确率 <75% 且已练 ≥5 题」──
    // 🔴 2026-09-29 修复：原分母误用「已练题数」⇒ 正确率恒 ≥75% ⇒ 本区块恒空。
    val weakChapters = remember(progress, questions, disc) {
        questions
            .filter { it.subject != "科三" || it.disc == disc }
            .groupBy { it.subject to it.chapter }
            .mapNotNull { (k, list) ->
                val stat = StatsCalculator.attemptStat(list, progress)
                if (stat.practiced < 5) return@mapNotNull null
                if (stat.acc >= 0.75f) return@mapNotNull null
                WeakChapter(k.first, k.second, stat.acc, stat.practiced)
            }
            .sortedWith(compareBy({ it.acc }, { -it.trained }))
            .take(5)
    }

    val heroSub = when (timeRange) {
        "7天" -> "最近 7 天"
        "30天" -> "最近 30 天"
        else -> "全部记录"
    }

    val listState = rememberLazyListState()
    val hasData = practiced > 0

    Crossfade(targetState = metaLoaded, animationSpec = tween(Motion.duration(rm, Motion.SLOW)), label = "statsLoad") { loaded ->
        if (!loaded) return@Crossfade

        // 🔴 2026-09-19 沉浸 Hero（对齐高保真稿）：外框不再整体加横向 padding，由 Hero 通栏铺满，
        //    其余元素各自补回 sp.16（统计行 / 列表 contentPadding）。
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
                // ── E1 Hero 统计头（无 action · 通栏沉浸）──
                HeroHeader(
                    title = "统计",
                    subtitle = heroSub,
                    icon = appPainter("bars"),
                    immersive = true,
                    statusBarInset = statusBarTop
                )
            }

            // 2026-09-25 晚：原 E10 收起态常驻栏（HubBar）已删 —— 折叠状态机退场后收起态不存在。

            // ══════════ 下半段：唯一滚动容器 ══════════
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // 2026-09-19：外框已移除横向 padding，此处补回 sp.16 保持内容边距不变。
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = com.jiaozi.sz.ui.components.NavTokens.ContentBottomPad),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasData) {
                            // ── E2 四列核心指标（窄卡：方徽章 + 标签在徽章下 + 数值，通栏进度条）──
                            // 2026-09-21：下沉进滚动区，横向边距由 LazyColumn contentPadding 提供。
                            // 2026-09-20 ⑤ 值色口径：本页为全站唯一「混合口径」页（高保真实证）——
                            // 「累计练题 / 近 7 天练了」为纯计数 ⇒ 中性深色；「平均正确率」＝达成度、
                            // 「待复习」＝告警指标 ⇒ 保留语义色。语义色另落在徽章 / 图标 / 进度条。
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard(
                                    icon = "edit", value = "$practiced", unit = "题", label = "累计练题",
                                    modifier = Modifier.weight(1f),
                                    valueColor = AppColors.textPrimary,
                                    iconTint = Color.White, iconBg = AppColors.blue,
                                    labelInline = false, containerColor = AppColors.blueBg,
                                    containerHPad = 10.dp, valueFontSize = 20.sp
                                )
                                StatCard(
                                    icon = "check", value = "${(overall * 100).toInt()}", unit = "%", label = "平均正确率",
                                    modifier = Modifier.weight(1f),
                                    valueColor = accColor(overall),
                                    iconTint = Color.White, iconBg = AppColors.success,
                                    labelInline = false, containerColor = AppColors.greenBg,
                                    containerHPad = 10.dp, valueFontSize = 20.sp
                                )
                                StatCard(
                                    icon = "calendar", value = "$activeDays7", unit = "天", label = "近 7 天练了",
                                    modifier = Modifier.weight(1f),
                                    valueColor = AppColors.textPrimary,
                                    iconTint = Color.White, iconBg = AppColors.purple,
                                    labelInline = false, containerColor = AppColors.purpleBg,
                                    containerHPad = 10.dp, valueFontSize = 20.sp
                                )
                                StatCard(
                                    icon = "bell", value = "$dueCount", unit = "题", label = "待复习",
                                    modifier = Modifier.weight(1f),
                                    valueColor = if (dueCount > 0) AppColors.warning else AppColors.success,
                                    iconTint = Color.White,
                                    iconBg = if (dueCount > 0) AppColors.warning else AppColors.success,
                                    labelInline = false, containerColor = AppColors.redBg,
                                    containerHPad = 10.dp, valueFontSize = 20.sp
                                )
                            }

                            // ── E3 周期分段控件（单浅底容器三等分，选中＝主色胶囊白字）──
                            SegmentedRange(
                                options = STATS_RANGES,
                                selected = timeRange,
                                onSelect = { timeRange = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (!hasData) {
                    // ── E9 空态（CMP-EMPTYHINT + 主行动按钮），不渲染全 0 图表 ──
                    item(key = "empty") {
                        EmptyHint(
                            icon = "info",
                            title = "还没有统计数据",
                            hint = "完成第一组练习后，这里会显示你的学习诊断",
                            action = {
                                Button(onClick = { nav.navigate(Screen.Practice.route) }) { Text("去练习") }
                            }
                        )
                    }
                    return@LazyColumn
                }

                // ── E4 练习趋势卡 ──
                // 🔴 2026-09-25 晚（E/C1）：区块间距 sp.20 —— 本列 spacedBy 恒 12dp 承担「卡片间距」，
                //    区块之间再补 8dp 前导 ⇒ 12+8=20dp（01 号 tokens.section_gap「区块之间恒 20dp」；
                //    06 号 high_fidelity「所有卡片间距恒定 12dp；区块间距 20dp」）。不动全局 spacedBy，
                //    以免误伤已合规的 12dp 卡片间距。
                item(key = "trend") {
                    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("练习趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "总题量 $rangeTotal 题",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TrendChart(
                            bars = filteredDaily.map { Triple(it.date, it.right + it.wrong, 0) },
                            highlightLast = true
                        )
                    }
                }

                // ── E5 科目分布卡 ──
                // 🔴 2026-09-25 晚（E/C1）：区块间距 sp.20 —— 本列 spacedBy 恒 12dp 承担「卡片间距」，
                //    区块之间再补 8dp 前导 ⇒ 12+8=20dp（01 号 tokens.section_gap「区块之间恒 20dp」；
                //    06 号 high_fidelity「所有卡片间距恒定 12dp；区块间距 20dp」）。不动全局 spacedBy，
                //    以免误伤已合规的 12dp 卡片间距。
                item(key = "subject") {
                    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                        Text("科目分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        if (subjectStats.isEmpty()) {
                            Text("暂无练习记录", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary)
                        } else {
                            subjectStats.forEach { s ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        BankStore.shortName(s.subject),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 13.sp,
                                        color = AppColors.textPrimary,
                                        maxLines = 1,
                                        modifier = Modifier.width(56.dp)
                                    )
                                    Box(
                                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(AppColors.trackGray)
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(s.acc.coerceIn(0f, 1f))
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(accColor(s.acc))
                                        )
                                    }
                                    Text(
                                        "${(s.acc * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = accColor(s.acc),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(44.dp)
                                    )
                                    Text(
                                        "${s.trained} 题",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.textSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.width(52.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── E6 + E7 需要加强 ──
                // 🔴 2026-09-25 晚（E/C1）：区块间距 sp.20 —— 本列 spacedBy 恒 12dp 承担「卡片间距」，
                //    区块之间再补 8dp 前导 ⇒ 12+8=20dp（01 号 tokens.section_gap「区块之间恒 20dp」；
                //    06 号 high_fidelity「所有卡片间距恒定 12dp；区块间距 20dp」）。不动全局 spacedBy，
                //    以免误伤已合规的 12dp 卡片间距。
                item(key = "weakHead") { SectionTitleDot("需要加强", trailing = "(${weakChapters.size})", modifier = Modifier.padding(top = 8.dp)) }
                if (weakChapters.isEmpty()) {
                    item(key = "weakEmpty") {
                        SectionCard {
                            Text(
                                "暂无明显薄弱章节 —— 保持练习，正确率低于 75% 且已练满 5 题的章节会出现在这里",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.textSecondary
                            )
                        }
                    }
                } else {
                    item(key = "weakList") {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                weakChapters.forEachIndexed { i, w ->
                                    if (i > 0) {
                                        HorizontalDivider(
                                            Modifier.padding(start = 16.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    // 🔴 2026-09-23（走查 #34）：原跳 `chapters`（管理页）与「去练这一章」语义不符，
                                    //    改为携带 (科目, 章节) 直达练习页，与章节页「优先练习」同一条通路。
                                    WeakChapterRow(w) {
                                        appVm.setPendingChapterPractice(w.subject, w.chapter)
                                        nav.navigate("practice")
                                    }
                                }
                            }
                        }
                    }
                }

                // ── E8 更多（3 列 QuickActionCard）──
                // 🔴 2026-09-25 晚（E/C1）：区块间距 sp.20 —— 本列 spacedBy 恒 12dp 承担「卡片间距」，
                //    区块之间再补 8dp 前导 ⇒ 12+8=20dp（01 号 tokens.section_gap「区块之间恒 20dp」；
                //    06 号 high_fidelity「所有卡片间距恒定 12dp；区块间距 20dp」）。不动全局 spacedBy，
                //    以免误伤已合规的 12dp 卡片间距。
                item(key = "moreHead") { GroupTitle("更多", modifier = Modifier.padding(top = 8.dp)) }
                item(key = "moreRow") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(
                            "tree", "知识图谱", "查看掌握度", "weak",
                            onClick = { nav.navigate("graph") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            "grid", "章节管理", "调整章节权重", "weak",
                            onClick = { nav.navigate("chapters") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionCard(
                            "inbox", "错题本", "收集错题", "weak",
                            // 🔴 2026-09-23（走查 #34）：错题本在校订页第 2 个 Tab，不应跳练习页
                            onClick = { appVm.setPendingProofTab("错题本"); nav.navigate("proof") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 页面私有小件（仅布局壳，非数据卡副本）
// ============================================================

/** 统计页标准卡壳：surfaceContainer + r24 + padding 16dp + 无投影（06 号卡片规格） */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content
        )
    }
}

/** 周期分段控件（06 号 E3）：单浅蓝容器三等分，选中＝主色胶囊 + 白字 */
@Composable
private fun SegmentedRange(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AppColors.blueLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(opt) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) Color.White else AppColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** 需要加强单行（06 号 E7：章节名 weight1f → 右对齐语义色百分比 48dp → chevron） */
@Composable
private fun WeakChapterRow(item: WeakChapter, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.chapter,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${BankStore.shortName(item.subject)} · 已练 ${item.trained} 题",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Text(
            "${(item.acc * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = accColor(item.acc),
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
        Icon(
            appPainter("chevron"),
            contentDescription = null,
            tint = AppColors.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 练习趋势柱状图（06 号 E4）：自绘 Canvas。
 * 规格：高 160dp、**单一主色**（今日实色 / 历史 70%）、柱顶圆角 4dp、最小柱高 4dp、
 * **无折线**、柱距 6dp；左侧 5 档刻度（yMax/…/0），底部 x 轴星期标签（一~日）。
 *
 * @param bars          Triple<ISO 日期, 当日题量, 预留>
 * @param highlightLast 末根（今日）用实色，其余 70% 透明度
 */
@Composable
private fun TrendChart(bars: List<Triple<String, Int, Int>>, highlightLast: Boolean = true) {
    if (bars.isEmpty()) return
    val plotH = 140.dp
    val maxTotal = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val yMax = (((maxTotal + 19) / 20) * 20).coerceAtLeast(20)
    val yLabels = (4 downTo 0).map { (yMax * it / 4).toString() }
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = AppColors.textSecondary
    val xStep = ((bars.size + 6) / 7).coerceAtLeast(1)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            // ── Y 轴刻度 ──
            Column(
                Modifier.width(26.dp).height(plotH),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor, fontSize = 11.sp) }
            }
            Spacer(Modifier.width(8.dp))
            // ── 柱区 ──
            Canvas(Modifier.weight(1f).height(plotH)) {
                val n = bars.size
                val gap = 6.dp.toPx()
                val slot = size.width / n
                val barW = (slot - gap).coerceAtLeast(2.dp.toPx())
                val minH = 4.dp.toPx()
                val r = 4.dp.toPx()
                // 网格线（4 等分）
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(
                        color = AppColors.trackGray,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }
                bars.forEachIndexed { i, bar ->
                    val total = bar.second
                    val h = (total.toFloat() / yMax * size.height).coerceAtLeast(minH)
                    val x = i * slot + (slot - barW) / 2f
                    val alpha = if (highlightLast && i == n - 1) 1f else 0.7f
                    drawRoundRect(
                        color = barColor.copy(alpha = alpha),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // ── X 轴星期标签（对齐柱位等分）──
        Row(Modifier.fillMaxWidth().padding(start = 34.dp)) {
            bars.forEachIndexed { i, bar ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % xStep == 0 || i == bars.lastIndex) {
                        Text(
                            weekdayLabel(bar.first),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
