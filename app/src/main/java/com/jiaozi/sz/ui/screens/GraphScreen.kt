package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.GlassIconButton
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.hubDragToScroll

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import com.jiaozi.sz.ui.components.AppColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.waitForUpOrCancellation

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
    // 🔴 2026-09-23 修复：`Color.value` 是 **ULong**（含色彩空间信息），`.toInt()` 截断后不是合法 ARGB
    //    ⇒ 原生 Canvas 把 hub / 连线 / 节点全画成**纯黑**（真机实测画布 8 万黑色像素、零语义色）。
    //    正解是 `Color.toArgb()`。全工程其它 Canvas 取色点同步修正。
    val primaryCol = MaterialTheme.colorScheme.primary.toArgb()
    val outlineCol = MaterialTheme.colorScheme.outline.toArgb()
    val onBgCol = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceCol = MaterialTheme.colorScheme.surface.toArgb()
    // 🔴 2026-09-23：R3 热力色改走全局语义 token（原为硬编码 "#3FA45B"/"#E0A52B"，
    //    与知识库页 AppColors.success/warning 不同源 ⇒ 跨页同一掌握度会出现两种绿/黄）
    val goodCol = AppColors.success.toArgb()
    val warnCol = AppColors.warning.toArgb()
    val badCol = MaterialTheme.colorScheme.error.toArgb()
    val noneCol = MaterialTheme.colorScheme.outlineVariant.toArgb()

    // 🔴 2026-09-25 晚（F/B3）：图谱画布高度由写死 `320.dp` 改为**屏高 42%**。
    //    依据：09 号 E4 `position`「概览下 sp.12，通栏，**高 ≈ 屏 42%**」+
    //    `high_fidelity.keywords`「图谱高度固定（约屏幕 42%），**不随节点数变化**」。
    //    原文「屏」= 屏幕 ⇒ 取 screenHeightDp（非本页可用高度），最贴合原文且不引入布局层级改动。
    //    ⚠️ 竖屏手机 ≈ 各机型 0.42 × 屏高（约 300~350dp，与旧写死值 320dp 基本重合）；
    //       横屏 / 高瘦平板下该比例会退化，是否加夹紧待杰哥裁决（本轮先严格照规范落地）。
    val graphHeight = LocalConfiguration.current.screenHeightDp.dp * 0.42f

    val nodes = remember(subj, chapterCounts, chapterAcc, knowledgeForSubject) {
        computeNodes(subj, chapterCounts, chapterAcc, knowledgeForSubject, goodCol, warnCol, badCol, noneCol)
    }

    // 缩放 / 平移状态（双指缩放 + 拖动，章节多时不拥挤）
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 🔴 2026-09-23（09 号 F4）：当前选中节点 —— null 表示未选中，浮层卡不渲染
    var selectedNode by remember { mutableStateOf<RadialNode?>(null) }

    // ── 顶部常驻块（图 3-2）：与知识库 / 课标库 / 教材页同款布局 ──
    val scrollState = rememberScrollState()

    // 科目切换 chips：固定带内一行横向滚动（2026-09-25 晚：原「两态互斥渲染」已随折叠退场取消）
    // 🔴 2026-09-28：展示名一律用《考试大纲》官方名（窄 chip 用官方简写，见 BankStore.shortName）
    val subjectChips: @Composable RowScope.() -> Unit = {
        subjects.forEach { s ->
            FilterChip(selected = subj == s, onClick = { subj = s }, label = { Text(BankStore.shortName(s), fontSize = 13.sp) })
        }
    }

    // 掌握度概览：章节数 / 已练 / 薄弱（提到顶部区块外计算，收起后仍可直接取值）
    val practicedN = chapterAcc.values.count { (r, w) -> (r + w) > 0 }
    val weakN = chapterAcc.values.count { (r, w) -> (r + w) > 0 && r.toFloat() / (r + w) < 0.5f }
    // 🔴 2026-09-23（09 号 F3）：图例三档计数 —— 已掌握 / 学习中 / 未掌握（+ 未练）
    val masteryCounts = remember(chapterAcc) {
        var good = 0; var mid = 0; var bad = 0
        chapterAcc.values.forEach { (r, w) ->
            val done = r + w
            if (done <= 0) return@forEach
            val acc = r.toFloat() / done
            if (acc >= 0.8f) good++ else if (acc >= 0.5f) mid++ else bad++
        }
        MasteryCounts(good, mid, bad, (chapterAcc.size - good - mid - bad).coerceAtLeast(0))
    }

    // 两段式布局（与知识库 / 课标库 / 教材页同款）：
    //   上半段（Hero）挂在**非滚动**外框上；
    //   下半段（缩放控制 / 放射图 / 图例 / 章节列表 / 枢纽详情 / 知识卡）是**唯一滚动容器**。
    //（2026-09-25 晚：原「收起态紧凑栏」已删；两段式骨架保留。）
    Column(
        Modifier.fillMaxSize().padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ① 顶部重区块（Hero 头 + 科目切换 + 掌握度概览）：常驻不滚，手势直通下方列表
        CollapsingTopBlocks(spacing = 10.dp, modifier = Modifier.hubDragToScroll(scrollState)) {
            // ── Hero：CMP-HERO / with_action ──
            // 🔴 2026-09-20 依 09 号 F8 / E1 新增右上「☰ 列表」视图互换键（图谱 → 知识库），
            //    这是 two_views_one_source 的图谱侧入口（知识库侧入口 F6 图未覆盖，故未实现）。
            //    形态按 09 号 token_binding.view_switch =「40dp 文字胶囊（primaryContainer 底 + primary 文字）」，
            //    与知识库页 hero 右上搜索键同位；本页是 heroHeaderRoutes 成员 ⇒ 返回条只留箭头、无标题胶囊。
            //    尺寸佐证：图 2 右实测该键内容 bbox ≈ 74×36px（px÷4=dp ⇒ 约 49×24dp），
            //    与「矮胶囊 + 图标 + 短标签」相符（对比 02 号 action_key「40dp 圆底 + 20dp 图标」为纯图标键）。
            //    ⚠️ 图 2 右半实测：键区文字为**深色**（灰阶 105）⇒ 与「primary 文字」口径一致；
            //       但整块 hero 底色实测为**浅色**（标题字形近黑 #131313、副标题灰 #A8A8A8）而非 CMP-HERO 蓝渐变，
            //       与 02 号 CMP-HERO 规格冲突 ⇒ 按铁律「图 vs 规范冲突须上报、不扩散」，
            //       本轮只落地 action 键，**不改 hero 底色**，待杰哥裁决。
            HeroHeader(
                "知识图谱", "科目 → 章节 → 知识的关联结构", icon = appPainter("graph"),
                // 🔴 2026-09-22 二级 Hero 统一沉浸通栏
                immersive = true,
                onBack = { nav.navigateUp() },
                action = {
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { nav.navigate("knowledge") }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                appPainter("menu"),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("列表", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        // ③ 滚动区：只有下方资料滚动，顶部 Hero 保持可见
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = subjectChips)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
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
        // 🔴 2026-09-23：Canvas 外包一层 Box，供**选中节点的浮层卡叠加在画布底部**浮出。
        //   此前浮层卡作为列内兄弟项排在 Canvas 之后，因其位于屏幕外而"看不见"
        //   （真机实测：点击已生效——画布选中态像素差异 7155/14484，但 UI 层级查不到浮层卡）。
        Box(Modifier.fillMaxWidth()) {
        Canvas(
            // 09 号 E4：通栏 + 高 ≈ 屏 42%（见上方 graphHeight 注释）
            Modifier.fillMaxWidth().height(graphHeight)
                // ① 缩放 / 平移
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
                // ② 点击命中节点
                // 🔴🔴 2026-09-23 定案（真机实证，三版实现踩坑记录）：
                //   ✗ 版1 两个 `.pointerInput()` 串联 + `detectTapGestures`：tap 收不到 ——
                //     `detectTapGestures` 内部是 `awaitFirstDown(requireUnconsumed = true)`，
                //     而前一个 `detectTransformGestures` 已消费 down。
                //   ✗ 版2/版3 同一个 `.pointerInput()` 内 `coroutineScope { launch×2 }`：
                //     **手势完全静默失效** —— `awaitPointerEventScope` 对同一 PointerInputScope
                //     **不允许并发调用**，两个 detector 并行时拿不到事件（真机实测：点画布任意位置，
                //     前后帧像素差异 = 0、UI 层级无浮层卡）。
                //   ✓ 正解 = **两个独立 `.pointerInput()`（各自串行拿到自己的事件流）+ tap 侧
                //     `awaitFirstDown(requireUnconsumed = false)`**，这样即便 down 已被 transform 消费，
                //     本修饰符仍能观测到；再用位移阈值把「点击」与「拖动/缩放」区分开。
                .pointerInput(nodes, scale, offset) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // ⚠️ 不要手写 while + awaitPointerEvent 找抬起：事件被 transform 消费后
                        //    `changes` 里已无 down.id ⇒ 提前 break、up 恒为 null。用官方 API 等待抬起/取消。
                        val up = waitForUpOrCancellation()
                        val moved = up != null && (up.position - down.position).getDistance() > 16f
                        if (up != null && !moved) {
                            val tap = up.position
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val cx = w / 2f; val cy = h / 2f
                            // 反解缩放/平移：画布坐标 → 归一化节点坐标
                            val lx = cx + (tap.x - offset.x - cx) / scale
                            val ly = cy + (tap.y - offset.y - cy) / scale
                            // 命中：44dp 触控圈内取最近节点；圈外不选中（点空白取消）
                            val hitR = 22.dp.toPx()
                            selectedNode = nodes
                                .map { nd ->
                                    val px = nd.x * w; val py = nd.y * h
                                    nd to ((px - lx) * (px - lx) + (py - ly) * (py - ly))
                                }
                                .filter { it.second <= hitR * hitR }
                                .minByOrNull { it.second }
                                ?.first
                        }
                    }
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
                // 半径按真实 size（章节按题数、知识卡固定）
                val base = if (nd.kind == "chapter") hubR * 0.55f else hubR * 0.34f
                val r = base * nd.size
                val nodeCol = if (nd.kind == "chapter") nd.heatColor else noneCol
                // 选中态：外扩 3dp primary 光环（未选中项降到 40% 透明度）
                val isSelected = selectedNode === nd
                if (isSelected) {
                    drawContext.canvas.nativeCanvas.drawCircle(px, py, r + 3.dp.toPx(), paint.apply {
                        color = primary; alpha = 77; strokeWidth = 0f  // ≈30%
                    })
                }
                drawContext.canvas.nativeCanvas.drawCircle(px, py, r, paint.apply {
                    color = nodeCol
                    alpha = if (selectedNode != null && !isSelected) 102 else 255
                    strokeWidth = 0f
                    style = android.graphics.Paint.Style.FILL
                })
                paint.color = onBg; paint.textSize = if (nd.kind == "chapter") 13.sp.toPx() else 11.sp.toPx()
                // 真实标签：章节显示名称（不截断）；知识卡显示分类名
                val label = nd.label
                drawContext.canvas.nativeCanvas.drawText(label, px, py - r - 4.dp.toPx(), paint)
                if (nd.kind == "chapter" && nd.payload.isNotBlank()) {
                    paint.color = onBg; paint.textSize = 11.sp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(nd.payload, px, py + r + 12.dp.toPx(), paint)
                }
            }
            // 中心 hub
            drawContext.canvas.nativeCanvas.drawCircle(cx, cy, hubR, paint.apply { color = primary; alpha = 255; strokeWidth = 0f })
            paint.color = surface; paint.textSize = 12.sp.toPx()
            val hubLabel = BankStore.shortName(subj)
            drawContext.canvas.nativeCanvas.drawText(hubLabel, cx, cy + 4.dp.toPx(), paint)

            drawContext.canvas.nativeCanvas.restore()
        }

            // ── 选中节点浮层卡（09 号 E6）：叠加在**画布底部**浮出，未选中不渲染 ──
            selectedNode?.let { nd ->
                Card(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(nd.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (nd.kind == "chapter") "章节 · ${nd.payload.ifBlank { "暂无数据" }}" else "知识卡 · ${nd.payload.ifBlank { nd.label }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 🔴 2026-10-01 九校：节点详情卡的关闭键统一为 Liquid Glass 玻璃钮
                        //   （同一套材质/交互反馈/按压果冻）。取 40dp：原 IconButton 默认 48dp，
                        //   在 16dp 内距的卡片里偏挤；图标 = size/2 = 20dp（原 18dp，略大更清晰）。
                        //   ⚠️ 不传 backdrop：本页在 NavHost 内部，属 layerBackdrop 录制范围。
                        GlassIconButton(
                            onClick = { selectedNode = null },
                            icon = "close",
                            contentDescription = "关闭",
                            // 统一 44dp（全站玻璃圆钮唯档尺寸，对齐二级界面左上角返回键）
                            size = 44.dp
                        )
                    }
                }
            }
        }   // ← 关闭 Box

        // R3 图例：掌握度热力含义（09 号 E6：12dp 圆点 + 13sp 文字 + 间距 6dp，并带各档计数）
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
            LegendDot(goodCol, "掌握良好 ${masteryCounts.good}")
            LegendDot(warnCol, "一般 ${masteryCounts.mid}")
            LegendDot(badCol, "薄弱 ${masteryCounts.bad}")
            LegendDot(noneCol, "未练 ${masteryCounts.none}")
        }

        Text("章节 → 题数 / 掌握度", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(220.dp)) {
            if (chapters.isEmpty()) {
                item { EmptyHint("bars", "该科目暂无章节", "去题库添加题目后这里会显示。") }
            }
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
                    colors = CardDefaults.cardColors(containerColor = if (selChapter == c.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Canvas(Modifier.size(12.dp)) {
                                drawContext.canvas.nativeCanvas.drawCircle(size.width / 2f, size.height / 2f, size.width / 2f,
                                    android.graphics.Paint().apply { color = if (acc < 0f) 0xFF9CA3AF.toInt() else heatCol; isAntiAlias = true; style = android.graphics.Paint.Style.FILL })
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
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
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
        if (knowledgeForSubject.isEmpty() && knowledgeOther.isEmpty()) {
            EmptyHint("book", "暂无关联知识卡", "该科目暂未关联知识卡，可去知识库补充。")
        } else {
            Card(Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
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
    }
}

/** R3 热力图例小圆点 */
@Composable
private fun LegendDot(color: Int, label: String) {
    // 🔴 2026-09-23（09 号 E6）：圆点 12dp（原 10）、间距 6dp（原 4）、文字 13sp（原 labelSmall 11sp）
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(12.dp)) {
            drawContext.canvas.nativeCanvas.drawCircle(
                size.width / 2f, size.height / 2f, size.width / 2f,
                android.graphics.Paint().apply { this.color = color; isAntiAlias = true }
            )
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 掌握度三档计数（09 号 F3）：已掌握 / 学习中 / 未掌握 / 未练。 */
private data class MasteryCounts(val good: Int, val mid: Int, val bad: Int, val none: Int)

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
        nodes.add(RadialNode("knowledge", cat, cx + cos(ang) * ringK, cy + sin(ang) * ringK, "知识", 0.8f, noneCol))
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
