package com.jiaozi.sz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
