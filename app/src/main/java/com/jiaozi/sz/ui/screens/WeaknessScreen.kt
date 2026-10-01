package com.jiaozi.sz.ui.screens

import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.EmptyHint
import com.jiaozi.sz.ui.components.FilterChip
import com.jiaozi.sz.ui.components.GlassBackButton
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.HubBar
import com.jiaozi.sz.ui.components.StatCardCompact
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.AppViewModel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.local.WeaknessEntity
import kotlinx.coroutines.launch

/**
 * 薄弱点攻坚（独立模块，与备课教案彻底分家）。
 *
 * 设计动机（2026-09-30）：原「练→备补强」把错题硬套进备课（教案）壳，名实不符——
 * 备课是科三教学设计练习，错题复盘应是轻量「标题 + 科目 + 正文」笔记。
 * 现拆为独立二级页：练习结算页「错题生成攻坚笔记」注入种子，本屏消费并落 `weakness` 表；
 * 也可手动新建。仅本地存储（不参与信封跨端同步，与进度同口径）。
 *
 * 内部结构机（与 LessonScreen 同款）：hub（中枢列表）/ edit（轻量编辑器）。
 */
@Composable
fun WeaknessScreen(nav: NavHostController) {
    var view by remember { mutableStateOf("hub") }
    var editTarget by remember { mutableStateOf<WeaknessEntity?>(null) }
    // 练习结算页注入的攻坚种子（消费后清空）
    var seedTitle by remember { mutableStateOf("") }
    var seedSubject by remember { mutableStateOf("") }
    var seedDisc by remember { mutableStateOf("") }
    var seedBody by remember { mutableStateOf("") }
    var seedFromExamId by remember { mutableStateOf("") }

    fun resetSeed() { seedTitle = ""; seedSubject = ""; seedDisc = ""; seedBody = ""; seedFromExamId = "" }

    val appVm = LocalAppVm.current
    val pendingSeed by appVm.pendingWeaknessSeed.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSeed) {
        val s = pendingSeed
        if (s != null) {
            seedTitle = s.title; seedSubject = s.subject; seedDisc = s.disc
            seedBody = s.body; seedFromExamId = s.fromExamId
            editTarget = null; view = "edit"
            appVm.clearPendingWeaknessSeed()
        }
    }

    when (view) {
        "edit" -> WeaknessEditor(
            appVm = appVm,
            target = editTarget,
            initialTitle = seedTitle,
            initialSubject = seedSubject,
            initialDisc = seedDisc,
            initialBody = seedBody,
            initialFromExamId = seedFromExamId,
            onBack = { view = "hub"; editTarget = null; resetSeed() },
            onSaved = { view = "hub"; editTarget = null; resetSeed() }
        )
        else -> WeaknessHub(
            appVm = appVm,
            onBack = { nav.navigateUp() },
            onNew = { editTarget = null; resetSeed(); view = "edit" },
            onEdit = { w -> editTarget = w; resetSeed(); view = "edit" }
        )
    }
}

/** 攻坚中枢：Hero + 统计卡 + 列表（两段式，与知识库/课标库/备课组同款）。 */
@Composable
internal fun WeaknessHub(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (WeaknessEntity) -> Unit
) {
    val items by appVm.repo.allWeaknessFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = appVm.viewModelScope
    val scrollState = rememberScrollState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // ══════════ 顶部常驻：Hero（沉浸）+ 统计卡 ══════════
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(scrollState)) {
            HeroHeader(
                title = "薄弱点攻坚",
                subtitle = "错题复盘与补强，独立笔记本",
                decorIcon = appPainter("target"),
                immersive = true,
                statusBarInset = statusBarTop,
                onBack = onBack
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCardCompact(items.size.toString(), "攻坚笔记", AppColors.danger)
                StatCardCompact("0", "本周新增", AppColors.danger)
                StatCardCompact(items.count { it.fromExamId.isNotBlank() }.toString(), "来自练习", AppColors.danger)
                StatCardCompact("0%", "待补强", AppColors.danger)
            }
        }

        // ── 常驻栏（HubBar）：标题·条数 + 新建 ──
        HubBar(
            title = "薄弱点攻坚 · ${items.size}",
            actions = {
                IconButton(onClick = onNew, modifier = Modifier.size(36.dp)) {
                    Icon(appPainter("plus"), contentDescription = "新建攻坚笔记", modifier = Modifier.size(20.dp), tint = AppColors.danger)
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp).hubDragToScroll(scrollState)
        )

        // ── 滚动区：列表（空态引导）──
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (items.isEmpty()) {
                EmptyHint(
                    "target",
                    "还没有攻坚笔记",
                    "在练习结算页点「错题生成攻坚笔记」一键转存错题归集，或点右上「+」手动新建。"
                )
            }
            items.forEach { w ->
                val meta = buildList {
                    if (w.subject.isNotBlank()) add(w.subject)
                    if (w.chapter.isNotBlank()) add(w.chapter)
                    if (w.disc.isNotBlank()) add(w.disc)
                }.joinToString(" · ").ifBlank { "未分类" }
                Card(
                    Modifier.fillMaxWidth().clickable { onEdit(w) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.Top) {
                        Box(Modifier.size(56.dp).background(AppColors.blueLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(appPainter("target"), contentDescription = null, tint = AppColors.danger, modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
                            Text(w.title.ifBlank { "(未命名)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(meta, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 11.sp)
                            if (w.body.isNotBlank()) {
                                Text(w.body, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            if (w.fromExamId.isNotBlank()) {
                                Text("来源：${w.fromExamId}", style = MaterialTheme.typography.labelSmall, color = AppColors.danger, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { scope.launch { appVm.repo.deleteWeakness(w.id) } }, modifier = Modifier.size(32.dp)) {
                            Icon(appPainter("trash"), contentDescription = "删除", tint = AppColors.danger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 轻量编辑器：标题 + 科目 +（科三学科）+ 正文。补强笔记不需要十二要素教案骨架。 */
@Composable
internal fun WeaknessEditor(
    appVm: AppViewModel,
    target: WeaknessEntity?,
    initialTitle: String = "",
    initialSubject: String = "",
    initialDisc: String = "",
    initialBody: String = "",
    initialFromExamId: String = "",
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = appVm.viewModelScope
    var title by remember { mutableStateOf(target?.title ?: initialTitle.ifBlank { "新攻坚笔记" }) }
    var subject by remember { mutableStateOf(target?.subject ?: initialSubject.ifBlank { "科一" }) }
    var disc by remember { mutableStateOf(target?.disc ?: initialDisc) }
    var body by remember { mutableStateOf(target?.body ?: initialBody) }
    var fromExamId by remember { mutableStateOf(target?.fromExamId ?: initialFromExamId) }
    var delTarget by remember { mutableStateOf<WeaknessEntity?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    fun onTitleChange(v: String) { title = v; dirty = true }
    fun onBodyChange(v: String) { body = v; dirty = true }
    fun onDiscChange(v: String) { disc = v; dirty = true }
    fun onSubjectChange(v: String) { subject = v; dirty = true }

    BackHandler(enabled = true) { if (dirty) showExitConfirm = true else onBack() }

    Column(Modifier.fillMaxSize()) {
        // ── 固定顶栏：返回 + 标题 + 保存 ──
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            GlassBackButton(onClick = { if (dirty) showExitConfirm = true else onBack() })
            Text(if (target == null) "新建攻坚笔记" else "编辑攻坚笔记", style = MaterialTheme.typography.titleLarge)
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (title.isNotBlank()) {
                                val now = System.currentTimeMillis()
                                val ent = WeaknessEntity(
                                    id = target?.id ?: "W$now",
                                    title = title.trim(),
                                    subject = subject,
                                    chapter = "",
                                    disc = if (subject == "科三") disc else "",
                                    body = body,
                                    fromExamId = if (target != null) target.fromExamId else fromExamId,
                                    createdAt = target?.createdAt ?: now,
                                    _mt = now
                                )
                                scope.launch { appVm.repo.upsertWeakness(ent) }
                                onSaved()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(appPainter("check"), contentDescription = "保存", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── 滚动内容区 ──
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { onTitleChange(it) }, label = { Text("标题 *") }, placeholder = { Text("如：错题补强 · 科一 · 9.30") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            // 科目选择（科一 / 科二 / 科三）
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("科目", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary, fontSize = 13.sp, modifier = Modifier.width(48.dp))
                listOf("科一", "科二", "科三").forEach { s ->
                    FilterChip(selected = s == subject, onClick = { onSubjectChange(s) }, label = { Text(s, fontSize = 13.sp) })
                }
            }

            // 科三学科（仅科三显示）
            if (subject == "科三") {
                OutlinedTextField(disc, { onDiscChange(it) }, label = { Text("学科（科三）") }, placeholder = { Text("如：美术") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(body, { onBodyChange(it) }, label = { Text("攻坚笔记正文") }, placeholder = { Text("记录错题归集、错因、补强思路与计划") }, minLines = 8, modifier = Modifier.fillMaxWidth())

            if (fromExamId.isNotBlank()) {
                Text("来源：$fromExamId", style = MaterialTheme.typography.labelSmall, color = AppColors.danger, fontSize = 13.sp)
            }

            if (target != null) {
                OutlinedButton(onClick = { delTarget = target }, modifier = Modifier.fillMaxWidth()) { Icon(appPainter("trash"), contentDescription = null, tint = AppColors.danger); Text(" 删除此笔记", color = AppColors.danger) }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("未保存修改？") },
            text = { Text("当前攻坚笔记尚未保存，退出将丢失本次修改。") },
            confirmButton = { TextButton(onClick = { showExitConfirm = false; onBack() }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("继续编辑") } }
        )
    }
    if (delTarget != null) {
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text("删除攻坚笔记") },
            text = { Text("确定删除《${delTarget!!.title.ifBlank { "(未命名)" }}》？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { scope.launch { appVm.repo.deleteWeakness(delTarget!!.id) }; delTarget = null; onBack() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { delTarget = null }) { Text("取消") } }
        )
    }
}
