@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * AI 出题（04 号 `practice.aiGen`）。
 *
 * 从 PracticeScreen.kt 拆分而来。2026-09-18 改造练习首页后：
 * - 练习首页「AI 生成」入口不再内嵌面板，改为 `nav.navigate("aigen")` 跳本页；
 * - 旧的 `ChapterPicker` 已删除 —— 其职能由 `PracticeSetupScreen`（路由 `practicesetup`）的
 *   「学科范围 + 题量 + 选择章节」区块完整覆盖，属去重后的死代码清理。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.AiGenViewModel
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.LocalPracticeVm
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.SectionTitleDot

/**
 * AI 生成题目页。
 *
 * 顶栏返回由全局 `FloatingBackButton` 承担（`secondaryTitles["aigen"] = "AI 生成"`），
 * 本页不再自建顶栏，避免出现「悬浮返回胶囊 + 页内标题」两套返回件。
 *
 * 🔴 外层**不加** `verticalScroll`：`AiGenPanel` 内部预览区自带 `LazyColumn`，
 *    同向嵌套滚动会触发「无限高度测量」崩溃（历史红线）。
 *    本页用 `weight(1f)` 给面板一个有界高度，由面板自己滚动。
 */
@Composable
fun AiGenScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val vm: PracticeViewModel = LocalPracticeVm.current

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        SectionTitleDot("生成配置", trailing = "先预览 · 后入库")
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f)) {
            AiGenPanel(appVm, vm, onLaunched = { nav.popBackStack() })
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** AI 出题面板：选科目/学科/数量，生成后预览审阅，确认再入库（对齐网页端「AI 生题后先预览」） */
@Composable
internal fun AiGenPanel(appVm: AppViewModel, vm: PracticeViewModel, onLaunched: () -> Unit = {}) {
    val aiVm: AiGenViewModel = viewModel()
    val aiState by aiVm.state.collectAsStateWithLifecycle()
    val aiKey by appVm.aiKey.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val subjects = listOf("科一", "科二", "科三")
    var subject by remember { mutableStateOf("科三") }
    var count by remember { mutableStateOf("10") }
    // 预览选中态：包含集合（默认全选），用 included id 集合表示
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(aiState.preview) {
        selected = aiState.preview.map { it.id }.toSet()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
            subjects.forEach { s ->
                FilterChip(selected = subject == s, onClick = { subject = s }, label = { Text(BankStore.shortName(s)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = count, onValueChange = { count = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("数量") }, modifier = Modifier.width(100.dp), singleLine = true
            )
            Button(
                onClick = { aiVm.preview(appVm.aiProvider.value, aiKey, subject, if (subject == "科三") disc else "", count.toIntOrNull() ?: 10, appVm.aiModel.value) },
                enabled = !aiState.generating
            ) { Text(if (aiState.generating) "生成中…" else "生成题目") }
        }
        if (aiKey.isBlank()) {
            Text("未配置 AI Key：将生成内置「离线样例」预览，仅供试用（不会污染同步）。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        aiState.error?.let { Text("出错：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }

        // 预览 / 审阅（默认全选，可逐题取消或整体取消/全选）
        if (aiState.preview.isNotEmpty()) {
            val allIds = aiState.preview.map { it.id }
            val allSelected = selected.size == allIds.size
            Text(
                "AI 已生成 ${aiState.preview.size} 题，请审阅后入库（默认全选）：",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(if (allSelected) "取消全选" else "全选") {
                    selected = if (allSelected) emptySet() else allIds.toSet()
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { aiVm.commit(aiState.preview.filter { it.id in selected }) },
                    enabled = selected.isNotEmpty()
                ) { Text("确认入库（${selected.size}）") }
                ActionButton("放弃") { aiVm.discard() }
            }
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(aiState.preview.size, contentType = { "aiprev" }) { i ->
                    val q = aiState.preview[i]
                    val checked = q.id in selected
                    Card(Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = if (it) selected + q.id else selected - q.id
                                })
                                Column(Modifier.weight(1f)) {
                                    Text("${BankStore.shortName(q.subject)} · ${q.chapter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(q.q, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (q.opt.isNotBlank()) {
                                Text(q.opt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("答案：${if (q.answer.isBlank()) "（主观题，自判）" else q.answer}", style = MaterialTheme.typography.labelSmall)
                            if (!q.analysis.isNullOrBlank()) {
                                Text("解析：${q.analysis}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        } else if (aiState.committed > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已入库 ${aiState.committed} 题", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ActionButton("去练习") {
                    vm.startUserBank(subject, if (subject == "科三") disc else "")
                    // 本页是二级路由，起练后需退回练习 Tab 才能看到答题会话
                    onLaunched()
                }
            }
        }
    }
}
