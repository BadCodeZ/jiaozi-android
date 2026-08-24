package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.StatsCalculator
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

@Composable
fun StatsScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val daily by appVm.repo.recentDailyStat(14).collectAsStateWithLifecycle(initialValue = emptyList())
    val streak by appVm.checkinStreak.collectAsStateWithLifecycle()
    val questions = appVm.repo.bank.exam

    // 统计计算较重（遍历 3300+ 题），统一记忆化：进度/日趋势/连续打卡变化时才重算，
    // 避免每次重组（如进度流推送）都重跑，消除切到「统计」与停留时的卡顿。
    val accBySubject = remember(progress, questions) { StatsCalculator.accuracyBySubject(questions, progress) }
    val ranking = remember(progress, questions) { StatsCalculator.chapterRanking(questions, progress).take(8) }
    val cause = remember(progress) { StatsCalculator.causeDistribution(progress).toList().sortedByDescending { it.second } }
    val trend = remember(daily) { StatsCalculator.trend(daily) }
    val overall = remember(progress) { StatsCalculator.overallAccuracy(progress) }
    val practiced = remember(progress) { StatsCalculator.totalPracticed(progress) }
    val mastery = remember(progress) { StatsCalculator.masteryRate(progress) }
    val wrongCount = remember(progress) { progress.values.count { it.wrongBook } }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("学习统计", style = MaterialTheme.typography.headlineSmall)

        // 概览卡片（4 项核心指标）
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("已练习题", "$practiced", Modifier.weight(1f))
            SummaryCard("总体正确率", "${(overall * 100).toInt()}%", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("掌握度", "${(mastery * 100).toInt()}%", Modifier.weight(1f))
            SummaryCard("连续打卡", "$streak 天", Modifier.weight(1f))
        }
        if (wrongCount > 0) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("错题本待复习", style = MaterialTheme.typography.bodyMedium)
                    Text("$wrongCount 题", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // 目标分差（对齐网页端统计「与目标分差」）
        val targetScore by appVm.targetScore.collectAsStateWithLifecycle()
        var targetInput by remember { mutableStateOf(targetScore.toString()) }
        val estimated = (overall * 150).toInt()
        val gap = targetScore - estimated
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("目标分差", style = MaterialTheme.typography.titleMedium)
                    Text("估分 $estimated / 150", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (practiced > 0) {
                    LinearProgressIndicator(progress = { (estimated / 150f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
                    Text(
                        if (gap > 0) "距目标（${targetScore} 分）还差 ${gap} 分，针对性补强弱章节即可追上。"
                        else "已超目标 ${-gap} 分，保持当前节奏！",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("还没有练习记录，先去「练习」做几题，这里会显示你的估分与目标差距。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("目标分（0–150）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val v = targetInput.toIntOrNull()?.coerceIn(0, 150) ?: 90
                        appVm.setTargetScore(v)
                        targetInput = v.toString()
                    }) { Text("设定目标") }
                }
            }
        }

        Text("各科目正确率", style = MaterialTheme.typography.titleMedium)
        if (accBySubject.isEmpty()) {
            Text("还没有练习记录，去「练习」做几题吧～", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            accBySubject.forEach { (subj, acc) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(subj, style = MaterialTheme.typography.labelMedium)
                            Text("${(acc * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { acc.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                    }
                }
            }
        }

        if (ranking.isNotEmpty()) {
            Text("薄弱章节（正确率由低到高）", style = MaterialTheme.typography.titleMedium)
            ranking.forEachIndexed { i, (chap, acc) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(chap, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { acc.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                    Text("${(acc * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (cause.isNotEmpty()) {
            Text("错因分布", style = MaterialTheme.typography.titleMedium)
            cause.forEach { (c, n) -> Text("· $c：$n 次", style = MaterialTheme.typography.bodyMedium) }
        }

        // AI 讲评错因聚合（跨会话缓存：最近一次 AI 讲评的错因汇总）
        var aiCauseCache by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { aiCauseCache = appVm.repo.getMeta(MetaKeys.AI_EXPLAIN_CACHE) }
        aiCauseCache?.let { raw ->
            runCatching {
                val obj = org.json.JSONObject(raw)
                val ts = obj.optLong("ts", 0L)
                val arr = obj.optJSONArray("causes")
                val dateStr = if (ts > 0) java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).format(java.util.Date(ts)) else ""
                val list = (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr?.optJSONObject(i)?.let { it.optString("c") to it.optInt("n", 0) } }
                if (list.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("AI 讲评错因聚合", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(if (dateStr.isNotBlank()) "更新于 $dateStr" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            list.sortedByDescending { it.second }.forEach { (c, n) -> Text("· $c：$n 次", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                            Text("来自最近一次 AI 讲评（跨会话缓存，可长期追踪高频错因）。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                } else null
            }.getOrNull()
        }

        if (trend.isNotEmpty()) {
            Text("近 14 天练习趋势", style = MaterialTheme.typography.titleMedium)
            trend.forEach { d ->
                val total = d.right + d.wrong
                val acc = if (total == 0) 0f else d.right.toFloat() / total
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.date.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(48.dp))
                    Box(Modifier.weight(1f).padding(end = 8.dp)) {
                        LinearProgressIndicator(progress = { acc }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                    Text("对${d.right}/错${d.wrong}", style = MaterialTheme.typography.labelSmall)
                }
            }
            val recent = trend.takeLast(7)
            val recentAcc = if (recent.isEmpty()) 0f else recent.sumOf { it.right }.toFloat() / recent.sumOf { it.right + it.wrong }.coerceAtLeast(1)
            val recentDays = recent.count { it.right + it.wrong > 0 }
            val summary = when {
                practiced == 0 -> "还没有练习记录，去「练习」做几题，明天这里会显示你的成长曲线。"
                recentDays == 0 -> "近 7 天没有练习，保持节奏很重要——每天做 20 题比突击更有效。"
                recentAcc >= 0.8 -> "近 7 天正确率 ${(recentAcc * 100).toInt()}%，状态很好，薄弱章节可继续攻坚。"
                recentAcc >= 0.6 -> "近 7 天正确率 ${(recentAcc * 100).toInt()}%，稳步提升中，建议针对薄弱章节专项练习。"
                else -> "近 7 天正确率 ${(recentAcc * 100).toInt()}%，偏低，先回归错题本巩固基础再提速。"
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
