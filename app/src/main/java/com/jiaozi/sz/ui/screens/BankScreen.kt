package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun BankScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val practiceVm: PracticeViewModel = LocalPracticeVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(150); debounced = query } // 150ms 防抖（防回归项）

    val results: List<Question> = remember(debounced) {
        if (debounced.isNotBlank()) repo.search(debounced) else emptyList()
    }

    val onQuestionClick: (Question) -> Unit = { q ->
        practiceVm.startByQuestion(q)
        nav.navigate(Screen.Practice.route)
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索题目 / 解析（最多 200 条）") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (debounced.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("找到 ${results.size} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                OutlinedButton(onClick = { query = ""; debounced = "" }) {
                    Text("返回大纲")
                }
            }
            LazyColumn(Modifier.padding(horizontal = 16.dp).padding(bottom = 92.dp)) {
                items(results, contentType = { "q" }) { q -> QuestionRow(q, onClick = { onQuestionClick(q) }) }
            }
        } else {
            // 浏览：按科目/章节（折叠 + LazyColumn）
            LazyColumn(Modifier.padding(16.dp).padding(bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("题库浏览", style = MaterialTheme.typography.titleMedium) }
                item { Text("${repo.bank.exam.size} 题 · 科三当前学科：$disc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                item { SpacerFill(8.dp) }

                val subjects = listOf("科一", "科二", "科三")
                subjects.forEach { subj ->
                    item(key = "subj-$subj") {
                        SubjectTree(
                            subj = subj,
                            repo = repo,
                            disc = disc,
                            onSectionClick = { chapter, section ->
                                practiceVm.startChapter(subj, chapter, section, 30)
                                nav.navigate(Screen.Practice.route)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 科三章节显示名：按当前学科加后缀（如「一、学科知识(语文)」），切换学科即变，与网页端一致 */
private fun chapterLabel(subj: String, baseName: String, disc: String): String =
    if (subj == "科三" && disc.isNotBlank()) "$baseName($disc)" else baseName

@Composable
private fun SubjectTree(
    subj: String,
    repo: com.jiaozi.sz.data.Repository,
    disc: String,
    onSectionClick: (String, String?) -> Unit
) {
    var expanded by remember(subj) { mutableStateOf(false) }
    val syllabus = repo.syllabus.find { it.subject == subj }
    val chapters = syllabus?.chapters ?: emptyList()
    val total = repo.countBySubject(subj, if (subj == "科三") disc else null)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$subj · ${syllabus?.name?.let { if (subj == "科三") it.replace("美术", disc) else it } ?: ""}", style = MaterialTheme.typography.titleSmall)
                    Text("$total 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                chapters.forEach { ch ->
                    ChapterNode(
                        subj = subj,
                        chapter = ch,
                        repo = repo,
                        disc = disc,
                        onSectionClick = onSectionClick
                    )
                    HorizontalDivider(Modifier.padding(start = 24.dp, end = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ChapterNode(
    subj: String,
    chapter: com.jiaozi.sz.data.model.SyllabusChapter,
    repo: com.jiaozi.sz.data.Repository,
    disc: String,
    onSectionClick: (String, String?) -> Unit
) {
    var expanded by remember(chapter.name, disc) { mutableStateOf(false) }
    val chCount = repo.countChapter(subj, chapter.name, null, if (subj == "科三") disc else null)
    val label = chapterLabel(subj, chapter.name, disc)

    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$chCount 题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (expanded) {
            chapter.sections.forEach { sec ->
                val secCount = repo.countChapter(subj, chapter.name, sec, if (subj == "科三") disc else null)
                if (secCount > 0) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSectionClick(chapter.name, sec) }.padding(start = 44.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sec, style = MaterialTheme.typography.bodySmall)
                        Text("$secCount 题 · 去练", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionRow(q: Question, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${q.subject} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(q.q, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
    }
}

@Composable
private fun SpacerFill(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}
