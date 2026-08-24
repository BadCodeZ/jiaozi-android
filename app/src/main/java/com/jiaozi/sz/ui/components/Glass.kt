package com.jiaozi.sz.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 透明玻璃组件（2026-08-16，V2.34 → V2.34.1 热修）。
 *
 * V2.34 曾尝试用 Android 原生 RenderEffect（API31+）对磨砂颗粒做真模糊，
 * 但在真机上因 GPU/驱动负载过重导致 UI 卡住（"一直动不了"）。
 * V2.34.1 回退为纯半透明 + 顶部高光 + 发丝边框： still "四周透明"（内容从玻璃后透出），
 * 且不依赖 RenderEffect，旧机型/小米 HyperOS 也不卡。
 * 后续如要真磨砂，应改用预模糊静态纹理或按机型白名单开启 RenderEffect，而非逐帧模糊。
 */
object GlassTokens {
    /** 玻璃圆角（导航药丸沿用 28.dp） */
    val Radius: Dp = 28.dp
    /** 玻璃投影高度 */
    val Elevation: Dp = 10.dp
    /**
     * 玻璃底色不透明度：0.5 让底层内容清晰透出。
     * 参考软件顶栏约 0.86，但移动端悬浮药丸要更透才显「玻璃」，否则看着像实底。
     */
    const val BG_ALPHA = 0.5f
    /** 发丝边框不透明度：勾勒玻璃边缘，比背景更关键（决定「透明面板」的轮廓） */
    const val BORDER_ALPHA = 0.16f
    /** 顶部高光强度（浅色更亮、深色克制） */
    const val SHEEN_LIGHT = 0.14f
    const val SHEEN_DARK = 0.06f
}

/** 玻璃面背景色：主题感知（深/浅色自动适配），透出底层内容 */
@Composable
fun glassBackgroundColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = GlassTokens.BG_ALPHA)

/** 玻璃发丝边框色 */
@Composable
fun glassBorderColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = GlassTokens.BORDER_ALPHA)

/** 玻璃顶部高光渐变（白→透明），模拟玻璃反射 */
@Composable
private fun glassSheen(): Brush {
    val a = if (isSystemInDarkTheme()) GlassTokens.SHEEN_DARK else GlassTokens.SHEEN_LIGHT
    return Brush.verticalGradient(
        0f to Color.White.copy(alpha = a),
        1f to Color.White.copy(alpha = 0f)
    )
}

/**
 * 把任意内容包成「玻璃容器」：圆角 + 半透明底色（透出内容） + 顶部高光（玻璃反射）
 * + 发丝边框 + 柔和投影。可直接替换原本的 Material3 Surface。
 *
 * 图层顺序（从底到顶）：
 *   1) 半透明底色：透出底层页面内容（滚动时内容从玻璃后透出 = 四周透明）；
 *   2) 顶部高光：模拟玻璃反射；
 *   3) 内容（如导航按钮）置于最顶层。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(GlassTokens.Radius),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, glassBorderColor()),
        shadowElevation = GlassTokens.Elevation,
        tonalElevation = 0.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            // 1) 半透明底色：透出底层页面内容（四周透明的基础）
            Box(Modifier.fillMaxSize().background(glassBackgroundColor()))
            // 2) 玻璃顶部高光：模拟玻璃反射
            Box(Modifier.fillMaxSize().background(glassSheen()))
            // 3) 内容置于最顶层
            content()
        }
    }
}
