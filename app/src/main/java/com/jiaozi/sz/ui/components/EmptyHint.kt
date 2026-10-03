package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 空状态引导：自研 SVG 图标（appPainter，无 emoji）+ 标题 + 一句话操作引导 + 可选主按钮。
 * 用于知识库/课标库/教材库/备课/收集箱/章节健康/题库/统计等首次进入或数据为空时，降低上手成本。
 *
 * @param action 可选主行动按钮槽（如题库空态的「章节管理」、统计空态的「去练习」）。
 *               默认 null ⇒ 既有调用点行为、间距、留白完全不变。
 */
@Composable
fun EmptyHint(
    icon: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 44.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            appPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        action?.invoke()
    }
}

/**
 * 内联错误态：数据加载异常 / 源缺失时展示，**语义区别于 [EmptyHint]（空≠错，错要可恢复）**。
 *
 * 与 EmptyHint 的三点差异（对照入 ux 审计①）：
 *   · tint 用**琥珀/危险语义色**（[AppColors.warning]/danger），不是纯色 outline ⇒ 一眼认出"出错了"；
 *   · 标题固定带错误措辞，副文案给出"发生了什么 + 怎么救"；
 *   · 固定主按钮 [retryText]（如「重试」）外搭可选副按钮 [secondary]（如「去下载」）。
 *
 * @param retry      主行动（必填，错误态必须有出口，否则用户卡死）
 * @param retryText  主按钮文案，默认「重试」
 * @param secondary  可选副按钮（如「去题库」）
 */
@Composable
fun ErrorInline(
    icon: String,
    title: String,
    hint: String,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
    retryText: String = "重试",
    secondary: (@Composable () -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.danger.copy(alpha = 0.06f))
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            appPainter(icon),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = AppColors.danger
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.textPrimary)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = retry) { Text(retryText) }
            secondary?.invoke()
        }
    }
}
