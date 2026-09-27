package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.IconBadge

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.AiChatEntity
import com.jiaozi.sz.ui.AiChatViewModel
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.island.IslandState
import com.jiaozi.sz.xiaomi.FloatingIslandService
import android.provider.Settings

@Composable
fun AiChatScreen(nav: NavHostController) {
    val vm: AiChatViewModel = viewModel()
    val allMessages by vm.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var pageSize by remember { mutableStateOf(100) }
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()
    val aiContext by appVm.pendingAiContext.collectAsStateWithLifecycle()
    var contextQ by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    // 🔴 2026-09-27（IDX6）：清空对话三入口统一收敛为二次确认，避免误清全部对话历史
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空对话") },
            text = { Text("将清空当前全部对话历史，此操作不可撤销。确定要继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    input = ""
                    contextQ = ""
                    showClearConfirm = false
                }) { Text("清空", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
    LaunchedEffect(aiContext) {
        if (aiContext.isNotBlank()) {
            contextQ = aiContext
            input = "关于这道题：\n「${aiContext}」\n请讲解考点、解题思路与易错点。"
            appVm.setPendingAiContext("")
        }
    }
    // 灵动岛（上岛）：AI 帮手思考中时把状态推到全局悬浮胶囊；开关开启时自动拉起服务
    val sending by vm.sending.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    LaunchedEffect(sending, islandEnabled) {
        if (sending) {
            if (islandEnabled && Settings.canDrawOverlays(ctx)) {
                ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
            }
            IslandBus.enter("ai", IslandState(kind = "ai", title = "AI 助手", detail = "思考中…"))
        } else {
            IslandBus.leave("ai")
        }
    }
    DisposableEffect(Unit) {
        onDispose { IslandBus.leave("ai") }
    }

    val listState = rememberLazyListState()

    // 是否贴近底部：用于决定是否自动跟随滚动。用户上滑看历史时不再被流式输出一次次拽回底部。
    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.totalItemsCount - 1
            last < 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= last - 1
        }
    }

    // 搜索：按内容过滤（复用网页端 150ms 防抖约定，这里用 derivedStateOf 即时过滤，输入框本身轻量）
    val q = query.trim()
    val filtered = remember(allMessages, q) {
        if (q.isBlank()) allMessages else allMessages.filter { it.content.contains(q, ignoreCase = true) }
    }
    // 分页：默认渲染最近 100 条，上拉加载更早（R2 性能缺口）
    val visible = remember(filtered, pageSize) { filtered.takeLast(pageSize) }

    // 新消息到达：仅当用户本就贴近底部时才自动滚动到底（不打断阅读历史）
    LaunchedEffect(visible.size, q) { if (visible.isNotEmpty() && isNearBottom) listState.scrollToItem(visible.size - 1) }
    // 流式输出时跟随逐字生成：同样仅在贴近底部时跟随，用户上滑看前文则不抢滚动
    LaunchedEffect(streaming) { if (streaming != null && isNearBottom) listState.scrollToItem(visible.size) }

        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
        // 顶部栏：标题 + 新建对话 + 菜单
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("AI 助手", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(appPainter("plus"), contentDescription = "新建对话", modifier = Modifier.size(22.dp))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(appPainter("more"), contentDescription = "菜单", modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("清空对话") }, onClick = { menuOpen = false; showClearConfirm = true })
                        DropdownMenuItem(text = { Text("退出就题追问") }, onClick = { menuOpen = false; contextQ = ""; input = "" })
                    }
                }
            }
        }
        // 上下文 chip（就题追问）
        if (contextQ.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "基于『${contextQ.take(24)}${if (contextQ.length > 24) "…" else ""}』的题目上下文",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        // close 用 Box + clickable（32dp，与全局 FilterChip 高度同档）而非 IconButton——
                        // M3 IconButton 会在调用方 modifier 之后追加 minimumInteractiveComponentSize，
                        // 写 size(20.dp) 真机实为 48dp，会把胶囊撑成 56dp 高。
                        Box(
                            Modifier.size(32.dp).clickable { contextQ = ""; input = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(appPainter("close"), contentDescription = "清除", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        // 顶部操作栏：搜索 + 清空
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 🔴 2026-09-21 按 13 号稿改**自绘**：M3 `OutlinedTextField` 的最小高锁在 56dp，
            //    上轮用 `Modifier.height(46.dp)` 强制压低后其内部仍按 56dp 排版 ⇒ 文字被上下裁切
            //    （真机实测「搜索历史对话…」只剩中间一截）。改 BasicTextField 自绘后高度/居中完全可控。
            Box(
                Modifier.weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(appPainter("search"), contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.textSecondary)
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("搜索历史对话…", fontSize = 14.sp, color = AppColors.textSecondary, maxLines = 1)
                                inner()
                            }
                        }
                    }
                )
            }
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(appPainter("trash"), contentDescription = "清空对话", modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (allMessages.isEmpty()) {
                // 空态（2026-09-21 按高保真稿）：assistant 侧「头像 + 问候气泡 + 3 个自适应宽胶囊快捷提问」
                // 原实现是 3 张 fillMaxWidth 的整行快捷卡，稿内为**窄胶囊**（primaryContainer + r999）。
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        AssistantAvatar()
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.fillMaxWidth(0.86f), Arrangement.spacedBy(10.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "你好，我是你的备考助手\n我可以帮你解答问题、解析题目、总结知识点，也可以根据题目进行针对性追问。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            listOf("帮我讲解这道材料分析题", "帮我出一套练习题", "总结这部分的常见错因").forEach { q ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(999.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { vm.send(q) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(q, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }
            } else if (visible.isEmpty()) {
                item { Text("没有匹配「$q」的历史。", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline) }
            } else {
                if (filtered.size > visible.size) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = AppColors.trackGray)
                            TextButton(onClick = { pageSize += 100 }) { Text("加载更早 ${filtered.size - visible.size} 条") }
                            HorizontalDivider(Modifier.weight(1f), color = AppColors.trackGray)
                        }
                    }
                }
                items(visible, contentType = { it.role }) { m -> ChatBubble(m) }
                // 流式输出：在已持久化消息之后追加一个实时气泡，生成完毕写入 Room 后消失
                if (streaming != null) {
                    item(key = "__live__", contentType = { "live" }) {
                        ChatBubble(AiChatEntity(id = "__live__", role = "assistant", content = streaming ?: "", ts = 0, _mt = 0))
                    }
                }
            }
        }

        if (vm.error != null) {
            Text(vm.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner -> if (input.isEmpty()) Text("请输入你的问题…", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline) else inner() }
            )
            // 发送键（2026-09-21 按高保真稿）：实色主色圆底 + 白纸飞机。
            // 外层 48dp 只承担触控区（满足 44dp 下限），内层 40dp 承担视觉圆底 —— 规避 M3 IconButton 的尺寸膨胀。
            val canSend = !sending && input.isNotBlank()
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else AppColors.trackGray)
                        .clickable(enabled = canSend) { vm.send(input); input = "" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(appPainter("send"), contentDescription = "发送", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (sending && streaming == null) {
            // P2 流式打字光标：首 token 到达前的空白期用动态三点消除「发问后空等」的焦虑感
            // 🔴 2026-09-21 按高保真稿校正：三点在前、文案在后（原实现文案在前）
            Row(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypingDots()
                Text("AI 思考中", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * 消息气泡（2026-09-21 按高保真稿）：**双角色头像 + 容器色双编码**。
 * - assistant：左侧蓝色圆角块（r10）+ 白 chat 图标；
 * - user：右侧蓝色圆形 + 白 person 图标；
 * - 气泡宽度上限 78%（为头像留出槽位，原为 85%）。
 *
 * ⚠️ 13 号规范原写「不用头像」并将其列入 anti_patterns；本轮按「高保真图为准」的既定裁定改为此形态，
 * 规范已同步回写（见 13_全局通用组.json → elements.E6）。
 */
@Composable
private fun ChatBubble(m: AiChatEntity) {
    val isUser = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            IconBadge("chat", AppColors.blue, size = 30.dp, shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(8.dp))
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            Text(m.content, style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            IconBadge("person", AppColors.blue, size = 30.dp, shape = CircleShape)
        }
    }
}

/** 空态问候用的 assistant 头像（与 [ChatBubble] 同形，抽出来给空态复用）。 */
@Composable
private fun AssistantAvatar() {
    IconBadge("chat", AppColors.blue, size = 30.dp, shape = RoundedCornerShape(10.dp))
}

/** 流式打字光标：首 token 到达前、以及生成中的微动效，消除「发问后空等」焦虑（P2） */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(420, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i"
            )
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), CircleShape))
        }
    }
}
