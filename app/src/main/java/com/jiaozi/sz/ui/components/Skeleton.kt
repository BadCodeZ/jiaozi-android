package com.jiaozi.sz.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.jiaozi.sz.ui.reduceMotionNow

/**
 * 骨架屏通用灰块原子（依据 03 号 `today.skeleton` / E1–E4 归一）。
 *
 * 规范绑定：
 *  - 灰块填充 = `AppColors.trackGray`（亮 #E8E8E8 / 暗 #2D2D2D，scheme 感知，避免暗色下高亮刺眼）
 *  - 圆角 `radius` **必须与所替代的真实组件同值**（hero/卡片 20dp、统计大卡 24dp、小条目 8~12dp）
 *  - 扫光 = 透明 → 白 8% → 透明，横向线性 1200ms（03 号 E4「扫光层」）
 *  - `reduceMotionNow == true` ⇒ 只留静态灰块、不做扫光（03 号 F3 / E4 spec）
 *
 * 🔴 03 号 `cross_screen_consistency` 明确要求：**其他页补骨架必须复用本原子**，
 * 不得再写页面私有的灰块实现（今日页原私有 ShimmerBox 已收敛到此处）。
 *
 * 用法：`ShimmerBox(Modifier.fillMaxWidth(), boxHeight = 100.dp, radius = 20.dp)`
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    boxHeight: Dp,
    radius: Dp
) {
    val reduced = reduceMotionNow(LocalContext.current)
    // 无条件创建动画（不在 if 内 remember，避免分支切换导致重组结构变化）；
    // reduced 时仅在绘制阶段跳过扫光层。
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    Box(
        modifier
            .height(boxHeight)
            .clip(RoundedCornerShape(radius))
            .background(AppColors.trackGray)
            .drawWithContent {
                drawContent()
                if (reduced) return@drawWithContent
                val w = size.width
                val band = w * 0.45f
                val startX = sweep * (w + band) - band
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        startX = startX,
                        endX = startX + band
                    )
                )
            }
    )
}
