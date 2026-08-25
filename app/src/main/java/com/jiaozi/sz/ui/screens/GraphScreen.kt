package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.data.model.SyllabusChapter
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 知识关联图谱（放射可视化）：中心 hub = 科目；内环 = 章节节点（按题数）；外环 = 知识卡节点。
 * 列表区（章节题数 + 知识卡）提供速览与交互；放射图用于一眼看清「科目 → 章节 → 知识」的结构。
 */
@Composable
fun GraphScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val repo = appVm.repo
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val progress by appVm.progressMap.collectAsStateWithLifecycle()
    val subjects = listOf("科一", "科二", "科三")
    var subj by remember { mutableStateOf("科一") }
    // 中心枢纽：点选章节后，下方展开该章的「掌握度 + 关联知识卡 + 去练该章」详情
    var selChapter by remember { mutableStateOf<String?>(null) }

    val chapters = repo.syllabus.find { it.subject == subj }?.chapters ?: emptyList()
    val chapterCounts = remember(chapters, disc) {
        chapters.associate { it.name to repo.countChapter(subj, it.name, null, if (subj == "科三") disc else null) }
    }
    // R3 掌握度热力：按 qid 聚合每章 right/wrong，得出正确率（heat: -1=未练，0..1=正确率）
    val chapterAcc = remember(subj, disc, chapters, progress) {
        chapters.associate { ch ->
            val qs = repo.bank.exam.filter {
                it.subject == subj && it.chapter == ch.name && (subj != "科三" || it.disc == disc)
            }
            var r = 0; var w = 0
            qs.forEach { q -> progress[q.id]?.let { e -> r += e.right; w += e.wrong } }
            ch.name to (r to w)
        }
    }
    // 真实关联：按当前科目筛选知识卡（tags 含科目/章节名；R4 放宽：标题/正文命中章节名也算）
    val knowledgeForSubject = remember(repo.knowledge, subj, chapters) {
        repo.knowledge.filter { k ->
            val hay = "${k.cat} ${k.tags} ${k.title} ${k.content}".lowercase()
            hay.contains(subj.lowercase()) || chapters.any { ch ->
                k.tags.contains(ch.name) || hay.contains(ch.name.lowercase())
            }
        }
    }
    // R4 兜底：tags 稀疏时被筛掉的卡仍能「未归类」区发现
    val knowledgeOther = remember(repo.knowledge, knowledgeForSubject) {
        repo.knowledge.filter { it !in knowledgeForSubject }
    }
    val knowledgeGrouped = remember(knowledgeForSubject) { knowledgeForSubject.groupBy { it.cat } }

    // 主题色预取（onDraw 非 @Composable，无法读取 MaterialTheme，故在组合作用域先算好 Int）
    val primaryCol = MaterialTheme.colorScheme.primary.value.toInt()
    val outlineCol = MaterialTheme.colorScheme.outline.value.toInt()
    val onBgCol = MaterialTheme.colorScheme.onSurface.value.toInt()
    val surfaceCol = MaterialTheme.colorScheme.surface.value.toInt()
    // R3 热力色：绿（掌握良好）/黄（一般）/红（薄弱）/灰（未练）——取稳定 token
    val goodCol = android.graphics.Color.parseColor("#3FA45B")
    val warnCol = android.graphics.Color.parseColor("#E0A52B")
    val badCol = MaterialTheme.colorScheme.error.value.toInt()
    val noneCol = MaterialTheme.colorScheme.outlineVariant.value.toInt()

    val nodes = remember(subj, chapterCounts, chapterAcc, knowledgeForSubject) {
        computeNodes(subj, chapterCounts, chapterAcc, knowledgeForSubject, goodCol, warnCol, badCol, noneCol)
    }

    // 缩放 / 平移状态（双指缩放 + 拖动，章节多时不拥挤）
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s ->
                FilterChip(selected = subj == s, onClick = { subj = s }, label = { Text(s) })
            }
        }

        // 掌握度概览：章节数 / 已练 / 薄弱
        val practicedN = chapterAcc.values.count { (r, w) -> (r + w) > 0 }
        val weakN = chapterAcc.values.count { (r, w) -> (r + w) > 0 && r.toFloat() / (r + w) < 0.5f }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${chapters.size}", style = MaterialTheme.typography.titleMedium)
                    Text("章节", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$practicedN", style = MaterialTheme.typography.titleMedium)
                    Text("已练", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$weakN", style = MaterialTheme.typography.titleMedium, color = if (weakN > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text("薄弱", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("双指捏合缩放 · 单指拖动 · 双击复位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scale = (scale * 0.8f).coerceIn(0.6f, 4f) }) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                IconButton(onClick = { scale = (scale * 1.25f).coerceIn(0.6f, 4f) }) { Text("+", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("重置") }
            }
        }

        // 放射图（题 ↔ 知识 节点），支持双指缩放 + 拖动平移（章节多时不拥挤）
        Canvas(
            Modifier.fillMaxWidth().height(320.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 4f)
                        offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        // 平移夹紧：防止图谱被拖出可视区而丢失（只能重置找回）。
                        // 注意：scale<1 时 (scale-1) 为负，必须先 clamp 到 >=0，否则 maxX 变负、范围翻转导致 coerceIn 抛异常。
                        val maxX = ((scale - 1) * size.width * 0.5f).coerceAtLeast(0f) + 60f
                        val maxY = ((scale - 1) * size.height * 0.5f).coerceAtLeast(0f) + 60f
                        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                }
        ) {
            val w = size.width; val h = size.height
            val cx = w / 2f; val cy = h / 2f
            val hubR = minOf(w, h) * 0.12f
            val primary = primaryCol
            val outline = outlineCol
            val onBg = onBgCol
            val surface = surfaceCol
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }

            // 以画布中心为锚做缩放，再叠加平移（屏幕像素）
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.translate(offset.x, offset.y)
            drawContext.canvas.nativeCanvas.scale(scale, scale, cx, cy)

            // 连线（真实结构：章节与中心 hub 相连；知识卡与中心相连，颜色区分）
            nodes.forEach { nd ->
                val px = nd.x * w; val py = nd.y * h
                drawContext.canvas.nativeCanvas.drawLine(cx, cy, px, py, paint.apply {
                    color = if (nd.kind == "chapter") nd.heatColor else outline
                    alpha = if (nd.kind == "chapter") 120 else 60
                    strokeWidth = if (nd.kind == "chapter") (3f * nd.size).coerceAtMost(6f) else 1.5f
                })
            }
            // 节点（半径按真实 size；R3 章节按掌握度热力标色）
            nodes.forEach { nd ->
                val px = nd.x * w; val py = nd.y * h
                val base = if (nd.kind == "chapter") hubR * 0.55f else hubR * 0.34f
                val r = base * nd.size
                drawContext.canvas.nativeCanvas.drawCircle(px, py, r, paint.apply {
                    color = if (nd.kind == "chapter") nd.heatColor else outline; alpha = 215; strokeWidth = 0f
                })
                paint.color = surface; paint.textSize = if (nd.kind == "chapter") 13.sp.value else 11.sp.value
                // 真实标签：章节显示名称（不截断）；知识卡显示分类名
                val label = nd.label
                drawContext.canvas.nativeCanvas.drawText(label, px, py - r - 4f, paint)
                if (nd.kind == "chapter" && nd.payload.isNotBlank()) {
                    paint.color = onBg; paint.textSize = 11.sp.value
                    drawContext.canvas.nativeCanvas.drawText(nd.payload, px, py + r + 12f, paint)
                }
            }
            // 中心 hub
            drawContext.canvas.nativeCanvas.drawCircle(cx, cy, hubR, paint.apply { color = primary; alpha = 255; strokeWidth = 0f })
            paint.color = surface; paint.textSize = 14.sp.value
            drawContext.canvas.nativeCanvas.drawText(subj, cx, cy + 5f, paint)

            drawContext.canvas.nativeCanvas.restore()
        }

        // R3 图例：掌握度热力含义
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            LegendDot(goodCol, "掌握良好(≥80%)")
            LegendDot(warnCol, "一般(50-80%)")
            LegendDot(badCol, "薄弱(<50%)")
            LegendDot(noneCol, "未练")
        }

        Text("章节 → 题数 / 掌握度", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(220.dp)) {
            items(chapters, contentType = { "graphChapter" }) { c ->
                val n = chapterCounts[c.name] ?: 0
                val (r, wq) = chapterAcc[c.name] ?: (0 to 0)
                val done = r + wq
                val acc = if (done > 0) r.toFloat() / done else -1f
                val heatCol = when {
                    acc < 0f -> noneCol
                    acc >= 0.8f -> goodCol
                    acc >= 0.5f -> warnCol
                    else -> badCol
                }
                val accText = if (acc < 0f) "未练" else "${(acc * 100).toInt()}% 正确"
                val label = if (subj == "科三" && disc.isNotBlank()) "${c.name}($disc)" else c.name
                Card(
                    Modifier.fillMaxWidth().clickable { selChapter = if (selChapter == c.name) null else c.name },
                    colors = CardDefaults.cardColors(containerColor = if (selChapter == c.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Canvas(Modifier.size(12.dp)) {
                                drawContext.canvas.nativeCanvas.drawCircle(size.width / 2f, size.height / 2f, size.width / 2f,
                                    android.graphics.Paint().apply { color = heatCol; isAntiAlias = true })
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("$n 题 · $accText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 中心枢纽详情：选中章节后展开（掌握度 + 关联知识卡 + 去练该章）
        selChapter?.let { sc ->
            val (r, wq) = chapterAcc[sc] ?: (0 to 0)
            val done = r + wq
            val acc = if (done > 0) r.toFloat() / done else -1f
            val cnt = chapterCounts[sc] ?: 0
            val relK = knowledgeForSubject.filter { k ->
                k.tags.contains(sc) || "${k.cat} ${k.title} ${k.content}".contains(sc)
            }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Text("枢纽详情：${if (subj == "科三" && disc.isNotBlank()) "$sc($disc)" else sc}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "题数 $cnt · ${if (acc < 0f) "未练习" else "正确率 ${(acc * 100).toInt()}%（已练 $done）"}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (relK.isNotEmpty()) {
                        Text("关联知识卡（${relK.size}）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        relK.take(3).forEach { k -> Text("· ${k.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        if (relK.size > 3) Text("… 还有 ${relK.size - 3} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Text("该章暂无直接关联的知识卡。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Button(onClick = {
                            appVm.setPendingChapterPractice(subj, sc)
                            nav.navigate("practice")
                        }) { Text("去练该章") }
                    }
                }
            }
        }

        Text("知识卡（已关联 ${knowledgeForSubject.size} / 共 ${repo.knowledge.size} 张）", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                knowledgeGrouped.entries.forEach { (cat, list) ->
                    Text(cat, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    list.forEach { k -> KnowledgeCard(k) }
                }
                // R4 兜底：tags 稀疏未能自动关联的卡在此可见，避免漏显
                if (knowledgeOther.isNotEmpty()) {
                    Text("未自动归类（可能相关）", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    knowledgeOther.forEach { k -> KnowledgeCard(k) }
                }
            }
        }
    }
}

/** R3 热力图例小圆点 */
@Composable
private fun LegendDot(color: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) {
            drawContext.canvas.nativeCanvas.drawCircle(
                size.width / 2f, size.height / 2f, size.width / 2f,
                android.graphics.Paint().apply { this.color = color; isAntiAlias = true }
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

private data class RadialNode(
    val kind: String,   // chapter / knowledge
    val label: String,
    val x: Float, val y: Float,  // 归一化坐标 0..1
    val payload: String = "",
    val size: Float = 1f,  // 相对大小（章节按题数、知识按关联强度）
    val heatColor: Int = 0  // R3：章节节点热力色（未练/薄弱/一般/良好）
)

private fun computeNodes(
    subj: String,
    chapterCounts: Map<String, Int>,
    chapterAcc: Map<String, Pair<Int, Int>>,
    knowledgeForSubject: List<Knowledge>,
    goodCol: Int,
    warnCol: Int,
    badCol: Int,
    noneCol: Int
): List<RadialNode> {
    val nodes = mutableListOf<RadialNode>()
    val cx = 0.5f; val cy = 0.5f
    // 章节：按真实题数决定半径（题越多节点越大）；R3 按正确率着色
    val chapters = chapterCounts.keys.toList()
    val maxCount = (chapterCounts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val nCh = chapters.size.coerceAtLeast(1)
    // R2 自适应：环半径随章节数外扩（避免节点交叠），由"估算最大节点半径×章数"推导，不写死
    // 估算最大节点归一化半径 ≈ hubR(0.12·min) × 0.55 × maxSize / min(w,h) ≈ 0.13
    val nodeBase = 0.13f
    val ringR = (nodeBase * nCh / PI.toFloat()).coerceIn(0.24f, 0.40f)
    // 节点尺寸随章数收敛，章越多越缩小以进一步防交叠
    val maxSize = if (nCh > 18) 1.0f else if (nCh > 10) 1.4f else 1.8f
    chapters.forEachIndexed { i, name ->
        val ang = (i.toFloat() / nCh) * 2 * PI.toFloat() - PI.toFloat() / 2
        val cnt = chapterCounts[name] ?: 0
        val size = (0.6f + (cnt.toFloat() / maxCount) * 1.2f).coerceAtMost(maxSize)
        val (ri, wi) = chapterAcc[name] ?: (0 to 0)
        val done = ri + wi
        val acc = if (done > 0) ri.toFloat() / done else -1f
        val heat = when {
            acc < 0f -> noneCol
            acc >= 0.8f -> goodCol
            acc >= 0.5f -> warnCol
            else -> badCol
        }
        val payload = if (acc < 0f) "$cnt 题" else "$cnt 题 ${(acc * 100).toInt()}%"
        nodes.add(RadialNode("chapter", name, cx + cos(ang) * ringR, cy + sin(ang) * ringR, payload, size, heat))
    }
    // 知识卡：真实关联（按 tag/章节命中），置于章节环外侧，半径同样自适应
    val cats = knowledgeForSubject.groupBy { it.cat }.keys.toList()
    val nK = cats.size.coerceAtLeast(1)
    val ringK = (ringR + 0.12f).coerceIn(0.40f, 0.46f)
    cats.forEachIndexed { j, cat ->
        val ang = (j.toFloat() / nK) * 2 * PI.toFloat() - PI.toFloat() / 2
        nodes.add(RadialNode("knowledge", cat, cx + cos(ang) * ringK, cy + sin(ang) * ringK, "知识", 0.8f, 0))
    }
    return nodes
}

/** 单张知识卡：默认折叠（3 行），点击展开全文 */
@Composable
private fun KnowledgeCard(k: Knowledge) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("· ${k.title}", style = MaterialTheme.typography.bodyMedium)
        Text(
            k.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = if (expanded) Int.MAX_VALUE else 3
        )
        if (k.content.length > 60) {
            Text(
                if (expanded) "收起" else "…展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}
