package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import androidx.compose.ui.graphics.painter.Painter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.jiaozi.sz.domain.StatsCalculator

/** “我的”页：聚合设置、图谱等低频二级入口，减少底部导航 tab 数量。
 *  重构方向（V2.59）：① 渐变肖像头部（对齐此前各屏头部）② 统计概览格（对齐 Stats 屏）
 *  ③ 菜单按 Miuix 分组标题归类，条目采用关于模块同款 surfaceVariant 卡片式（标题+副标题+箭头）。 */
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

    // 统计概览（对齐 Stats 屏）
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val streak by appVm.checkinStreak.collectAsStateWithLifecycle()
    val questions = appVm.repo.bank.exam
    val practiced = remember(progress, questions) { StatsCalculator.totalPracticed(progress) }
    val overall = remember(progress) { StatsCalculator.overallAccuracy(progress) }
    val mastery = remember(progress) { StatsCalculator.masteryRate(progress) }
    val wrongCount = remember(progress) { progress.values.count { it.wrongBook } }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // —— ① 渐变肖像头部（对齐此前各屏头部风格）——
        Box(
            Modifier.fillMaxWidth().background(AppGradients.hero(themePack, isSystemInDarkTheme()), RoundedCornerShape(20.dp)).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 头像
                    Box(
                        Modifier.size(60.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(appPainter("person"), contentDescription = null, Modifier.size(34.dp), tint = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${disc}师范生", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (daysLeft == null) "备考中" else "距考 $daysLeft 天 · 备考中",
                            style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                Text(
                    Motivation.dailyPhrase(),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // —— ② 统计概览格（对齐 Stats 屏 SummaryCard）——
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("已练习", "$practiced", Modifier.weight(1f))
            StatTile("总体正确率", "${(overall * 100).toInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("掌握度", "${(mastery * 100).toInt()}%", Modifier.weight(1f))
            StatTile("连续打卡", "$streak 天", Modifier.weight(1f))
        }
        if (wrongCount > 0) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("错题本待复习", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text("$wrongCount 题", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // —— ③ 菜单：Miuix 分组标题 + 关于模块同款卡片式条目 ——
        SectionTitle("学习")
        EntryCard(title = "知识库", subtitle = "先学知识再练题（输入侧）", icon = appPainter("book"), onClick = { nav.navigate("knowledge") })
        EntryCard(title = "知识图谱", subtitle = "章节题数·掌握度与知识卡速览", icon = appPainter("tree"), onClick = { nav.navigate("graph") })
        EntryCard(title = "章节健康度", subtitle = "按章节聚合正确率·错题数", icon = appPainter("bars"), onClick = { nav.navigate("chapters") })
        EntryCard(title = "备课教案", subtitle = "记录每节课的备考设计", icon = appPainter("school"), onClick = { nav.navigate("lesson") })
        EntryCard(title = "收集箱", subtitle = "随手收集资料、链接、好题", icon = appPainter("inbox"), onClick = { nav.navigate("inbox") })

        SectionTitle("备考工具")
        EntryCard(title = "全局搜索", subtitle = "跨题库/知识/备课/收集箱检索", icon = appPainter("search"), onClick = { nav.navigate("search") })
        EntryCard(title = "校订", subtitle = "复核 AI 待审题目", icon = appPainter("proof"), onClick = { nav.navigate("proof") })
        EntryCard(title = "AI 帮手", subtitle = "对话式备考问答", icon = appPainter("chat"), onClick = { nav.navigate("aichat") })

        SectionTitle("通用")
        EntryCard(title = "设置", subtitle = "主题、字体、动态取色、学科、AI Key、WebDAV 同步", icon = appPainter("gear"), onClick = { nav.navigate("settings") })
        EntryCard(title = "关于", subtitle = "版本更新 · 赞助支持 · 社交（GitHub / 小红书 / 抖音）", icon = appPainter("info"), onClick = { nav.navigate("about") })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

/** 卡片式条目（关于模块同款：surfaceVariant 底 + 标题(主色) + 副标题(次要) + 右箭头） */
@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = title,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(
                painter = appPainter("chevron"),
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
