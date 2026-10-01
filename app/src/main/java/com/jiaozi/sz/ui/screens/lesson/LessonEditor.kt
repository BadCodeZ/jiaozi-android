package com.jiaozi.sz.ui.screens.lesson

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GlassBackButton
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.data.model.LessonDims
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.data.model.LessonTemplate
import com.jiaozi.sz.data.model.ringCount
import com.jiaozi.sz.data.model.selfCheckAuto
import com.jiaozi.sz.ui.AppViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** 十二要素结构化编辑器 */
@Composable
internal fun LessonEditor(
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
    var title by remember { mutableStateOf(target?.title ?: initialTitle.ifBlank { "新教案" }) }
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
    // 🔴 2026-09-27（IDX7）：存模板重名策略＝拒绝重名（内联提示，不覆盖、不生成副本）
    var tplError by remember { mutableStateOf<String?>(null) }
    var delTarget by remember { mutableStateOf<LessonEntity?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    fun upd(block: (LessonFields) -> LessonFields) { f = block(f); dirty = true }

    BackHandler(enabled = true) { if (dirty) showExitConfirm = true else onBack() }

    Column(Modifier.fillMaxSize()) {
        // ── 固定顶栏（第 4 批③）：返回 + 标题 + 保存，不随内容滚动 ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            // 返回：统一为左上角圆形磨砂返回件（P2）；onClick 内的未保存拦截保持原样
            GlassBackButton(onClick = { if (dirty) showExitConfirm = true else onBack() })
            Text(if (target == null) "新建教案" else "编辑教案", style = MaterialTheme.typography.titleLarge)
            // 保存键：2026-09-21 按高保真稿改为**实色主色圆底 + 白勾**（外层 48dp 只承担触控区，
            // 内层 38dp 承担视觉圆底 —— 规避 M3 IconButton 内部强制 minimumInteractiveComponentSize 的尺寸膨胀）
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(appPainter("check"), contentDescription = "保存", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── 滚动内容区（第 4 批③）：承接剩余高度，顶栏固定不滚 ──
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        OutlinedTextField(title, { title = it; dirty = true }, label = { Text("课题 *") }, placeholder = { Text("请输入课题名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SegmentedRow("学段", LessonDims.GRADE, f.grade) { v -> upd { it.copy(grade = v) } }
        SegmentedRow("学科", LessonDims.SUBJ, disc) { disc = it; dirty = true }
        SegmentedRow("课型", LessonDims.TYPE, f.type) { v -> upd { it.copy(type = v) } }

        // 完成度（2026-09-21 按高保真稿：左**环形进度 + 环心 `ring/12`**，右侧「结构化完成度」+ 加粗进度条）
        val ring = ringCount(f)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { ring / 12f },
                        modifier = Modifier.size(46.dp),
                        color = AppColors.blue,
                        trackColor = AppColors.trackGray,
                        strokeWidth = 4.dp
                    )
                    Text("$ring/12", style = MaterialTheme.typography.labelSmall, color = AppColors.blue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("结构化完成度", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary)
                    LinearProgressIndicator(
                        progress = { ring / 12f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                        color = AppColors.blue,
                        trackColor = AppColors.trackGray
                    )
                }
            }
        }

        ZoneCard("骨 · 目标与依据", itemsCount = 6) {
            LessonField("课标依据", "锚定核心素养 + 条目号 / 原文", 2, f.curric) { v -> upd { it.copy(curric = v) } }
            LessonField("教材分析", "是什么 / 从哪来 / 往哪去 / 核心矛盾 / 独特价值", 3, f.textbook) { v -> upd { it.copy(textbook = v) } }
            LessonField("学情分析", "起点 / 认知特点 / 困难兴趣点", 2, f.student) { v -> upd { it.copy(student = v) } }
            LessonField("教学目标", "学科化·可观测行为动词", 2, f.objective) { v -> upd { it.copy(objective = v) } }
            LessonField("重难点·重点", "本课最核心的达成点", 1, f.keyPoints.focus) { v -> upd { it.copy(keyPoints = it.keyPoints.copy(focus = v)) } }
            LessonField("重难点·难点", "学生最难突破处，含成因", 2, f.keyPoints.difficult) { v -> upd { it.copy(keyPoints = it.keyPoints.copy(difficult = v)) } }
        }
        ZoneCard("肉 · 过程与活动", itemsCount = 8) {
            LessonField("情境导入", "真实性四检验：去掉情境是否仍可成立", 2, f.context) { v -> upd { it.copy(context = v) } }
            LessonField("教学过程", "每行一个环节，用 → 写设计意图", 4, f.processText) { v -> upd { it.copy(processText = v) } }
            LessonField("课堂提问链", "≥3 条，标注层级 L1–L5 与追问", 3, f.questionsText) { v -> upd { it.copy(questionsText = v) } }
            LessonField("分层任务·基础", "全体可达成的保底任务", 1, f.diff.basic) { v -> upd { it.copy(diff = it.diff.copy(basic = v)) } }
            LessonField("分层任务·进阶", "多数学生可挑战的任务", 1, f.diff.mid) { v -> upd { it.copy(diff = it.diff.copy(mid = v)) } }
            LessonField("分层任务·挑战", "学优生拓展任务", 1, f.diff.top) { v -> upd { it.copy(diff = it.diff.copy(top = v)) } }
            LessonField("教学方法", "如 欣赏·探究·创作·展评", 1, f.method) { v -> upd { it.copy(method = v) } }
            LessonField("教学准备", "教具 / 素材 / 学具", 1, f.prep) { v -> upd { it.copy(prep = v) } }
        }
        ZoneCard("皮 · 呈现与反思", itemsCount = 4) {
            LessonField("板书设计", "提纲 / 图表公式 / 概念网络", 2, f.blackboard) { v -> upd { it.copy(blackboard = v) } }
            SegmentedRow("板书三型", LessonDims.BLACKBOARD, f.blackboardType) { v -> upd { it.copy(blackboardType = v) } }
            LessonField("分层作业", "基础 + 拓展，可操作", 2, f.homework) { v -> upd { it.copy(homework = v) } }
            LessonField("教学反思", "目标达成 / 学情 / 改进动作，写具体", 2, f.reflect) { v -> upd { it.copy(reflect = v) } }
        }

        // 专家自检清单（自动项）
        val checks = selfCheckAuto(f)
        Card(Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
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
            OutlinedButton(onClick = { tplName = title.ifBlank { "我的模板" }; tplError = null; showSaveTpl = true }, modifier = Modifier.weight(1f)) { Icon(appPainter("star"), contentDescription = null); Text(" 存为模板") }
        }
        if (target != null) {
            // 🔴 2026-09-21 按高保真稿：删除按钮文案与图标改为语义红（c.danger），与全局 trash 图标色一致
            OutlinedButton(onClick = { delTarget = target }, modifier = Modifier.fillMaxWidth()) { Icon(appPainter("trash"), contentDescription = null, tint = AppColors.danger); Text(" 删除此教案", color = AppColors.danger) }
        }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("未保存修改？") },
            text = { Text("当前教案尚未保存，退出将丢失本次修改。") },
            confirmButton = { TextButton(onClick = { showExitConfirm = false; onBack() }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("继续编辑") } }
        )
    }

    if (showSaveTpl) {
        AlertDialog(
            onDismissRequest = { showSaveTpl = false },
            title = { Text("存为模板") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tplName,
                        onValueChange = { tplName = it; tplError = null },
                        label = { Text("模板名称") },
                        singleLine = true,
                        isError = tplError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tplError != null) {
                        Text(tplError!!, color = AppColors.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = tplName.trim()
                    if (name.isBlank()) {
                        tplError = "模板名称不能为空"
                        return@TextButton
                    }
                    if (appVm.lessonTemplates.value.any { it.name == name }) {
                        tplError = "已存在同名模板，请更换名称"
                        return@TextButton
                    }
                    val id = "T${System.currentTimeMillis()}"
                    appVm.saveLessonTemplate(LessonTemplate(id = id, name = name, grade = f.grade, type = f.type, fields = f.copy(disc = disc)))
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
