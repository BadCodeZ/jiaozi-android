package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.local.UserQuestionEntity
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import java.io.File

/**
 * 校订（对齐网页端 `VIEW.proof` 四标签）：
 * - 待审：AI 生成题经质量护栏标记「待审」的人工复核；
 * - 错题本：wrongBook 题，可「导出 PDF」（系统分享打印）；
 * - 归类：未归类用户题手动指派章节；
 * - 复核：本地质量抽检（解析过短/缺答案）。
 */
@Composable
fun ProofScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf("待审") }
    val tabs = listOf("待审", "错题本", "归类", "复核")

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) }) }
        }

        when (tab) {
            "待审" -> PendingTab(appVm, repo)
            "错题本" -> WrongBookTab(appVm, repo, disc, nav)
            "归类" -> ClassifyTab(appVm, repo)
            "复核" -> ReviewTab(appVm, repo)
        }
    }
}

@Composable
private fun PendingTab(appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository) {
    var pending by remember { mutableStateOf<List<Question>>(emptyList()) }
    var reviewed by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        pending = repo.pendingProofQuestions()
        reviewed = repo.proofReviewedIds()
    }
    val pendingUnreviewed = pending.filter { it.id !in reviewed }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI 生成的题目经质量护栏标记「待审」，人工确认后即可放心入库。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pending.isEmpty()) {
            Text("题库中没有待校订的题目，质量良好。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            Text("待校订 ${pendingUnreviewed.size} / 共 ${pending.size} 题", style = MaterialTheme.typography.titleMedium)
            if (pendingUnreviewed.isNotEmpty()) {
                Button(onClick = { appVm.viewModelScope.launch { pendingUnreviewed.forEach { repo.markProofReviewed(it.id) }; reviewed = repo.proofReviewedIds() } }) { Text("全部标记已校订") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)) {
                items(pendingUnreviewed, contentType = { "proof" }) { q -> ProofCard(q, appVm, repo, reviewed) { appVm.viewModelScope.launch { reviewed = repo.proofReviewedIds() } } }
            }
        }
    }
}

@Composable
private fun ProofCard(q: Question, appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository, reviewed: Set<String>, onReviewed: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("${q.subject} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("待审", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(q.q, style = MaterialTheme.typography.bodyMedium)
            if (!q.analysis.isNullOrBlank()) Text("解析：${q.analysis}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 4)
            if (!q.flagMsg.isNullOrBlank()) Text("校订提示：${q.flagMsg}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                Button(onClick = { appVm.viewModelScope.launch { repo.markProofReviewed(q.id); onReviewed() } }) { Text("标记已校订") }
            }
        }
    }
}

@Composable
private fun WrongBookTab(appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository, disc: String, nav: NavHostController) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<Pair<Question, ProgressEntity>>>(emptyList()) }
    LaunchedEffect(disc) { items = repo.wrongBookQuestions(if (disc.isNotBlank()) disc else null) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("错题本（科三按当前学科「$disc」隔离显示）", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            appVm.viewModelScope.launch {
                val html = buildString {
                    append("<html><head><meta charset='utf-8'><title>错题本</title></head><body>")
                    append("<h2>综合教资备考 · 错题本（${items.size} 题）</h2>")
                    items.forEachIndexed { i, (q, _) ->
                        append("<p><b>${i + 1}. [${q.subject}] ${q.chapter}</b><br>${q.q}<br>")
                        append("答案：${q.answer.ifBlank { "（主观题）" }}<br>")
                        if (!q.analysis.isNullOrBlank()) append("解析：${q.analysis}")
                        append("</p>")
                    }
                    append("</body></html>")
                }
                val file = File(ctx.cacheDir, "wrong_book.html")
                file.writeText(html)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"; putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(intent, "导出/打印错题本"))
            }
        }) { Icon(appPainter("share"), contentDescription = null); Text(" 导出 PDF / 打印") }

        if (items.isEmpty()) {
            Text("错题本是空的，继续保持！", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)) {
                items(items, contentType = { "wrong" }) { (q, p) ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(2.dp)) {
                            Text("${q.subject} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(q.q, style = MaterialTheme.typography.bodyMedium)
                            if (!q.answer.isNullOrBlank()) Text("答案：${q.answer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            // 草稿落盘接入历史错题库：展示当时主观题作答（复盘可见）
                            if (!p.draft.isNullOrBlank()) {
                                Text("我的作答：${p.draft}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.End) {
                                IconButton(onClick = { appVm.setPendingAiContext(q.q); nav.navigate("aichat") }) {
                                    Icon(appPainter("chat"), contentDescription = "问 AI", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassifyTab(appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository) {
    var items by remember { mutableStateOf<List<UserQuestionEntity>>(emptyList()) }
    LaunchedEffect(Unit) { items = repo.unclassifiedUserQuestions() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("未归类题目（来自收集箱转题/AI 出题），手动指派科目与章节。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (items.isEmpty()) {
            Text("没有未归类题目。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)) {
                items(items, contentType = { "classify" }) { e ->
                    var subj by remember { mutableStateOf(e.subject.ifBlank { "科一" }) }
                    var ch by remember { mutableStateOf("") }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                            Text(e.q.take(80), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(subj, { subj = it }, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(ch, { ch = it }, label = { Text("章节") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Button(onClick = { appVm.viewModelScope.launch { repo.setUserQuestionChapter(e.id, subj.trim(), ch.trim().ifBlank { "未分类" }); items = repo.unclassifiedUserQuestions() } }) { Text("保存归类") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewTab(appVm: AppViewModel, repo: com.jiaozi.sz.data.AppRepository) {
    var ids by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { ids = repo.localQualityCheck(10) }
    val pending by remember { mutableStateOf<List<Question>>(emptyList()) }
    var full by remember { mutableStateOf<List<Question>>(emptyList()) }
    LaunchedEffect(Unit) { full = repo.pendingProofQuestions() }
    val hits = full.filter { it.id in ids }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("本地质量抽检：解析过短或缺答案的待审题（无 Key 也可运行）。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hits.isEmpty()) {
            Text("未检出质量问题，待审题解析完整。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)) {
                items(hits, contentType = { "review" }) { q -> ProofCard(q, appVm, repo, emptySet()) {} }
            }
        }
    }
}
