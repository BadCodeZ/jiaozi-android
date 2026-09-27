package com.jiaozi.sz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import com.jiaozi.sz.data.local.CurricEntity
import com.jiaozi.sz.data.model.LessonDims
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.DocRow
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.DocReader
import com.jiaozi.sz.ui.components.formatDocSize
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatBand
import com.jiaozi.sz.ui.components.StatBandItem
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.screens.lesson.SegmentedRow
import com.jiaozi.sz.util.toIsoDate
import kotlinx.coroutines.launch

/**
 * 课标库（对齐高保真图 1-2 / 1-3）
 *
 * - 顶部浅色统计带（StatBand · 4col）：课标文件 / 学段 / 学科分类 / 覆盖率
 * - 「导入课标」引导卡（支持 PDF、Word、TXT、MD）
 * - 学段筛选 chip + 课标文档列表（名称 · 学段·学科 · 日期 · 体积）
 * - 点击条目进入**分页全文阅读**（顶栏 1/N + 进度条 + 上一页/下一页 + 章节导航）
 *
 * 兼容性：数据读写仍走 `repo.allCurricFlow / upsertCurric / deleteCurric`，
 * 字段与 [CurricEntity] 完全未动 ⇒ 老版本升级上来的数据零迁移、零丢失。
 */
@Composable
fun CurricScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = appVm.viewModelScope
    val items by appVm.repo.allCurricFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var detail by remember { mutableStateOf<CurricEntity?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("全部") }
    // 图 1-2：Hero 右上「漏斗」→ 展开真实搜索框（按名称 / 学科 / 学段过滤课标文档）
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // 全文阅读：非空时整页切到阅读器
    var reading by remember { mutableStateOf<CurricEntity?>(null) }
    /** 待删除课标（行内删除二次确认，08 号 F4） */
    var pendingDelete by remember { mutableStateOf<CurricEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val txt = appVm.repo.readFileText(ctx, uri)
        if (txt != null) importText = txt
        else android.widget.Toast.makeText(ctx, "无法读取文件内容：PDF 需 Android 14+，或格式不支持；请改用 txt/md", android.widget.Toast.LENGTH_LONG).show()
    }

    // ── 全文阅读视图（图 1-3）──
    if (reading != null) {
        DocReader(
            title = reading!!.topic.ifBlank { "(未命名主题)" },
            subtitle = "${reading!!.grade} · ${reading!!.subject.ifBlank { "未分类" }}",
            text = reading!!.text,
            onClose = { reading = null }
        )
        return
    }

    // 学段筛选 ∩ 关键词搜索（名称 / 学科 / 学段三字段命中，忽略大小写）
    val shown = remember(items, filter, query) {
        val q = query.trim()
        items
            .filter { filter == "全部" || it.grade == filter }
            .filter {
                q.isEmpty() ||
                    it.topic.contains(q, ignoreCase = true) ||
                    it.subject.contains(q, ignoreCase = true) ||
                    it.grade.contains(q, ignoreCase = true)
            }
            .sortedByDescending { it._mt }
    }
    // 🔴 2026-09-20 订正：原为 List<String>（旧 CurricStatCard 私有壳的消费口径），归一为 StatBand
    //    后四列均为「数值 + 标签」，须改为计数（与教材页 grades/bookNames 同构）——
    //    否则 `"$grades"` 会渲染成 Kotlin 列表 toString 的 `[]`（真机实测『学段=[]』『学科分类=[]』）。
    val grades = remember(items) { items.map { it.grade.trim() }.filter { it.isNotBlank() }.distinct().size }
    val subjects = remember(items) { items.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct().size }
    val covered = remember(items) { items.count { it.text.isNotBlank() } }
    val coverage = if (items.isEmpty()) 0 else (covered * 100 / items.size)

    val listState = rememberLazyListState()

    // 🔴 2026-09-27（IDX5 搜索结果直达）：消费 pendingOpenDoc，按 id 列表定位 + 高亮
    val pendingOpen by appVm.pendingOpenDoc.collectAsStateWithLifecycle()
    var hlCurricId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOpen, shown) {
        val po = pendingOpen
        if (po != null && po.route == "curric" && po.id.isNotBlank()) {
            val idx = shown.indexOfFirst { it.id == po.id }
            if (idx >= 0) {
                listState.scrollToItem(idx + 1) // +1：hubBand 头项
                hlCurricId = po.id
            }
            // 🔴 clearPendingOpenDoc() 改写 pendingOpen = 本 effect 的 key ⇒ 会取消本协程，
            //    必须放在状态写入之后；放 delay 之前会使高亮永久驻留（见 KnowledgeScreen 同款注释）。
            appVm.clearPendingOpenDoc()
        }
    }
    // 高亮 2.2s 后自动清除（独立 effect：不随 pendingOpenDoc 变更被取消）
    LaunchedEffect(hlCurricId) {
        if (hlCurricId != null) { delay(2200); hlCurricId = null }
    }

    // 学段筛选 chips：固定带内一行横向滚动（2026-09-25 晚：原「两态互斥渲染」已随折叠退场取消）
    val gradeChips: @Composable RowScope.() -> Unit = {
        (listOf("全部") + LessonDims.GRADE).forEach { g ->
            FilterChip(
                selected = filter == g,
                onClick = { filter = g },
                label = { Text(g, fontSize = 12.sp) }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ① 顶部重区块（Hero + 统计 + 导入引导 + 筛选 chips）：常驻不滚，手势直通下方列表
        CollapsingTopBlocks(spacing = 14.dp, modifier = Modifier.hubDragToScroll(listState)) {
        HeroHeader(
            title = "课标库",
            subtitle = "导入课标原文，结构化索引，随备课信封同步",
            icon = appPainter("text"),
            // 🔴 2026-09-22 二级 Hero 统一沉浸通栏（同教材页）
            immersive = true,
            onBack = { nav.navigateUp() },
            action = {
                // 图 1-2：右上为「漏斗 / 搜索」入口（导入入口保留在下方蓝色引导卡，功能不丢）
                //
                // 🔴 2026-09-20 走查修正：原为 `IconButton` + `Modifier.size(40.dp)`，
                //    但 M3 `IconButton` 内部在调用方 modifier 之后追加 `minimumInteractiveComponentSize()`
                //    ⇒ 真机实测圆底被顶到 **48dp**（教材页同款写法同样 192px），
                //    与 02 号 `size.action_key =「40dp 圆底 + 20dp 图标」` 及 08 号 curric.main
                //    `high_fidelity.keywords`「右上 **40dp** 漏斗图标键」均不符，且与知识库页 `Box` 写法（40dp）不同构。
                //    正解＝两层结构：外层 48dp 只承担触控区（满足 01 号 `sz.touch_target_min = 44dp`），
                //    内层 40dp 承担视觉圆底 ⇒ 视觉尺寸精确回 40dp，触控区不回退。
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            appPainter(if (searchOpen) "close" else "filter"),
                            contentDescription = if (searchOpen) "收起搜索" else "搜索课标",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        SectionTitle("课标文档  全部 ${shown.size}${if (query.isNotBlank()) " · 命中「${query.trim()}」" else ""}")
        if (shown.isEmpty()) {
            when {
                items.isEmpty() -> EmptyHint("tree", "还没有课标", "点「导入课标」加入一份课标原文，可随备课信封跨端同步。")
                query.isNotBlank() -> EmptyHint("search", "没有匹配的课标", "换个关键词试试，或清空右上搜索框查看全部。")
                else -> EmptyHint("tree", "该学段下没有课标", "把筛选切回「全部」，或导入该学段的课标原文。")
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ── 真实搜索框（漏斗展开）：按 名称 / 学科 / 学段 三字段过滤 ──
                    if (searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("搜索课标名称 / 学科 / 学段", fontSize = 13.sp) },
                            leadingIcon = { Icon(appPainter("search"), contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(appPainter("close"), contentDescription = "清空搜索", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // ── 统计带：CMP-STATCARD · band 变体（2026-09-20 归一，与教材页同规格）──
                    // 整条 primaryContainer 浅蓝底 + 值 primary 深蓝 20sp Bold + 标签 11sp；4 列等分、无竖分隔线
                    // 🔴 2026-09-20 覆盖率阈值着色落地：前 3 列是纯计数 ⇒ 保持 primary；
                    //    第 4 列「覆盖率」是页面核心**评价性指标**（08 号 F1 + high_fidelity 明文要求），
                    //    按 01 号 `color.thresholds.coverage`（≥90 success / 70~90 warning / <70 danger）
                    //    经 StatBandItem.valueColor 覆写。此前因 01 号未登记该阈值而搁置，本次已补登记。
                    StatBand(
                        listOf(
                            StatBandItem("${items.size}", "课标文件"),
                            StatBandItem("$grades", "学段"),
                            StatBandItem("$subjects", "学科分类"),
                            StatBandItem(
                                "$coverage", "覆盖率", "%",
                                valueColor = when {
                                    coverage >= 90 -> AppColors.success
                                    coverage >= 70 -> AppColors.warning
                                    else -> AppColors.danger
                                }
                            )
                        )
                    )

                    // ── 导入课标引导卡 ──
                    Card(
                        onClick = { picker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.blueBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(appPainter("upload"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("导入课标", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AppColors.textPrimary)
                                Spacer(Modifier.height(3.dp))
                                Text("支持 PDF、Word、TXT 等格式", fontSize = 12.sp, color = AppColors.textSecondary)
                            }
                            Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }

                    // ── 学段筛选 ──
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = gradeChips
                    )
                }
            }

            items(shown, key = { it.id }) { e ->
                DocRow(
                    name = e.topic.ifBlank { "(未命名主题)" },
                    meta = "${e.grade.ifBlank { "未分段" }} · ${e.subject.ifBlank { "未分类" }} · ${e._mt.toIsoDate()} · ${formatDocSize(e.text)}",
                    onClick = { reading = e },
                    // 🔴 2026-09-23（08 号 F4）：删除改为二次确认，直删不可逆
                    onDelete = { pendingDelete = e },
                    highlighted = hlCurricId == e.id,
                    leading = {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.blueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("text"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }
        }
    }

    // 🔴 行内删除二次确认（08 号 F4）
    if (pendingDelete != null) {
        val d = pendingDelete!!
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除课标") },
            text = { Text("确定删除「${d.topic.ifBlank { "未命名主题" }}」？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { appVm.repo.deleteCurric(d.id) }
                    pendingDelete = null
                }) { Text("删除", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    // 详情（简要信息，保留原入口，避免删除行为变化）
    if (detail != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(detail!!.topic.ifBlank { "(未命名主题)" }) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${detail!!.grade} · ${detail!!.subject.ifBlank { "未分类" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(detail!!.text.ifBlank { "(空)" }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }
        )
    }

    if (importText != null) {
        var grade by remember(importText) { mutableStateOf(LessonDims.GRADE.first()) }
        var subject by remember(importText) { mutableStateOf("") }
        var topic by remember(importText) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("录入课标信息") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已读取原文 ${importText!!.length} 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    SegmentedRow("学段", LessonDims.GRADE, grade) { grade = it }
                    OutlinedTextField(subject, { subject = it }, label = { Text("学科") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(topic, { topic = it }, label = { Text("主题 / 条目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (topic.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            appVm.repo.upsertCurric(
                                CurricEntity(id = "C$now", grade = grade, subject = subject.trim(), topic = topic.trim(), text = importText ?: "", _mt = now)
                            )
                        }
                        importText = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { importText = null }) { Text("取消") } }
        )
    }
}

// 🔴 2026-09-20 二次归一：CurricStatCard（渐变容器 + HeroStatCell 白字）**已废止**，
//    改为与教材页同一 StatBand（CMP-STATCARD · band 4col），调用点已内联，私有壳已删除。
//    依据：08 号 curric.main E2 spec『原 CurricStatCard 私有渐变卡废止，改为与教材页同一
//    StatBand 浅色带形态，消除页面私有副本』+ 02 号 CMP-STATCARD.forbidden 第 4 条。
//    ✅ 2026-09-20 覆盖率阈值着色**已闭环**（原此处标注『本轮未落地』，现订正）：
//       ① 01 号 `color.thresholds.coverage` 已登记（≥90 success / 70~90 warning / <70 danger）；
//       ② 02 号 `CMP-STATCARD.variants.band` 已加『逐列着色例外条款』（默认 primary，
//          仅『已登记阈值的评价性指标』列可覆写）+ forbidden 新增对应禁令；
//       ③ `StatBandItem` 新增可选 `valueColor`，CurricScreen 覆盖率列按阈值传入。
//       ⇒ 规范先登记、再动业务页，无原子越界。

// 🔴 2026-09-23 第 4 批①：原私有 `CurricDocRow` 已收敛进通用 `DocRow`（ui/components/DocRow.kt），
//    本屏调用点改为 `DocRow(leading = { 图标块 })`，删除私有副本以消除与教材页 BookRow 的 90% 同构重复。
