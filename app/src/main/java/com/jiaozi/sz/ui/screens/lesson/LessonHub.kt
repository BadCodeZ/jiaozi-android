package com.jiaozi.sz.ui.screens.lesson

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.StatCardCompact

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.data.model.ringCount
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.HubBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 备课中枢：Hero + 紧凑栏 + 快捷操作 + 教案列表（两段式布局）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LessonHub(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (LessonEntity) -> Unit,
    onTemplates: () -> Unit,
    onCurric: () -> Unit,
    onBooks: () -> Unit
) {
    val lessons by appVm.repo.allLessonsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val templates by appVm.lessonTemplates.collectAsStateWithLifecycle()
    val scope = appVm.viewModelScope

    // 顶部常驻块：Hero + 统计卡不随滚动（折叠已停用），返回键/筛选/快捷操作常驻
    val scrollState = rememberScrollState()

    // 🔴 2026-09-27（IDX5 搜索结果直达）：消费 pendingOpenDoc，按 id 在教案列表定位 + 高亮
    val pendingOpen by appVm.pendingOpenDoc.collectAsStateWithLifecycle()
    var hlLessonId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOpen, lessons) {
        val po = pendingOpen
        if (po != null && po.route == "lesson" && po.id.isNotBlank()) {
            val target = lessons.find { it.id == po.id }
            if (target != null) {
                hlLessonId = target.id
            }
            // 🔴 clearPendingOpenDoc() 改写 pendingOpen = 本 effect 的 key ⇒ 会取消本协程，
            //    必须放在状态写入之后；放 delay 之前会使高亮永久驻留（见 KnowledgeScreen 同款注释）。
            appVm.clearPendingOpenDoc()
        }
    }
    // 高亮 2.2s 后自动清除（独立 effect：不随 pendingOpenDoc 变更被取消）
    LaunchedEffect(hlLessonId) {
        if (hlLessonId != null) { delay(2200); hlLessonId = null }
    }

    // 最近编辑的教案（用于「继续编辑」快捷入口）
    val lastLesson = lessons.maxByOrNull { it._mt }

    // 全局平均完成度
    val avgRing = if (lessons.isEmpty()) 0 else {
        lessons.mapNotNull { l ->
            val (f, _) = appVm.repo.parseLessonData(l.data)
            ringCount(f).takeIf { it > 0 }
        }.average().toInt()
    }

    // 两段式布局（与知识库 / 课标库同款）：顶栏 + Hero + 紧凑栏固定在外框，
    // 只有下方「筛选 / 快捷操作 / 教案列表」滚动 ⇒ 收起后紧凑栏恒钉顶部。
    // ⚠️ 固定带/HubBar 用 hubDragToScroll 直通手势（折叠已停用，外框不挂监听）
    // 🔴 2026-09-22 按 12 号稿 1:1 复刻（杰哥四条裁定）：
    //    ① Hero 改**沉浸式**（蓝底铺到状态栏、返回件内联进 Hero 蓝区内）——稿内实测即如此；
    //    ② Hero + 统计卡 同属**顶部常驻块**（12 号规范原文：CollapsingTopBlocks{ Hero + StatCard 组 }）；
    //    ③ Hero 渐变改用稿实测亮天蓝（见 AppGradients.hero）。
    //    ⚠️ 修复上一轮「信息带下沉」的误操作：当时整块 Hero 被搬进滚动区，
    //    导致 CollapsedHubBar（「备课组 · N」）被顶到 Hero **上方**，与规范/稿的顺序相反。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {

        // ══════════ 顶部常驻：Hero（沉浸）+ 统计卡 ══════════
        // 常驻不随滚动走：沉浸 Hero 若滚出，状态栏区域会露出页面底色而白色图标不可见。
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(scrollState)) {
            // ── Hero：沉浸 · 返回件内联（左上白圆底箭头）· 右侧 lesson 图标 ──
            HeroHeader(
                title = "备课组",
                subtitle = "结构化教案编辑，AI辅助生成",
                // 🔴 2026-09-23：装饰图标改走 decorIcon —— 传 onBack 时旧 icon 参数会被左端槽吞掉
                decorIcon = appPainter("lesson"),
                immersive = true,
                statusBarInset = statusBarTop,
                onBack = onBack
            )
            // ── 4 个统计卡（全局 CMP-STATCARD · compact 变体）──
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCardCompact(lessons.size.toString(), "我的教案", AppColors.blue)
                StatCardCompact("0", "本周新增", AppColors.blue)
                StatCardCompact("0", "AI生成", AppColors.blue)
                StatCardCompact("0%", "使用率", AppColors.blue)
            }
        }

        // 筛选状态提升到 HubBar 之前：chips 逻辑现只喂**滚动区首行的筛选栏**。
        //（2026-09-25 晚：原「展开态筛选行 / 收起态紧凑栏」两态互斥写法已随折叠状态机退场。）
        var lessonFilter by remember { mutableStateOf("全部") }
        val lessonChips: @Composable RowScope.() -> Unit = {
            listOf("全部", "小学", "语文", "人教版").forEach { s ->
                FilterChip(selected = s == lessonFilter, onClick = { lessonFilter = s }, label = { Text(s, fontSize = 12.sp) })
            }
        }

        // ── 常驻栏（HubBar）：标题·条数 + 新建 ──
        // 🔴 2026-09-25 晚：原 `CollapsedHubBar(persistent = true)` 已随折叠状态机退场改名为 `HubBar`；
        // 本栏恒常驻（与高保真稿一致：统计卡可见时同样有本栏，语义 =「分区标题 + 主操作」）。
        // 筛选栏在滚动区首行，本栏不重复补 chips（见组件 KDoc）。
        HubBar(
            title = "备课组 · ${lessons.size}",
            actions = {
                IconButton(onClick = onNew, modifier = Modifier.size(36.dp)) {
                    Icon(appPainter("plus"), contentDescription = "新建教案", modifier = Modifier.size(20.dp), tint = AppColors.blue)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp).hubDragToScroll(scrollState)
        )

        // ── 滚动区：只有下方内容滚动，顶栏 / Hero / 紧凑栏保持可见 ──
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // （原 Hero 渐变卡 + 4 统计卡已上移至顶部常驻块 · 2026-09-22）

        // ── 筛选栏（真实筛选：学段 / 学科）──
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(appPainter("filter"), contentDescription = null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
            lessonChips()
        }

        // 预解析教案字段并按筛选条件过滤（"全部" = 不过滤；小学看学段，语数英看学科）
        val rows = lessons.sortedByDescending { it._mt }
            .map { it to appVm.repo.parseLessonData(it.data).first }
            .filter { (l, f) ->
                when (lessonFilter) {
                    "全部" -> true
                    "小学" -> f.grade == "小学"
                    "人教版" -> f.textbook.contains("人教版")
                    else -> f.disc == lessonFilter || l.subject == lessonFilter
                }
            }

        // ── 快捷操作：模板库 + 新建教案（文案按高保真稿 2026-09-21）──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.weight(1f).clickable { onTemplates() }, colors = CardDefaults.cardColors(containerColor = AppColors.greenBg), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(appPainter("bars"), contentDescription = null, tint = AppColors.success, modifier = Modifier.size(24.dp))
                    Text("模板库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.success)
                    Text("海量优质模板，快速套用", style = MaterialTheme.typography.bodySmall, color = AppColors.success, fontSize = 11.sp)
                }
            }
            Card(Modifier.weight(1f).clickable { onNew() }, colors = CardDefaults.cardColors(containerColor = AppColors.purpleBg), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(appPainter("plus"), contentDescription = null, tint = AppColors.purple, modifier = Modifier.size(24.dp))
                    Text("新建教案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.purple)
                    Text("从零开始，AI辅助生成", style = MaterialTheme.typography.bodySmall, color = AppColors.purple, fontSize = 11.sp)
                }
            }
        }

        // ── 教案列表 ──
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("教案列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("共 ${rows.size}", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 11.sp)
        }

        if (rows.isEmpty()) {
            EmptyHint(
                "school",
                if (lessons.isEmpty()) "还没有教案" else "没有符合筛选的教案",
                if (lessons.isEmpty()) "点「新建教案」用十二要素结构化模板记录一节备考课的设计。" else "换个筛选条件试试。"
            )
        }
        rows.forEach { (l, fields) ->
            val meta = "${fields.grade} · ${l.subject.ifBlank { fields.disc }.ifBlank { "未结构化" }} · ${fields.type}"
            val ring = ringCount(fields)
            val hl = hlLessonId == l.id
            val bri = remember(l.id) { BringIntoViewRequester() }
            if (hl) LaunchedEffect(Unit) { bri.bringIntoView() }
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(l) }
                    .bringIntoViewRequester(bri),
                colors = CardDefaults.cardColors(containerColor = if (hl) AppColors.blueBg else MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.Top) {
                    // 封面图占位
                    Box(Modifier.size(56.dp).background(AppColors.blueLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(appPainter("lesson"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
                        Text(l.title.ifBlank { "(未命名)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 11.sp)
                        // 完成度：胶囊「9/12  75%」+ 加宽进度条（2026-09-21 按高保真稿；
                        // 原为「完成度 9/12」纯文字 + 60dp 条 —— 稿内无「完成度」字样且条更宽）
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(AppColors.blueBg).padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("$ring/12", style = MaterialTheme.typography.labelSmall, color = AppColors.blue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("${ring * 100 / 12}%", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary, fontSize = 11.sp)
                            }
                            LinearProgressIndicator(
                                progress = { ring / 12f },
                                modifier = Modifier.width(96.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = AppColors.blue,
                                trackColor = AppColors.trackGray
                            )
                        }
                    }
                    IconButton(onClick = { scope.launch { appVm.repo.deleteLesson(l.id) } }, modifier = Modifier.size(32.dp)) {
                        Icon(appPainter("trash"), contentDescription = "删除", tint = AppColors.danger, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        }
    }
}

// ────────────────────────────────────────────────────────────
// 2026-09-18 P3 死代码清理：本文件原先尾部悬挂 4 个声明，经全工程引用扫描后处置如下 ——
//   · private fun StatCard(label, value, color)   → 收敛：改由全局 StatCardCompact 承担（调用点已换），删除
//   · internal data class Feat(title, desc, icon, soon) → 零引用，删除
//   · private fun StatCell(label, value)          → 零引用，删除
//   · private fun FeatCard(f: Feat, onClick)      → 零引用，删除
// 判定依据见本文件所属规范 14_导航与浮层总览.json · pending_normalization_rollup。
// ────────────────────────────────────────────────────────────
