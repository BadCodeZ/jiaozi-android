package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.ShimmerBox
import com.jiaozi.sz.ui.components.StatCard
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.rememberPressFeedback
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BackupManager
import com.jiaozi.sz.domain.StatsCalculator
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.util.formatTs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ============================================================
// 页面私有行模型与工具（07 号 E7）
// ============================================================

/**
 * 「我的」页分组行（07 号 E7 · MineToolItem）。
 *
 * Anatomy 严格三槽：Leading（[icon] + [accent] 语义浅底徽章）/ Content（[title] + [desc]）
 * / Extra（[chevron]）——**尾部至多一个元素**。
 */
private data class MineEntry(
    val icon: String,
    val title: String,
    val desc: String? = null,
    val route: String,
    /** 行徽章语义色（浅底 = accent@14%，图标 = accent 实色） */
    val accent: Color,
    /** 尾部 chevron（『关于』行按高保真图不带箭头，版本号走副文案槽） */
    val chevron: Boolean = true,
    /** 图标右上角状态点（07 号 E3：未配置 AI 时提示） */
    val dot: Boolean = false
)

/** 正确率 → 语义色。阈值与 06 号统计页口径一致（≥70% success / ≥60% warning / 其余 danger）。 */
private fun mineAccColor(acc: Float): Color = when {
    acc >= 0.70f -> AppColors.success
    acc >= 0.60f -> AppColors.warning
    else -> AppColors.danger
}

/**
 * 一级 Tab · 我的（07 号 `mine.main`，两段式布局）。
 *
 * 结构（🔴 不许回退，同 05 号 critical_history）：
 * 非滚动外框 Column → `CollapsingTopBlocks[Hero『我的』]`（3 列概览已下沉进滚动区首项）
 * → **唯一滚动容器** `LazyColumn(Modifier.fillMaxWidth().weight(1f))`。
 *
 * ⭐ 固定带挂 `hubDragToScroll(listState)` 做**手势直通**（固定带自身是非滚动
 * Column，无滚动节点 ⇒ 不挂则手指落上去既不滚列表也无任何反应）。
 * 🔴 折叠机制 2026-09-21 已整体停用、2026-09-25 死码清理 ⇒ 外框**不再挂**任何折叠监听。
 *
 * 缺 `weight(1f)` 会导致「整页滑不动 + 悬浮导航栏被空底衬成一块白色遮罩」。
 *
 * 合规说明（01 / 07 号全工程禁令）：
 *  - **无账号体系** ⇒ 不渲染头像 / 昵称 / VIP 徽章 / 社交 / 排行榜。
 *  - **禁 streak** ⇒ 概览第三格为**累计制**「备考天数」（今日 − 最早有练习记录的日期 + 1），
 *    数据源 `Repository.earliestDailyStatDate()`，不依赖 `checkinStreak`。
 *  - 本页**不放 Pro 付费入口**（只在设置页出现）。
 *  - 分组标题用 [GroupTitle]（plain，无圆点前缀）。
 */
@Composable
fun MineScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)

    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val metaLoaded by appVm.metaLoaded.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val aiKey by appVm.aiKey.collectAsStateWithLifecycle()

    val questions = appVm.repo.bank.exam
    val practiced = remember(progress) { StatsCalculator.totalPracticed(progress) }
    val overall = remember(progress) { StatsCalculator.overallAccuracy(progress) }

    // ── 备考天数（累计制，替代被全工程禁用的 streak）──
    var prepDays by remember { mutableStateOf(0) }
    LaunchedEffect(metaLoaded) {
        if (!metaLoaded) return@LaunchedEffect
        val earliest = appVm.repo.earliestDailyStatDate()
        prepDays = if (earliest.isNullOrBlank()) 0 else runCatching {
            (ChronoUnit.DAYS.between(LocalDate.parse(earliest), LocalDate.now()).toInt() + 1).coerceAtLeast(1)
        }.getOrDefault(0)
    }

    // ── 上次本地备份时间（07 号 E5 副文案）──
    var lastBackup by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(metaLoaded) {
        if (!metaLoaded) return@LaunchedEffect
        lastBackup = withContext(Dispatchers.IO) {
            BackupManager.listSnapshots(ctx).firstOrNull()?.timeMillis
        }
    }

    // ── 版本号（07 号 E6：格式 `v{versionName}`）──
    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "2.77"
    }

    // ── 距考试天数（用于「备考天数」进度条分母；未设定考试日则退化为百日周期）──
    val daysLeft = remember(targetDay) {
        if (targetDay.isBlank()) null else runCatching {
            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(targetDay)).toInt()
        }.getOrNull()
    }
    val prepProgress = remember(prepDays, daysLeft) {
        if (daysLeft != null && daysLeft > 0) {
            (prepDays.toFloat() / (prepDays + daysLeft).coerceAtLeast(1)).coerceIn(0f, 1f)
        } else {
            (prepDays / 100f).coerceIn(0f, 1f)
        }
    }
    val coverage = (practiced.toFloat() / questions.size.coerceAtLeast(1)).coerceIn(0f, 1f)

    // ── 分组行数据 ──
    val aiConfigured = aiKey.isNotBlank()
    val backupDesc = lastBackup?.let { "上次备份：" + formatTs(it, "yyyy-MM-dd HH:mm") } ?: "暂无本地备份"

    val toolEntries = remember(aiConfigured) {
        listOf(
            MineEntry("chat", "AI 助手", "智能答疑 · 题目解析", "aichat", AppColors.blue, dot = !aiConfigured),
            MineEntry("search", "全局搜索", "快速查找内容", "search", AppColors.blue)
        )
    }
    val contentEntries = remember {
        listOf(
            MineEntry("book", "教材管理", "查看与管理教材", "books", AppColors.warning),
            MineEntry("menu", "章节管理", "章节学习与练习", "chapters", AppColors.purple),
            MineEntry("note", "知识库", "收藏与笔记资料", "knowledge", AppColors.success),
            // 🔴 2026-09-20 新增（杰哥报「找不到图谱页入口」）：
            //   图谱原先仅有两处入口——① 统计页旧紧凑栏 HubIconAction（已随折叠退场删除）
            //   ② 统计页 E8「更多」三列卡（须滚到列表底部，且受 hasData 前置）
            //   ⇒ 首屏无可见入口。按 07 号 module_level_notes.overlap_policy
            //   「本页与题库页在资料类入口上刻意重叠，允许重叠」+ E4 spec，在本组补齐；
            //   图标/文案与统计页 E8 入口保持一致（同一功能的入口须同图标同文案）。
            MineEntry("tree", "知识图谱", "掌握度网状分布", "graph", AppColors.blue)
        )
    }
    val dataEntries = remember(backupDesc) {
        listOf(MineEntry("cloud", "本地备份与恢复", backupDesc, "settings", AppColors.blue))
    }
    val aboutEntries = remember(versionName) {
        listOf(
            MineEntry("gear", "设置", "外观 · 备份 · 同步 · AI · Pro", "settings", AppColors.textSecondary),
            MineEntry("info", "关于", "版本 v$versionName", "about", AppColors.purple, chevron = false)
        )
    }

    val listState = rememberLazyListState()

    Crossfade(targetState = metaLoaded, animationSpec = tween(Motion.duration(rm, Motion.SLOW)), label = "mineLoad") { loaded ->
        // 加载中渲染骨架屏（复用 03 号 today.skeleton 范式与公共原子 ShimmerBox；设计稿图 2 右）。
        // 原实现直接 return ⇒ 冷启动白屏，违反「首屏显示骨架而非空白」。
        if (!loaded) {
            SkeletonMineScreen()
            return@Crossfade
        }

        // 🔴 2026-09-19 沉浸 Hero（对齐高保真稿）：外框不再整体加横向 padding，由 Hero 通栏铺满，
        //    其余元素各自补回 sp.16（统计行 / 列表 contentPadding）。
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .background(AppColors.bg),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════ 上半段（非滚动）：Hero + 3 列概览 ══════════
            // ⚠️ 沉浸 Hero 背景向上溢出容器边界（原 clip=false 参数已随折叠退场移除）。
            CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
                HeroHeader(
                    title = "我的",
                    subtitle = if (prepDays > 0) "备考第 $prepDays 天" else "开始记录你的备考",
                    icon = appPainter("person"),
                    immersive = true,
                    statusBarInset = statusBarTop,
                    action = {
                        // 🔴 2026-09-20 补底圈：本处此前漏写 .background(...)，齿轮裸浮在渐变上，
                        //    与今日页 / 收集箱 / 校订 / 题库 / 课标库 5 处的「40dp 白 18% 圆底」不一致。
                        //    图标同步由 22dp 收敛到全局 20dp。
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { nav.navigate("settings") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("gear"), contentDescription = "设置", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }

            // 2026-09-25 晚：原 E8 收起态常驻栏已删 —— 折叠状态机退场后收起态不存在；
            //  『设置』入口由滚动区内的设置组承担（未丢失）。

            // ══════════ 下半段：唯一滚动容器 ══════════
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 76.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
                item(key = "hubBand") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ── E2 三列概览（圆徽章 + 标签同行 + 大数字 + 通栏进度条，各卡自带语义浅底）──
                        // 2026-09-21：下沉进滚动区，横向边距由 LazyColumn contentPadding 提供。
                        // 2026-09-20 ⑤ 值色口径：「累计练题 / 备考天数」为纯计数 ⇒ 中性深色；
                        // 「平均正确率」＝达成度指标 ⇒ 保留语义色。语义色另落在徽章 / 图标 / 进度条。
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard(
                                icon = "edit", value = "$practiced", unit = "题", label = "累计练题",
                                modifier = Modifier.weight(1f),
                                valueColor = AppColors.textPrimary,
                                iconTint = Color.White, iconBg = AppColors.blue, iconShape = CircleShape, iconSize = 22.dp,
                                labelInline = true, containerColor = AppColors.blueBg, containerHPad = 12.dp,
                                progress = coverage, progressColor = AppColors.blue
                            )
                            StatCard(
                                icon = "check", value = "${(overall * 100).toInt()}", unit = "%", label = "平均正确率",
                                modifier = Modifier.weight(1f),
                                valueColor = mineAccColor(overall),
                                iconTint = Color.White, iconBg = AppColors.success, iconShape = CircleShape, iconSize = 22.dp,
                                labelInline = true, containerColor = AppColors.greenBg, containerHPad = 12.dp,
                                progress = overall, progressColor = mineAccColor(overall)
                            )
                            StatCard(
                                icon = "calendar", value = "$prepDays", unit = "天", label = "备考天数",
                                modifier = Modifier.weight(1f),
                                valueColor = AppColors.textPrimary,
                                iconTint = Color.White, iconBg = AppColors.purple, iconShape = CircleShape, iconSize = 22.dp,
                                labelInline = true, containerColor = AppColors.purpleBg, containerHPad = 12.dp,
                                progress = prepProgress, progressColor = AppColors.purple
                            )
                        }
                    }
                }

                item(key = "learning") { MineGroup("学习工具", toolEntries) { nav.navigate(it.route) } }
                item(key = "content") { MineGroup("内容", contentEntries) { nav.navigate(it.route) } }
                item(key = "data") { MineGroup("数据", dataEntries) { nav.navigate(it.route) } }
                item(key = "about") { MineGroup("关于", aboutEntries) { nav.navigate(it.route) } }
            }
        }
    }
}

/**
 * 「我的」页冷启动骨架屏（03 号 `today.skeleton` 范式的跨页复用；设计稿图 2 右）。
 *
 * 逐块 1:1 对齐真实布局，避免加载完成瞬间的跳变：
 *  E1 Hero（r.medium 20dp）→ E2 三列概览（r.large 24dp，与 StatCard 同高）
 *  → E3~E6 四个分组（标题短条 + 分组容器，行高按真实 MineToolItem 52dp 累计）
 *
 * 骨架刻意不复用折叠基建 —— 加载期不存在滚动行为。
 * 灰块色/圆角/扫光规则全部由公共原子 [ShimmerBox] 提供（03 号 cross_screen_consistency）。
 */
@Composable
private fun SkeletonMineScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(Modifier.fillMaxWidth(), 100.dp, 20.dp)                      // E1 Hero
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.weight(1f), 118.dp, 24.dp)                      // E2 概览 ×3
            ShimmerBox(Modifier.weight(1f), 118.dp, 24.dp)
            ShimmerBox(Modifier.weight(1f), 118.dp, 24.dp)
        }
        // E3~E6：学习工具(2) / 内容(3) / 数据(1) / 关于(2)
        listOf(2, 3, 1, 2).forEach { rows ->
            ShimmerBox(Modifier.width(96.dp), 20.dp, 8.dp)                      // 分组标题短条
            ShimmerBox(Modifier.fillMaxWidth(), 52.dp * rows + 8.dp, 20.dp)     // 分组容器
        }
    }
}

// ============================================================
// 页面私有小件（仅布局壳，概览卡统一走 components 的 StatCard）
// ============================================================

/**
 * 分组（07 号）：[GroupTitle] plain 标题 + 单容器内多行。
 * 容器 = surfaceContainer / r20 / 无投影；行间 1dp `outlineVariant` **通栏分隔**（高保真图为通栏，不缩进）。
 */
@Composable
private fun MineGroup(title: String, entries: List<MineEntry>, onOpen: (MineEntry) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GroupTitle(title)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                entries.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MineToolItem(e) { onOpen(e) }
                }
            }
        }
    }
}

/** 分组行（07 号 E7）：Leading 徽章 → sp.12 → 标题 + 副文案(weight 1f) → Extra 尾部。 */
@Composable
private fun MineToolItem(entry: MineEntry, onClick: () -> Unit) {
    // 🔴 2026-09-25 补 CMP-PRESS：07 号 mine.main hf「按压态：整行 surfaceContainerHigh 覆盖 + scaleTo(0.99)」。
    //    此前仅 Modifier.clickable（M3 默认 ripple），无按压底色覆盖、无缩放。
    val press = rememberPressFeedback(
        scale = 0.99f,
        highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    Row(
        Modifier
            .fillMaxWidth()
            .then(press.modifier)
            .clickable(
                interactionSource = press.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MineIconBadge(entry.icon, entry.accent, entry.dot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.desc != null) {
                Text(
                    entry.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (entry.chevron) {
            Icon(
                appPainter("chevron"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 行徽章：38dp 圆角方块（accent 浅底 + accent 图标）；[dot] 为图标右上角状态点。 */
@Composable
private fun MineIconBadge(icon: String, accent: Color, dot: Boolean) {
    Box(Modifier.size(38.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(appPainter(icon), contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(AppColors.danger)
                    .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            )
        }
    }
}

