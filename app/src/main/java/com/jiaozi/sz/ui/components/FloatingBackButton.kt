package com.jiaozi.sz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.xiaomi.Haptic

/**
 * 统一返回键原子（2026-09-18，P2「返回键样式统一」）。
 *
 * 形态与质感规格：
 *  - 形态：左上角【圆形悬浮件】，直径 44dp（CircleShape，非圆角方形）；
 *  - 质感：磨砂玻璃，由四层合成 ——
 *      ① 羽化雾面层：同色圆经 [blur] 得到的雾面扩散（API 31+ 走硬件 RenderEffect，
 *         低于 31 时 [blur] 为静默空操作，该层退化为被 ② 完全覆盖的实心圆，不影响布局与可读性）；
 *      ② 半透明玻璃底：surfaceContainerLow @0.80（与底部导航 NavSurface 同色值）；
 *      ③ 1dp 发丝高光边：outlineVariant @0.75（玻璃边缘）；
 *      ④ 投影：8dp，与 [NavTokens.Elevation] 完全一致；
 *  - 交互：Haptic.tick 触感反馈 + primary 企鹅蓝箭头；
 *  - 无障碍：跟随系统「减少动态效果」，开启时去掉投影与羽化，瞬时响应。
 *
 * 与导航栏保持一致的依据：见 Glass.kt —— NavTokens(Radius 28 / Height 60 / Elevation 8) 与
 * NavSurface(实色 surfaceContainerLow + 纯投影)。此处沿用同一色值与同一投影高度，
 * 仅把实色降为半透明并叠加羽化层，在不新增任何依赖、不改变「轻量、不卡、机型一致」底线的前提下取得磨砂玻璃观感。
 */
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "back",
    contentDescription: String = "返回",
    diameter: Dp = 44.dp
) {
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)
    Box(modifier.size(diameter)) {
        // ① 羽化雾面层（低版本自动降级为空操作）
        Box(
            Modifier
                .matchParentSize()
                .blur(if (rm) 0.dp else 8.dp, BlurredEdgeTreatment.Unbounded)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f), CircleShape)
        )
        // ②③④ 玻璃主圆：半透明底 + 发丝高光边 + 与导航栏同高度的投影
        Box(
            Modifier
                .matchParentSize()
                .shadow(
                    elevation = if (rm) 0.dp else NavTokens.Elevation,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.16f),
                    clip = false
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.80f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f), CircleShape)
                .clickable {
                    Haptic.tick(ctx)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                appPainter(icon),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 悬浮返回键（二级界面统一入口，2026-09-18 起为「左上角圆形磨砂件 + 标题胶囊」）。
 *
 * 设计要点：
 * - 不再占用 Scaffold 固定顶栏高度，作为 overlay 叠加在内容之上（AppRoot 已给二级页预留 top padding 56dp）；
 * - 返回键为左上角【圆形磨砂悬浮件】（[GlassBackButton]），标题紧随其右、同样以磨砂胶囊承载，
 *   玻璃语言与底部导航一致；已带 HeroHeader 的二级屏由 HeroHeader 承担标题，此处只留圆形返回件
 *   （showTitle=false），不再出现「空大白条」形态；
 * - 整体高度 = 44 + 6*2 = 56dp，与 AppRoot 为二级页预留的 padding(top = 56.dp) 完全吻合，挂载点无需改动；
 * - 沉浸式答题（练习页）为一 Tab 且由自身渲染返回键，不经过此处。
 */
@Composable
fun FloatingBackButton(nav: NavHostController, title: String, showTitle: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassBackButton(onClick = { nav.navigateUp() })
        if (showTitle && title.isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
