package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import androidx.compose.ui.graphics.painter.Painter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.Motivation
import com.jiaozi.sz.ui.theme.AppGradients

/** “我的”页：聚合设置、图谱等低频二级入口，减少底部导航 tab 数量 */
@Composable
fun MineScreen(nav: NavHostController) {
    val appVm = LocalAppVm.current
    val themePack by appVm.themePack.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val daysLeft = remember(targetDay) {
        if (targetDay.isBlank()) null else runCatching {
            val t = java.time.LocalDate.parse(targetDay).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            ((t - now) / 86400000).toInt()
        }.getOrNull()
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 渐变头部（对齐网页端高级配色，强化主题包风格）
        Box(
            Modifier.fillMaxWidth().background(AppGradients.hero(themePack, isSystemInDarkTheme()), RoundedCornerShape(20.dp)).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${disc}师范生", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    if (daysLeft == null) "备考中" else "备考中 · 距考 $daysLeft 天",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    Motivation.dailyPhrase(),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        EntryCard(
            title = "设置",
            subtitle = "主题、字体、动态取色、学科、AI Key、WebDAV 同步",
            icon = appPainter("gear"),
            onClick = { nav.navigate("settings") }
        )
        EntryCard(
            title = "知识库",
            subtitle = "先学知识再练题（输入侧）",
            icon = appPainter("book"),
            onClick = { nav.navigate("knowledge") }
        )
        EntryCard(
            title = "全局搜索",
            subtitle = "跨题库/知识/备课/收集箱检索",
            icon = appPainter("search"),
            onClick = { nav.navigate("search") }
        )
        EntryCard(
            title = "知识图谱",
            subtitle = "章节题数·掌握度与知识卡速览",
            icon = appPainter("tree"),
            onClick = { nav.navigate("graph") }
        )
        EntryCard(
            title = "章节健康度",
            subtitle = "按章节聚合正确率·错题数（高级维护）",
            icon = appPainter("bars"),
            onClick = { nav.navigate("chapters") }
        )
        EntryCard(
            title = "备课教案",
            subtitle = "记录每节课的备考设计",
            icon = appPainter("school"),
            onClick = { nav.navigate("lesson") }
        )
        EntryCard(
            title = "收集箱",
            subtitle = "随手收集资料、链接、好题",
            icon = appPainter("inbox"),
            onClick = { nav.navigate("inbox") }
        )
        EntryCard(
            title = "校订",
            subtitle = "复核 AI 待审题目",
            icon = appPainter("proof"),
            onClick = { nav.navigate("proof") }
        )
        EntryCard(
            title = "AI 帮手",
            subtitle = "对话式备考问答",
            icon = appPainter("chat"),
            onClick = { nav.navigate("aichat") }
        )
        EntryCard(
            title = "关于",
            subtitle = "与网页端「综合教资备考工作台」数据互通 · 正式版 V1",
            icon = appPainter("info"),
            onClick = { }
        )
    }
}

@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                painter = appPainter("chevron"),
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
