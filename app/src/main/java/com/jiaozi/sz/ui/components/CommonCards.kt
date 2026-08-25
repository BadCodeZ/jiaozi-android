package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.theme.AppGradients

/**
 * 渐变 Hero 头部（对齐 Mine / 各主屏）：hero 渐变底 + 可选圆底图标 + 白字标题/副标题，右上可选操作按钮（导入/添加/清空）。
 * 比例规范：标题 titleLarge（对齐 Mine 头部 22sp）、副标题 bodyMedium、行距 6dp、padding 20dp；图标为 46dp 白底圆 + 26dp 图标。
 */
@Composable
fun HeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    action: @Composable (() -> Unit)? = null,
    showPenguin: Boolean = false
) {
    val themePack by LocalAppVm.current.themePack.collectAsStateWithLifecycle()
    Box(
        modifier
            .fillMaxWidth()
            .background(AppGradients.hero(themePack, isSystemInDarkTheme()), RoundedCornerShape20)
            .padding(20.dp)
    ) {
        // 企鹅剪影装饰：仅 showPenguin=true 时绘制（默认只在首页展示），Canvas 用 matchParentSize 不撑开布局
        if (showPenguin) {
            androidx.compose.foundation.Canvas(
                Modifier.matchParentSize().padding(end = 8.dp, bottom = 4.dp),
                onDraw = {
                    val bodyR = size.height * 0.22f
                    val headR = bodyR * 0.6f
                    val px = size.width - bodyR * 1.6f
                    val py = size.height - bodyR * 1.4f
                    val fill = Color.White.copy(alpha = 0.15f)

                    // 身体（椭圆）
                    drawOval(fill, topLeft = Offset(px - bodyR, py - bodyR * 0.7f), size = androidx.compose.ui.geometry.Size(bodyR * 2f, bodyR * 1.8f))
                    // 白肚皮
                    drawOval(Color.White.copy(alpha = 0.08f), topLeft = Offset(px - bodyR * 0.5f, py - bodyR * 0.3f), size = androidx.compose.ui.geometry.Size(bodyR * 0.8f, bodyR * 1.1f))
                    // 头（圆）
                    drawCircle(fill, headR, Offset(px, py - bodyR * 0.8f))
                    // 喙（橙色小三角形）
                    val beak = Path().apply {
                        moveTo(px + headR * 0.4f, py - bodyR * 0.85f)
                        lineTo(px + headR * 1.1f, py - bodyR * 0.8f)
                        lineTo(px + headR * 0.4f, py - bodyR * 0.75f)
                        close()
                    }
                    drawPath(beak, Color(0xFFE67E22).copy(alpha = 0.3f))
                    // 眼睛（小白点）
                    drawCircle(Color.White.copy(alpha = 0.4f), headR * 0.2f, Offset(px - headR * 0.25f, py - bodyR * 0.85f))
                    drawCircle(Color.White.copy(alpha = 0.4f), headR * 0.2f, Offset(px + headR * 0.3f, py - bodyR * 0.85f))
                    // 翅膀（左）
                    drawPath(Path().apply {
                        moveTo(px - bodyR * 0.9f, py - bodyR * 0.2f)
                        quadraticBezierTo(px - bodyR * 1.3f, py + bodyR * 0.1f, px - bodyR * 0.7f, py + bodyR * 0.3f)
                        lineTo(px - bodyR * 0.5f, py + bodyR * 0.1f)
                        close()
                    }, fill)
                    // 翅膀（右）
                    drawPath(Path().apply {
                        moveTo(px + bodyR * 0.9f, py - bodyR * 0.2f)
                        quadraticBezierTo(px + bodyR * 1.3f, py + bodyR * 0.1f, px + bodyR * 0.7f, py + bodyR * 0.3f)
                        lineTo(px + bodyR * 0.5f, py + bodyR * 0.1f)
                        close()
                    }, fill)
                }
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(46.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, Modifier.size(26.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            }
            action?.invoke()
        }
    }
}

/** Miuix 风格分组小标题（主色） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(start = 4.dp, top = 4.dp))
}

/** 2×2 统计格（对齐 Mine 概览） */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * surfaceVariant 数据项卡（对齐关于模块同款）：左图标(主色) + 标题(主色) + 副标题(次要) + 右侧 trailing（删除/箭头等）。
 * 点击整卡触发 onClick；trailing 用于次要操作（如删除按钮），不触发整卡点击。
 */
@Composable
fun ItemCard(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Card(
        modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(painter = icon, contentDescription = title, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            trailing()
        }
    }
}

private val RoundedCornerShape20 = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
