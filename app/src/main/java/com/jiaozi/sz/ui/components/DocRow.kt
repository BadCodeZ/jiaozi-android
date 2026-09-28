package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 文档列表行通用组件（第 4 批①：收敛 [BookScreen] 的 BookRow 与 [CurricScreen] 的 CurricDocRow
 * 的 90% 同构重复）。
 *
 * 圆角统一 16dp（与设置组 / 备课组一致，见 `13_全局通用组.json` 的 `revision_20260921`：
 * 搜索页 r14 结果卡退役 → CMP-GROUPCARD 16dp，跨页圆角归一）。
 *
 * 两屏仅在前导块（教材＝类型色块 / 课标＝图标块）与是否存在状态徽标、chevron 上不同，
 * 这些差异通过参数化 [leading] / [statusBadge] / [showChevron] 保留，避免破坏各自现有视觉。
 *
 * @param leading  前导 40dp 块（类型色块 / 图标块由调用方自行提供，保持两屏视觉差异）
 * @param name     标题（15sp SemiBold）
 * @param meta     副信息（12sp secondary）
 * @param onClick  整行点击
 * @param statusBadge  状态徽标文案；null 则不显示（课标屏无状态）
 * @param onDelete 删除回调；null 则不显示删除键（图标统一为语义红，与全局 trash 一致）
 * @param showChevron 是否显示行尾 chevron（教材屏有跳转，课标屏无）
 */
@Composable
fun DocRow(
    leading: @Composable () -> Unit,
    name: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusBadge: String? = null,
    onDelete: (() -> Unit)? = null,
    showChevron: Boolean = false,
    highlighted: Boolean = false
) {
    // 状态徽标配色（与教材屏原映射一致）：已解析 green / 失败 red / 待解析 blue
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) AppColors.blueBg else MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 前导：40dp 块（类型色块 / 图标块由调用方提供）
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 状态徽标（statusBadge 非空时显示；配色与本屏原映射一致）
            if (statusBadge != null) {
                val (badgeBg, badgeFg) = when (statusBadge) {
                    "已解析" -> AppColors.greenBg to AppColors.success
                    "失败" -> AppColors.redBg to AppColors.danger
                    else -> AppColors.blueBg to AppColors.blue
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(badgeBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(statusBadge, color = badgeFg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // 行尾删除键：40dp 触控区（不与整行点击冲突）；语义红与全局 trash 一致
            if (onDelete != null) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(appPainter("trash"), contentDescription = "删除", tint = AppColors.danger, modifier = Modifier.size(18.dp))
                }
            }
            if (showChevron) {
                Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
            }
        }
    }
}
