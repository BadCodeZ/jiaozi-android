package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.IconBadge
import com.jiaozi.sz.ui.components.MasteryBadge
import com.jiaozi.sz.ui.components.MasteryState
import com.jiaozi.sz.ui.components.knowledgeTypeColor
import com.jiaozi.sz.ui.components.knowledgeTypeIcon
import com.jiaozi.sz.ui.components.masteryOf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors

/**
 * 知识库（对齐网页端 `VIEW.knowledge`）：分类 chips + 搜索 + 卡片列表。
 * 知识卡为静态种子（knowledge.json），支持按分类筛选与全文搜索；
 * 收藏/到期复习标记通过 meta 持久化（轻量，不影响题库进度）。
 * 重构（V2.60）：渐变 Hero 头部 + 统计格 + surfaceVariant 浏览卡。
 * V2.78：Hero + 统计 + 搜索 + 分类 chips 归入顶部常驻块（⚠️ 原「滚动折叠」行为已于 2026-09-21
 *       停用、2026-09-25 清理死码；顶块现恒常驻，下方列表独立滚动）。
 */
@Composable
fun KnowledgeScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val all = repo.knowledge

    var query by remember { mutableStateOf(TextFieldValue("")) }
    val cats = remember(all) { listOf("全部") + all.map { it.cat }.distinct() }
    var cat by remember { mutableStateOf("全部") }

    val favState by appVm.knowledgeFav.collectAsStateWithLifecycle()

    val filtered = remember(all, query.text, cat) {
        all.filter { k ->
            (cat == "全部" || k.cat == cat) &&
                (query.text.isBlank() || "${k.title} ${k.content} ${k.tags}".contains(query.text, ignoreCase = true))
        }
    }

    val favCount = favState.size
    val catCount = (cats.size - 1).coerceAtLeast(0)
    // 进度条归一化基数：与 InboxScreen / ProofScreen 同口径（全站唯一写法，保证跨页一致）
    val progressBase = maxOf(all.size, catCount, favCount).coerceAtLeast(1)

    // ── 掌握度聚合（09 号 E5 角标）────────────────────────────────────────────
    // 🔴 数据通路：knowledge.json 的 42 条卡 `link` **全为空串** ⇒ 无法按 qid 直连题库，
    //    改按 `knowledge.cat ↔ bank.exam.chapter` 聚合。二者 17 个分类名**完全一一对应**
    //    （已核验），故以 cat 为键聚合该章节下全部题目的 right/wrong。
    // ⚠️ 派生口径与 GraphScreen L84-97 的 chapterAcc **同源**（按章聚合 r/w），
    //    阈值复用 `masteryOf`（0.8 / 0.5，与图谱热力色一致），不得另立一套。
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val catAcc = remember(all, progress) {
        val byCat = HashMap<String, Pair<Int, Int>>(all.size)
        all.map { it.cat }.distinct().forEach { c ->
            val qs = repo.bank.exam.filter { it.chapter == c }
            var r = 0; var w = 0
            qs.forEach { q -> progress[q.id]?.let { e -> r += e.right; w += e.wrong } }
            byCat[c] = r to w
        }
        byCat
    }

    val listState = rememberLazyListState()
    // Hero 右上搜索键 → 聚焦下方搜索框
    val searchFocus = remember { FocusRequester() }

    // 🔴 2026-09-27（IDX5 搜索结果直达）：消费 pendingOpenDoc，按 id 在列表定位 + 高亮
    val pendingOpen by appVm.pendingOpenDoc.collectAsStateWithLifecycle()
    var hlKnowledgeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOpen, filtered) {
        val po = pendingOpen
        if (po != null && po.route == "knowledge" && po.id.isNotBlank()) {
            val idx = filtered.indexOfFirst { it.id == po.id }
            if (idx >= 0) {
                listState.scrollToItem(idx + 1) // +1：hubBand 头项
                hlKnowledgeId = po.id
            }
            // 🔴 请求必须「消费即清空」，但 clearPendingOpenDoc() 会改写 pendingOpen（本 effect 的 key）
            //    从而取消本协程 ⇒ 只能放在所有状态写入之后。此前把它放在 delay 之前，导致
            //    delay / hlKnowledgeId=null 永不执行、高亮永久驻留（2026-09-27 真机像素实证 #13283F）。
            appVm.clearPendingOpenDoc()
        }
    }
    // 高亮 2.2s 后自动清除（独立 effect：键为 hlKnowledgeId，不随 pendingOpenDoc 变更被取消）
    LaunchedEffect(hlKnowledgeId) {
        if (hlKnowledgeId != null) { delay(2200); hlKnowledgeId = null }
    }

    // 分类 chips：固定带内一行横向滚动
    val catChips: @Composable RowScope.() -> Unit = {
        cats.forEach { c ->
            FilterChip(
                selected = cat == c,
                onClick = { cat = c },
                label = { Text(c) }
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ① 顶部重区块：常驻不滚，手势直通下方列表
        CollapsingTopBlocks(modifier = Modifier.hubDragToScroll(listState)) {
            // Hero：CMP-HERO / with_action —— title + 数据摘要副标题 + 右上 40dp 搜索键
            // 🔴 2026-09-20 依高保真图 2 左像素/OCR 实证订正两处：
            //    ① 副标题原为文案「先学知识，再练题目——备考的输入侧」，图上实测为**数据摘要**
            //       「286 个知识点 · 已掌握 128」（OCR 置信 0.82）⇒ 改为与图谱页同构的数据副标题；
            //    ② 原为手绘渐变 Card 且右上只有装饰性 book 图标，图上右上为**搜索键**（8x 放大呈
            //       放大镜轮廓，bbox 18×17px ≈ 40dp 触控区）⇒ 改用全局 HeroHeader + action 槽。
            HeroHeader(
                title = "知识库",
                subtitle = "${all.size} 个知识点 · 已收藏 $favCount",
                icon = appPainter("book"),
                // 🔴 2026-09-22 二级 Hero 统一沉浸通栏
                immersive = true,
                onBack = { nav.navigateUp() },
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 🔴 2026-09-25 补 A8（#406 报告 A 类）：09 号 knowledge.main F6「跳知识图谱」规定
                        //    「从知识库切换到图谱视图（two_views_one_source 的知识库侧入口）」，
                        //    高保真图未覆盖落位 ⇒ 按规范推断的「hero 右上并列小键」实现。
                        //    此前本页无 navigate("graph")，唯一入口在 StatsScreen:234/440，属单向可达。
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { nav.navigate("graph") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("graph"), contentDescription = "知识图谱", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { searchFocus.requestFocus() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("search"), contentDescription = "搜索知识卡", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        // 🔴 2026-09-25 晚（E/C2）：本行是滚动区「知识卡」区块的区块头，
        //    与上方 Hero 固定带之间须 20dp ⇒ 外层 Column spacedBy 12dp + 本处补 4dp
        //    + SectionTitle 自带 top 4dp = 20dp（01 号 tokens.section_gap）。
        SectionTitle("知识卡", Modifier.padding(top = 4.dp))
        LazyColumn(
            // 🔴 2026-09-25 晚（E/C2）：卡间距 8dp → **12dp**。
            //    09 号 E4 position「滚动容器内，卡间距 12dp」+ high_fidelity「卡片间距 12dp，区块间距 20dp」
            //    ⇒ 本列 spacedBy 承担「卡片间距 12dp」；区块间距 20dp 由下方 hubBand 底部 +8dp 补齐（见该处注释）。
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()
        ) {
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                // 🔴 2026-09-25 晚（E/C2）：区块间距 sp.20 —— 信息带（统计三卡 + 搜索框 + 分类 chips）
                //    与下方「知识卡列表」是两个区块，间距须 20dp；本列 spacedBy 恒 12dp 承担卡间距，
                //    故在此补 8dp 底部前导 ⇒ 12+8=20dp（01 号 tokens.section_gap「区块之间恒 20dp」）。
                Column(
                    Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ── 统计三卡：CMP-STATCARD · default 变体 ──
                    // 🔴 2026-09-20 依高保真图 2 左像素实证：28dp **圆角方**徽章（RoundedCornerShape(9.dp)，
                    //    非圆形；掩码轮廓实测为圆角矩形）+ 徽章底色＝语义主色、图标纯白（实测图标笔画 #F0FDFF）
                    //    + 标签与徽章同行（labelInline）+ 值 26sp + 单位「个」13sp + 6dp 进度条。
                    //    ✅ ⑤ 值色口径已于 2026-09-20 裁定并全局落地：三卡（知识卡/分类/已收藏）均为**纯计数**
                    //    ⇒ 数值一律中性深色 StatCard 默认值 textPrimary（本页本即合规，未改）；
                    //    语义色只落在徽章底色 / 图标 tint / 进度条。裁定全文见 02 号 CMP-STATCARD.color_variant。
                    //    ⚠️ 图上三卡底色实测几乎同色（#F5FAFE / #F6FAFD / #F5F5FD，色相差 <6 阶），
                    //    与「各自语义浅底」不符；此处按 09 号 E2 明文 + 与 Inbox/Proof 一致用各自语义浅底。
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(
                            icon = "book", value = "${all.size}", unit = "个", label = "知识卡",
                            modifier = Modifier.weight(1f),
                            iconTint = Color.White, iconBg = AppColors.blue,
                            iconShape = RoundedCornerShape(9.dp), iconSize = 28.dp,
                            labelInline = true, containerColor = AppColors.blueBg,
                            progress = all.size.toFloat() / progressBase, progressColor = AppColors.blue
                        )
                        StatCard(
                            icon = "grid", value = "$catCount", unit = "个", label = "分类",
                            modifier = Modifier.weight(1f),
                            iconTint = Color.White, iconBg = AppColors.success,
                            iconShape = RoundedCornerShape(9.dp), iconSize = 28.dp,
                            labelInline = true, containerColor = AppColors.greenBg,
                            progress = catCount.toFloat() / progressBase, progressColor = AppColors.success
                        )
                        StatCard(
                            icon = "star", value = "$favCount", unit = "个", label = "已收藏",
                            modifier = Modifier.weight(1f),
                            iconTint = Color.White, iconBg = AppColors.purple,
                            iconShape = RoundedCornerShape(9.dp), iconSize = 28.dp,
                            labelInline = true, containerColor = AppColors.purpleBg,
                            progress = favCount.toFloat() / progressBase, progressColor = AppColors.purple
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        singleLine = true,
                        leadingIcon = { Icon(appPainter("search"), contentDescription = null) },
                        placeholder = { Text("搜索知识卡…") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    // 分类 chips（横向滚动，避免嵌套滚动）
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = catChips
                    )
                }
            }

            if (all.isEmpty()) {
                item { EmptyHint("book", "知识库为空", "导入或收藏知识卡，备考随手查。") }
            } else if (filtered.isEmpty()) {
                item { EmptyHint("search", "没有匹配的知识卡", "换个关键词或分类试试。") }
            } else {
                items(filtered, contentType = { "knowledge" }) { k ->
                    val acc = catAcc[k.cat]
                    KnowledgeBrowseCard(
                        k = k,
                        fav = favState.contains(k.id),
                        mastery = masteryOf(acc?.first ?: 0, acc?.second ?: 0),
                        onFav = { appVm.toggleKnowledgeFav(k.id) },
                        highlighted = hlKnowledgeId == k.id
                    )
                }
            }
        }
    }
}

/**
 * 知识卡（09 号 E4 `knowledge-browse-card` / 02 号 `CMP-ICONBADGE` + `CMP-MASTERYBADGE`）。
 *
 * 🔴 2026-09-20 依高保真图 2 左 + 09 号明文改造，原形态（分类文字行 + 摘要 3 行 + 无徽章/角标）已弃用：
 * ```
 * ┌────────────────────────────────────┐
 * │ [▣] 标题                      [☆]  │  28dp 前导徽章 + sp.10 + 标题 16sp SemiBold weight(1f) + 36dp 收藏键
 * │     摘要 14sp maxLines=2（缩进对齐标题）
 * │     [已掌握]                        │  掌握度角标 11sp r.full 语义浅底，靠左
 * └────────────────────────────────────┘
 * ```
 * 规格：surfaceContainer + r.medium 20dp + padding 16dp；块间距 sp.8 / sp.10。
 *
 * 🔴 图面实证三则（2026-09-20）：
 *  ① **前导位原是分类名文字，图上无此文字行** —— 图上卡内仅「徽章 + 标题 + 摘要 2 行 + 角标」四要素，
 *     分类信息由前导徽章的**类型色**承载 ⇒ 删除原 `Text(k.cat)` 行（与线框图一致）。
 *  ② **tags 行图上不存在** —— 高保真 3 张卡（素质教育/问题解决/认知负荷）均无 tag chips；
 *     09 号 E4 `layout` 亦未列该行 ⇒ 本页不再渲染 tags（数据字段保留，供搜索命中用）。
 *  ③ 摘要缩进对齐**标题**（即让过 28dp 徽章 + sp.10 间距 = 38dp），而非整宽。
 */
@Composable
private fun KnowledgeBrowseCard(
    k: Knowledge,
    fav: Boolean,
    mastery: MasteryState,
    onFav: () -> Unit,
    highlighted: Boolean = false
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) AppColors.blueBg else MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 前导图标徽章：knowledge_type → brain 蓝 / bulb 紫 / target 红 / menu 青
                IconBadge(icon = knowledgeTypeIcon(k.knowledgeType), bg = knowledgeTypeColor(k.knowledgeType))
                Spacer(Modifier.width(10.dp))   // sp.10
                Text(
                    k.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 收藏键：36dp 触控区（09 号 E4）；M3 IconButton 会强制补足 48dp 交互区，
                // 此处沿用全站既定手法 —— Box + clickable 严格按 size 渲染。
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onFav),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        appPainter("star"),
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) AppColors.warning else AppColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))       // sp.8
            // 摘要缩进对齐标题：38dp = 徽章 28dp + sp.10
            Text(
                k.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 14.sp * 1.45f,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 38.dp)
            )
            Spacer(Modifier.height(10.dp))      // sp.10
            MasteryBadge(mastery)
        }
    }
}

// 2026-09-18 P3 死代码清理：原先尾部的 private fun KnowledgeStatCard(label, value, color)
// 与备课组 / 模板库两份同名实现逐字重复 ⇒ 收敛为全局 StatCardCompact（调用点已换），此处删除。
