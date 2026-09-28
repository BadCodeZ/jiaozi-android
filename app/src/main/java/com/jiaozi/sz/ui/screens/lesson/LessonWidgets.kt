package com.jiaozi.sz.ui.screens.lesson

import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.AppChipStyle
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 分段选择器（学段/学科/课型/板书三型等） */
@Composable
fun SegmentedRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) },
                    // 分段型口径（选中 = primaryContainer 浅蓝底 + primary 蓝字）走统一封装，
                    // 与「筛选型」（选中 = 实心蓝白字）区分开
                    style = AppChipStyle.Container
                )
            }
        }
    }
}

/**
 * 分区卡片（骨/肉/皮三区分组）。
 *
 * 🔴 2026-09-21 按高保真稿补：标题行右侧加**整体折叠箭头**（稿内展开态为 `^`），
 * 点标题行即收起整区 —— 18 个字段的长表单靠它做二级收纳。
 */
@Composable
internal fun ZoneCard(title: String, itemsCount: Int? = null, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(true) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 🔴 2026-09-21 按高保真稿：收起态在标题与 chevron 之间补「N 项」计数（展开态不显示，避免与内容重复）
                if (!open && itemsCount != null) {
                    Text(
                        "$itemsCount 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.textSecondary.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    appPainter("chevron"),
                    contentDescription = if (open) "收起该区" else "展开该区",
                    tint = AppColors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp).rotate(if (open) -90f else 90f)
                )
            }
            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}

/**
 * 十二要素「行编辑」：默认折叠为内联行，点按展开编辑。已有内容自动展开，空白默认折叠。
 *
 * 🔴 2026-09-21 按高保真稿重排为**行卡三槽**：`标题（深色）+ 摘要/占位（灰）+ chevron`。
 * 原实现是「标签（灰）+ 右侧『编辑 ▾』文字链 + 已填摘要」——稿内没有文字链，
 * 收起态直接以占位文案（如「请输入课标依据…」）占位、右侧用 chevron 表达可展开。
 */
@Composable
internal fun LessonField(label: String, hint: String, rows: Int, value: String, onValue: (String) -> Unit) {
    var expanded by remember(value) { mutableStateOf(value.isNotBlank()) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (value.isNotBlank()) {
                            value.lines().first().let { if (it.length > 32) it.take(32) + "…" else it }
                        } else hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    appPainter("chevron"),
                    contentDescription = if (expanded) "收起" else "展开编辑",
                    tint = AppColors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                OutlinedTextField(
                    value = value, onValueChange = onValue, placeholder = { Text(hint) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = if (rows > 1) rows else 1,
                    maxLines = if (rows > 1) rows + 2 else 1,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
