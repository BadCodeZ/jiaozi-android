package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.domain.StatsCalculator
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.GlassIconButton
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.HubChip
import com.jiaozi.sz.ui.components.NavRowCard
import com.jiaozi.sz.ui.components.SectionTitleDot
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.hubDragToScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import com.jiaozi.sz.ui.reduceMotionNow

/**
 * 一级 Tab · 题库（05 号 `bank.main`，两段式布局的标准范例）。
 *
 * 结构（🔴 不许回退）：
 * 非滚动外框 Column（折叠已停用，不挂任何监听）
 * → `CollapsingTopBlocks[Hero + 3 列统计 + 搜索胶囊 + 7 宫格]`（挂 `hubDragToScroll(listState)`）
 *（2026-09-25 晚：原 `CollapsedHubBar` 收起态栏已随折叠状态机退场删除）
 * → **唯一滚动容器** `LazyColumn(Modifier.fillMaxWidth().weight(1f))`。
 *
 * 🔴 本页历史故障：`LazyColumn` 漏 `Modifier.weight(1f)` ⇒ 「整页滑不动 + 悬浮导航栏被空底
 * 衬成一块白色遮罩」（05 号 `critical_history`）。该 `weight(1f)` 是**禁止回退项**。
 *
 * 🔴 二次故障（2026-09-18 修复）：固定带本身是非滚动 Column，内部无任何滚动节点 ⇒
 * 手指落在 Hero / 统计卡 / 搜索胶囊 / 快捷入口上**既不滚列表也不折叠**，整页像"死机"。
 * 修法＝固定带挂 `hubDragToScroll(listState)` 做手势直通（详见该 modifier KDoc）。
 *
 * 关键设计约束（05 号）：
 *  - E3 搜索是**通栏胶囊**（整条可点 → `search` 路由），**不是**就地输入的 `TextField`
 *    （与 `search` 页职责重复，属 anti_pattern）。
 *  - E4 七个资料入口**必须全部留在固定带内**（4 列 × 2 行），不得拆一半到滚动区。
 *  - E6『章节题量』标题作为滚动容器首个 item，**不留在固定带**（否则标题与内容视觉分离）。
 *  - 学科筛选（05 号未收录件）按 E6 先例**下沉进滚动区**：它是章节清单的过滤器，
 *    留在固定带会白占 59dp，把本就只剩 76dp 的列表区挤到几乎不可见。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val rm = reduceMotionNow(LocalContext.current)

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val contentAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(Motion.duration(rm, Motion.SLOW)), label = "bankFade")

    // 🔴 2026-10-02 UI 密度改造：固定带 Step1 → 5 宫格，「更多资料」半屏 sheet
    var showMore by remember { mutableStateOf(false) }

    // 学科筛选：默认「科一」（与高保真一致）
    val subjectTabs = listOf("科一", "科二", "科三", "全部")
    var filter by remember { mutableStateOf("科一") }

    // ── 统计三列（E2）──
    val totalQuestions = repo.bank.exam.size
    val practicedCount = progress.values.count { it.right + it.wrong > 0 }
    val wrongCount = remember(progress) { progress.values.sumOf { it.wrong } }

    // ── 章节题量数据（E7）──
    val chapterData = remember(repo, progress, disc, filter) {
        val subjects = if (filter == "全部") listOf("科一", "科二", "科三") else listOf(filter)
        subjects.flatMap { subj ->
            val syllabus = repo.syllabus.find { it.subject == subj }
            syllabus?.chapters?.map { ch ->
                val qs = repo.bank.exam.filter { it.subject == subj && it.chapter == ch.name && (subj != "科三" || it.disc == disc) }
                // 🔴 2026-09-29 修：原分母误用「已练题数」⇒ 正确率可 >100%；此处口径下沉 StatsCalculator
                val stat = StatsCalculator.attemptStat(qs, progress)
                val practiced = stat.practiced
                val acc = stat.accPercent
                val pct = if (qs.isNotEmpty()) practiced * 100 / qs.size else 0
                ChapterDisplay(subj, ch.name, qs.size, practiced, stat.wrong, acc, pct)
            } ?: emptyList()
        }.filter { it.total > 0 }
    }

    val listState = rememberLazyListState()

    // 🔴 2026-10-02 UI 密度改造（DENSITY_PLAN 方案二）：7 宫格 → 5 宫格
    // 「备课组」「校订」低频入口收进「更多资料」，固定带因此从 4+3 两行收敛为 4+1 两行。
    val quickRow1 = listOf(
        Triple("book", "教材", "books"),
        Triple("text", "课标", "curric"),
        Triple("grid", "章节", "chapters"),
        Triple("inbox", "收集箱", "inbox")
    )
    val quickRow2 = listOf(
        Triple("note", "知识库", "knowledge"),
        Triple("mor", "更多资料", "more")  // 第 5 格，点开「更多资料」半屏 sheet
    )

    // 🔴 2026-09-19 沉浸 Hero（对齐高保真稿）：本页各元素本就自带 sp.16
    //（统计行 / 列表 contentPadding），所以只需把 Hero 设为 immersive；
    //「溢出到屏幕顶 + 对外少报高度」由 HeroHeader 内部完成，详情见其 KDoc。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 🔴 固定带拖动（hubDragToScroll）与列表拖动（LazyColumn 自身）是**两个互斥命中的滚动节点**：
    //    一次手势只驱动命中的那一个 ⇒ 不会双倍滚动。折叠监听 2026-09-25 已随死码清理移除。
    Column(
        Modifier
            .fillMaxSize()
            .alpha(contentAlpha)
            .background(AppColors.bg)
    ) {
        // ══════════ 顶部常驻：仅 Hero（2026-09-21 折叠停用；信息带已下沉进滚动区）══════════
        // Hero 常驻、不随滚动走：沉浸 Hero 若滚出，状态栏区域会露出页面底色而白色图标不可见。
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
            // ── E1 Hero 题库头（with_action：右上 40dp 搜索键 · 通栏沉浸）──
            HeroHeader(
                title = "题库",
                subtitle = "${"%,d".format(totalQuestions)} 题 · 三科全覆盖",
                icon = appPainter("book"),
                immersive = true,
                statusBarInset = statusBarTop,
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

        }

        // 2026-09-25 晚：原 E5 收起态紧凑栏已整块删除（折叠状态机整体退场，收起态不存在）。

        // ══════════ 下半段：唯一滚动容器（🔴 weight(1f) 禁止回退）══════════
        // 折叠已停用（2026-09-25 死码清理）⇒ 本容器只负责滚动，不挂任何折叠监听。
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = com.jiaozi.sz.ui.components.NavTokens.ContentBottomPad),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 学科筛选（原固定带件，下沉为滚动区首项）──
            // 05 号 E1–E10 未收录本件；它是「章节清单的过滤器」，与 E6 同属「属于列表的控件」
            // ⇒ 按 E6 先例下沉。固定带因此减高 47dp+12dp 间距，首屏列表可视区从 11dp 增至约 70dp。
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ── E2 题库统计卡组（3col）──
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 三卡均：圆徽章 + 标签同行 → 大数字 + 通栏进度条 + 语义浅底（对齐高保真 B_top_tall）
                    // 2026-09-20 ⑤ 值色口径：三卡均为「纯计数」，数值一律中性深色；
                    // 语义色只落在徽章底色 / 图标 tint / 进度条（与高保真图一致，含负向的「错题待清」也不着色）。
                    StatCard(
                        icon = "book", value = "$totalQuestions", unit = "题", label = "总题量",
                        modifier = Modifier.weight(1f),
                        valueColor = AppColors.textPrimary,
                        iconTint = Color.White, iconBg = AppColors.blue,
                        iconShape = CircleShape, iconSize = 22.dp,
                        labelInline = true, containerColor = AppColors.blueBg,
                        containerHPad = 12.dp,
                        progress = 1f, progressColor = AppColors.blue
                    )
                    StatCard(
                        icon = "check", value = "$practicedCount", unit = "题", label = "已练",
                        modifier = Modifier.weight(1f),
                        valueColor = AppColors.textPrimary,
                        iconTint = Color.White, iconBg = AppColors.success,
                        iconShape = CircleShape, iconSize = 22.dp,
                        labelInline = true, containerColor = AppColors.greenBg,
                        containerHPad = 12.dp,
                        progress = (practicedCount.toFloat() / totalQuestions.coerceAtLeast(1)).coerceIn(0f, 1f),
                        progressColor = AppColors.success
                    )
                    val wrongAccent = if (wrongCount > 0) AppColors.danger else AppColors.success
                    StatCard(
                        icon = "inbox", value = "$wrongCount", unit = "题", label = "错题待清",
                        modifier = Modifier.weight(1f),
                        valueColor = AppColors.textPrimary,
                        iconTint = Color.White, iconBg = wrongAccent,
                        iconShape = CircleShape, iconSize = 22.dp,
                        labelInline = true,
                        containerColor = if (wrongCount > 0) AppColors.redBg else AppColors.greenBg,
                        containerHPad = 12.dp,
                        progress = (wrongCount.toFloat() / practicedCount.coerceAtLeast(1)).coerceIn(0f, 1f),
                        progressColor = wrongAccent
                    )
                }

                // 🔴 2026-10-02 UI 密度改造（方案二①）：删除固定带通栏搜索胶囊
                //   （Hero 右侧已有 44dp 玻璃搜索钮直达 search，通栏胶囊冗余，删省 44dp+12dp）。

                // ── E4 快捷入口组（4 列 + 1 列 = 5 宫格，固定带瘦身两行→两行但行2 2列占比更小）──
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    quickRow1.forEach { (icon, label, route) ->
                        HubChip(icon, label, { nav.navigate(route) }, Modifier.weight(1f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    quickRow2.forEach { (icon, label, route) ->
                        HubChip(icon, label, {
                            if (route == "more") showMore = true else nav.navigate(route)
                        }, Modifier.weight(1f))
                    }
                    // 第 2 列占位，保证与行1 4 列不对齐到最右
                    Spacer(Modifier.weight(2f))
                }
                }
            }

            item(key = "subjectFilter") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    subjectTabs.forEach { tab ->
                        val selected = tab == filter
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { filter = tab }
                        ) {
                            Text(
                                if (tab == "全部") "全部" else BankStore.shortName(tab),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) AppColors.blue else AppColors.textSecondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            Box(
                                Modifier
                                    .width(24.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selected) AppColors.blue else Color.Transparent)
                            )
                        }
                    }
                }
            }

            // ── E6 章节分组标题（dot 变体，下沉为 item）──
            item(key = "chapterTitle") { SectionTitleDot("章节题量", trailing = "共 ${chapterData.size} 章") }

            if (chapterData.isEmpty()) {
                // ── E10 空态 ──
                item(key = "chapterEmpty") {
                    EmptyHint(
                        icon = "book",
                        title = "题库还是空的",
                        hint = "先去章节管理导入或建立章节",
                        modifier = Modifier.fillMaxWidth(),
                        action = { Button(onClick = { nav.navigate("chapters") }) { Text("章节管理") } }
                    )
                }
            } else {
                // ── E7 章节题量卡（CMP-LISTROW / nav 变体：标题 + 指标 + 进度条 + chevron）──
                items(chapterData, key = { "${it.subject}|${it.name}" }, contentType = { "chapter" }) { ch ->
                    NavRowCard(
                        icon = "book",
                        title = ch.name,
                        subtitle = "${ch.total} 题 · 已练 ${ch.practiced} · 错 ${ch.wrong}",
                        iconTint = AppColors.blue,
                        iconBg = AppColors.blueLight,
                        progress = ch.pct / 100f,
                        progressColor = AppColors.blue,
                        onClick = {
                            practiceVm.startChapter(ch.subject, ch.name, null, 30, if (ch.subject == "科三") disc else null)
                            nav.navigate(Screen.Practice.route)
                        }
                    )
                }
            }

            // 🔴 2026-10-02 UI 密度改造（方案二③）：题库管理从固定带下沉到章节列表末尾
            //   （管理类＝低频，归入滚动区底部，不再占首屏固定带高度）。
            item(key = "bankManageBottom") { GroupTitle("题库管理", Modifier.padding(top = 8.dp)) }
            item(key = "bankManageCard") {
                NavRowCard(
                    icon = "download",
                    title = "题库管理",
                    subtitle = "下载 / 移除科目包，增删本地自加题",
                    iconTint = AppColors.blue,
                    iconBg = AppColors.blueLight,
                    onClick = { nav.navigate("bankmanage") }
                )
            }
        }
    }

    // ──「更多资料」半屏 sheet：收纳低频入口（备课组 / 校订），第 5 宫格点开 ──
    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("更多资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                NavRowCard(
                    icon = "lesson",
                    title = "备课组",
                    subtitle = "按知识点整理的备课资料",
                    iconTint = AppColors.blue,
                    iconBg = AppColors.blueLight,
                    onClick = { nav.navigate("lesson"); showMore = false }
                )
                NavRowCard(
                    icon = "proof",
                    title = "校订",
                    subtitle = "校对 / 修订题目内容",
                    iconTint = AppColors.blue,
                    iconBg = AppColors.blueLight,
                    onClick = { nav.navigate("proof"); showMore = false }
                )
                NavRowCard(
                    icon = "note",
                    title = "知识库",
                    subtitle = "所有科目资料汇总",
                    iconTint = AppColors.blue,
                    iconBg = AppColors.blueLight,
                    onClick = { nav.navigate("knowledge"); showMore = false }
                )
            }
        }
    }
}

private data class ChapterDisplay(
    val subject: String,
    val name: String,
    val total: Int,
    val practiced: Int,
    val wrong: Int,
    val acc: Int,
    val pct: Int
)
