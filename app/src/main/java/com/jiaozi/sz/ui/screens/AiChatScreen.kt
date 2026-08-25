package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            IslandBus.enter("ai", IslandState(kind = "ai", title = "AI 帮手", detail = "思考中…"))
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
        // 顶部操作栏：搜索 + 清空
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(appPainter("search"), contentDescription = null) },
                placeholder = { Text("搜索历史对话…") },
                textStyle = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = { vm.clearHistory() }) {
                Icon(appPainter("trash"), contentDescription = "清空对话", modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (allMessages.isEmpty()) {
                item { Text("问我任何备考问题：考点解释、答题模板、备课思路都行。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            } else if (visible.isEmpty()) {
                item { Text("没有匹配「$q」的历史。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            } else {
                if (filtered.size > visible.size) {
                    item { TextButton(onClick = { pageSize += 100 }) { Text("加载更早 ${filtered.size - visible.size} 条") } }
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
            Text(vm.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner -> if (input.isEmpty()) Text("输入问题…", color = MaterialTheme.colorScheme.outline) else inner() }
            )
            IconButton(
                onClick = { vm.send(input); input = "" },
                enabled = !sending && input.isNotBlank()
            ) {
                Icon(appPainter("send"), contentDescription = "发送", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (sending && streaming == null) {
            // P2 流式打字光标：首 token 到达前的空白期用动态三点消除「发问后空等」的焦虑感
            Row(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 思考中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                TypingDots()
            }
        }

        if (contextQ.isNotBlank()) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(
                        "就题追问：${contextQ.take(60)}${if (contextQ.length > 60) "…" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { contextQ = ""; input = "" }) { Text("清除") }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: AiChatEntity) {
    val isUser = m.role == "user"
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(m.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
    }
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
