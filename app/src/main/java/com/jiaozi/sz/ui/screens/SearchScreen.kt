package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.IconBadge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.DocHit
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import kotlinx.coroutines.delay

/**
 * 全局搜索：六块——题库 / 知识库 / 备课·教案(FTS) / 课标库(FTS) / 教材库(FTS) / 收集箱。
 * 题库/知识库/收集箱沿用内存子串匹配；课标库/教材库/教案走 Room FTS4 全文检索（B 阶段）。
 * 题库命中可点击直接去练；其余块点击可跳对应模块。
 *
 * 🔴 2026-09-21 按高保真稿重排列表结构：由「独立分节标题 + 每条结果一张 r14 卡」
 * 改为 **一分节一张分组卡**（CMP-GROUPCARD）——
 * 卡内首行＝实色圆角图标 + 域名称 + 小 chevron + 右侧「共 N」，卡内下方为结果行。
 * 分组顺序与空态策略不变（6 个分域固定顺序；为 0 也渲染，用紧凑空态行而非大留白）。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchScreen(nav: NavHostController, initial: String = "") {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val inbox by repo.allInboxFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var raw by remember { mutableStateOf(TextFieldValue(initial)) }
    var query by remember { mutableStateOf(initial) }
    // 小米传送门：AppRoot 已带 pendingSearch 跳转本屏，这里取初始词并消费清空
    val pendingSearch by appVm.pendingSearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        if (pendingSearch.isNotBlank()) {
            raw = TextFieldValue(pendingSearch)
            query = pendingSearch
            appVm.setPendingSearch("")
        }
    }
    LaunchedEffect(raw) { delay(150); query = raw.text }

    val q = query.trim()
    // 全文检索命中（课标库/教材库/教案），按来源分流
    var docHits by remember { mutableStateOf<List<DocHit>>(emptyList()) }
    LaunchedEffect(q) {
        docHits = if (q.isBlank()) emptyList() else repo.searchDocs(q)
    }
    val curricHits = docHits.filter { it.source == "curric" }
    val bookHits = docHits.filter { it.source == "books" }
    val lessonHits = docHits.filter { it.source == "lesson" }

    val examHits: List<Question> = remember(q) { if (q.isBlank()) emptyList() else repo.search(q) }
    val kwHits = remember(q) {
        if (q.isBlank()) emptyList() else repo.knowledge.filter { "${it.title} ${it.content} ${it.tags}".contains(q, ignoreCase = true) }
    }
    val inboxHits = remember(q) {
        if (q.isBlank()) emptyList() else inbox.filter { "${it.content} ${it.note}".contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // 🔴 2026-09-21 按 13 号稿：搜索框改**自绘**（原 M3 OutlinedTextField 最小高度锁定 56dp，
        //    真机实测 55dp，而稿内仅 ≈42dp）⇒ 用 BasicTextField + decorationBox 精确控制
        //    高度 42dp / 圆角 999dp / 图标 18dp / 文字 11sp，与稿同口径。
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = raw,
                onValueChange = { raw = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(appPainter("search"), contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.textSecondary)
                        Box(Modifier.weight(1f)) {
                            if (raw.text.isEmpty()) {
                                Text("搜索题目 / 知识 / 备课 / 课标 / 教材…", fontSize = 14.sp, color = AppColors.textSecondary, maxLines = 1)
                            }
                            inner()
                        }
                    }
                }
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding().padding(horizontal = 16.dp)
        ) {
            if (q.isBlank()) {
                item {
                    // 🔴 2026-09-21 按 13 号稿：引导文案＝**深色强调**（稿内明显重于『热门搜索』）。
                    //    ⚠️ 与 13 号 JSON 的 E2 规格（bodyMedium + onSurfaceVariant 灰）**冲突**，此处从稿。
                    Text(
                        "输入关键词，从题库、知识库、备课、课标、教材中一次找齐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
                // 🔴 2026-09-21 按 13 号稿：『热门搜索』＝**次级灰小字**（稿内轻于引导文案，
                //    与真机原先的『黑色粗体 titleSmall』层级相反）；此处从稿。
                item { Text("热门搜索", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary, fontSize = 12.sp) }
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("教资科目一", "综合素质", "教案设计", "课标2022", "美术史", "材料分析", "教学方法", "教育心理学").forEach { kw ->
                            // 🔴 2026-09-21 按稿：热门词改「浅灰实底 + 无边框」胶囊，且内边距收到 10dp
                            //    —— M3 FilterChip 水平内边距更大，8 个词会挤成 3 行；收窄后回到稿的 2 行。
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { raw = TextFieldValue(kw); query = kw }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(kw, fontSize = 12.sp, lineHeight = 14.sp, color = AppColors.textSecondary)
                            }
                        }
                    }
                }
            }

            // ── 题库（最高价值路径：命中即可直接开练，故固定最靠前）──
            item(key = "g-exam") {
                GroupCard("exam", AppColors.blue, "题库", examHits.size) {
                    if (examHits.isEmpty()) SearchEmptyRow("search", "暂无匹配的题目")
                    examHits.take(20).forEach { qn ->
                        ResultRow(qn.q, snippet(qn.q, q)) {
                            practiceVm.startByQuestion(qn); nav.navigate(Screen.Practice.route)
                        }
                    }
                }
            }
            // ── 知识库 ──
            item(key = "g-kw") {
                GroupCard("book", AppColors.success, "知识库", kwHits.size) {
                    if (kwHits.isEmpty()) SearchEmptyRow("book", "暂无匹配的知识卡")
                    kwHits.take(10).forEach { k ->
                        ResultRow(k.title, snippet("${k.cat} ${k.content}", q)) { appVm.requestOpenDoc("knowledge", k.id); nav.navigate("knowledge") }
                    }
                }
            }
            // ── 备课·教案（FTS）──
            item(key = "g-lesson") {
                GroupCard("lesson", AppColors.purple, "备课 · 教案", lessonHits.size) {
                    if (lessonHits.isEmpty()) SearchEmptyRow("bars", "暂无匹配的教案")
                    lessonHits.take(10).forEach { d ->
                        ResultRow(d.title.ifBlank { "(无标题教案)" }, snippet(d.body, q)) { appVm.requestOpenDoc("lesson", d.sourceId); nav.navigate("lesson") }
                    }
                }
            }
            // ── 课标库（FTS）──
            item(key = "g-curric") {
                GroupCard("bars", AppColors.warning, "课标库", curricHits.size) {
                    if (curricHits.isEmpty()) SearchEmptyRow("bars", "暂无匹配的课标")
                    curricHits.take(10).forEach { d ->
                        ResultRow(d.title.ifBlank { "(未命名课标)" }, snippet(d.body, q)) { appVm.requestOpenDoc("curric", d.sourceId); nav.navigate("curric") }
                    }
                }
            }
            // ── 教材库（FTS）──
            item(key = "g-books") {
                GroupCard("layers", AppColors.teal, "教材库", bookHits.size) {
                    if (bookHits.isEmpty()) SearchEmptyRow("book", "暂无匹配的教材")
                    bookHits.take(10).forEach { d ->
                        ResultRow(d.title.ifBlank { "(未命名教材)" }, snippet(d.body, q)) { appVm.requestOpenDoc("books", d.sourceId); nav.navigate("books") }
                    }
                }
            }
            // ── 收集箱 ──
            item(key = "g-inbox") {
                GroupCard("inbox", AppColors.danger, "收集箱", inboxHits.size) {
                    if (inboxHits.isEmpty()) SearchEmptyRow("bars", "暂无匹配的条目")
                    inboxHits.take(10).forEach { e ->
                        ResultRow(e.content, e.note.ifBlank { null }) { appVm.requestOpenDoc("inbox", e.id); nav.navigate("inbox") }
                    }
                }
            }
        }
    }
}

/**
 * 搜索分域分组卡（CMP-GROUPCARD，2026-09-21 新增，登记于 02 号组件库）。
 *
 * 版式：`Card(surfaceContainer, r16)` → 组头行（`IconBadge 30dp/r10` + 域名称 + 小 chevron
 * + 右侧「共 N」+ chevron）→ 1dp 分隔线 → 卡内结果行（由调用方给出）。
 * 组头图标用实色圆角块（与 09 号 `IconBadge`、设置页行图标同一原子，仅尺寸取 30dp）。
 */
@Composable
private fun GroupCard(
    icon: String,
    iconBg: Color,
    title: String,
    count: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconBadge(icon, iconBg, size = 30.dp, shape = RoundedCornerShape(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                Text("共 $count", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary, fontSize = 12.sp)
                Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/** 分组卡内的结果行：标题 + 命中片段（可空）+ 尾部 chevron。 */
@Composable
private fun ResultRow(title: String, snippetText: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!snippetText.isNullOrBlank()) {
                Text(
                    snippetText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = AppColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

/**
 * 搜索分节空状态（紧凑行）：复用全局空状态语言（appPainter 描边图标 + 灰字），
 * 但用紧凑内边距而非 EmptyHint 的大留白块，避免六分节同时为空时整屏被空块占满。
 */
@Composable
private fun SearchEmptyRow(icon: String, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(appPainter(icon), contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.textSecondary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 12.sp)
    }
}

/**
 * 从正文截取命中上下文预览：以首个查询词定位，左右各取 radius 字，过长截断加省略号。
 */
fun snippet(body: String, q: String, radius: Int = 36): String {
    if (body.isBlank()) return ""
    val term = q.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: return body.take(120)
    val idx = body.indexOf(term, ignoreCase = true)
    return if (idx < 0) {
        body.take(120)
    } else {
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + term.length + radius).coerceAtMost(body.length)
        (if (start > 0) "…" else "") + body.substring(start, end) + (if (end < body.length) "…" else "")
    }
}
