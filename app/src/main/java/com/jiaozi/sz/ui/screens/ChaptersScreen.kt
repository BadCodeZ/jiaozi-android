package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.Canvas
import com.jiaozi.sz.ui.components.EmptyHint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.PracticeEngine
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

/**
 * 章节健康度（chapters 视图，高级维护功能，对齐网页端章节健康）。
 * 按章节聚合：题数、已练数、正确率、错题数，并以热力色标识薄弱/一般/良好/未练。
 * 提供「去练该章」入口，一键进入该章节专项练习；章节可编辑（显示名/权重/数据自检）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val subjects = listOf("科一", "科二", "科三")
    var subj by remember { mutableStateOf("科一") }
    var sortBy by remember { mutableStateOf("掌握度") }
    // 章节配置（显示名 + 权重）：来自 AppViewModel，编辑后实时刷新
    val chapterConfig by appVm.chapterConfig.collectAsStateWithLifecycle()
    // 编辑中章节（null = 未打开抽屉）
    var editing by remember { mutableStateOf<ChapterHealth?>(null) }

    // 该科目全部题（科三按当前学科隔离）
    val qs = remember(repo.bank.exam, subj, disc) {
        repo.bank.exam.filter { it.subject == subj && (subj != "科三" || it.disc == disc) }
    }
    // 章节聚合（按题中出现过的章节名，保证覆盖未进大纲的题）
    val health = remember(qs, progress) {
        qs.groupBy { it.chapter.ifBlank { "未归类" } }.entries.map { (ch, list) ->
            var r = 0; var w = 0; var wrong = 0
            list.forEach { q ->
                progress[q.id]?.let { e: ProgressEntity ->
                    r += e.right; w += e.wrong
                    if (e.wrongBook) wrong++
                }
            }
            val done = r + w
            val acc = if (done > 0) r.toFloat() / done else -1f
            ChapterHealth(ch, list.size, done, acc, wrong)
        }.sortedWith(compareBy({ it.acc }, { -it.count }))
    }

    val goodCol = Color(0xFF3FA45B)
    val warnCol = Color(0xFFE0A52B)
    val badCol = Color(0xFFE94634)
    val noneCol = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> FilterChip(selected = subj == s, onClick = { subj = s }, label = { Text(s) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("掌握度", "题数").forEach { k ->
                FilterChip(selected = sortBy == k, onClick = { sortBy = k }, label = { Text(if (k == "掌握度") "按掌握度" else "按题数") })
            }
        }
        val weak = health.count { it.acc in 0f..0.5f }
        val unpracticed = health.count { it.acc < 0f }
        Text(
            "共 ${health.size} 章 · 薄弱 ${weak} 章 · 未练 ${unpracticed} 章。未练章节已标记「优先练习」，薄弱章节建议专项突破。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val sorted = remember(health, sortBy) {
            if (sortBy == "题数") health.sortedWith(compareBy({ -it.count }))
            else health.sortedWith(compareBy({ it.acc }, { -it.count }))
        }

        if (health.isEmpty()) {
            EmptyHint("bars", "该科目暂无题目", "先去「练习」或收集箱转题，章节健康度会自动统计。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                items(sorted, contentType = { "chapter" }) { h ->
                    val col = when {
                        h.acc < 0f -> noneCol
                        h.acc >= 0.8f -> goodCol
                        h.acc >= 0.5f -> warnCol
                        else -> badCol
                    }
                    val status = when {
                        h.acc < 0f -> "未练"
                        h.acc >= 0.8f -> "良好"
                        h.acc >= 0.5f -> "一般"
                        else -> "薄弱"
                    }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (h.acc in 0f..0.5f) {
                                        Canvas(Modifier.size(10.dp)) {
                                            drawContext.canvas.nativeCanvas.drawCircle(
                                                size.width / 2f, size.height / 2f, size.width / 2f,
                                                android.graphics.Paint().apply { color = badCol.value.toInt(); isAntiAlias = true }
                                            )
                                        }
                                    }
                                    Canvas(Modifier.size(12.dp)) {
                                        drawContext.canvas.nativeCanvas.drawCircle(
                                            size.width / 2f, size.height / 2f, size.width / 2f,
                                            android.graphics.Paint().apply { color = col.value.toInt(); isAntiAlias = true }
                                        )
                                    }
                                    val dispName = chapterConfig[PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, h.chapter)]?.name?.takeIf { it.isNotBlank() } ?: h.chapter
                                    Text(dispName, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    TextButton(onClick = { editing = h }) { Text("编辑") }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("题数 ${h.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("已练 ${h.done}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("错题 ${h.wrong}", style = MaterialTheme.typography.bodySmall, color = if (h.wrong > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (h.acc < 0f) "未练" else "正确 ${(h.acc * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (h.acc >= 0f) {
                                LinearProgressIndicator(
                                    progress = { h.acc.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = col,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                                if (h.acc < 0f) {
                                    Button(onClick = {
                                        appVm.setPendingChapterPractice(subj, h.chapter)
                                        nav.navigate("practice")
                                    }) { Text("优先练习") }
                                } else {
                                    OutlinedButton(onClick = {
                                        appVm.setPendingChapterPractice(subj, h.chapter)
                                        nav.navigate("practice")
                                    }) { Text("去练该章") }
                                }
                            }
                        }
                    }
                }

            }

                // 章节编辑抽屉：显示名（改名）+ 模考权重 + 数据自检
                if (editing != null) {
                    val eh = editing!!
                    val key = PracticeEngine.chapterKey(subj, if (subj == "科三") disc else null, eh.chapter)
                    val cfg = chapterConfig[key]
                    var name by remember(eh.chapter) { mutableStateOf(cfg?.name ?: "") }
                    var weight by remember(eh.chapter) { mutableStateOf((cfg?.weight ?: 1.0).toFloat()) }
                    val chQs = if (eh.chapter == "未归类")
                        qs.filter { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }
                    else
                        qs.filter { it.chapter == eh.chapter }
                    val noAnalysis = chQs.count { it.analysis.isNullOrBlank() || it.analysis.length < 6 }
                    val unclassified = chQs.count { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }
                    ModalBottomSheet(onDismissRequest = { editing = null }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("编辑章节 · ${eh.chapter}", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(name, { name = it }, label = { Text("显示名（改名）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("模考权重", style = MaterialTheme.typography.bodyMedium)
                                Text("${"%.1f".format(weight)}×", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                            Slider(value = weight, onValueChange = { weight = it }, valueRange = 0f..2f, steps = 39)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("数据自检", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("题数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${chQs.size}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("缺解析", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$noAnalysis", style = MaterialTheme.typography.bodySmall, color = if (noAnalysis > 0) warnCol else goodCol)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("未归类章节", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$unclassified", style = MaterialTheme.typography.bodySmall, color = if (unclassified > 0) warnCol else goodCol)
                                    }
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                        Text("权重状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (weight <= 0f) "已停用" else "正常", style = MaterialTheme.typography.bodySmall, color = if (weight <= 0f) badCol else goodCol)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editing = null }, Modifier.weight(1f)) { Text("取消") }
                                Button(onClick = { appVm.saveChapterConfig(key, name.trim(), weight.toDouble()); editing = null }, Modifier.weight(1f)) { Text("保存") }
                            }
                        }
                    }
                }

        }
    }
}

private data class ChapterHealth(
    val chapter: String,
    val count: Int,
    val done: Int,
    val acc: Float,
    val wrong: Int
)
