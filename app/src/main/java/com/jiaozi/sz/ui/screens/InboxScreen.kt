package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.StatTile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
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
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.InboxEntity
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm

@Composable
fun InboxScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val items by appVm.repo.allInboxFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    var convertTarget by remember { mutableStateOf<InboxEntity?>(null) }
    var convSubject by remember { mutableStateOf("科三") }
    var convChapter by remember { mutableStateOf("收集箱") }
    var showForm by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("text") }
    var content by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val types = listOf("text" to "文本", "link" to "链接", "question" to "题干")

    fun reset() { content = ""; note = ""; type = "text"; showForm = false }
    fun save() {
        if (content.isBlank()) return
        val now = System.currentTimeMillis()
        val ent = InboxEntity(id = "I$now", type = type, content = content.trim(), note = note.trim(), createdAt = now, _mt = now)
        appVm.viewModelScope.launch { appVm.repo.upsertInbox(ent) }
        reset()
    }

    val qCount = items.count { it.type == "question" }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroHeader(
            title = "收集箱",
            subtitle = "随手收集灵感、链接、好题，可同步网页端",
            icon = appPainter("inbox"),
            action = {
                Button(onClick = { if (showForm) reset() else { reset(); showForm = true } }) {
                    Icon(appPainter("plus"), contentDescription = null)
                    Text(if (showForm) "收起" else "添加")
                }
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("收集条目", "${items.size}", Modifier.weight(1f))
            StatTile("可转题目", "$qCount", Modifier.weight(1f))
        }

        if (showForm) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { (t, label) -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) }) }
                    }
                    if (type == "question") Text(
                        "支持格式：题干|~|选项(A.x B.x)|~|答案（不填则整段作主观题，可在「练习」作答）",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(content, { content = it }, label = { Text(if (type == "link") "链接地址" else "内容 *") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 6)
                    OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), Arrangement.End) { Button(onClick = { save() }) { Text("保存") } }
                }
            }
        }

        SectionTitle("收集内容")
        if (items.isEmpty() && !showForm) {
            EmptyHint("inbox", "收集箱是空的", "通勤看到好资料，随手存进来，备考时再整理。")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
            items(items, contentType = { "inbox" }) { e ->
                val typeLabel = types.find { it.first == e.type }?.second ?: e.type
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.Top) {
                        Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (e.type == "link") Icon(appPainter("link"), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(e.content, style = MaterialTheme.typography.bodyMedium)
                            if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            IconButton(onClick = { convSubject = "科三"; convChapter = "收集箱"; convertTarget = e }) {
                                Icon(appPainter("play"), contentDescription = "转为题目", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { appVm.viewModelScope.launch { appVm.repo.deleteInbox(e.id) } }) {
                                Icon(appPainter("trash"), contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (convertTarget != null) {
            val e = convertTarget!!
            AlertDialog(
                onDismissRequest = { convertTarget = null },
                title = { Text("转为题目 · 选择归属") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("科目", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("科一", "科二", "科三").forEach { s ->
                                FilterChip(selected = convSubject == s, onClick = { convSubject = s }, label = { Text(s) })
                            }
                        }
                        if (convSubject == "科三") {
                            Text("学科：$disc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = convChapter,
                            onValueChange = { convChapter = it },
                            label = { Text("章节（可留空，默认「收集箱」）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        appVm.viewModelScope.launch {
                            appVm.repo.inboxToQuestion(e, convSubject, convChapter, if (convSubject == "科三") disc else null)
                            Toast.makeText(ctx, "已转为${convSubject}题目，可在「题库」练习", Toast.LENGTH_SHORT).show()
                        }
                        convertTarget = null
                    }) { Text("确认转入") }
                },
                dismissButton = { Button(onClick = { convertTarget = null }) { Text("取消") } }
            )
        }
    }
}
