package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.SectionTitle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.DocHit
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import kotlinx.coroutines.delay

/**
 * 全局搜索：六块——题库 / 知识库 / 备课·教案(FTS) / 课标库(FTS) / 教材库(FTS) / 收集箱。
 * 题库/知识库/收集箱沿用内存子串匹配；课标库/教材库/教案走 Room FTS4 全文检索（B 阶段）。
 * 题库命中可点击直接去练；其余块点击可跳对应模块。
 */
@Composable
fun SearchScreen(nav: NavHostController, initial: String = "") {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val inbox by repo.allInboxFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var raw by remember { mutableStateOf(TextFieldValue(initial)) }
    var query by remember { mutableStateOf(initial) }
    // 小米传送门：AppRoot 已带 pendingSearch 跳转本屏，这里取初始词并消费清空
    val pendingSearch by appVm.pendingSearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        if (pendingSearch.isNotBlank()) {
            raw = TextFieldValue(pendingSearch)
            query = pendingSearch
            appVm.setPendingSearch("")
        }
    }
    LaunchedEffect(raw) { delay(150); query = raw.text }

    val q = query.trim()
    // 全文检索命中（课标库/教材库/教案），按来源分流
    var docHits by remember { mutableStateOf<List<DocHit>>(emptyList()) }
    LaunchedEffect(q) {
        docHits = if (q.isBlank()) emptyList() else repo.searchDocs(q)
    }
    val curricHits = docHits.filter { it.source == "curric" }
    val bookHits = docHits.filter { it.source == "books" }
    val lessonHits = docHits.filter { it.source == "lesson" }

    val examHits: List<Question> = remember(q) { if (q.isBlank()) emptyList() else repo.search(q) }
    val kwHits = remember(q) {
        if (q.isBlank()) emptyList() else repo.knowledge.filter { "${it.title} ${it.content} ${it.tags}".contains(q, ignoreCase = true) }
    }
    val inboxHits = remember(q) {
        if (q.isBlank()) emptyList() else inbox.filter { "${it.content} ${it.note}".contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            singleLine = true,
            leadingIcon = { Icon(appPainter("search"), contentDescription = null) },
            placeholder = { Text("搜索题目 / 知识 / 备课 / 课标 / 教材…") },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding().padding(horizontal = 16.dp)
        ) {
            if (q.isBlank()) {
                item { Text("输入关键词，跨题库、知识库、备课、课标库、教材库、收集箱检索。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            }
            // 题库
            item { SectionTitle("题库（${examHits.size}）") }
            if (examHits.isEmpty()) item { Text("无匹配题目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(examHits.take(20), contentType = { "exam" }) { qn ->
                Card(Modifier.fillMaxWidth().clickable {
                    practiceVm.startByQuestion(qn); nav.navigate(Screen.Practice.route)
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${qn.subject} · ${qn.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(qn.q, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                    }
                }
            }
            // 知识库
            item { SectionTitle("知识库（${kwHits.size}）") }
            if (kwHits.isEmpty()) item { Text("无匹配知识卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(kwHits.take(10), contentType = { "kw" }) { k ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(k.cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(k.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // 备课·教案（FTS）
            item { SectionTitle("备课·教案（${lessonHits.size}）") }
            if (lessonHits.isEmpty()) item { Text("无匹配教案", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(lessonHits.take(10), contentType = { "lesson" }) { d ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("lesson") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(d.title.ifBlank { "(无标题教案)" }, style = MaterialTheme.typography.bodyMedium)
                        Text(snippet(d.body, q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3)
                    }
                }
            }
            // 课标库（FTS）
            item { SectionTitle("课标库（${curricHits.size}）") }
            if (curricHits.isEmpty()) item { Text("无匹配课标", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(curricHits.take(10), contentType = { "curric" }) { d ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("curric") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(d.title.ifBlank { "(未命名课标)" }, style = MaterialTheme.typography.bodyMedium)
                        Text(snippet(d.body, q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3)
                    }
                }
            }
            // 教材库（FTS）
            item { SectionTitle("教材库（${bookHits.size}）") }
            if (bookHits.isEmpty()) item { Text("无匹配教材", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(bookHits.take(10), contentType = { "books" }) { d ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("books") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(d.title.ifBlank { "(未命名教材)" }, style = MaterialTheme.typography.bodyMedium)
                        Text(snippet(d.body, q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3)
                    }
                }
            }
            // 收集箱
            item { SectionTitle("收集箱（${inboxHits.size}）") }
            if (inboxHits.isEmpty()) item { Text("无匹配条目", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(inboxHits.take(10), contentType = { "inbox" }) { e ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("inbox") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

/**
 * 从正文截取命中上下文预览：以首个查询词定位，左右各取 radius 字，过长截断加省略号。
 */
fun snippet(body: String, q: String, radius: Int = 36): String {
    if (body.isBlank()) return ""
    val term = q.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: return body.take(120)
    val idx = body.indexOf(term, ignoreCase = true)
    return if (idx < 0) {
        body.take(120)
    } else {
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + term.length + radius).coerceAtMost(body.length)
        (if (start > 0) "…" else "") + body.substring(start, end) + (if (end < body.length) "…" else "")
    }
}
