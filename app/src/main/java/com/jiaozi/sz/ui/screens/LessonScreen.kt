package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.EmptyHint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.data.model.BUILTIN_TEMPLATES
import com.jiaozi.sz.data.model.LessonDims
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.data.model.LessonTemplate
import com.jiaozi.sz.data.model.ringCount
import com.jiaozi.sz.data.model.selfCheckAuto
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 备课模块（对标网页端「备课中枢 + 十二要素结构化编辑器 + 模板库」）。
 * 内部视图状态机：hub（中枢）/ edit（结构化编辑器）/ templates（模板库）。
 */
@Composable
fun LessonScreen(nav: NavHostController) {
    var view by remember { mutableStateOf("hub") }
    // 编辑器目标：null = 新建；非 null = 编辑既有
    var editTarget by remember { mutableStateOf<LessonEntity?>(null) }
    // 模板库应用：非空表示用模板预填后进入编辑
    var tplSeed by remember { mutableStateOf<LessonFields?>(null) }

    when (view) {
        "edit" -> LessonEditor(
            appVm = LocalAppVm.current,
            target = editTarget,
            seed = tplSeed,
            onBack = { view = "hub"; editTarget = null; tplSeed = null },
            onSaved = { view = "hub"; editTarget = null; tplSeed = null }
        )
        "templates" -> LessonTemplateLibrary(
            appVm = LocalAppVm.current,
            onBack = { view = "hub" },
            onUse = { seed -> tplSeed = seed; editTarget = null; view = "edit" }
        )
        else -> LessonHub(
            appVm = LocalAppVm.current,
            onNew = { editTarget = null; tplSeed = null; view = "edit" },
            onEdit = { l -> editTarget = l; tplSeed = null; view = "edit" },
            onTemplates = { view = "templates" },
            onCurric = { nav.navigate("curric") },
            onBooks = { nav.navigate("books") }
        )
    }
}

/** 备课中枢：快捷操作 + 概览 + feat 网格 + 最近教案 */
@Composable
private fun LessonHub(
    appVm: AppViewModel,
    onNew: () -> Unit,
    onEdit: (LessonEntity) -> Unit,
    onTemplates: () -> Unit,
    onCurric: () -> Unit,
    onBooks: () -> Unit
) {
    val lessons by appVm.repo.allLessonsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val templates by appVm.lessonTemplates.collectAsStateWithLifecycle()
    val scope = appVm.viewModelScope

    // 最近编辑的教案（用于「继续编辑」快捷入口）
    val lastLesson = lessons.maxByOrNull { it._mt }

    // 全局平均完成度
    val avgRing = if (lessons.isEmpty()) 0 else {
        lessons.mapNotNull { l ->
            val (f, _) = appVm.repo.parseLessonData(l.data)
            ringCount(f).takeIf { it > 0 }
        }.average().toInt()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── 快捷操作行 ──
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            if (lastLesson != null) {
                Card(
                    Modifier.weight(1f).clickable { onEdit(lastLesson) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("继续编辑", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            lastLesson.title.ifBlank { "(未命名)" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Card(
                Modifier.weight(if (lastLesson != null) 1f else 1f).clickable { onNew() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("新建教案", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("十二要素结构化模板", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // ── 概览统计卡（含均完成度进度条）──
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    StatCell("教案", lessons.size.toString())
                    StatCell("模板", templates.size.toString())
                    StatCell("均完成度", "$avgRing/12")
                }
                if (lessons.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { avgRing / 12f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }
        }

        // ── 功能入口网格 ──
        Text("功能入口", style = MaterialTheme.typography.titleMedium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listOf(
                Feat("结构化编辑器", "骨/肉/皮十二要素，实时完成度", "school", false),
                Feat("模板库", "内置骨架 + 我的模板", "bars", false),
                Feat("课标库", "课标原文结构化索引", "tree", false),
                Feat("教材库", "教材原文结构化索引", "book", false)
            )) { f ->
                FeatCard(f, onClick = {
                    when (f.icon) {
                        "school" -> onNew()
                        "tree" -> onCurric()
                        "book" -> onBooks()
                        else -> onTemplates()
                    }
                })
            }
        }

        // ── 最近教案列表（倒序，最近编辑在前）──
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("最近教案（${lessons.size}）", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onNew) { Icon(appPainter("plus"), contentDescription = null, modifier = Modifier.size(16.dp)); Text(" 新建") }
        }

        if (lessons.isEmpty()) {
            EmptyHint("school", "还没有教案", "点「新建」用十二要素结构化模板记录一节备考课的设计。")
        }
        lessons.sortedByDescending { it._mt }.forEach { l ->
            val (fields, _) = appVm.repo.parseLessonData(l.data)
            val meta = "${fields.grade} · ${l.subject.ifBlank { fields.disc }.ifBlank { "未结构化" }} · ${fields.type}"
            val ring = ringCount(fields)
            val cardColor = when {
                ring >= 10 -> MaterialTheme.colorScheme.primaryContainer
                ring >= 5 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            Card(Modifier.fillMaxWidth().clickable { onEdit(l) }, colors = CardDefaults.cardColors(containerColor = cardColor)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.Top) {
                    Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                        Text(l.title.ifBlank { "(未命名)" }, style = MaterialTheme.typography.titleMedium)
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("完成度 $ring/12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(
                                progress = { ring / 12f },
                                modifier = Modifier.width(80.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        }
                    }
                    IconButton(onClick = { scope.launch { appVm.repo.deleteLesson(l.id) } }) {
                        Icon(appPainter("trash"), contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
private data class Feat(val title: String, val desc: String, val icon: String, val soon: Boolean)

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun FeatCard(f: Feat, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(132.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(appPainter(f.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(0.3f))
            Text(f.title, style = MaterialTheme.typography.titleMedium)
            Text(f.desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            if (f.soon) Text("即将上线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** 十二要素结构化编辑器 */
@Composable
private fun LessonEditor(
    appVm: AppViewModel,
    target: LessonEntity?,
    seed: LessonFields?,
    chapter: String = "",
    initialTitle: String = "",
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = appVm.viewModelScope
    val existing = remember(target) {
        target?.let { appVm.repo.parseLessonData(it.data) } ?: (LessonFields() to JsonObject(emptyMap()))
    }
    var title by remember { mutableStateOf(target?.title ?: initialTitle.ifBlank { seed?.let { "新教案" } ?: "" }) }
    var f by remember {
        mutableStateOf(
            seed ?: run {
                val (ef, _) = existing
                // 旧版纯文本教案（data 为空但有 content）迁入 body，避免历史数据丢失
                if (target != null && target.data.isBlank() && target.content.isNotBlank()) ef.copy(body = target.content) else ef
            }
        )
    }
    var extra by remember { mutableStateOf(existing.second) }
    var disc by remember { mutableStateOf(target?.subject?.ifBlank { existing.first.disc }?.takeIf { it.isNotBlank() } ?: existing.first.disc.ifBlank { "美术" }) }
    var showSaveTpl by remember { mutableStateOf(false) }
    var tplName by remember { mutableStateOf("") }
    var delTarget by remember { mutableStateOf<LessonEntity?>(null) }

    fun upd(block: (LessonFields) -> LessonFields) { f = block(f) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(appPainter("back"), contentDescription = "返回") }
            Text(if (target == null) "新建教案" else "编辑教案", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = {
                if (title.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val ent = LessonEntity(
                        id = target?.id ?: "L$now",
                        title = title.trim(),
                        subject = disc,
                        chapter = chapter,
                        data = appVm.repo.serializeLessonData(f.copy(disc = disc), extra),
                        createdAt = target?.createdAt ?: now,
                        _mt = now
                    )
                    scope.launch { appVm.repo.upsertLesson(ent) }
                    onSaved()
                }
            }) { Icon(appPainter("check"), contentDescription = "保存", tint = MaterialTheme.colorScheme.primary) }
        }

        OutlinedTextField(title, { title = it }, label = { Text("课题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SegmentedRow("学段", LessonDims.GRADE, f.grade) { v -> upd { it.copy(grade = v) } }
        SegmentedRow("学科", LessonDims.SUBJ, disc) { disc = it }
        SegmentedRow("课型", LessonDims.TYPE, f.type) { v -> upd { it.copy(type = v) } }

        // 完成度环
        val ring = ringCount(f)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("结构化完成度", style = MaterialTheme.typography.labelMedium)
                    Text("$ring/12", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { ring / 12f }, modifier = Modifier.fillMaxWidth())
            }
        }

        ZoneCard("骨 · 目标与依据") {
            LessonField("课标依据", "锚定核心素养 + 条目号 / 原文", 2, f.curric) { v -> upd { it.copy(curric = v) } }
            LessonField("教材分析", "是什么 / 从哪来 / 往哪去 / 核心矛盾 / 独特价值", 3, f.textbook) { v -> upd { it.copy(textbook = v) } }
            LessonField("学情分析", "起点 / 认知特点 / 困难兴趣点", 2, f.student) { v -> upd { it.copy(student = v) } }
            LessonField("教学目标", "学科化·可观测行为动词", 2, f.objective) { v -> upd { it.copy(objective = v) } }
            LessonField("重难点·重点", "本课最核心的达成点", 1, f.keyPoints.focus) { v -> upd { it.copy(keyPoints = it.keyPoints.copy(focus = v)) } }
            LessonField("重难点·难点", "学生最难突破处，含成因", 2, f.keyPoints.difficult) { v -> upd { it.copy(keyPoints = it.keyPoints.copy(difficult = v)) } }
        }
        ZoneCard("肉 · 过程与活动") {
            LessonField("情境导入", "真实性四检验：去掉情境是否仍可成立", 2, f.context) { v -> upd { it.copy(context = v) } }
            LessonField("教学过程", "每行一个环节，用 → 写设计意图", 4, f.processText) { v -> upd { it.copy(processText = v) } }
            LessonField("课堂提问链", "≥3 条，标注层级 L1–L5 与追问", 3, f.questionsText) { v -> upd { it.copy(questionsText = v) } }
            LessonField("分层任务·基础", "全体可达成的保底任务", 1, f.diff.basic) { v -> upd { it.copy(diff = it.diff.copy(basic = v)) } }
            LessonField("分层任务·进阶", "多数学生可挑战的任务", 1, f.diff.mid) { v -> upd { it.copy(diff = it.diff.copy(mid = v)) } }
            LessonField("分层任务·挑战", "学优生拓展任务", 1, f.diff.top) { v -> upd { it.copy(diff = it.diff.copy(top = v)) } }
            LessonField("教学方法", "如 欣赏·探究·创作·展评", 1, f.method) { v -> upd { it.copy(method = v) } }
            LessonField("教学准备", "教具 / 素材 / 学具", 1, f.prep) { v -> upd { it.copy(prep = v) } }
        }
        ZoneCard("皮 · 呈现与反思") {
            LessonField("板书设计", "提纲 / 图表公式 / 概念网络", 2, f.blackboard) { v -> upd { it.copy(blackboard = v) } }
            SegmentedRow("板书三型", LessonDims.BLACKBOARD, f.blackboardType) { v -> upd { it.copy(blackboardType = v) } }
            LessonField("分层作业", "基础 + 拓展，可操作", 2, f.homework) { v -> upd { it.copy(homework = v) } }
            LessonField("教学反思", "目标达成 / 学情 / 改进动作，写具体", 2, f.reflect) { v -> upd { it.copy(reflect = v) } }
        }

        // 专家自检清单（自动项）
        val checks = selfCheckAuto(f)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("专家自检清单（自动检测）", style = MaterialTheme.typography.labelLarge)
                checks.forEach { (name, ok) ->
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterVertically) {
                        Icon(appPainter(if (ok) "check" else "close"), contentDescription = null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (title.isBlank()) return@Button
                val now = System.currentTimeMillis()
                val ent = LessonEntity(
                    id = target?.id ?: "L$now",
                    title = title.trim(),
                    subject = disc,
                    chapter = "",
                    data = appVm.repo.serializeLessonData(f.copy(disc = disc), extra),
                    createdAt = target?.createdAt ?: now,
                    _mt = now
                )
                scope.launch { appVm.repo.upsertLesson(ent) }
                onSaved()
            }, modifier = Modifier.weight(1f)) { Icon(appPainter("check"), contentDescription = null); Text(" 保存教案") }
            OutlinedButton(onClick = { tplName = title.ifBlank { "我的模板" }; showSaveTpl = true }, modifier = Modifier.weight(1f)) { Icon(appPainter("star"), contentDescription = null); Text(" 存为模板") }
        }
        if (target != null) {
            OutlinedButton(onClick = { delTarget = target }, modifier = Modifier.fillMaxWidth()) { Icon(appPainter("trash"), contentDescription = null); Text(" 删除此教案") }
        }
    }

    if (showSaveTpl) {
        AlertDialog(
            onDismissRequest = { showSaveTpl = false },
            title = { Text("存为模板") },
            text = {
                OutlinedTextField(tplName, { tplName = it }, label = { Text("模板名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tplName.isNotBlank()) {
                        val id = "T${System.currentTimeMillis()}"
                        appVm.saveLessonTemplate(LessonTemplate(id = id, name = tplName.trim(), grade = f.grade, type = f.type, fields = f.copy(disc = disc)))
                    }
                    showSaveTpl = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveTpl = false }) { Text("取消") } }
        )
    }
    if (delTarget != null) {
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("删除教案") },
            text = { Text("确定删除《${delTarget!!.title.ifBlank { "(未命名)" }}》？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { scope.launch { appVm.repo.deleteLesson(delTarget!!.id) }; delTarget = null; onBack() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("取消") } }
        )
    }
}

/** 模板库：内置骨架 + 我的模板 */
@Composable
private fun LessonTemplateLibrary(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onUse: (LessonFields) -> Unit
) {
    val templates by appVm.lessonTemplates.collectAsStateWithLifecycle()
    val scope = appVm.viewModelScope
    var delId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(appPainter("back"), contentDescription = "返回") }
            Text("模板库", style = MaterialTheme.typography.titleLarge)
        }

        Text("内置骨架模板", style = MaterialTheme.typography.titleMedium)
        BUILTIN_TEMPLATES.forEach { (type, bone, meat) ->
            Card(Modifier.fillMaxWidth().clickable { onUse(LessonFields(type = type, processText = meat)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), Arrangement.spacedBy(2.dp)) {
                        Text("$type · $bone", style = MaterialTheme.typography.titleMedium)
                        Text(meat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(appPainter("edit"), contentDescription = "用此骨架", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text("我的模板（${templates.size}）", style = MaterialTheme.typography.titleMedium)
        if (templates.isEmpty()) Text("在编辑器中点「存为模板」即可把当前教案存为可复用模板。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        templates.forEach { t ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onUse(t.fields.copy(grade = t.grade, type = t.type)) }, Arrangement.spacedBy(2.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text("${t.grade} · ${t.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = { delId = t.id }) { Icon(appPainter("trash"), contentDescription = "删除模板", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (delId != null) {
        AlertDialog(
            onDismissRequest = { delId = null },
            title = { Text("删除模板") },
            text = { Text("确定删除该模板？") },
            confirmButton = { TextButton(onClick = { appVm.deleteLessonTemplate(delId!!); delId = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { delId = null }) { Text("取消") } }
        )
    }
}

// —— 编辑器小组件 ——

@Composable
fun SegmentedRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt ->
                FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(opt) })
            }
        }
    }
}

@Composable
private fun ZoneCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            content()
        }
    }
}

@Composable
private fun LessonField(label: String, hint: String, rows: Int, value: String, onValue: (String) -> Unit) {
    // 十二要素「行编辑」：默认折叠为内联行（标签 + 内容预览），点按展开编辑，提升 17 字段扫描性。
    // 已有内容的字段首次进入自动展开，避免历史教案内容被藏住；空白字段默认折叠，编辑器更紧凑。
    var expanded by remember(value) { mutableStateOf(value.isNotBlank()) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // 点按收起仅绑定在标题行，避免展开后点文本框编辑误触发折叠（文本框区域只负责编辑）
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (expanded) "收起 ▴" else "编辑 ▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (value.isNotBlank()) {
                Text(
                    value.lines().first().let { if (it.length > 36) it.take(36) + "…" else it },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                OutlinedTextField(
                    value = value, onValueChange = onValue, placeholder = { Text(hint) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    minLines = if (rows > 1) rows else 1,
                    maxLines = if (rows > 1) rows + 2 else 1,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
