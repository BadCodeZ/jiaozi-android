package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.Screen
import kotlinx.coroutines.delay

/**
 * 全局搜索（对齐网页端 `VIEW.search` `secBlock`）：四块——题库 / 知识库 / 备课 / 收集箱。
 * 题库命中可点击直接去练；其余块点击可跳对应模块。
 */
@Composable
fun SearchScreen(nav: NavHostController, initial: String = "") {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val lessons by repo.allLessonsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
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
    val examHits: List<Question> = remember(q) { if (q.isBlank()) emptyList() else repo.search(q) }
    val kwHits = remember(q) {
        if (q.isBlank()) emptyList() else repo.knowledge.filter { "${it.title} ${it.content} ${it.tags}".contains(q, ignoreCase = true) }
    }
    val lessonHits = remember(q) {
        if (q.isBlank()) emptyList() else lessons.filter { "${it.title} ${it.subject} ${it.chapter} ${it.content}".contains(q, ignoreCase = true) }
    }
    val inboxHits = remember(q) {
        if (q.isBlank()) emptyList() else inbox.filter { "${it.content} ${it.note}".contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(appPainter("search"), contentDescription = null) },
            placeholder = { Text("搜索题目 / 知识 / 备课 / 收集箱…") },
            textStyle = MaterialTheme.typography.bodyMedium
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)
        ) {
            if (q.isBlank()) {
                item { Text("输入关键词，跨题库、知识库、备课、收集箱检索。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) }
            }
            // 题库
            item { Text("题库（${examHits.size}）", style = MaterialTheme.typography.titleMedium) }
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
            item { Text("知识库（${kwHits.size}）", style = MaterialTheme.typography.titleMedium) }
            if (kwHits.isEmpty()) item { Text("无匹配知识卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(kwHits.take(10), contentType = { "kw" }) { k ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(k.cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(k.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // 备课
            item { Text("备课（${lessonHits.size}）", style = MaterialTheme.typography.titleMedium) }
            if (lessonHits.isEmpty()) item { Text("无匹配教案", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(lessonHits.take(10), contentType = { "lesson" }) { l ->
                Card(Modifier.fillMaxWidth().clickable { nav.navigate("lesson") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(l.title, style = MaterialTheme.typography.bodyMedium)
                        if (l.subject.isNotBlank() || l.chapter.isNotBlank())
                            Text("${l.subject} · ${l.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // 收集箱
            item { Text("收集箱（${inboxHits.size}）", style = MaterialTheme.typography.titleMedium) }
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
