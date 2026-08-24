package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

/** 科一（综合素质）教案模板 */
private val LESSON_TPL_K1 = """
    一、教学目标
    1. 职业理念（教育观 / 学生观 / 教师观）
    2. 教育法律法规（权利义务 / 责任事故）
    3. 教师职业道德（三爱两人一终身）
    4. 文化素养（历史 / 科技 / 文学 / 艺术）
    5. 基本能力（阅读 / 逻辑 / 信息处理 / 写作）

    二、教学重难点
    重点：
    难点：

    三、教学过程
    1. 导入（5 分钟）：
    2. 新授（20 分钟）：
    3. 巩固练习（10 分钟）：
    4. 小结与作业（5 分钟）：

    四、板书设计
""".trimIndent()

/** 科二（教育知识与能力）教案模板 */
private val LESSON_TPL_K2 = """
    一、教学目标
    1. 教育基础知识与基本原理
    2. 中学教学 / 德育 / 班级管理
    3. 中学生学习心理与发展心理
    4. 学科教学设计能力

    二、教学重难点
    重点：
    难点：

    三、教学过程
    1. 导入（5 分钟）：
    2. 新授（20 分钟）：
    3. 巩固练习（10 分钟）：
    4. 小结与作业（5 分钟）：

    四、板书设计
""".trimIndent()

/** 科三（学科教学）教案模板，按当前学科注入标题 */
private fun lessonTplK3(disc: String) = """
    一、教学内容分析（学科：$disc）
    1. 课程标准要求
    2. 教材地位与作用

    二、教学目标
    1. 知识与技能：
    2. 过程与方法：
    3. 情感态度与价值观：

    三、教学重难点
    重点：
    难点：

    四、教学过程
    1. 导入（5 分钟）：
    2. 新授（20 分钟）：
    3. 巩固练习（10 分钟）：
    4. 小结与作业（5 分钟）：

    五、板书设计
""".trimIndent()

@Composable
fun LessonScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val lessons by appVm.repo.allLessonsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LessonEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    fun reset() {
        editing = null; title = ""; subject = ""; chapter = ""; content = ""; showForm = false
    }
    fun startEdit(l: LessonEntity) {
        editing = l; title = l.title; subject = l.subject; chapter = l.chapter; content = l.content; showForm = true
    }
    fun save() {
        if (title.isBlank()) return
        val now = System.currentTimeMillis()
        val ent = LessonEntity(
            id = editing?.id ?: "L$now",
            title = title.trim(),
            subject = subject.trim(),
            chapter = chapter.trim(),
            content = content.trim(),
            createdAt = editing?.createdAt ?: now,
            _mt = now
        )
        appVm.viewModelScope.launch { appVm.repo.upsertLesson(ent) }
        reset()
    }
    fun applyTemplate(subj: String) {
        title = title.ifBlank { "新备课教案" }
        subject = subject.ifBlank { subj }
        content = if (content.isBlank()) {
            when (subj) {
                "科一" -> LESSON_TPL_K1
                "科二" -> LESSON_TPL_K2
                else -> lessonTplK3(disc)
            }
        } else content
        showForm = true
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Button(onClick = { if (showForm) reset() else { reset(); showForm = true } }) {
                Icon(appPainter("plus"), contentDescription = null)
                Text(if (showForm) "收起" else "新建")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = false, onClick = { applyTemplate("科一") }, label = { Text("科一模板") })
                FilterChip(selected = false, onClick = { applyTemplate("科二") }, label = { Text("科二模板") })
                FilterChip(selected = false, onClick = { applyTemplate("科三") }, label = { Text("科三模板") })
            }
        }

        if (showForm) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("教案标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(subject, { subject = it }, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(chapter, { chapter = it }, label = { Text("章节") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(content, { content = it }, label = { Text("教案内容") }, modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 10)
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Button(onClick = { save() }) { Text("保存") }
                    }
                }
            }
        }

        if (lessons.isEmpty() && !showForm) {
            Text("还没有教案。点「新建」记录一节备考课的设计。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 92.dp)) {
            items(lessons, contentType = { "lesson" }) { l ->
                Card(Modifier.fillMaxWidth().clickable { startEdit(l) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.Top) {
                        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                            Text(l.title, style = MaterialTheme.typography.titleMedium)
                            if (l.subject.isNotBlank() || l.chapter.isNotBlank())
                                Text("${l.subject} · ${l.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            if (l.content.isNotBlank())
                                Text(l.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3)
                        }
                        IconButton(onClick = {
                            appVm.viewModelScope.launch { appVm.repo.deleteLesson(l.id) }
                        }) { Icon(appPainter("trash"), contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
