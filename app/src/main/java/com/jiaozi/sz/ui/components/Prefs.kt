package com.jiaozi.sz.ui.components
import com.jiaozi.sz.ui.components.appPainter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
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
