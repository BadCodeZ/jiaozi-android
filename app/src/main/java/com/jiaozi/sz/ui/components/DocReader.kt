package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 原文全文阅读（对齐高保真图 1-3「课标全文阅读」）。
 *
 * 课标库 / 教材库共用：顶栏标题 + 第 x/N 页 + 进度条 + 上一页/下一页 + 章节导航。
 * 纯展示组件，不持有任何数据（分页按固定字数切分，纯内存计算）。
 */
@Composable
fun DocReader(
    title: String,
    subtitle: String,
    text: String,
    onClose: () -> Unit
) {
    val pages = remember(text) { paginateText(text) }
    var page by remember(text) { mutableStateOf(0) }
    val cur = page.coerceIn(0, pages.lastIndex)

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 关闭阅读器：统一为左上角圆形磨砂返回件（P2）
            GlassBackButton(onClick = onClose)
            Column(Modifier.weight(1f)) {
                Text("全文阅读", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(title, fontSize = 13.sp, color = AppColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${cur + 1}/${pages.size}", fontSize = 13.sp, color = AppColors.textSecondary)
        }
        LinearProgressIndicator(
            progress = { (cur + 1).toFloat() / pages.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)),
            color = AppColors.blue,
            trackColor = AppColors.trackGray
        )
        Text(
            subtitle,
            fontSize = 13.sp,
            color = AppColors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                pages[cur],
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = AppColors.textPrimary
            )
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = { if (cur > 0) page = cur - 1 }, enabled = cur > 0) {
                Icon(appPainter("chevron"), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" 上一页")
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("章节导航", fontSize = 13.sp, color = AppColors.textSecondary)
            }
            TextButton(onClick = { if (cur < pages.lastIndex) page = cur + 1 }, enabled = cur < pages.lastIndex) {
                Text("下一页 ")
                Icon(appPainter("chevron"), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** 按固定字数分页（默认每页 600 字）。 */
fun paginateText(text: String, perPage: Int = 600): List<String> {
    if (text.isBlank()) return listOf("(空)")
    val pages = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        pages.add(text.substring(i, minOf(i + perPage, text.length)))
        i += perPage
    }
    return pages
}

/** 文档体积：小于 1KB 显示 B，否则 KB（按 UTF-8 字节数近似）。 */
fun formatDocSize(text: String): String {
    val b = text.toByteArray(Charsets.UTF_8).size
    return if (b < 1024) "$b B" else "${b / 1024} KB"
}
