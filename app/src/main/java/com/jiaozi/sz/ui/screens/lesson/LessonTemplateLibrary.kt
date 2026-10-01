package com.jiaozi.sz.ui.screens.lesson

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.hubDragToScroll
import com.jiaozi.sz.ui.components.StatCardCompact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.data.model.BUILTIN_TEMPLATES
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.HubBar

/** 模板库：内置骨架 + 我的模板 */
@Composable
internal fun LessonTemplateLibrary(
    appVm: AppViewModel,
    onBack: () -> Unit,
    onUse: (LessonFields) -> Unit
) {
    val templates by appVm.lessonTemplates.collectAsStateWithLifecycle()
    var delId by remember { mutableStateOf<String?>(null) }

    // 顶部常驻块：Hero + 统计卡不随滚动（折叠已停用），返回栏与筛选 chips 常驻
    val scrollState = rememberScrollState()

    // 两段式布局（与知识库 / 课标库同款）：顶栏 + Hero + 紧凑栏固定在外框，
    // 只有下方「筛选 / 模板列表」滚动 ⇒ 收起后紧凑栏恒钉顶部。
    // ⚠️ 固定带/HubBar 用 hubDragToScroll 直通手势（折叠已停用，外框不挂监听）
    // 🔴 2026-09-22 按 12 号稿 1:1 复刻：Hero 改沉浸式（返回件内联进蓝区）、
    //    Hero + 统计卡 同属顶部常驻块；修复上一轮「信息带下沉」把 Hero 误搬进滚动区、
    //    导致「模板库 · N」紧凑栏被顶到 Hero 上方的问题。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {

        // ══════════ 顶部常驻：Hero（沉浸）+ 统计卡 ══════════
        CollapsingTopBlocks(spacing = 12.dp, modifier = Modifier.hubDragToScroll(scrollState)) {
            HeroHeader(
                title = "教案模板",
                // 🔴 2026-09-23 按 12 号 E2 + 高保真稿：本页 Hero **只有标题**，无副标题（原副标题为多余项，已删）
                subtitle = "",
                // 🔴 2026-09-23：装饰图标改走 decorIcon —— 传 onBack 时旧 icon 参数会被左端槽吞掉
                decorIcon = appPainter("bars"),
                immersive = true,
                statusBarInset = statusBarTop,
                onBack = onBack
            )
            // 统计卡（全局 CMP-STATCARD · compact 变体）
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCardCompact("${BUILTIN_TEMPLATES.size}", "内置模板", AppColors.blue)
                StatCardCompact("${templates.size}", "我的模板", AppColors.blue)
                StatCardCompact("0", "本周使用", AppColors.blue)
                StatCardCompact("0%", "使用率", AppColors.blue)
            }
        }

        // 筛选状态提升到 HubBar 之前：chips 逻辑现只喂**滚动区首行的筛选栏**。
        //（2026-09-25 晚：原「展开态筛选行 / 收起态紧凑栏」两态互斥写法已随折叠状态机退场。）
        var tplFilter by remember { mutableStateOf("全部") }
        val tplChips: @Composable RowScope.() -> Unit = {
            listOf("全部", "新授", "复习", "实验", "公开课").forEach { s ->
                FilterChip(selected = s == tplFilter, onClick = { tplFilter = s }, label = { Text(s, fontSize = 13.sp) })
            }
        }

        // 常驻栏（HubBar）：标题·条数（与备课组 hub 同构）
        // 🔴 2026-09-25 晚：原 `CollapsedHubBar(persistent = true)` 已随折叠状态机退场改名为 `HubBar`。
        HubBar(
            title = "模板库 · ${BUILTIN_TEMPLATES.size + templates.size}",
            modifier = Modifier.padding(horizontal = 16.dp).hubDragToScroll(scrollState)
        )

        // ── 滚动区：只有下方内容滚动，顶栏 / Hero / 紧凑栏保持可见 ──
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // （原 Hero 渐变卡 + 4 统计卡已上移至顶部常驻块 · 2026-09-22）

        // 筛选栏（真实筛选：模板类型；「实验」前缀匹配「实验探究」）
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 🔴 2026-09-22 按稿补：本页 chips 行最左有漏斗图标（与备课组 hub 同构；原先漏画）
            Icon(appPainter("filter"), contentDescription = null, tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
            tplChips()
        }
        // 内置模板按 Triple.first(type) 过滤，「我的模板」按 LessonTemplate.type 过滤
        val builtins = BUILTIN_TEMPLATES.filter { tplFilter == "全部" || it.first.startsWith(tplFilter) }
        val myTpls = templates.filter { tplFilter == "全部" || it.type.startsWith(tplFilter) }

        // 内置骨架模板
        Text("内置骨架模板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        builtins.forEach { (type, bone, meat) ->
            Card(Modifier.fillMaxWidth().clickable { onUse(LessonFields(type = type, processText = meat)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(AppColors.blueLight, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(appPainter("lesson"), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f), Arrangement.spacedBy(4.dp)) {
                        Text("$type · $bone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(meat, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                    Icon(appPainter("edit"), contentDescription = "用此骨架", tint = AppColors.blue, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 我的模板
        Text("我的模板（${myTpls.size}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        if (myTpls.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.bg), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(appPainter("bars"), contentDescription = null, tint = AppColors.textSecondary, modifier = Modifier.size(40.dp))
                    if (templates.isEmpty()) {
                        Text("还没有自己的模板", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("在编辑器中点「存为模板」即可把当前教案存为可复用模板。", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary, fontSize = 13.sp)
                    } else {
                        Text("当前筛选下没有匹配的模板。", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
        myTpls.forEach { t ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(AppColors.greenBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(appPainter("star"), contentDescription = null, tint = AppColors.success, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).clickable { onUse(t.fields.copy(grade = t.grade, type = t.type)) }, Arrangement.spacedBy(4.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("${t.grade} · ${t.type}", style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp)
                    }
                    IconButton(onClick = { delId = t.id }, modifier = Modifier.size(32.dp)) {
                        Icon(appPainter("trash"), contentDescription = "删除模板", tint = AppColors.danger, modifier = Modifier.size(18.dp))
                    }
                }
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

// 2026-09-18 P3 死代码清理：原先尾部的 private fun TemplateStatCard(label, value, color)
// 与备课组 / 知识库两份同名实现逐字重复 ⇒ 收敛为全局 StatCardCompact（调用点已换），此处删除。
