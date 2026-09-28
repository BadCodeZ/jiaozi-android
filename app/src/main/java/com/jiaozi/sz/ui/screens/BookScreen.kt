package com.jiaozi.sz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.DocRow
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.BookEntity
import com.jiaozi.sz.data.model.LessonDims
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.GlassBackButton
import com.jiaozi.sz.ui.components.StatBand
import com.jiaozi.sz.ui.components.StatBandItem
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.DocReader
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitle
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.formatDocSize
import com.jiaozi.sz.ui.screens.lesson.SegmentedRow
import com.jiaozi.sz.util.toIsoDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 教材管理 / 教材详情（对齐高保真图 1-4）
 *
 * - 列表：蓝色统计卡（教材文件 / 学段 / 教材数 / 已下载）+ 导入卡 + 学段筛选 + 教材条目
 * - 详情：封面卡（教材名 + 学段·单元·课 + 版本·体积）+ 目录 / 简介 / 相关资源 三 tab + 底部「全文阅读」
 * - 全文阅读复用 [DocReader]（分页 1/N + 上一页/下一页 + 章节导航）
 *
 * 兼容性：数据读写仍走 `repo.allBooksFlow / upsertBook / deleteBook`，
 * [BookEntity] 字段与存储结构未动 ⇒ 老版本升级上来的教材数据零迁移、零丢失。
 */
@Composable
fun BookScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = appVm.viewModelScope
    val items by appVm.repo.allBooksFlow().collectAsStateWithLifecycle(initialValue = emptyList())

    var importText by remember { mutableStateOf<String?>(null) }
    /** 导入文件的扩展名（08 号 F5 类型色块用），由选择器 URI 后缀推断 */
    var importExt by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }
    var detail by remember { mutableStateOf<BookEntity?>(null) }
    var reading by remember { mutableStateOf<BookEntity?>(null) }
    /** 行内删除的待确认对象（08 号 F7 二次确认） */
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // 记录扩展名（pdf / epub / txt），供列表行类型色块使用
        val name = uri.lastPathSegment ?: ""
        importExt = name.substringAfterLast('.', "").lowercase().take(4)
        val txt = appVm.repo.readFileText(ctx, uri)
        if (txt != null) importText = txt
        else android.widget.Toast.makeText(ctx, "无法读取文件内容：PDF 需 Android 14+，或格式不支持；请改用 txt/md", android.widget.Toast.LENGTH_LONG).show()
    }

    if (reading != null) {
        DocReader(
            title = reading!!.book.ifBlank { "(未命名教材)" },
            subtitle = listOf(reading!!.grade, reading!!.unit, reading!!.lesson)
                .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未分类" },
            text = reading!!.text,
            onClose = { reading = null }
        )
        return
    }

    if (detail != null) {
        BookDetail(
            book = detail!!,
            sameBookItems = remember(items, detail) { items.filter { it.book == detail!!.book } },
            onBack = { detail = null },
            onRead = { reading = detail },
            onDelete = { b ->
                scope.launch { appVm.repo.deleteBook(b.id) }
                detail = null
            },
            onOpenLesson = { nav.navigate("lesson") },
            onOpenCurric = { nav.navigate("curric") }
        )
        return
    }

    // ── 2026-09-22 按 08 号 `books.main` 高保真补齐 ──
    // 筛选维度口径（杰哥 2026-09-22 裁定）：**保留学段筛选**（全部/小学/初中/高中），
    // 把「解析状态」下沉到清单**双分组**表达（「已导入(N)」「未解析(N)」）——贴稿且不破坏
    // 全站学段筛选口径。稿里 native 的「科一/科二/待解析」chips 不再逐字复刻。
    val shown = remember(items, filter) {
        if (filter == "全部") items.sortedByDescending { it._mt }
        else items.filter { it.grade == filter }.sortedByDescending { it._mt }
    }
    // 双分组：已导入 = 解析出正文 / 未解析 = 无正文
    val parsedGroup = remember(shown) { shown.filter { it.text.isNotBlank() } }
    val pendingGroup = remember(shown) { shown.filter { it.text.isBlank() } }
    val grades = remember(items) { items.map { it.grade.trim() }.filter { it.isNotBlank() }.distinct().size }
    val bookNames = remember(items) { items.map { it.book.trim() }.filter { it.isNotBlank() }.distinct().size }
    val downloaded = remember(items) { items.count { it.text.isNotBlank() } }
    // Hero 副标题第二行：N 本 · 覆盖 <科目/学段>
    val heroSub2 = remember(items) {
        if (items.isEmpty()) "尚未导入教材，点右上导入"
        else {
            val cover = items.map { it.grade.trim() }.filter { it.isNotBlank() }.distinct()
            val coverTxt = if (cover.isEmpty()) "未分段" else cover.joinToString("/")
            "${items.size} 本 · 覆盖 $coverTxt"
        }
    }

    val listState = rememberLazyListState()

    // 🔴 2026-09-27（IDX5 搜索结果直达）：消费 pendingOpenDoc，按 id 在列表定位 + 高亮
    val pendingOpen by appVm.pendingOpenDoc.collectAsStateWithLifecycle()
    var hlBookId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingOpen, parsedGroup, pendingGroup) {
        val po = pendingOpen
        if (po != null && po.route == "books" && po.id.isNotBlank()) {
            val targetId = po.id
            val absIdx = run {
                var i = 1 // hubBand=0
                if (parsedGroup.isNotEmpty()) {
                    i += 1 // grpParsed 头项
                    val p = parsedGroup.indexOfFirst { it.id == targetId }
                    if (p >= 0) return@run i + p
                    i += parsedGroup.size
                }
                if (pendingGroup.isNotEmpty()) {
                    i += 1 // grpPending 头项
                    val q = pendingGroup.indexOfFirst { it.id == targetId }
                    if (q >= 0) return@run i + q
                }
                -1
            }
            if (absIdx >= 0) {
                listState.scrollToItem(absIdx)
                hlBookId = targetId
            }
            // 🔴 clearPendingOpenDoc() 改写 pendingOpen = 本 effect 的 key ⇒ 会取消本协程，
            //    必须放在状态写入之后；放 delay 之前会使高亮永久驻留（见 KnowledgeScreen 同款注释）。
            appVm.clearPendingOpenDoc()
        }
    }
    // 高亮 2.2s 后自动清除（独立 effect：不随 pendingOpenDoc 变更被取消）
    LaunchedEffect(hlBookId) {
        if (hlBookId != null) { delay(2200); hlBookId = null }
    }

    // 学段筛选 chips：固定带内一行横向滚动（2026-09-25 晚：原「两态互斥渲染」已随折叠退场取消）
    val gradeChips: @Composable RowScope.() -> Unit = {
        (listOf("全部") + LessonDims.GRADE).forEach { g ->
            FilterChip(selected = filter == g, onClick = { filter = g }, label = { Text(g, fontSize = 12.sp) })
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ① 顶部重区块（Hero + 统计 + 导入引导 + 筛选 chips）：常驻不滚，手势直通下方列表
        CollapsingTopBlocks(spacing = 14.dp, modifier = Modifier.hubDragToScroll(listState)) {
        HeroHeader(
            title = "教材管理",
            // 🔴 2026-09-22 按 08 号 `books.main` 高保真：subtitle 两行（第 2 行为动态覆盖度）。
            //   ⚠️ 文案精简说明：08 号原稿第 1 行为「导入教材原文，结构化索引，随备课信封同步」共 20 字，
            //   按 02 号 CMP-HERO 规定的 `t.bodyMedium`（14sp）计算需 280dp，而真机（MuMu 360dp）
            //   实测可用宽度仅 ≈219dp（x 81dp → 右侧 40dp action 键左沿 300dp）⇒ 物理上必然截断。
            //   按红线「14 号总览＝全局基准，字号体系不动」⇒ **不改字号，改文案**，
            //   精简为 12 字（≈168dp，含分隔符）保证完整可读，语义不变（"随备课信封同步"属次要信息，
            //   已由第 2 行动态覆盖度承担"有什么"的表达）。
            subtitle = "导入教材原文 · 结构化索引\n$heroSub2",
            icon = appPainter("book"),
            // 🔴 2026-09-22 按杰哥裁定「二级 Hero 统一沉浸通栏」：
            //   破框（左右铺满）与状态栏避让均由 HeroHeader(immersive=true) 内建完成，
            //   外框 padding(16.dp) 保留不动（下游兄弟元素对齐零变化）。
            immersive = true,
            onBack = { nav.navigateUp() },
            action = {
                // 🔴 2026-09-20 走查修正（第一轮）：原为 Material3 `Button`（默认 containerColor = primary #1A5BB5），
                //    而 hero 渐变同位实测 ≈ #1858AE ⇒ ΔRGB 仅 (1.7, 0.9, 0.7)/255，
                //    按钮容器**视觉隐形**（仅白字「导入」浮空可见），与 08 号 F1 规范不符。
                //    08 号 `books.main F1` 明文：「图标＝『云 + 加号』（upload 语义），**白 18% 圆底 40dp**」。
                //
                // 🔴 2026-09-20 走查修正（第二轮）：第一轮改用 `IconButton` + `Modifier.size(40.dp)` 后，
                //    真机实测圆底 **192px = 48dp**（知识库页同为 action 键的 `Box` 写法实测 **160px = 40dp**）。
                //    根因：M3 `IconButton` 内部在调用方 modifier 之后追加 `minimumInteractiveComponentSize()`
                //    ⇒ 传入的 `size(40.dp)` 被顶到 48dp，与 02 号 `size.action_key =「40dp 圆底 + 20dp 图标」`
                //    及 08 号 F1「40dp」均不符，且与知识库页不同构。
                //    正解＝两层结构：外层 48dp 只承担触控区（满足 01 号 `sz.touch_target_min = 44dp`），
                //    内层 40dp 承担视觉圆底 ⇒ 视觉尺寸与规范精确一致，触控区不回退。
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            picker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown"))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            appPainter("upload"),
                            contentDescription = "导入教材",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )
        }

        // 2026-09-25 晚：原 ② 收起态紧凑栏已整块删除（折叠状态机退场，收起态不存在）。

        if (shown.isEmpty()) {
            EmptyHint("book", if (items.isEmpty()) "还没有教材" else "该学段下没有教材", "点右上「导入」加入一份教材原文，可随备课信封跨端同步。")
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 固定带下沉（2026-09-21）：Hero 之外的信息带随列表滚动 ──
            item(key = "hubBand") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ── 统计带：CMP-STATCARD · band 变体（2026-09-20 高保真图 1 像素实证）──
                    // 🔴 2026-09-22 复核订正：列间竖分隔线 08 号规范（1dp outlineVariant）**存在**，
                    //    重测高保真图列界 x≈228/355/482 确认有极浅竖痕 ⇒ 开启 showDivider=true。
                    StatBand(
                        listOf(
                            StatBandItem("${items.size}", "教材文件"),
                            StatBandItem("$grades", "学段"),
                            StatBandItem("$bookNames", "教材数"),
                            StatBandItem("$downloaded", "已下载")
                        ),
                        showDivider = true
                    )

                    // ── 导入引导条（08 号 F3 两态文案，2026-09-22）──
                    // 无教材 ⇒ 主行动入口「还没有教材？点这里导入」；有教材 ⇒ 退化为状态提示「继续导入更多教材」
                    val noBook = items.isEmpty()
                    Card(
                        onClick = { picker.launch(arrayOf("application/pdf", "text/plain", "text/markdown", "text/x-markdown")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.blueBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(appPainter(if (noBook) "plus" else "upload"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(22.dp))
                            }
                            Text(
                                if (noBook) "还没有教材？点这里导入" else "继续导入更多教材",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = gradeChips)
                }
            }

            // ── 清单双分组（08 号 F11）：『● 已导入(N)』『● 未解析(N)』 ──
            if (parsedGroup.isNotEmpty()) {
                item(key = "grpParsed") { GroupHeader("已导入", parsedGroup.size, AppColors.success) }
                items(parsedGroup, key = { it.id }) { e -> BookRowFor(e, onDelete = { pendingDelete = e }, onClick = { detail = e }, highlighted = hlBookId == e.id) }
            }
            if (pendingGroup.isNotEmpty()) {
                item(key = "grpPending") { GroupHeader("未解析", pendingGroup.size, AppColors.textSecondary) }
                items(pendingGroup, key = { it.id }) { e -> BookRowFor(e, onDelete = { pendingDelete = e }, onClick = { detail = e }, highlighted = hlBookId == e.id) }
            }
        }
    }

    // ── 行内删除二次确认（08 号 F7）──
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除教材") },
            text = { Text("确定删除「${pendingDelete!!.book.ifBlank { "未命名教材" }}」？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val b = pendingDelete!!
                    scope.launch { appVm.repo.deleteBook(b.id) }
                    pendingDelete = null
                }) { Text("删除", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    // ── 导入信息录入（行为与旧版一致）──
    if (importText != null) {
        var grade by remember(importText) { mutableStateOf(LessonDims.GRADE.first()) }
        var book by remember(importText) { mutableStateOf("") }
        var unit by remember(importText) { mutableStateOf("") }
        var lesson by remember(importText) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("录入教材信息") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已读取原文 ${importText!!.length} 字", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    SegmentedRow("学段", LessonDims.GRADE, grade) { grade = it }
                    OutlinedTextField(book, { book = it }, label = { Text("教材名 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(unit, { unit = it }, label = { Text("单元") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(lesson, { lesson = it }, label = { Text("课") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (book.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            appVm.repo.upsertBook(
                                BookEntity(
                                    id = "B$now", grade = grade, book = book.trim(), unit = unit.trim(), lesson = lesson.trim(),
                                    text = importText ?: "", _mt = now,
                                    status = if ((importText ?: "").isNotBlank()) "parsed" else "pending",
                                    ext = importExt.ifBlank { book.trim().substringAfterLast('.', "").lowercase().take(4) },
                                    sizeBytes = (importText ?: "").toByteArray(Charsets.UTF_8).size.toLong()
                                )
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

// 2026-09-18 P3 死代码清理：原先的 private fun BookStatCell(value, label, modifier)
// 与课标库的 CurricStatCell 逐字重复 ⇒ 收敛为全局 HeroStatCell（调用点已换），此处删除。

/** 字节数 → 人类可读（08 号 F5 meta「4.2 MB」口径）。 */
private fun formatBytes(b: Long): String = when {
    b >= 1024L * 1024L -> String.format("%.1f MB", b / 1024.0 / 1024.0)
    b >= 1024L -> "${b / 1024} KB"
    else -> "$b B"
}

/**
 * 分组小标题（08 号 F11）：`● 已导入（N）`，圆点用状态语义色。
 */
@Composable
private fun GroupHeader(label: String, count: Int, dotColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Text("$label（$count）", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.textPrimary)
    }
}

/** 由 [BookEntity] 推导行所需参数（类型色块 / meta / 状态徽标）。 */
@Composable
private fun BookRowFor(e: BookEntity, onDelete: () -> Unit, onClick: () -> Unit, highlighted: Boolean = false) {
    // 文件类型：优先实体 ext 字段，缺失时按正文/书名代理（默认 txt）
    val ext = e.ext.trim().lowercase().ifBlank {
        when {
            e.book.contains(".pdf", true) -> "pdf"
            e.book.contains(".epub", true) -> "epub"
            else -> "txt"
        }
    }
    val head = listOf(e.book, e.unit, e.lesson).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")
    // meta：页数 · 体积 · 日期（未知值一律显示「—」；体积无实体值时按正文估算，正文亦空则视为未知）
    val sizeTxt = when {
        e.sizeBytes > 0 -> formatBytes(e.sizeBytes)
        e.text.isNotBlank() -> formatDocSize(e.text)
        else -> "—"
    }
    val meta = "${if (e.pages > 0) "${e.pages} 页" else "—"} · $sizeTxt · ${e._mt.toIsoDate()}"
    // 状态：实体 status 优先，空则按正文代理
    val status = e.status.trim().ifBlank { if (e.text.isNotBlank()) "parsed" else "pending" }
    val statusBadge = when (status) {
        "parsed" -> "已解析"
        "failed" -> "失败"
        else -> "待解析"
    }
    // 类型色块配色（08 号 F5）：PDF=primary / EPUB=purple / TXT=teal
    val (blockBg, blockFg) = when (ext) {
        "pdf" -> AppColors.blue to Color.White
        "epub" -> AppColors.purple to Color.White
        else -> AppColors.teal to Color.White
    }
    DocRow(
        name = head.ifBlank { e.book.ifBlank { "(未命名教材)" } },
        meta = meta,
        onClick = onClick,
        onDelete = onDelete,
        showChevron = true,
        statusBadge = statusBadge,
        highlighted = highlighted,
        leading = {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(blockBg),
                contentAlignment = Alignment.Center
            ) {
                Text(ext.uppercase().take(4), color = blockFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * 教材详情（08 号 `books.detail`）：信息卡（头部 + 键值对两段）+ 关联跳转组 + 本书条目清单（圆形序号位）+ 危险删除。
 */
@Composable
private fun BookDetail(
    book: BookEntity,
    sameBookItems: List<BookEntity>,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onDelete: (BookEntity) -> Unit,
    onOpenLesson: () -> Unit,
    onOpenCurric: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    // 文件类型（08 号：头部 56dp 类型色块）
    val ext = book.ext.trim().lowercase().ifBlank {
        when {
            book.book.contains(".pdf", true) -> "pdf"
            book.book.contains(".epub", true) -> "epub"
            else -> "txt"
        }
    }
    val (blockBg, blockFg) = when (ext) {
        "pdf" -> AppColors.blue to Color.White
        "epub" -> AppColors.purple to Color.White
        else -> AppColors.teal to Color.White
    }
    val status = book.status.trim().ifBlank { if (book.text.isNotBlank()) "parsed" else "pending" }
    val (badgeBg, badgeFg, badgeTxt) = when (status) {
        "parsed" -> Triple(AppColors.greenBg, AppColors.success, "已解析")
        "failed" -> Triple(AppColors.redBg, AppColors.danger, "失败")
        else -> Triple(AppColors.blueBg, AppColors.blue, "待解析")
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // 顶栏（08 号 E1：本页非 hero 页 ⇒ 显示完整标题）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassBackButton(onClick = onBack)
            Text("教材详情", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── E2 信息容器：头部（56dp 类型色块 + 书名 + 作者 + 状态徽标）→ 1dp 分隔 → 键值对 ──
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(blockBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ext.uppercase().take(4), color = blockFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(book.book.ifBlank { "(未命名教材)" }, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("作者：${book.author.ifBlank { "—" }}", fontSize = 13.sp, color = AppColors.textSecondary)
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(badgeBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(badgeTxt, color = badgeFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    BookIntroRow("页数", if (book.pages > 0) "${book.pages} 页" else "—")
                    IntroDivider()
                    BookIntroRow("大小", when {
                        book.sizeBytes > 0 -> formatBytes(book.sizeBytes)
                        book.text.isNotBlank() -> formatDocSize(book.text)
                        else -> "—"
                    })
                    IntroDivider()
                    BookIntroRow("导入时间", book._mt.toIsoDate())
                    IntroDivider()
                    BookIntroRow("所属学段", book.grade.ifBlank { "—" })
                    IntroDivider()
                    BookIntroRow("科目", book.unit.ifBlank { book.lesson.ifBlank { "—" } })
                }
            }

            // ── E3 关联跳转组 ──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("关联")
                BookLinkRow("link", "课标库", "查看相关课标文件") { onOpenCurric() }
                BookLinkRow("link", "备课组", "查看同书备课资源") { onOpenLesson() }
            }

            // ── E4 本书条目清单（圆形序号位 + 查看全部）──
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("本书条目（${sameBookItems.size}）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
                    // 行内文字键「查看全部」（primary 13sp，触控 ≥44dp）
                    Box(Modifier.heightIn(min = 44.dp).clickable { onRead() }, contentAlignment = Alignment.Center) {
                        Text("查看全部", fontSize = 13.sp, color = AppColors.blue)
                    }
                }
                if (sameBookItems.isEmpty()) {
                    EmptyHint("book", "暂无目录", "该教材还没有导入任何章节原文。")
                } else {
                    sameBookItems.forEachIndexed { i, e ->
                        Card(
                            onClick = { onRead() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 32dp 圆形序号位
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${i + 1}", fontSize = 13.sp, color = AppColors.textSecondary)
                                }
                                Text(
                                    listOf(e.unit, e.lesson).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "正文" },
                                    fontSize = 15.sp, color = AppColors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ── E5 删除本书（危险操作：danger 描边、透明底、置底）──
            Box(
                Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Transparent)
                    .border(1.dp, AppColors.danger, RoundedCornerShape(14.dp))
                    .clickable { confirmDelete = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(appPainter("trash"), contentDescription = null, tint = AppColors.danger, modifier = Modifier.size(16.dp))
                    Text("删除本书", color = AppColors.danger, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(4.dp))

            // 底部全文阅读
            Button(
                onClick = onRead,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Icon(appPainter("book"), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" 全文阅读", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除教材") },
            text = { Text("确定删除「${book.book.ifBlank { "未命名教材" }}」？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(book) }) { Text("删除", color = AppColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun IntroDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun BookIntroRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = AppColors.textSecondary, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 14.sp, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BookLinkRow(icon: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 42dp 图标底衬（blueLight 底 + primary link 图标）
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.blueLight), contentAlignment = Alignment.Center) {
                Icon(appPainter(icon), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(desc, fontSize = 12.sp, color = AppColors.textSecondary)
            }
            Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}
