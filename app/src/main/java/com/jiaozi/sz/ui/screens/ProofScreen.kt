package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.GlassIconButton
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.StatCard

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.local.UserQuestionEntity
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import java.io.File

/**
 * 校订（对齐网页端 `VIEW.proof` 四标签）：
 * - 待审：AI 生成题经质量护栏标记「待审」的人工复核；
 * - 错题本：wrongBook 题，可「导出 PDF」（系统分享打印）；
 * - 归类：未归类用户题手动指派章节；
 * - 复核：本地质量抽检（解析过短/缺答案）。
 *
 * V2.78：Hero 头与各 Tab 的说明区/操作区归入顶部常驻块；**四个 Tab 切换 chips 常驻**。
 * ⚠️ 原「滚动折叠」行为已于 2026-09-21 停用、2026-09-25 清理死码，顶块现恒常驻。
 */
@Composable
fun ProofScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("待审") }
    val tabs = listOf("待审", "错题本", "归类", "复核")
    // 🔴 2026-09-23（走查 #34）：消费统计页「错题本」入口指定的落位 Tab。
    //    消费后立即清空，避免下次从别处进来仍停在旧 Tab（「只生效一次」语义）。
    val pendingProofTab by appVm.pendingProofTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingProofTab) {
        pendingProofTab?.let { t -> if (t in tabs) tab = t; appVm.clearPendingProofTab() }
    }

    // —— E2 统计口径（校订页三卡；待归类/错题数按各 Tab 的数据源计数）——
    var statPending by remember { mutableStateOf(0) }
    var statClassify by remember { mutableStateOf(0) }
    var statWrong by remember { mutableStateOf(0) }
    // 🔴 2026-09-24 修硬编码：Hero 副标题与「复核」Tab 计数原为写死的字面量 `3`
    //    （10 号规范里「3 待复核」只是示意图的样例数字），与真实抽检结果无关 ⇒
    //    复核数变 0 或变 7 时界面仍显示 3，属数据失真。此处按 ReviewTab 同一口径真算：
    //    本地质量抽检命中集 ∩ 待审题集。副标题第三项与 Tab 徽标共用此值，保证两处自洽。
    var statReview by remember { mutableStateOf(0) }
    // 🔴 2026-09-25 G1 配套：「已掌握」把题移出错题本后，Hero 的「N 条错题」与 Tab 徽标必须同步减。
    //    做法＝一个自增 tick 作为 LaunchedEffect 依赖；子 Tab 落盘成功后 +1 ⇒ 统计块重算。
    var statTick by remember { mutableStateOf(0) }
    LaunchedEffect(tab, disc, statTick) {
        val pending = repo.pendingProofQuestions()
        statPending = pending.count { it.id !in repo.proofReviewedIds() }
        statClassify = repo.unclassifiedUserQuestions().size
        statWrong = repo.wrongBookQuestions(if (disc.isNotBlank()) disc else null).size
        val reviewIds = repo.localQualityCheck(10)
        statReview = pending.count { it.id in reviewIds }
    }
    // 进度条语义（10 号 JSON）：该卡值 / 同组最大值
    val progressBase = maxOf(statPending, statClassify, statWrong).coerceAtLeast(1)

    val listState = rememberLazyListState()

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(AppColors.bg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ① Hero 头 + 统计带：常驻不滚（沉浸通栏，手势直通下方列表）
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
            HeroHeader(
                title = "校订",
                subtitle = "$statPending 待处理 · $statClassify 待归类 · $statReview 待复核",
                icon = appPainter("proof"),
                immersive = true,
                statusBarInset = statusBarTop,
                // 返回键内联进 Hero 首行（与右上搜索键左右成对）——沉浸页不再由 AppNav 叠加返回件，
                // 否则「磨砂白圆 + 标题胶囊」会正好压在本行的 46dp 图标徽章上（2026-09-19 实测）。
                onBack = { nav.navigateUp() },
                action = {
                    // 🔴 2026-10-01 九校二迭代：Hero 动作键统一为 Liquid Glass 圆钮 44dp
                    //   （原 40dp 白 18% 圆 + 白图标；改玻璃后图标同步改主色）。
                    GlassIconButton(
                        onClick = { nav.navigate("search") },
                        icon = "search",
                        contentDescription = "搜索",
                        size = 44.dp
                    )
                }
            )

            // ── E2 统计卡组（3col，各带实心语义圆徽章 + 进度条 + 语义浅底）──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = "clock", value = "$statPending", unit = "条", label = "待处理",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.textPrimary,
                    iconTint = Color.White, iconBg = AppColors.warning,
                    iconShape = CircleShape, iconSize = 22.dp,
                    labelInline = true, containerColor = AppColors.warningBg,
                    containerHPad = 12.dp,
                    progress = statPending.toFloat() / progressBase,
                    progressColor = AppColors.warning
                )
                StatCard(
                    icon = "layers", value = "$statClassify", unit = "条", label = "待归类",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.textPrimary,
                    iconTint = Color.White, iconBg = AppColors.blue,
                    iconShape = CircleShape, iconSize = 22.dp,
                    labelInline = true, containerColor = AppColors.blueBg,
                    containerHPad = 12.dp,
                    progress = statClassify.toFloat() / progressBase,
                    progressColor = AppColors.blue
                )
                StatCard(
                    icon = "alert", value = "$statWrong", unit = "条", label = "错题",
                    modifier = Modifier.weight(1f),
                    valueColor = AppColors.textPrimary,
                    iconTint = Color.White, iconBg = AppColors.danger,
                    iconShape = CircleShape, iconSize = 22.dp,
                    labelInline = true, containerColor = AppColors.redBg,
                    containerHPad = 12.dp,
                    progress = statWrong.toFloat() / progressBase,
                    progressColor = AppColors.danger
                )
            }
        }

        // ② Tab 切换常驻（关键导航，任何状态都保留）——自绘 chip：选中 primary 实底白字 + 内嵌计数
        //    🔴 通栏无阴影 + 底部 1dp outlineVariant 分隔；不得随 CollapsingTopBlocks 收起
        //
        // 🔴 返回入口：由 Hero 首行的**内联返回键**唯一承担。
        //（2026-09-25 晚：原「收起态于本行最左补返回键」已随折叠状态机退场删除，全页现仅此一个返回入口。）
        Column(Modifier.fillMaxWidth().hubDragToScroll(listState)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 🔴 chip「一屏全见」的由来：四枚 chip 固有总宽 1298px，可用宽度 1312px 刚好放下。
                //（2026-09-19 实测曾因插入 36dp 返回键把末位 chip 压到 11px「复核」裁字；
                //  该返回键已随折叠退场删除，宽度压力消除，内边距恒定 14dp 即够。）
                //    外层仍加 horizontalScroll 作兜底（更窄屏/更长文案时不裁字，可横滑看到）。
                // 🔴 2026-09-25 晚：原「收起态 8dp / 展开态 14dp」已随折叠状态机退场（收起态不存在）。
                //    恒定 14dp = 四枚 chip 固有总宽 1298px，展开态可用宽度刚好放下，保证「一屏全见」。
                val chipHPad = 14.dp
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                tabs.forEach { t ->
                    val selected = tab == t
                    val count = when (t) {
                        "待审" -> statPending
                        "错题本" -> statWrong
                        "归类" -> statClassify
                        else -> statReview // 复核：真实抽检数（原为写死 3）
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) AppColors.blue else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { tab = t }
                            .padding(horizontal = chipHPad, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            t,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else AppColors.textSecondary,
                            maxLines = 1
                        )
                        Text(
                            "$count",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) Color.White.copy(alpha = 0.85f) else AppColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }

        when (tab) {
            "待审" -> PendingTab(listState, appVm, repo)
            "错题本" -> WrongBookTab(listState, appVm, repo, disc, nav, onCountChanged = { statTick++ })
            "归类" -> ClassifyTab(listState, appVm, repo)
            "复核" -> ReviewTab(listState, appVm, repo)
        }
    }
}

@Composable
private fun PendingTab(
    listState: LazyListState,
    appVm: AppViewModel,
    repo: com.jiaozi.sz.data.AppRepository
) {
    var pending by remember { mutableStateOf<List<Question>>(emptyList()) }
    var reviewed by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        pending = repo.pendingProofQuestions()
        reviewed = repo.proofReviewedIds()
    }
    val pendingUnreviewed = pending.filter { it.id !in reviewed }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 说明区：顶部常驻 + 手势直通
        CollapsingTopBlocks(modifier = Modifier.hubDragToScroll(listState)) {
        }
        if (pending.isEmpty()) {
            EmptyHint("check", "题库质量良好", "没有待校订的题目，继续加油。")
        } else {
            Text(
                "待校订 ${pendingUnreviewed.size} / 共 ${pending.size} 题",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (pendingUnreviewed.isNotEmpty()) {
                Button(
                    onClick = { appVm.viewModelScope.launch { pendingUnreviewed.forEach { repo.markProofReviewed(it.id) }; reviewed = repo.proofReviewedIds() } },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { Text("全部标记已校订") }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).navigationBarsPadding()
            ) {
                // ── 下沉（2026-09-21）：随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AI 生成题标记「待审」，人工确认后入库。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(pendingUnreviewed, contentType = { "proof" }) { q -> ProofCard(q, appVm, repo, reviewed) { appVm.viewModelScope.launch { reviewed = repo.proofReviewedIds() } } }
            }
        }
    }
}

/**
 * 待校订题卡（对齐高保真右屏 E4）：
 * ```
 * [36dp 蓝底 menu]  题干（maxLines=3）                      ›
 *                   来源 meta（12sp 灰）
 *                   [采纳 primary 实底] [修正 描边] [丢弃 描边 danger]
 * ```
 * 🔴 三键落地口径（数据层 2026-09-19 新增 [com.jiaozi.sz.data.AppRepository.setProofFlag]）：
 *  - 采纳 → 既有 `markProofReviewed`（双写，移出待审池）
 *  - 修正 → 覆盖层 flag='需修正'（**留在待审池**，供后续继续处理）
 *  - 丢弃 → 覆盖层 flag='已丢弃'（移出待审池）
 */
@Composable
private fun ProofCard(q: Question, appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository, reviewed: Set<String>, onReviewed: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 前导图标：36dp 浅蓝底圆角方块 + 蓝 menu 图标
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.blueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(appPainter("menu"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), Arrangement.spacedBy(6.dp)) {
                Text(
                    q.q,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                // 来源 meta：科目 · 章节（12sp 灰，缩进与题干对齐）——科目用大纲官方简写
                Text(
                    listOfNotNull(
                        q.subject.takeIf { it.isNotBlank() }?.let { BankStore.shortName(it) },
                        q.chapter.takeIf { it.isNotBlank() }
                    )
                        .joinToString(" · ").ifBlank { "未分类" },
                    fontSize = 13.sp,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!q.analysis.isNullOrBlank()) Text(
                    "解析：${q.analysis}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!q.flagMsg.isNullOrBlank()) Text(
                    "校订提示：${q.flagMsg}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.warning
                )
                Spacer(Modifier.height(2.dp))
                // ── 三键：采纳（primary 实底 32dp）/ 修正（描边）/ 丢弃（描边 danger）──
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { appVm.viewModelScope.launch { repo.markProofReviewed(q.id); onReviewed() } },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
                    ) { Text("采纳", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { appVm.viewModelScope.launch { repo.setProofFlag(q.id, "需修正"); onReviewed() } },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        border = BorderStroke(1.dp, AppColors.blue),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.blue)
                    ) { Text("修正", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { appVm.viewModelScope.launch { repo.setProofFlag(q.id, "已丢弃"); onReviewed() } },
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        border = BorderStroke(1.dp, AppColors.danger),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.danger)
                    ) { Text("丢弃", fontSize = 13.sp) }
                }
            }
            Icon(
                appPainter("chevron"),
                contentDescription = null,
                tint = AppColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WrongBookTab(
    listState: LazyListState,
    appVm: AppViewModel,
    repo: com.jiaozi.sz.data.AppRepository,
    disc: String,
    nav: NavHostController,
    onCountChanged: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    var items by remember { mutableStateOf<List<Pair<Question, ProgressEntity>>>(emptyList()) }
    LaunchedEffect(disc) { items = repo.wrongBookQuestions(if (disc.isNotBlank()) disc else null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ① 科目筛选：固定教资三科（某科暂无错题也可点，显示空态提示）——常驻，收起后仍可切科
        // 数据键仍为「科一/科二/科三」，展示名走大纲官方名（综合素质 / 教育知识 / 学科知识）
        val subjOptions = listOf("全部", "科一", "科二", "科三")
        var subjFilter by remember { mutableStateOf("全部") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            subjOptions.forEach { s ->
                FilterChip(
                    selected = subjFilter == s,
                    onClick = { subjFilter = s },
                    label = { Text(if (s == "全部") "全部" else BankStore.shortName(s)) }
                )
            }
        }
        val filtered = if (subjFilter == "全部") items else items.filter { it.first.subject == subjFilter }

        // ② 视图切换：错因聚类 / 按科目（声明在外，分组计算要用）
        var viewBy by remember { mutableStateOf("错因") }

        // ③ 说明 / 导出 / 视图切换：顶部常驻 + 手势直通
        CollapsingTopBlocks(modifier = Modifier.hubDragToScroll(listState)) {
        }


        if (filtered.isEmpty()) {
            EmptyHint("inbox", if (subjFilter == "全部") "错题本是空的" else "该科目暂无错题", "继续保持，做错的题会自动归到这里。")
        } else {
            val groups = remember(filtered, viewBy) {
                val g = when (viewBy) {
                    // 「按科目」分组直接用大纲官方名出键（数据键仍是 科一/科二/科三）
                    "科目" -> filtered.groupBy { BankStore.shortName(it.first.subject) }
                    else -> filtered.groupBy { (it.second.cause?.takeIf { c -> c.isNotBlank() } ?: "未标注错因") }
                }
                g.toList().sortedByDescending { it.second.size }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), state = listState, modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                // ── 下沉（2026-09-21）：随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("错题本（科目三《学科知识与教学能力》按「$disc」隔离，共 ${filtered.size} 题）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                            appVm.viewModelScope.launch {
                                val html = buildString {
                                    append("<html><head><meta charset='utf-8'><title>错题本</title></head><body>")
                                    append("<h2>综合教资备考 · 错题本（${filtered.size} 题）</h2>")
                                    filtered.forEachIndexed { i, (q, _) ->
                                        append("<p><b>${i + 1}. [${BankStore.officialName(q.subject)}] ${q.chapter}</b><br>${q.q}<br>")
                                        append("答案：${q.answer.ifBlank { "（主观题）" }}<br>")
                                        if (!q.analysis.isNullOrBlank()) append("解析：${q.analysis}")
                                        append("</p>")
                                    }
                                    append("</body></html>")
                                }
                                val file = File(ctx.cacheDir, "wrong_book.html")
                                file.writeText(html)
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/html"; putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                ctx.startActivity(Intent.createChooser(intent, "导出/打印错题本"))
                            }
                        }) { Icon(appPainter("share"), contentDescription = null); Text(" 导出 PDF / 打印") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("错因", "科目").forEach { v -> FilterChip(selected = viewBy == v, onClick = { viewBy = v }, label = { Text(v) }) }
                            }
                    }
                }

                groups.forEach { (key, group) ->
                    item(key = "h_$key") {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                            Text("$key · ${group.size} 题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                        }
                    }
                    items(group, contentType = { "wrong" }) { (q, p) ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                            Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                                // 🔴 2026-09-25 补 A9（#406 报告 A 类）：10 号 proof.main E5 规定
                                //    「Row(24dp 状态圆位 + 题干 maxLines=2)」，hf 明写「错题行复用结算页答题卡状态色」，
                                //    spec 进一步要求「状态圆点色与结算页答题卡一致」。此前无状态圆位、卡为单色底。
                                //    口径严格对齐 PracticeSession.kt:599/605（错误态 = errorContainer 底 + onErrorContainer 字 + ✗）。
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "✗",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        q.q,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text("${BankStore.shortName(q.subject)} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                // ── 答案区（2026-09-25 G1）：正确答案用 success 语义色单列。
                                //    此前为中性灰单行「答案：…」，在错题卡里与解析的 blue 块几乎同权重。
                                //    ⚠️ 客观题的「我的答案」未持久化（ProgressEntity 不存 selected，
                                //    PracticeViewModel L409 仅主观题写 draft）⇒ 客观题不得编造，只显正确答案；
                                //    主观题有草稿才补「我的答案」，靠 danger 语义色拉开对错对比。
                                if (!p.draft.isNullOrBlank()) {
                                    Text("我的答案：${p.draft}", style = MaterialTheme.typography.bodySmall, color = AppColors.danger)
                                }
                                q.answer.takeIf { it.isNotBlank() }?.let { a ->
                                    Text("正确答案：$a", style = MaterialTheme.typography.bodySmall, color = AppColors.success, fontWeight = FontWeight.SemiBold)
                                }
                                // ── 解析块（2026-09-24 对齐练习会话 E6b：解析是反馈主内容，错因不得压过）──
                                //    此前本卡完全不展示解析（仅导出 HTML 用到），是错因/解析层级的最大缺口。
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
                                if (!p.draft.isNullOrBlank()) {
                                    Text("我的作答：${p.draft}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // 错因：辅助信息（中性小字，仅在确有标记时显示；未标记不占位）
                                p.cause?.takeIf { it.isNotBlank() }?.let { c ->
                                    Text("错因：$c", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // ── 🔴 2026-09-25 G1 落地：「已掌握 / 重练」双键 ──
                                    //    语义（杰哥裁定）：「已掌握」= 把该题移出错题本、不再算错题。
                                    //    实现直接复用 ProgressEntity.wrongBook=false —— ProgressDao.wrongBook()
                                    //    查询即 `WHERE wrongBook = 1`，写 false 即天然从错题本消失，零新增字段。
                                    //    同题再次做错时练习流程会自动写回 true（PracticeViewModel.submit 分支），
                                    //    故「已掌握」是可逆的，不是永久删除。
                                    OutlinedButton(
                                        onClick = {
                                            appVm.viewModelScope.launch {
                                                // 先落盘再改本地列表，避免落盘失败却已从 UI 消失（假成功）
                                                repo.upsertProgress(p.copy(wrongBook = false, _mt = System.currentTimeMillis()))
                                                items = items.filterNot { it.first.id == q.id }
                                                onCountChanged()
                                                Toast.makeText(ctx, "已移出错题本", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("已掌握", style = MaterialTheme.typography.labelMedium) }
                                    OutlinedButton(
                                        onClick = { practiceVm.startByQuestion(q); nav.navigate("practice") },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("重练", style = MaterialTheme.typography.labelMedium) }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { appVm.setPendingAiContext(q.q); nav.navigate("aichat") }) {
                                        Icon(appPainter("chat"), contentDescription = "问 AI", tint = MaterialTheme.colorScheme.primary)
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

@Composable
private fun ClassifyTab(
    listState: LazyListState,
    appVm: AppViewModel,
    repo: com.jiaozi.sz.data.AppRepository
) {
    var items by remember { mutableStateOf<List<UserQuestionEntity>>(emptyList()) }
    LaunchedEffect(Unit) { items = repo.unclassifiedUserQuestions() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollapsingTopBlocks(modifier = Modifier.hubDragToScroll(listState)) {
        }
        if (items.isEmpty()) {
            EmptyHint("bars", "没有未归类题目", "收集箱转题或 AI 出题后会出现在这里。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), state = listState, modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                // ── 下沉（2026-09-21）：随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("未归类题（来自收集箱/AI 出题），手动指派科目与章节。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(items, contentType = { "classify" }) { e ->
                    var subj by remember { mutableStateOf(e.subject.ifBlank { "科一" }) }
                    var ch by remember { mutableStateOf("") }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                            Text(e.q.take(80), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(subj, { subj = it }, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(ch, { ch = it }, label = { Text("章节") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Button(onClick = { appVm.viewModelScope.launch { repo.setUserQuestionChapter(e.id, subj.trim(), ch.trim().ifBlank { "未分类" }); items = repo.unclassifiedUserQuestions() } }) { Text("保存归类") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewTab(
    listState: LazyListState,
    appVm: AppViewModel,
    repo: com.jiaozi.sz.data.AppRepository
) {
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { ids = repo.localQualityCheck(10) }
    val pending by remember { mutableStateOf<List<Question>>(emptyList()) }
    var full by remember { mutableStateOf<List<Question>>(emptyList()) }
    LaunchedEffect(Unit) { full = repo.pendingProofQuestions() }
    val hits = full.filter { it.id in ids }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CollapsingTopBlocks(modifier = Modifier.hubDragToScroll(listState)) {
        }
        if (hits.isEmpty()) {
            EmptyHint("check", "未检出质量问题", "待审题解析完整，质量良好。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), state = listState, modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                // ── 下沉（2026-09-21）：随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("本地质量抽检：解析过短或缺答案的题（无需 Key）。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(hits, contentType = { "review" }) { q -> ProofCard(q, appVm, repo, emptySet()) {} }
            }
        }
    }
}
