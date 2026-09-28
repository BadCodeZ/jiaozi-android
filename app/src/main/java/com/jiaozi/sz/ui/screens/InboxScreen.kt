package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatCard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.local.InboxEntity
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

/**
 * 收集箱（对齐高保真 `资料组 · 收集箱 校订.png` 左屏 / 10 号 JSON `screens[0] = inbox.main`）。
 *
 * 结构（自上而下，严格按图）：
 *  - E1 Hero：**沉浸通栏**（顶部贴屏幕边 + 仅底部两角圆角）+ 右上双键（齿轮 → 设置 / 白底加号 → 录入）
 *  - E2 统计卡组：`StatCard` default 变体 ×3（待处理 / 已归类 / 本周新增），各带实心语义圆徽章 + 进度条 + 语义浅底
 *  - E3 录入引导条：36dp 蓝方块 upload + 文案 + chevron，整条可点 → 展开录入面板
 *  - E4 列表标题：8dp primary 圆点 + SectionTitle + 右侧「去校订 ›」→ proof
 *  - E5 素材行：36dp **按来源类型着色**的浅底方块图标 + 主文案 2 行 + 删除键 + 缩进对齐的来源 meta
 *
 * 🔴 布局铁律：外框 `Column` **非滚动**且**不再套 16dp padding**（否则 Hero 无法沉浸通栏），
 *    内只放 `CollapsingTopBlocks`，唯一滚动容器 `LazyColumn` 必须 `weight(1f)`；
 *    水平 16dp 由各块自行 `padding(horizontal = 16.dp)` 补齐。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val items by appVm.repo.allInboxFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    var convertTarget by remember { mutableStateOf<InboxEntity?>(null) }
    var convSubject by remember { mutableStateOf("科三") }
    var convChapter by remember { mutableStateOf("收集箱") }
    var showForm by remember { mutableStateOf(false) }
    /** 待删除素材（行内删除二次确认，10 号 F6） */
    var pendingDelete by remember { mutableStateOf<InboxEntity?>(null) }
    var type by remember { mutableStateOf("text") }
    var content by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val types = listOf("text" to "文本", "link" to "链接", "question" to "题干")

    fun reset() { content = ""; note = ""; type = "text"; showForm = false }
    fun save() {
        if (content.isBlank()) return
        val now = System.currentTimeMillis()
        val ent = InboxEntity(id = "I$now", type = type, content = content.trim(), note = note.trim(), createdAt = now, _mt = now)
        appVm.viewModelScope.launch { appVm.repo.upsertInbox(ent) }
        reset()
    }

    // —— E2 统计口径（数据层无「已归类/本周新增」概念，按现有字段最小可用映射）——
    val totalCount = items.size                                  // 待处理 = 全部收集条目
    val classifiedCount = items.count { it.type == "question" }  // 已归类 = 已转题目
    val weeklyNew = remember(items) {
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        items.count { it.createdAt >= weekAgo }                  // 本周新增 = 近 7 天录入
    }
    // 进度条语义（10 号 JSON）：该卡值 / 同组最大值
    val progressBase = maxOf(totalCount, classifiedCount, weeklyNew).coerceAtLeast(1)

    val listState = rememberLazyListState()

    // 🔴 2026-09-27（IDX5 搜索结果直达）：消费 pendingOpenDoc，按 id 在列表定位 + 高亮
    val pendingOpen by appVm.pendingOpenDoc.collectAsStateWithLifecycle()
    var hlInboxId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOpen, items) {
        val po = pendingOpen
        if (po != null && po.route == "inbox" && po.id.isNotBlank()) {
            val idx = items.indexOfFirst { it.id == po.id }
            if (idx >= 0) {
                listState.scrollToItem(idx + 1) // +1：hubBand 头项
                hlInboxId = po.id
            }
            // 🔴 clearPendingOpenDoc() 改写 pendingOpen = 本 effect 的 key ⇒ 会取消本协程，
            //    必须放在状态写入之后；放 delay 之前会使高亮永久驻留（见 KnowledgeScreen 同款注释）。
            appVm.clearPendingOpenDoc()
        }
    }
    // 高亮 2.2s 后自动清除（独立 effect：不随 pendingOpenDoc 变更被取消）
    LaunchedEffect(hlInboxId) {
        if (hlInboxId != null) { delay(2200); hlInboxId = null }
    }

    // 沉浸 Hero：状态栏白字直接压在渐变上，内容下移避让（HeroHeader 内部做「上溢 + 少报高度」）
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(AppColors.bg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ① 顶部重区块（Hero + 统计 + 引导条）：常驻不滚，手势直通下方列表
        // ⚠️ 沉浸 Hero 背景向上溢出容器边界（原 clip=false 参数已随折叠退场移除）。
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(listState)) {
            // ── E1 Hero（沉浸通栏 + 右上双键）──
            HeroHeader(
                title = "收集箱",
                subtitle = "$totalCount 条待处理 · $classifiedCount 已归类 · $weeklyNew 本周新增",
                icon = appPainter("inbox"),
                immersive = true,
                statusBarInset = statusBarTop,
                // 返回键内联进 Hero 首行（与右上 action 左右成对）——沉浸页不再由 AppNav 叠加返回件，
                // 否则「磨砂白圆 + 标题胶囊」会正好压在本行的 46dp 图标徽章上（2026-09-19 实测）。
                onBack = { nav.navigateUp() },
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 齿轮：半透明白圆 + 白线条（设置入口）
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { nav.navigate("settings") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("gear"), contentDescription = "设置", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        // 加号：白底 primary 实心圆（录入开关）
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { if (showForm) reset() else { reset(); showForm = true } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("plus"), contentDescription = if (showForm) "收起" else "添加", tint = AppColors.blue, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ── E2 统计卡组（3col，StatCard default 变体 · 实心语义圆徽章 + 进度条 + 语义浅底）──
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = "clock", value = "$totalCount", unit = "条", label = "待处理",
                            modifier = Modifier.weight(1f),
                            valueColor = AppColors.textPrimary,
                            iconTint = Color.White, iconBg = AppColors.warning,
                            iconShape = CircleShape, iconSize = 22.dp,
                            labelInline = true, containerColor = AppColors.warningBg,
                            containerHPad = 12.dp,
                            progress = totalCount.toFloat() / progressBase,
                            progressColor = AppColors.warning
                        )
                        StatCard(
                            icon = "check", value = "$classifiedCount", unit = "条", label = "已归类",
                            modifier = Modifier.weight(1f),
                            valueColor = AppColors.textPrimary,
                            iconTint = Color.White, iconBg = AppColors.success,
                            iconShape = CircleShape, iconSize = 22.dp,
                            labelInline = true, containerColor = AppColors.greenBg,
                            containerHPad = 12.dp,
                            progress = classifiedCount.toFloat() / progressBase,
                            progressColor = AppColors.success
                        )
                        StatCard(
                            icon = "arrow", value = "$weeklyNew", unit = "条", label = "本周新增",
                            modifier = Modifier.weight(1f),
                            valueColor = AppColors.textPrimary,
                            iconTint = Color.White, iconBg = AppColors.purple,
                            iconShape = CircleShape, iconSize = 22.dp,
                            labelInline = true, containerColor = AppColors.purpleBg,
                            containerHPad = 12.dp,
                            progress = weeklyNew.toFloat() / progressBase,
                            progressColor = AppColors.purple
                        )
                    }

                    // ── E3 录入引导条（1dp outlineVariant + r.small 浅底，整条可点 → 展开录入）──
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()

                            .clickable { if (showForm) reset() else { reset(); showForm = true } },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.blue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(appPainter("upload"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                "粘贴题干 / 知识点，自动解析",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ⚠️ 表单必须放在滚动容器内：此前挂在非滚动外框上，展开后溢出屏外、「保存」按钮不可达
            if (showForm) {
                item(key = "form") {
                    Card(Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                types.forEach { (t, label) -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) }) }
                            }
                            if (type == "question") Text(
                                "格式：题干|~|选项|~|答案（空=整段作主观题）",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                            )
                            OutlinedTextField(content, { content = it }, label = { Text(if (type == "link") "链接地址" else "内容 *") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 6)
                            OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), Arrangement.End) { Button(onClick = { save() }) { Text("保存") } }
                        }
                    }
                }
            }

            // ── E4 列表标题：primary 圆点 + 标题 + 右侧「去校订 ›」──
            item(key = "sectionTitle") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.blue))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "待处理（$totalCount）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.textPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { nav.navigate("proof") }
                    ) {
                        Text("去校订", style = MaterialTheme.typography.bodyMedium, color = AppColors.blue)
                        Icon(appPainter("chevron"), contentDescription = "去校订", tint = AppColors.blue, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (items.isEmpty() && !showForm) {
                item(key = "emptyHint") { EmptyHint("inbox", "收集箱是空的", "通勤看到好资料，随手存进来，备考时再整理。") }
            }

            // ── E5 素材行：36dp 按来源类型着色的浅底方块图标 + 主文案 + 删除键 + 缩进 meta ──
            items(items, contentType = { "inbox" }) { e ->
                val typeLabel = types.find { it.first == e.type }?.second ?: e.type
                // 🔴 图标按来源类型着色（对齐高保真：蓝 / 紫 / 绿三色），而非统一蓝
                val accent = when (e.type) {
                    "question" -> AppColors.purple
                    "link" -> AppColors.success
                    else -> AppColors.blue
                }
                val accentBg = when (e.type) {
                    "question" -> AppColors.purpleBg
                    "link" -> AppColors.greenBg
                    else -> AppColors.blueBg
                }
                val rowIcon = if (e.type == "link") "link" else "book"
                Card(
                    Modifier
                        .fillMaxWidth()
                        // 图（E5）行内只有删除键 ⇒ 「转为题目」下沉为长按手势，保住功能不丢
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { convSubject = "科三"; convChapter = "收集箱"; convertTarget = e }
                        ),
                    colors = CardDefaults.cardColors(containerColor = if (hlInboxId == e.id) AppColors.blueBg else MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 前导图标：36dp 浅语义底圆角方块 + 语义主色图标
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter(rowIcon), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
                            Text(
                                e.content,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    append(typeLabel)
                                    if (e.note.isNotBlank()) append(" · ").append(e.note)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = AppColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 🔴 2026-09-23（10 号 F6）：删除改为二次确认 —— 直删不可逆
                        IconButton(onClick = { pendingDelete = e }, modifier = Modifier.size(40.dp)) {
                            Icon(appPainter("trash"), contentDescription = "删除", tint = AppColors.textSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // 长按提示（图未覆盖；「转为题目」已下沉为素材行长按手势，此处仅作可发现性提示）
            if (items.isNotEmpty()) {
                item(key = "convertHint") {
                    Text(
                        "长按素材可「转为题目」，转入校订指派章节。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)
                    )
                }
            }
        }

        // 🔴 行内删除二次确认（10 号 F6）：删除不可逆，必须拦一道
        if (pendingDelete != null) {
            val d = pendingDelete!!
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除素材") },
                text = { Text("确定删除「${d.content.take(20).ifBlank { "未命名素材" }}」？该操作不可撤销。") },
                confirmButton = {
                    TextButton(onClick = {
                        appVm.viewModelScope.launch { appVm.repo.deleteInbox(d.id) }
                        pendingDelete = null
                    }) { Text("删除", color = AppColors.danger) }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
            )
        }

        if (convertTarget != null) {
            val e = convertTarget!!
            AlertDialog(
                onDismissRequest = { convertTarget = null },
                title = { Text("转为题目 · 选择归属") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("科目（大纲口径）", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("科一", "科二", "科三").forEach { s ->
                                FilterChip(
                                    selected = convSubject == s,
                                    onClick = { convSubject = s },
                                    label = { Text(BankStore.shortName(s)) }
                                )
                            }
                        }
                        if (convSubject == "科三") {
                            Text("学科：$disc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = convChapter,
                            onValueChange = { convChapter = it },
                            label = { Text("章节（可留空，默认「收集箱」）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        appVm.viewModelScope.launch {
                            appVm.repo.inboxToQuestion(e, convSubject, convChapter, if (convSubject == "科三") disc else null)
                            Toast.makeText(ctx, "已转为《${BankStore.officialName(convSubject)}》题目，可在「题库」练习", Toast.LENGTH_SHORT).show()
                        }
                        convertTarget = null
                    }) { Text("确认转入") }
                },
                dismissButton = { Button(onClick = { convertTarget = null }) { Text("取消") } }
            )
        }
    }
}
