package com.jiaozi.sz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.CurricEntity
import com.jiaozi.sz.data.model.LessonDims
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.ItemCard
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatTile
import com.jiaozi.sz.ui.components.appPainter
import kotlinx.coroutines.launch

/**
 * 课标库：导入 PDF / TXT / MD 课标原文，录入学段·学科·主题后结构化索引，
 * 可随备课信封（curric 集合）跨端同步。全文存于 CurricEntity.text。
 * 重构（V2.60）：渐变 Hero 头部 + 统计格 + 关于模块同款 surfaceVariant 项卡。
 */
@Composable
fun CurricScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = appVm.viewModelScope
    val items by appVm.repo.allCurricFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var detail by remember { mutableStateOf<CurricEntity?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }

    val total = items.size
    val chars = items.sumOf { it.text.length }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val txt = appVm.repo.readFileText(ctx, uri)
        if (txt != null) importText = txt
        else android.widget.Toast.makeText(ctx, "无法读取文件内容：PDF 需 Android 14+，或格式不支持；请改用 txt/md", android.widget.Toast.LENGTH_LONG).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroHeader(
            title = "课标库",
            subtitle = "导入课标原文，结构化索引，随备课信封同步",
            icon = appPainter("text"),
            action = {
                Button(onClick = {
                    picker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown"))
                }) {
                    Icon(appPainter("upload"), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 导入")
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("课标条目", "$total", Modifier.weight(1f))
            StatTile("收录字数", "$chars", Modifier.weight(1f))
        }

        SectionTitle("课标原文")
        if (items.isEmpty()) {
            EmptyHint("tree", "还没有课标", "点右上「导入」加入一份课标原文，可随备课信封跨端同步。")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { e ->
                ItemCard(
                    icon = appPainter("text"),
                    title = e.topic.ifBlank { "(未命名主题)" },
                    subtitle = "${e.grade} · ${e.subject.ifBlank { "未分类" }}",
                    onClick = { detail = e }
                ) {
                    IconButton(onClick = { scope.launch { appVm.repo.deleteCurric(e.id) } }) {
                        Icon(appPainter("trash"), contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (detail != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(detail!!.topic.ifBlank { "(未命名主题)" }) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${detail!!.grade} · ${detail!!.subject.ifBlank { "未分类" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(detail!!.text.ifBlank { "(空)" }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } }
        )
    }

    if (importText != null) {
        var grade by remember(importText) { mutableStateOf(LessonDims.GRADE.first()) }
        var subject by remember(importText) { mutableStateOf("") }
        var topic by remember(importText) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("录入课标信息") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已读取原文 ${importText!!.length} 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    SegmentedRow("学段", LessonDims.GRADE, grade) { grade = it }
                    OutlinedTextField(subject, { subject = it }, label = { Text("学科") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(topic, { topic = it }, label = { Text("主题 / 条目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (topic.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            appVm.repo.upsertCurric(
                                CurricEntity(id = "C$now", grade = grade, subject = subject.trim(), topic = topic.trim(), text = importText ?: "", _mt = now)
                            )
                        }
                        importText = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { importText = null }) { Text("取消") } }
        )
    }
}
