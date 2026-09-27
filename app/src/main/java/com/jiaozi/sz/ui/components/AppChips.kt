package com.jiaozi.sz.ui.components

import androidx.compose.material3.FilterChip as M3FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * chip 选中态口径（2026-09-21 按 11/12/13/08 号高保真稿确立）。
 *
 * 🔴 稿里 chip 分**两类**，**不得混用同一套色**：
 * - [Solid]     筛选型（设置页分类、备课组/教案模板筛选、搜索分类、教材/课标/章节筛选…）
 *                ⇒ 选中 = **实心 primary 蓝底 + 白字**
 * - [Container] 分段型（新建教案 学段/学科/课型、收集箱 Tab、校订 Tab…）
 *                ⇒ 选中 = **primaryContainer 浅蓝底 + primary 蓝字**
 *
 * 两类**未选态一致**：`surfaceVariant` 浅灰底 + `textSecondary` 灰字 + **无边框**（稿实测灰底无描边，
 * 而 M3 默认是「透明底 + outline 描边」的幽灵态，故此处统一覆盖）。
 */
enum class AppChipStyle { Solid, Container }

/**
 * 全站统一 chip —— **替代直接调用 M3 `FilterChip`**。
 *
 * 用法同 M3（`selected` / `onClick` / `label`），差别只有两点：
 * 1. 未选态改为「浅灰实底 + 无边框」（见 [AppChipStyle] 说明）；
 * 2. 选中态按 [style] 走两套口径。
 *
 * 为什么做成**同名**：调用点有近 20 处且签名一致，同名可让调用点零改动、只换 import
 * （`androidx.compose.material3.FilterChip` → `com.jiaozi.sz.ui.components.FilterChip`），
 * 降低误改风险。
 *
 * ⚠️ 本封装**不接受** `colors` / `border` 覆盖：需要新形态请先扩展 [AppChipStyle]，
 * 不要在调用点各自传色（避免又出现「同一语义两套色」）。
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppChipStyle = AppChipStyle.Solid
) {
    M3FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            // 未选：浅灰实底 + 次级灰字（稿口径）
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = AppColors.textSecondary,
            selectedContainerColor = when (style) {
                AppChipStyle.Solid -> AppColors.blue
                AppChipStyle.Container -> MaterialTheme.colorScheme.primaryContainer
            },
            selectedLabelColor = when (style) {
                AppChipStyle.Solid -> Color.White
                AppChipStyle.Container -> MaterialTheme.colorScheme.primary
            }
        ),
        // 无边框：稿内 chip 是「实底 + 无描边」；M3 默认会给未选态描一圈 outline
        border = null
    )
}
