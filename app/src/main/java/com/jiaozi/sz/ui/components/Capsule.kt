package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * 胶囊形悬浮提示（CMP-CAPSULE）：暗底 #1A1A1A @ 0.92 药丸 + 主色点缀 + 文本。
 * 视觉对齐 HyperOS「焦点通知/灵动岛」——深色毛玻璃药丸，应用内自绘。
 *
 * 🔴 14 号规范 `overlays.capsule` 记两个变体：
 * - **状态提示（带 8dp 状态点）** —— 本组件的默认形态，灵动岛常驻指示用
 *   （前台可见才计：站内切 Tab 继续累计；真正退后台即暂停冻结；
 *    回前台时若离开超过 `StudyTimerService.BACKGROUND_RESET_TIMEOUT_MS`
 *    则整段作废归零重计，见 xiaomi.StudyTimerService）。
 * - **纯文本提示（无状态点）** —— 稿面示例「添加成功」。
 *   2026-09-21 按 14 号高保真稿补：新增 [showDot] 开关，原实现强制渲染状态点，
 *   无法表达稿面的纯文本变体。
 *
 * @param showDot 是否显示前导状态点。常驻状态用 `true`（默认）；一次性操作反馈
 *   （如「添加成功」「已保存」）用 `false`。
 */
@Composable
fun Capsule(
    text: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showDot: Boolean = true
) {
    Row(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color(0xFF1A1A1A).copy(alpha = 0.92f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showDot) {
            // 实时状态点（上岛常驻指示）；纯文本变体不渲染
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color.White
        )
    }
}
