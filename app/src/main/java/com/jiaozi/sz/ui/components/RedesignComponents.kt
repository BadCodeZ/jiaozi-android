package com.jiaozi.sz.ui.components

import com.jiaozi.sz.ui.components.AppPalette

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.reduceMotionNow

// —— 设计图辅助色（语义色，明/暗双值，由 LocalAppSemantic 提供，调用点零改）——
data class AppSemantic(
    val success: Color, val warning: Color, val purple: Color, val danger: Color,
    val blue: Color, val blueLight: Color, val blueBg: Color, val purpleBg: Color,
    val greenBg: Color, val redBg: Color, val bg: Color, val textPrimary: Color,
    val textSecondary: Color, val trackGray: Color,
    /** 🔴 2026-09-19 新增：warning 语义浅底（收集箱/校订「待处理」卡容器）。
     *  此前 01 号只有 greenBg/redBg/blueBg/purpleBg 四支浅底，缺 warning 支 ⇒ 卡1 只能借用中性底，
     *  与高保真「每卡各自语义浅底」不符。补齐后四语义浅底齐备，01 号 tokens 同步登记。 */
    val warningBg: Color,
    /** 🔴 2026-09-20 新增：teal（青蓝）语义色。
     *  背景：09 号 E4「知识卡前导徽章」要求 menu(青)，08 号 `SP-DOC-ROW` / `E10 文件类型色块`
     *  已两处引用「teal 青蓝」（PDF=primary 蓝 / EPUB=purple 紫 / TXT=teal 青蓝），
     *  但 01 号 tokens 漏登记该色、工程内亦无成员 ⇒ 本处补登记（属补漏项，非新增组件）。
     *  色值来源：高保真图 2 左第 4 枚徽章（认知负荷）实色像素高饱和 25% 中位数实测 #1CCCB3，
     *  按本文件既有「设计稿饱和色 → 实现降饱和」口径降到与 blue/purple/danger 同调性。 */
    val teal: Color,
    /** 浮空胶囊（CMP-CAPSULE）底色：亮色深药丸 / 暗色浅药丸，保证暗色主题下对比不丢失 */
    val capsuleBg: Color,
    /** 浮空胶囊前景（文本）色：亮色白 / 暗色近黑 */
    val capsuleFg: Color
)

/**
 * 亮色语义（企鹅蓝体系）。
 *
 * 统一口径：蓝族对齐 Theme.primary(0xFF3B8CF7) / primaryContainer(0xFFEAF1FE)；
 * 背景 / 文本 / 轨道灰对齐 Theme 的中性灰刻度（F7F7F7 / 1A1A1A / 666666 / E8E8E8），
 * 修掉原先「Tailwind 冷灰 + 宝蓝」与「主题中性灰 + 企鹅蓝」同屏两套色系的问题。
 * 状态色（success/warning/danger）保留语义辨识度，仅降饱和以贴合克制的整体调性。
 *
 * 🔴 2026-09-19 按高保真稿校正：原 0xFF305070 实测偏灰暗（H206 S40 L31），
 * 设计稿全站蓝色实测为 #1A5BB5（H214 S75 L41），浅蓝底 #EAF1FE ⇒ 本段同步更新。
 * 🔴 2026-09-22 杰哥裁定「全站换现在这个蓝」：blue 由 #1A5BB5 提亮为 Hero 主体色 #3B8CF7，与 Theme.primary 恒等。
 */
val LightSemantic = AppSemantic(
    success = AppPalette.c_ff2f9e6e, warning = AppPalette.c_ffd98a1f, purple = AppPalette.c_ff7c6bb0, danger = AppPalette.c_ffd64b3f,
    blue = AppPalette.c_ff3b8cf7, blueLight = AppPalette.c_ffeaf1fe, blueBg = AppPalette.c_fff1f6fe,
    purpleBg = AppPalette.c_fff2f0f8, greenBg = AppPalette.c_ffedf7f2, redBg = AppPalette.c_fffcf0ee,
    bg = AppPalette.c_fff7f7f7, textPrimary = AppPalette.c_ff1a1a1a, textSecondary = AppPalette.c_ff666666, trackGray = AppPalette.c_ffe8e8e8,
    warningBg = AppPalette.c_fffdf6ec,
    teal = AppPalette.c_ff2aa294,
    capsuleBg = AppPalette.c_ff1a1a1a, capsuleFg = Color.White
)

/**
 * 暗色语义（企鹅蓝提亮版）：蓝族对齐 Theme 暗态 primary(0xFF6E96BF) / primaryContainer(0xFF14334F)
 *
 * 🔴 2026-10-01 十五校：主色两支随 [com.jiaozi.sz.ui.theme.JiaoziTheme] 的暗态色板同步上移 ——
 *   `blue` #6E96BF → **#4C9DF8**（= Theme 暗态 primary，始终保持"语义 blue ≡ 主题 primary"这条不变式）；
 *   `textPrimary` #E0E0E0 → **#F2F2F2**（= 暗态 onSurface）。
 *   注意：**容器色不动**（blueLight #14334F / blueBg #13283F）—— 它们是「浅蓝底」的暗态对应物，
 *   底色本就该压深以托住前景；只有"在深底上当前景用"的那两支需要提亮。
 */
val DarkSemantic = AppSemantic(
    success = AppPalette.c_ff4dbe8c, warning = AppPalette.c_ffe0a94a, purple = AppPalette.c_ff9e8fcb, danger = AppPalette.c_ffe97266,
    blue = AppPalette.c_ff4c9df8, blueLight = AppPalette.c_ff14334f, blueBg = AppPalette.c_ff13283f,
    purpleBg = AppPalette.c_ff232030, greenBg = AppPalette.c_ff14291f, redBg = AppPalette.c_ff2e1a18,
    bg = AppPalette.c_ff121212, textPrimary = AppPalette.c_fff2f2f2, textSecondary = AppPalette.c_ffb0b0b0, trackGray = AppPalette.c_ff363636,
    warningBg = AppPalette.c_ff2a2118,
    teal = AppPalette.c_ff4fc9ba,
    capsuleBg = AppPalette.c_ffe0e0e0, capsuleFg = AppPalette.c_ff121212
)

/**
 * 语义辅助色（状态色 / 文本色 / 背景色）：随明/暗切换。
 * 设计为普通 object，故非 composable 上下文（如 DrawScope onDraw、普通工具函数）也可直接读取；
 * 暗态由 JiaoziTheme 在挂载时写入 isDark，颜色属性按明暗返回 LightSemantic / DarkSemantic 对应字段。
 */
object AppColors {
    /** 当前是否暗色主题；仅由 JiaoziTheme 写入 */
    var isDark: Boolean = false
        internal set
    private val L get() = LightSemantic
    private val D get() = DarkSemantic
    val success get() = if (isDark) D.success else L.success
    val warning get() = if (isDark) D.warning else L.warning
    val purple get() = if (isDark) D.purple else L.purple
    val danger get() = if (isDark) D.danger else L.danger
    val blue get() = if (isDark) D.blue else L.blue
    val blueLight get() = if (isDark) D.blueLight else L.blueLight
    val blueBg get() = if (isDark) D.blueBg else L.blueBg
    val purpleBg get() = if (isDark) D.purpleBg else L.purpleBg
    val greenBg get() = if (isDark) D.greenBg else L.greenBg
    val redBg get() = if (isDark) D.redBg else L.redBg
    val bg get() = if (isDark) D.bg else L.bg
    val textPrimary get() = if (isDark) D.textPrimary else L.textPrimary
    val textSecondary get() = if (isDark) D.textSecondary else L.textSecondary
    val trackGray get() = if (isDark) D.trackGray else L.trackGray
    val warningBg get() = if (isDark) D.warningBg else L.warningBg
    /** teal 青蓝（2026-09-20 补登记）：知识卡 menu 类前导徽章 / 文档行 TXT 类型色块 */
    val teal get() = if (isDark) D.teal else L.teal
    /** 浮空胶囊底色/前景（明暗双态） */
    val capsuleBg get() = if (isDark) D.capsuleBg else L.capsuleBg
    val capsuleFg get() = if (isDark) D.capsuleFg else L.capsuleFg
}

/**
 * 快捷操作卡配色：四个入口统一为「浅企鹅蓝卡面 + 企鹅蓝图标」。
 * 2026-09-18 按高保真图（统计页「更多」/今日页「快捷入口」）对齐：卡面改为 blueBg 浅蓝，
 * 与白底页面拉开层次；图标徽章用 blueLight，靠图标与文案区分功能。
 */
@Composable
fun actionBgColor(key: String): Color = when (key) {
    "practice", "mock", "wrong", "weak" -> AppColors.blueBg
    else -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
fun actionIconColor(key: String): Color = when (key) {
    "practice", "mock", "wrong", "weak" -> AppColors.blue
    else -> MaterialTheme.colorScheme.primary
}

// ============================================================
// 0. 按压反馈原子 —— CMP-PRESS（2026-09-25 新增）
// ============================================================

/**
 * 按压反馈句柄：`modifier` 挂到目标节点，`interactionSource` 传给可点组件
 * （`Card(onClick=)` / `Modifier.clickable(...)`）。
 */
class PressFeedback internal constructor(
    val interactionSource: MutableInteractionSource,
    val modifier: Modifier
)

/**
 * 统一按压反馈（CMP-PRESS）—— 补 04 / 07 号规范此前**只写在稿面、代码零实现**的条目。
 *
 * **规范来源**
 *  - 04 号 `practice.home` hf：`按压反馈：scaleTo(0.98) + alpha 90%，m.FAST 150ms`
 *  - 07 号 `mine.main`  hf：`整行 surfaceContainerHigh 覆盖 + scaleTo(0.99)`
 *
 * **实现口径**
 *  - 用 `collectIsPressedAsState()` 驱动 `animateFloatAsState`，时长取全局 `Motion.FAST`(150ms)；
 *  - 系统「减少动态效果」开启时经 `Motion.duration()` 退化为 `tween(0)`（瞬时切换），
 *    与前庭敏感 / 低端机口径一致（§39 P3）；
 *  - `highlight` 为 null ⇒ 只做缩放 + 整体 alpha（卡片语义）；
 *    非 null ⇒ 做缩放 + 在内容之下铺一层该色覆盖（列表行语义，容器形状由外层 Card 裁剪）。
 *
 * @param scale     按下时缩放到的比例（0.98 / 0.99）
 * @param alpha     按下时整体不透明度（仅 highlight == null 时生效）
 * @param highlight 按下时叠加的底色；null ⇒ 用 alpha 方案
 */
@Composable
fun rememberPressFeedback(
    scale: Float = 0.98f,
    alpha: Float = 0.9f,
    highlight: Color? = null
): PressFeedback {
    val ctx = LocalContext.current
    val reduce = reduceMotionNow(ctx)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val anim by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(Motion.duration(reduce, Motion.FAST)),
        label = "pressFeedback"
    )
    val base = Modifier.graphicsLayer {
        scaleX = 1f - (1f - scale) * anim
        scaleY = 1f - (1f - scale) * anim
        if (highlight == null) this.alpha = 1f - (1f - alpha) * anim
    }
    val mod = if (highlight == null) base else base.drawBehind {
        if (anim > 0f) drawRect(highlight.copy(alpha = highlight.alpha * anim))
    }
    return PressFeedback(src, mod)
}

// ============================================================
// 1. StatCard —— 核心数据卡（图标徽章 + 标签 + 大数字/单位 [+ 进度条]，左对齐）
// ============================================================

/**
 * 核心数据卡（CMP-STATCARD · default 变体）。**2026-09-18 按高保真图重构为左对齐徽章形态**：
 *
 * ```
 * [徽章] 标签            ← labelInline = true（2~3 列宽卡 / 今日 / 题库）
 * [徽章]                 ← labelInline = false（4 列窄卡 / 统计）
 * 标签
 * 大数字 单位
 * [进度条]
 * ```
 *
 * 三个形态维度（均默认关闭，向后兼容旧调用）：
 *  · [iconBg]     —— 图标徽章底色；null ⇒ 无底色纯图标（旧形态）
 *  · [iconShape]  —— 徽章形状：`CircleShape`（题库/今日 实心圆）/ `RoundedCornerShape(Radius.xs)`（统计 方徽章）
 *  · [containerColor] —— 非 null 时**本卡自带容器**（r20 + padding 14dp），
 *                        页面不再需要私有 shell；浅底色由语义色系给出（blueBg / greenBg / redBg…）
 *
 * @param progress 可选进度条（0f..1f），null ⇒ 无进度条、布局零变化。
 */
@Composable
fun StatCard(
    icon: String,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColors.textPrimary,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBg: Color? = null,
    iconShape: Shape = RoundedCornerShape(Radius.xs),
    iconSize: Dp = 28.dp,
    labelInline: Boolean = false,
    containerColor: Color? = null,
    progress: Float? = null,
    progressColor: Color = iconTint,
    /** 进度条高度：今日页 2 列卡用 4dp（03 号 E2），其余默认 6dp */
    progressHeight: Dp = 6.dp,
    /** 自带容器的左右内边距：4 列窄卡（统计页）收窄到 10dp，避免「486 题」换行 */
    containerHPad: Dp = 14.dp,
    /** 数值字号：4 列窄卡（统计页）降到 20sp，其余默认 26sp */
    valueFontSize: TextUnit = 26.sp
) {
    val body: @Composable ColumnScope.() -> Unit = {
        if (labelInline) {
            // 🔴 间距 8dp→6dp（2026-09-18 实测）：360dp 屏上 3 列窄卡（题库页 E2）内容宽仅 77.25dp，
            //    扣掉 22dp 图标后只剩 55dp，而「错题待清」4 个 12sp 汉字需 48dp —— 8dp 间距下
            //    可用 47.25dp，差 0.75dp 导致被省略成「错题待...」。收紧到 6dp 后 49.25dp 恰好容纳。
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatIconBadge(icon, iconTint, iconBg, iconShape, iconSize)
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            StatIconBadge(icon, iconTint, iconBg, iconShape, iconSize)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = valueColor, fontSize = valueFontSize, maxLines = 1)
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressHeight)
                    .clip(RoundedCornerShape(progressHeight / 2)),
                color = progressColor,
                trackColor = AppColors.trackGray
            )
        }
    }

    if (containerColor != null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = containerHPad, vertical = 14.dp),
                content = body
            )
        }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.Start, content = body)
    }
}

/** StatCard 图标徽章：`bg == null` 时退化为无底色纯图标（旧形态）。 */
@Composable
private fun StatIconBadge(icon: String, tint: Color, bg: Color?, shape: Shape, size: Dp) {
    if (bg == null) {
        Icon(appPainter(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
    } else {
        Box(
            modifier = Modifier.size(size).clip(shape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(appPainter(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.57f))
        }
    }
}

/**
 * StatCard 的 **compact 变体**（CMP-STATCARD · variant = compact）。
 * 形态：无图标、自带浅底容器、数值在上 / 标签在下。
 * 规格：容器 surfaceContainer + r 12dp + padding v12 / h8；数值 titleLarge Bold **18sp**；标签 labelSmall **10sp**。
 * 场景：「一行 3~4 枚」的密集统计（备课组 / 模板库 / 知识库）。
 * 🔴 18sp 与 10sp 属 inline 字号覆写 —— **本函数是 01 号文档 inline_override_whitelist 内唯一合法来源**，
 *    页面侧不得再写同名私有副本（2026-09-18 P1 归一，原 3 份私有实现已删除）。
 */
@Composable
fun StatCardCompact(
    value: String,
    label: String,
    valueColor: Color = AppColors.textPrimary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
        // 🔴 2026-09-22 按 12 号稿校正：标签 10 → **12sp**（稿内实测 ≈14.7sp = 屏宽 3.9%，
        //    真机 10sp = 2.6%，差 47% 超出量测噪声）。取 12sp 同时与 CMP-STATCARD `default`
        //    变体的标签口径（12sp）统一。影响面 = 仅备课组 / 教案模板两页（compact 的唯一调用方）。
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1)
    }
}

/**
 * StatCard 的 **onHero 变体**（CMP-STATCARD · variant = onHero）。
 * 形态：铺在 HeroHeader 渐变上的白字统计格 —— 无自带容器（渐变即底）。
 * 规格：数值 **20sp** Bold 纯白；标签 **11sp** 白 @0.85，格间距 3dp；等分由调用方给的 Modifier.weight(1f) 负责。
 * 场景：教材页 / 课标库的渐变统计带；结算页结果总览四格（走 [valueFontSize] 覆写，见下）。
 * 🔴 本函数是 01 号文档 inline_override_whitelist 内 20sp / 11sp 的唯一合法来源（2026-09-18 P1 归一，原 2 份私有副本已删除）。
 *
 * 🔴 **2026-09-27 新增 [valueFontSize] / [labelFontSize] 可选覆写**（默认值＝原规格，既有调用点零影响）：
 *   结算页「结果总览」为**四格并排**，单格可用宽约 80dp（屏 360dp − 卡内 padding 20dp×2 后四等分）；
 *   「用时」值为长文本（如「12分34秒」= 4 数字 + 2 中文），20sp 实测宽约 88dp ⇒ **会溢出单格被截断**，
 *   故该页覆写为 值 17sp / 标签 12sp（原私有 `SummaryStatCell` 的既有取值，视觉零变化）。
 *   ⇒ 私有副本因此可删除，消除「页内私有格 vs 全局原子」双轨。**新增覆写必须在 02 号 CMP-STATCARD 登记。**
 */
@Composable
fun HeroStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueFontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = valueFontSize, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = labelFontSize, maxLines = 1)
    }
}

/**
 * [StatBand] 的一列：数值 + 可选单位 + 标签 + 可选逐列值色。
 *
 * 🔴 [valueColor] 默认 `null` ⇒ 用 band 默认的 `primary` 蓝。**仅当该列数值表达
 * 『评价性指标』（达成度 / 完备度 / 告警）且阈值已在 01 号 `color.thresholds` 登记**时
 * 才可传入语义色 —— 见 02 号 `CMP-STATCARD.variants.band` 的「逐列着色例外条款」。
 * 当前唯一使用者＝课标库『覆盖率』（01 号 `thresholds.coverage`，90/70）。
 * 纯计数列（课标文件 / 学段 / 学科分类、教材文件 / 教材数 / 已下载）**禁传**。
 */
data class StatBandItem(
    val value: String,
    val label: String,
    val unit: String = "",
    val valueColor: Color? = null
)

/**
 * StatCard 的 **band 变体**（CMP-STATCARD · variant = band，2026-09-19 登记 / 2026-09-20 抽取原子）。
 *
 * 形态：**单条容器承载 N 列**（不是 N 张独立卡）——整条浅蓝底，内部 N 列等分。
 * ```
 * ┌──────────────────────────────────────────┐  primaryContainer 浅蓝底 / r.large 20dp
 * │   12      4       8        5             │  值 primary 20sp Bold（单位 13sp 同基线）
 * │  教材文件  学段   教材数   已下载          │  标签 11sp onSurfaceVariant
 * └──────────────────────────────────────────┘
 * ```
 *
 * 🔴 规格（02 号 `CMP-STATCARD.variants.band` + `size.band_container`）：
 *  · 容器 = **`primaryContainer`**（#EAF1FE / dark #14334F）—— **2026-09-20 像素实证订正**，
 *    原记 `surfaceContainer`（纯白）有误：高保真图实测底色 `(226,240,253)`。
 *  · 值色 = **`primary`**（#3B8CF7，2026-09-22 提亮前为 #1A5BB5）—— 实测 `(7,77,175)`；**不是**白字
 *    （白字属 `onHero` 变体，铺在 hero 渐变上；band 是独立浅色条，与 hero 分离）。
 *  · 圆角 `r.large` 20dp；padding 竖 14dp / 横 16dp；列间距靠 `weight(1f)` 等分。
 *  · 每列纵向：值（20sp Bold）+ 单位（13sp，`padding(bottom = 3.dp)` 对齐基线）→ 3dp → 标签（11sp）。
 *
 * 🔴 **列间竖分隔线**：02 号规范列为「1dp `outlineVariant`」，但 2026-09-20 像素实证显示
 * 高保真图列界（x≈227/354/481）亮度恒为 240、与对照点同形 ⇒ **图上未绘制**。
 * 故本原子默认 `showDivider = false`（贴合图），保留开关供规范侧增强时启用。
 *
 * @param items 列数据（`value` / `label` / 可选 `unit`）；列宽由 `weight(1f)` 等分，故条数即列数。
 * @param showDivider 是否绘制列间 1dp 竖分隔线（默认 false，与高保真图一致）。
 *
 * 🔴 使用纪律：`CMP-STATCARD.forbidden` 第 4/5 条 —— 页面**不得**再自建私有统计条实现，
 *    也**不得**把 band 当成「N 张卡排一行」拼装。教材管理 / 课标库一律走本原子。
 */
@Composable
fun StatBand(
    items: List<StatBandItem>,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0 && showDivider) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 🔴 逐列值色：默认 primary；仅『已登记阈值的评价性指标』列可覆写（见 StatBandItem KDoc）
                    val col = item.valueColor ?: MaterialTheme.colorScheme.primary
                    Text(
                        item.value,
                        fontWeight = FontWeight.Bold,
                        color = col,
                        fontSize = 20.sp,
                        maxLines = 1
                    )
                    if (item.unit.isNotBlank()) {
                        Text(
                            item.unit,
                            color = col,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 3.dp),
                            maxLines = 1
                        )
                    }
                }
                Text(
                    item.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================
// 2. QuickActionCard —— 快捷操作卡（居中徽章 + 标题 + 副文案）
// ============================================================

/**
 * 快捷操作卡（CMP-QUICKACTION · 3col 变体）。
 * **2026-09-18 按高保真图对齐为居中排版**：图标徽章居中在上 → 标题居中 → 副文案居中；
 * 卡面 = [actionBgColor]（浅企鹅蓝 blueBg），徽章 = blueLight + 企鹅蓝图标。
 * 横向排布在 3 列（每列约 110dp）时文字仍可能截断，故 title/desc 均 maxLines = 1 + Ellipsis。
 */
@Composable
fun QuickActionCard(
    icon: String,
    title: String,
    desc: String,
    actionKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = actionBgColor(actionKey)),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(AppColors.blueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(appPainter(icon), contentDescription = null, tint = actionIconColor(actionKey), modifier = Modifier.size(20.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================
// 3. ChapterRow —— 薄弱章节行（排名徽章 + 章节名/百分比 + 进度条 + 详情）
// ============================================================

@Composable
fun ChapterRow(
    rank: Int?,
    title: String,
    detail: String,
    progress: Float,
    percent: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 排名色仅保留「最弱=危险红 / 次弱=警示橙 / 其余=企鹅蓝」三档，去掉原先第 3 名硬编码的琥珀色
    val rankColor = when (rank) {
        1 -> AppColors.danger
        2 -> AppColors.warning
        else -> AppColors.blue
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(rankColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rank", color = rankColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(percent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = rankColor, fontSize = 16.sp, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = rankColor,
                    trackColor = AppColors.trackGray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary, fontSize = 13.sp, maxLines = 1)
            }
            Icon(appPainter("chevron"), contentDescription = null, tint = AppColors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}

// ============================================================
// 3b. SectionTitleDot —— CMP-SECTIONTITLE / dot 变体
// ============================================================

/**
 * 点状分组标题（CMP-SECTIONTITLE · dot 变体）：4dp 主色圆点 + t.titleSmall SemiBold 标题 + 可选尾部计数。
 * 🔴 `08/03` 号规范：**同页圆点标题上限 2 处**，超出请改用 [GroupTitle]（plain 变体）。
 * 今日页（今日任务 / 快捷入口）、统计页、我的页共用本实现，禁止各页私有副本。
 */
@Composable
fun SectionTitleDot(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

// ============================================================
// 3c. NavRowCard —— CMP-LISTROW / nav 变体（任务卡 · 章节卡 · 工具行共用）
// ============================================================

/**
 * 可点导航行卡（CMP-LISTROW · nav 变体）：42dp 圆角图标底衬 → 标题/副文案 → 可选角标 → chevron。
 *
 * 规格（03 / 05 / 07 号共用）：surfaceContainer 实色 + r.medium 20dp + padding 16dp + 投影 2dp；
 * 标题 t.titleSmall SemiBold；副文案 t.bodySmall；chevron 18dp onSurfaceVariant@40%；整卡可点。
 * 🔴 卡内**只允许一个跳转语义** —— 禁止再塞「立即开始」按钮（Card 禁忌落地）。
 * 🔴 02 号：卡片用实色 + 投影，**不描边**（描边唯一例外是 GlassBackButton 发丝边）。
 */
@Composable
fun NavRowCard(
    icon: String,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = AppColors.blue,
    iconBg: Color = AppColors.blueLight,
    /** 可选进度条（0f..1f）：题库章节卡的「已练进度」用，传 null 不渲染 */
    progress: Float? = null,
    progressColor: Color = iconTint,
    /** 标题行右侧角标槽（任务类型角标 / 题量角标） */
    badge: @Composable (() -> Unit)? = null,
    /**
     * 第二行副文案（可选）：练习首页「继续练习」需要「上次：…」+「N 题待完成」两行。
     * 传 null 与旧行为完全一致（向后兼容）。
     */
    subtitle2: String? = null,
    /**
     * 卡面底色（可选）。默认 null = surfaceContainer。
     * 练习首页按高保真图给「继续练习 / 模考入口」接浅底语义卡时传入。
     */
    containerColor: Color? = null,
    /** chevron 颜色（可选）。默认 null = onSurfaceVariant@40%；模考入口按图用 danger 红。 */
    chevronTint: Color? = null,
    /**
     * 🔴 2026-09-30 新增（修实测缺陷：标题被无差别截断）。
     *
     * 默认 `1`（既有 20+ 调用点零回归）。今日页任务卡传 `2`：其标题是**完整章节名**
     * （如「一、教育基础知识和基本原理」14 字），配 titleSmall + 右侧类型角标后
     * 可用宽仅约 148dp、需约 224dp ⇒ 真机渲染成「一、教育基础知…」，
     * **用户点进去前不知道练哪一章**。
     *
     * ⚠️ 传 2 时该卡会比其他卡高一行（约 22dp）—— 今日任务卡是独立圆角卡、非等高网格，
     * 以「信息完整」优先于「行高整齐」。
     */
    titleMaxLines: Int = 1
) {
    // 🔴 2026-09-25 补 CMP-PRESS：04 号 practice.home hf「按压反馈：scaleTo(0.98) + alpha 90%，m.FAST 150ms」
    //    此前全工程 0 处按压缩放（仅 M3 默认 ripple），属规范已写、代码未落地。本件是练习首页 / 题库章节卡
    //    / 我的页工具行的共用载体 ⇒ 在此一处接入，三页同时达标。
    val press = rememberPressFeedback(scale = 0.98f, alpha = 0.9f)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press.modifier),
        interactionSource = press.interactionSource,
        colors = CardDefaults.cardColors(containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(appPainter(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = titleMaxLines,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    badge?.invoke()
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (subtitle2 != null) {
                    Text(
                        subtitle2,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = progressColor,
                        trackColor = AppColors.trackGray
                    )
                }
            }
            Icon(
                appPainter("chevron"),
                contentDescription = null,
                tint = chevronTint ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 小圆角语义角标（任务类型 / 题量）：r.full + padding 6×2dp + t.labelSmall 11sp。
 * 配色按 03 号 E7：优先练习 blueBg/blue｜章节补强 purpleBg/purple｜错题复习 redBg/danger｜到期复习 greenBg/success。
 */
@Composable
fun MiniBadge(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ============================================================
// 4. GroupTitle / SettingRow —— 我的页功能分组
// ============================================================

/**
 * 分组标题（CMP-SECTIONTITLE / `group` 变体）。
 *
 * 🔴 2026-09-21 按高保真稿（设置页 / 关于页）校正：左侧加 **3×14dp 圆角竖条（primary）**，
 * 文本由 `textSecondary` 灰字改为 `onSurface` 深色（实测稿内标题为近黑 #000215）。
 * 本原子被 6 处共用（我的 / 设置 / 关于 / 统计 / 今日 / 练习），故为全局形态变更。
 */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.blue)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 设置行（CMP-LISTROW / `setting` 变体）。
 *
 * 🔴 2026-09-21 按高保真稿（设置页）校正两处：
 * 1. 前导图标由「surfaceVariant 浅底 + primary 着色图标」改为**实色语义块 + 白图标**
 *    （尺寸/圆角/图标比直接复用 `IconBadge`：28dp / r9 / 0.57）；
 * 2. 当前值由「标题下方的副标题」改为**行尾右侧灰字**（实测稿内值色 #62666F 中性灰，
 *    非 primary）—— 稿内所有设置行均为「图标 + 标题 + ⋯ + 当前值 + chevron」单行三槽。
 */
@Composable
fun SettingRow(
    icon: String,
    title: String,
    value: String? = null,
    /**
     * 🔴 2026-09-26 新增**内容槽第二行**（副文案）。
     *
     * 闭环关于页 `AboutRow` 自建副文案、与 [SettingSelectRow] / [SettingSwitchRow] 的 `summary`
     * 不同构的问题：02 号规范 `CMP-LISTROW.anatomy` 内容槽本就允许「主标题 + 副文案（maxLines ≤ 2）」。
     *
     * 默认 `null`（仅主标题）：既有调用点（设置主页 / 学习目标 / 本地备份 / AI 配置 / 新手引导）
     * 渲染路径一字不变（`summary == null` 走原单行分支）。
     */
    summary: String? = null,
    modifier: Modifier = Modifier,
    iconBg: Color = AppColors.blue,
    trailing: @Composable (() -> Unit)? = null,
    /**
     * 🔴 2026-09-26 新增**末行抑制**（闭环 11 号规范 `divider_exception_20260925.open_items` 第 1 条
     * 「SettingRow 末行之下分隔线未抑制（无末行感知）」）。
     *
     * 本行自带底部分隔线用于分隔**同卡内的相邻行**，但**卡内最后一行**之下不该有线
     * （否则卡片底部悬空一条线，或与卡片圆角边界叠成双线）。
     * 组内末行由调用方显式传 `showDivider = false`。
     */
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    // 🔴 2026-09-23 第 4 批②：包裹一层 Column，行尾加**实色 1dp 分隔线**（outlineVariant 非 alpha0.5），
    // 与子设置页 Prefs.kt 的 PrefRow 范式对齐；横向 padding 由 4dp 统一为 16dp，使本行文案与同组
    // 嵌套 SettingsSection 内 PrefRow（16dp 内距）左缘对齐（见 SettingsCard 14dp 内距 + 本 16dp = 30dp 同齐）。
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon = icon, bg = iconBg)
            if (summary == null) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // 内容槽第二行：与 SettingSelectRow / SettingSwitchRow 同构（CMP-LISTROW 允许主标题 + 副文案）
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.textSecondary,
                    maxLines = 1
                )
            }
            trailing?.invoke()
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 设置单选行（CMP-LISTROW / `setting` 变体 · 单选型）。
 *
 * 🔴 2026-09-26 新增，闭环「二级页**外壳**已统一、页内行控件仍是新旧两套体系混用」的半成品状态。
 * 此前 8 个设置二级页里的单选行走旧体系 `Prefs.kt::InlineExpandSelect`（**无前导图标**、
 * 行尾值用 `colorScheme.primary`、summary 用 `colorScheme.outline`），与页内另一半
 * `SettingRow`（实色徽章 + 灰值）同屏并存。
 *
 * 本函数把单选行**升到新体系**：行骨架与 [SettingRow] **同口径**（`IconBadge` 28dp 实色块 + 白图标、
 * 标题 `bodyLarge`/Medium 占 `weight(1f)`、行尾值 `labelMedium` / `AppColors.textSecondary`、
 * 实色 1dp `HorizontalDivider(outlineVariant)`、`showDivider` 末行抑制），
 * 交互沿用就地展开下拉（点击在行右浮出面板、不重排布局，选中项主色 + 对勾）。
 *
 * 与旧 `InlineExpandSelect` 的三点差异（均为「升到新体系」的必然结果）：
 *  1. 前导补**实色图标徽章**（旧实现无前导件 ⇒ 与同卡 SettingRow 左缘不对齐）；
 *  2. 行尾当前值由 `primary` 蓝改为 `AppColors.textSecondary` 中性灰
 *     （对齐 `SettingRow` 的「稿内值色 #62666F 中性灰，非 primary」口径）；
 *  3. 副文案（summary）由 `colorScheme.outline` 改为 `AppColors.textSecondary`。
 *
 * 兼容红线：本函数**纯 UI**，不触碰任何 SharedPreferences / meta key，
 * `onSelect` 由调用方原样绑定既有 setter（见 `SP-SETTINGS-COMPAT`）。
 *
 * @param icon    前导图标名（限 02 号 `CMP-ICON` 白名单）
 * @param title   行主标题
 * @param summary 可选副文案（CMP-LISTROW 内容槽第二行，maxLines=1）
 * @param options 选项（value → 展示文案），选中项展示文案即行尾值
 * @param selected 当前值；若不在 options 内则原样回显（未知值不伪造）
 * @param onSelect 选中回调（即时生效：调用方直接写 setter，无保存按钮）
 * @param iconBg  徽章实色底
 * @param showDivider 是否绘制行底分隔线（卡内末行传 false）
 */
@Composable
fun SettingSelectRow(
    icon: String,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconBg: Color = AppColors.blue,
    showDivider: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    // 动效收敛：跟随系统「减少动态效果」把 chevron 旋转时长压到 0
    val reduce = reduceMotionNow(LocalContext.current)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(Motion.duration(reduce, Motion.FAST)),
        label = "chevron-rotation"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon = icon, bg = iconBg)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 右侧锚点：当前值 + chevron 同处一个 Box，DropdownMenu 以它为锚在原位置靠右浮出
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        options.firstOrNull { it.first == selected }?.second ?: selected,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.textSecondary,
                        maxLines = 1
                    )
                    Icon(
                        appPainter("chevron"),
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = AppColors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 200.dp)
                ) {
                    options.forEach { (value, label) ->
                        val isSel = selected == value
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = if (isSel) {
                                {
                                    Icon(
                                        appPainter("check"),
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                onSelect(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * 设置开关行（CMP-LISTROW / `setting` 变体 · 开关型）。
 *
 * 🔴 2026-09-26 新增，与 [SettingSelectRow] 同批：把旧体系 `Prefs.kt::SwitchPref`（无前导图标）
 * 与 WebDav 页裸写的 `Row + Column + Switch` 统一升到新体系行骨架。
 *
 * 骨架/分隔线/末行抑制口径与 [SettingRow] 完全一致；`summary` 走内容槽第二行。
 * 兼容红线同 [SettingSelectRow]：纯 UI，`onCheckedChange` 由调用方绑定既有 setter。
 */
@Composable
fun SettingSwitchRow(
    icon: String,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    iconBg: Color = AppColors.blue,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBadge(icon = icon, bg = iconBg)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// ============================================================
// 5b. HubChip —— 内容中枢宫格单元（图标 + 标签，用于题库/我的/练习的归类入口）
// ============================================================

@Composable
fun HubChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(AppColors.blueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(appPainter(icon), contentDescription = null, tint = AppColors.blue, modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppColors.textPrimary,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

// ============================================================
// 5. RecommendCard —— 为你推荐卡（浅蓝渐变 + 装饰圆 + 开始按钮）
// ============================================================

@Composable
fun RecommendCard(
    title: String,
    desc: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {
            // 装饰性半透明圆（右侧，两个叠加）—— 改用品牌色低透明叠加，
            // 原先的 Color.White 在浅蓝→白的渐变底上几乎不可见，暗色下又过亮。
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(100.dp)
                    .offset(x = 30.dp, y = -20.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(70.dp)
                    .offset(x = 20.dp, y = 15.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), CircleShape)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(appPainter("star"), contentDescription = null, tint = AppColors.warning, modifier = Modifier.size(20.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(desc, style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary, fontSize = 14.sp)
                }
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(buttonText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(appPainter("chevron"), contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ============================================================
// 6. IconBadge / MasteryBadge —— 知识卡前导徽章 + 掌握度角标
//    （09 号 E4 / E5；2026-09-20 依高保真图新增，02 号组件库已登记 CMP-ICONBADGE / CMP-MASTERYBADGE）
// ============================================================

/**
 * 前导图标徽章（CMP-ICONBADGE）。**实色圆角块 + 白色线条图标**，28dp 方徽章（r 9dp）。
 *
 * 🔴 图文口径冲突与裁定（2026-09-20，已记入 02 号 `CMP-ICONBADGE.revision`）：
 *  · 09 号 E4 `spec` 文字写作「徽章底色＝对应语义浅底、图标同色」；
 *  · 但高保真图 2 左 4 枚属性徽章（素质教育的…/新课改…/问题解决/认知负荷）**像素级实测**
 *    均为「实色圆角块（中心高饱和）+ 白色线条图标（笔画 #F0FDFF）」，
 *    与同页统计三卡徽章口径**一致**（见 KnowledgeScreen L144-152 既有结论）。
 *  ⇒ 按「图为准」铁律取图口径：实色底 + 白图标。本函数与 `StatIconBadge` 像素同构。
 *
 * ⚠️ 图未覆盖（09 号 `not_covered_by_fidelity`）：前导徽章的**配色规则**（类型固定 vs 掌握度耦合）。
 *    图 2 仅 3 个样本（brain 蓝 / bulb 紫 / target 红），无法排除耦合可能。
 *    本轮按「配色随知识点类型**固定**」实现，配色规则待杰哥确认。
 *
 * @param icon 图标名（限 02 号 `CMP-ICON` 白名单：brain / bulb / target / menu …）
 * @param bg   徽章实色底（语义主色：blue / purple / danger / teal）
 */
@Composable
fun IconBadge(
    icon: String,
    bg: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    shape: Shape = RoundedCornerShape(Radius.xs),
    iconTint: Color = Color.White
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(appPainter(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.57f))
    }
}

/** 掌握度四态（09 号 E5）。三档色映射与图谱页热力节点**同源**（09 号 `shared_patterns[0]`：全 App 唯一）。 */
enum class MasteryState(val label: String) {
    /** 正确率 ≥ 0.8 —— s.success / 浅绿底 */
    MASTERED("已掌握"),
    /** 正确率 ≥ 0.5 —— s.warning / 浅黄底 */
    LEARNING("学习中"),
    /** 正确率 > 0 且 < 0.5 —— s.danger / 浅红底 */
    WEAK("未掌握"),
    /** 无作答记录 —— 中性（surfaceVariant + onSurfaceVariant） */
    UNTOUCHED("未学习")
}

/**
 * 由 (right, wrong) 派生掌握度四态。阈值与 `GraphScreen` 热力色**同源**
 * （`acc >= 0.8f -> good` / `acc >= 0.5f -> warn` / else `bad` / 无记录 `none`），
 * 不得另立一套阈值（09 号 `shared_patterns[0]` 明示「全 App 唯一映射」）。
 */
fun masteryOf(right: Int, wrong: Int): MasteryState {
    val n = right + wrong
    if (n <= 0) return MasteryState.UNTOUCHED
    val acc = right.toFloat() / n
    return when {
        acc >= 0.8f -> MasteryState.MASTERED
        acc >= 0.5f -> MasteryState.LEARNING
        else -> MasteryState.WEAK
    }
}

/**
 * 掌握度角标（CMP-MASTERYBADGE）：`r.full` + padding 横 8dp / 竖 3dp + t.labelSmall **11sp** Medium。
 *
 * ⚠️ 与 [IconBadge] 的「类型色」是**两个正交维度**：徽章＝类型（固定不变），角标＝掌握度（随作答变化），
 *    禁止用同一套色板混用（09 号 E5 `spec` 明文）。
 */
@Composable
fun MasteryBadge(state: MasteryState, modifier: Modifier = Modifier) {
    val fg: Color
    val bg: Color
    when (state) {
        MasteryState.MASTERED -> { fg = AppColors.success; bg = AppColors.greenBg }
        MasteryState.LEARNING -> { fg = AppColors.warning; bg = AppColors.warningBg }
        MasteryState.WEAK -> { fg = AppColors.danger; bg = AppColors.redBg }
        MasteryState.UNTOUCHED -> {
            fg = MaterialTheme.colorScheme.onSurfaceVariant
            bg = MaterialTheme.colorScheme.surfaceVariant
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            state.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 知识点类型 → 前导徽章图标名（brain / bulb / target / menu，均已在 02 号 `CMP-ICON` 白名单）。
 * 未知类型兜底 brain（与 `Models.kt` 的默认值一致）。
 */
fun knowledgeTypeIcon(type: String): String = when (type) {
    "bulb", "target", "menu" -> type
    else -> "brain"
}

/**
 * 知识点类型 → 前导徽章实色底。
 * brain 蓝（识记理解）/ bulb 紫（方法策略）/ target 红（问题解决）/ menu 青（分类脉络）。
 * ⚠️ menu 用的 `AppColors.teal` 系 2026-09-20 补登记（08 号 `SP-DOC-ROW` / `E10` 早已引用「teal 青蓝」，
 *    01 号 tokens 漏登记）—— 见 01 号 `color.revision`。
 */
@Composable
fun knowledgeTypeColor(type: String): Color = when (type) {
    "bulb" -> AppColors.purple
    "target" -> AppColors.danger
    "menu" -> AppColors.teal
    else -> AppColors.blue
}
