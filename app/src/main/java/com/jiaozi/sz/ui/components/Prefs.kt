package com.jiaozi.sz.ui.components
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer

/**
 * MIUI / HyperOS 风格设置组件，复刻开源库 Miuix（top.yukonga.miuix.kmp）的设置页范式：
 * 小标题分组 + 卡片容器 + 统一的偏好行（标题/副标题 + 右侧开关或箭头）。
 * 因本工程 Kotlin 1.9 / Compose BOM 2024.06 与 Miuix(Kotlin 2.x) 不兼容，故手动还原其视觉，
 * 而非直接依赖该库，以保证已能跑通的构建稳定。
 */

/** 分组：左侧主色小标题 + 卡片容器，内部由若干 PrefRow 组成。 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(content = content)
        }
    }
}

/**
 * 偏好行：左侧标题 + 可选副标题，右侧自定义 trailing（开关 / 值 / 箭头）。
 * 点击可选（用于导航型偏好）。组内默认带细分隔线。
 */
@Composable
fun PrefRow(
    title: String,
    summary: String? = null,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) trailing()
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/** 带开关的偏好行。 */
@Composable
fun SwitchPref(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    PrefRow(
        title = title,
        summary = summary,
        showDivider = showDivider,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

/** 导航型偏好行：右侧箭头，点击进入二级页。 */
@Composable
fun NavPref(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    PrefRow(
        title = title,
        summary = summary,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            Icon(
                appPainter("chevron"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/**
 * 芯片组偏好：标题 + 下方一排可选芯片（用于主题模式 / 美术主题包 / 字体大小等多选项）。
 * 选项多于 3 个也能整排容纳（窄屏自动换行）。
 */
@Composable
fun PrefChipGroup(
    title: String,
    summary: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    showDivider: Boolean = true
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) }
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

/**
 * 原地下拉式选择偏好（Material3 DropdownMenu 风，对齐椒盐笔记设置展开范式，全 App 统一操作元素）：
 * 标题 + 可选副标题（summary）在左，当前值 + 右侧 chevron（展开时旋转 90°）在右；点击后在该行原位置
 * 靠右浮出小下拉面板（盖在下方内容之上、不重排布局），选中项主色文字 + 右侧对勾，点选即填充并收起。
 * 适用于所有「单选设置项」（主题 / 配色 / 字体 / 学科 / AI 服务商 / WebDAV 预设等）。
 */
@Composable
fun InlineExpandSelect(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    showDivider: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevron-rotation"
    )
    Box(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (summary != null) {
                        Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                // 右侧锚点：当前值 + chevron 同处一个 Box，DropdownMenu 以它为锚在原位置靠右浮出
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            options.firstOrNull { it.first == selected }?.second ?: selected,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            appPainter("chevron"),
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.widthIn(min = 200.dp)
                    ) {
                        options.forEach { (value, label) ->
                            val isSel = selected == value
                            DropdownMenuItem(
                                text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                trailingIcon = if (isSel) {
                                    {
                                        Icon(
                                            appPainter("check"),
                                            contentDescription = "已选中",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onSelect(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            if (showDivider) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

