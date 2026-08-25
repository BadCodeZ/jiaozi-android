package com.jiaozi.sz.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 悬浮导航容器（2026-08-16，V2.35.1 对齐设计稿）。
 *
 * 设计来源：UI重设计预览.html 第56-57行底部导航规范：
 *   background: surface-2（实色，非透明）；box-shadow: shadow-md；圆角 28px。
 * 不使用 RenderEffect / BlurMaskFilter / 半透明 / 高光渐变 / 发丝边框。
 * 目标：轻量、不卡、在所有机型上视觉一致。
 */
object NavTokens {
    /** 导航药丸圆角 */
    val Radius: Dp = 28.dp
    /** 导航药丸高度 */
    val Height: Dp = 60.dp
    /** 投影高度（柔和悬浮感） */
    val Elevation: Dp = 8.dp
}

/**
 * 轻量悬浮导航容器：实色底 + 圆角药丸 + 柔和投影。
 * 直接用于底部导航栏、设置面板等需要"浮起"的组件。
 *
 * 与旧 GlassSurface 的区别：
 *   - 旧版：半透明 alpha 0.5 + 顶部高光渐变 + 1dp 发丝边框 = 玻璃拟态（重、机型差异大）
 *   - 新版：实色 surfaceContainerLow 底色 + 纯投影 = 设计稿一致（轻、稳定）
 */
@Composable
fun NavSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(NavTokens.Radius),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = NavTokens.Elevation,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

// ---- 向后兼容别名（AppNav.kt 等已用 GlassSurface） ----

/** @deprecated 使用 [NavSurface] 替代。保留此别名避免编译报错，后续迁移完毕后删除。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(NavTokens.Radius),
    content: @Composable () -> Unit
) = NavSurface(modifier, shape, content)
