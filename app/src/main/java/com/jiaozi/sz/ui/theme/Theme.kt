package com.jiaozi.sz.ui.theme

import com.jiaozi.sz.ui.components.AppPalette

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiaozi.sz.ui.components.AppColors

/**
 * 教资备考平台主题（HyperOS 风格 + 企鹅蓝主色）。
 *
 * 配色/圆角/字体刻度 1:1 参考开源库 Miuix（top.yukonga.miuix.kmp，Apache-2.0）。
 * 因本工程 Kotlin 1.9 / Compose BOM 2024.06 与 Miuix(Kotlin 2.x) 不兼容，故手动还原其 token。
 *
 * - 主色：统一「企鹅蓝」（亮 0xFF3B8CF7 / 暗 0xFF6E96BF）。2026-09-22 按杰哥裁定把亮色档由 0xFF1A5BB5 提亮为 Hero 主体色 0xFF3B8CF7（全站一蓝）；更早 2026-09-19 曾由 0xFF305070 校正为 0xFF1A5BB5（原值偏灰暗）；全站唯一品牌色，不再提供多余美术主题包；
 *   用户可在设置里手动开启“跟随系统壁纸取色”（dynamicColor）覆盖主色。
 * - 字体：使用 FontFamily.Default（系统默认）。
 * - 圆角：卡片默认 20dp（Material3 medium），大容器 28dp。
 */

// —— 企鹅蓝主色（亮）——
private val HyperLight = lightColorScheme(
    // 🔴 2026-09-22 杰哥裁定「换成现在这个蓝色，全局都换」：主色由 #1A5BB5 提亮为
    //    Hero 渐变主体色 #3B8CF7（稿内实测 (59,140,247)）⇒ 全站按钮/chip/选中态与 Hero 同色，视觉一蓝。
    //    更早沿革：2026-09-19 曾按高保真稿把 #305070（H206 S40 L31，偏灰暗）校正为 #1A5BB5。
    //    浅蓝底 primaryContainer 仍为 #EAF1FE（未在本次裁定范围内）。
    primary = AppPalette.c_ff3b8cf7,
    onPrimary = Color.White,
    primaryContainer = AppPalette.c_ffeaf1fe,
    onPrimaryContainer = AppPalette.c_ff0b2e5c,
    secondary = AppPalette.c_ffe6e6e6,
    onSecondary = Color.Black,
    // 🔴 2026-09-21 按高保真稿校正（11/12/13/08 号「筛选 chips」）：M3 FilterChip 选中态容器取
    //    secondaryContainer。原值 #F0F0F0 灰 ⇒ 全站选中 chip 呈灰底（设置/备课组/教案模板/搜索…），
    //    与稿实测「选中 = 企鹅蓝实底白字、未选 = 浅灰底」不符，也与 13 号知识库显式 blue 口径不一致。
    //    统一为 primary 蓝 / onPrimary 白字。secondaryContainer 仅被 FilterChip 消费，无其他组件引用。
    // 🔴 2026-09-22 随主色提亮：同步 #1A5BB5 → #3B8CF7，保持 FilterChip 选中态与 primary 恒等。
    secondaryContainer = AppPalette.c_ff3b8cf7,
    onSecondaryContainer = Color.White,
    tertiary = AppPalette.c_ff7090b0,
    onTertiary = Color.White,
    tertiaryContainer = AppPalette.c_fff0f0f0,
    onTertiaryContainer = Color.Black,
    error = AppPalette.c_ffe94634,
    onError = Color.White,
    errorContainer = AppPalette.c_fffdf6f4,
    onErrorContainer = AppPalette.c_ff410002,
    background = AppPalette.c_fff7f7f7,
    onBackground = AppPalette.c_ff1a1a1a,
    surface = AppPalette.c_fff7f7f7,
    onSurface = AppPalette.c_ff1a1a1a,
    surfaceVariant = AppPalette.c_ffefefef,
    onSurfaceVariant = AppPalette.c_ff666666,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AppPalette.c_fff2f2f2,
    surfaceContainer = Color.White,
    surfaceContainerHigh = AppPalette.c_ffe8e8e8,
    surfaceContainerHighest = AppPalette.c_ffe8e8e8,
    outline = AppPalette.c_ffd9d9d9,
    outlineVariant = AppPalette.c_ffececec,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    inversePrimary = AppPalette.c_ff5070b0,
    scrim = AppPalette.c_52000000
)

// —— 企鹅蓝主色（暗）——
private val HyperDark = darkColorScheme(
    // 🔴🔴 2026-10-01 十五校（杰哥：「参考 UFIPanel 的深色模式进行优化我的深色模式」）：
    //   **暗态主色由 #6E96BF（灰蓝，饱和 81/255）提为 #4C9DF8（鲜蓝，饱和 172/255）**。
    //
    // 实证（真机截图逐像素 vs UFIPanel 深色参考图）：
    //   · 参考图底部导航「选中」图标实测 **#267AF7**（饱和 **209/255**）、右上圆钮图标中性灰；
    //   · 本工程暗态导航「选中」图标实测 **#6E96BF**（饱和 **仅 81**）—— 也就是本色的直接投影。
    //   ⇒ 暗态全站蓝色元素（选中导航项 / chip 选中 / 进度 / Hero 徽章…）都蒙着一层灰，
    //     整屏读起来"发闷"，这正是与参考图最大的单点差异。
    //
    // 取值：#4C9DF8 = 参考蓝的同族，按本工程暗底 #121212 收敛亮度 ——
    //   对比度 **6.68:1**（原 #6E96BF 6.05:1，参考 #267AF7 on #000 5.18:1）⇒ 明亮但不刺眼；
    //   色相与浅色档 #3B8CF7 同族 ⇒ 「全站一蓝」跨明暗依然成立（不再出现"浅色是鲜蓝、深色是灰蓝"）。
    primary = AppPalette.c_ff4c9df8,
    onPrimary = AppPalette.c_ff071b2e,
    primaryContainer = AppPalette.c_ff14334f,
    onPrimaryContainer = AppPalette.c_ffd6e6f2,
    secondary = AppPalette.c_ff505050,
    onSecondary = Color.White,
    // 同亮色：选中 chip 容器对齐暗态 primary 蓝（onPrimary 深字，保证对比）
    secondaryContainer = AppPalette.c_ff4c9df8,
    onSecondaryContainer = AppPalette.c_ff071b2e,
    tertiary = AppPalette.c_ff84a6c8,
    onTertiary = Color.Black,
    tertiaryContainer = AppPalette.c_ff2c2c2c,
    onTertiaryContainer = AppPalette.c_ffe0e0e0,
    error = AppPalette.c_ffff6b61,
    onError = Color.Black,
    errorContainer = AppPalette.c_ff2e0603,
    onErrorContainer = AppPalette.c_ffffdad6,
    background = AppPalette.c_ff121212,
    // 🔴 2026-10-01 十五校：暗态前景由 #E0E0E0 提到 **#F2F2F2**。
    //   实测依据：参考图深色导航「未选中」图标/文字 = **#F2F2F2**，本工程同位置 = #E0E0E0（亮 18 阶差）。
    //   对比度复核：onSurface(#F2F2F2) / surface(#121212) = **16.7:1**、
    //   onSurface(#F2F2F2) / surfaceContainer(#242424) = **14.0:1** ⇒ 远高于 AA，安全。
    onBackground = AppPalette.c_fff2f2f2,
    surface = AppPalette.c_ff121212,
    onSurface = AppPalette.c_fff2f2f2,
    surfaceVariant = AppPalette.c_ff1f1f1f,
    onSurfaceVariant = AppPalette.c_ffb0b0b0,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = AppPalette.c_ff121212,
    // 🔴 2026-10-01 十五校：**卡片层次整体上移一档**（唯一改动 = 三个 surfaceContainer 档）。
    //   实证（真机截图 vs 参考图量测）：
    //     参考图「页底→卡片」反差 = #000000 → #242424 = **36 阶**（卡片一眼起层）；
    //     本工程「页底→卡片」反差 = #121212 → #1A1A1A = **仅 8 阶**（卡片几乎糊在页底上，
    //     只能靠 outlineVariant 发丝边勾轮廓）—— 这是与参考图第二个单点差异。
    //   修法：卡片取参考图的**同一个色值** #242424（页底保持不变 #121212 ⇒ 反差 8→**18**，
    //   提升 2.25×；不把页底压到纯黑是为了不推翻全站既有暗色基线）。
    //   高/最高两档同步递推 +8，保持三档之间的原有间距（8/8）不变。
    surfaceContainer = AppPalette.c_ff242424,
    surfaceContainerHigh = AppPalette.c_ff2c2c2c,
    surfaceContainerHighest = AppPalette.c_ff363636,
    outline = AppPalette.c_ff404040,
    // 🔴 2026-10-01 十五校：随卡片层次联动（#2A2A2A → #333333）。
    //   outlineVariant 的唯一职责是「在容器上画出看得见的线」（全站 16 处：设置项分隔、
    //   列表行分隔、目录列竖线、图表"无数据"色）。卡片底 +8 之后，旧的 #2A2A2A 与卡片
    //   只剩 6 阶差 ⇒ 这些线会集体消失。取「新卡片底 + 15」把原有可见度原样搬过来。
    outlineVariant = AppPalette.c_ff333333,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = AppPalette.c_ff9dbbda,
    scrim = AppPalette.c_52000000
)

/**
 * HyperOS 字体刻度（来自 Miuix TextStyles，单位 sp）：
 * title1=32 / title2=24 / title3=20 / title4=18 / body1=16 / body2=14 /
 * footnote1=13 / footnote2=11；标题字重 MiSans Medium(500)。
 */
private val HyperTypography
    @Composable
    get() = androidx.compose.material3.Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 16.sp)
    )

/**
 * HyperOS 圆角：small=12 / medium=16（卡片默认 20，见下）/ large=20 / extraLarge=28。
 * 注：为让全站卡片统一为 20dp 圆角，这里把 medium 设为 20，使默认 Card 即获得 HyperOS 大圆角。
 */
private val HyperShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Hero 渐变（企鹅蓝）：亮/暗两态，统一主色。文字统一白色（可读性已校验）。
 * 不依赖 ColorScheme，避免 primaryContainer 色相漂移导致渐变发灰。
 */
object AppGradients {
    fun hero(dark: Boolean): Brush = if (dark)
        Brush.linearGradient(listOf(AppPalette.c_ff14375f, AppPalette.c_ff2a5b96))
    else
        // 🔴 2026-09-22 按 12 号备课组稿 / 教案模板稿**双页像素实证**重校（杰哥裁定「改稿的亮蓝」）：
        //    ① 色值整体提高一档明度 —— 两稿主体实测 (58~92,138~168,244~250)，即 #3C8DF6 亮天蓝；
        //       原 #2263B3→#3077C9 是沉藏蓝，与稿的观感差一个明度档（这也是「配色对不上」的主因）。
        //    ② 方向为「左上亮 → 右下暗」（两页稿横向逐点一致：R/G 通道左高右低）。
        //       ⚠️ 与 09-20 批设置页稿测得的「左上暗→右下亮」相反 —— 两批生成稿方向不一致，
        //       本轮以**更新的 15:44 批 + 双页一致**为准。
        //    ③ 端点对齐稿内实测两端：亮端 #5EA8FB（稿左端实测 (96,166,251)）
        //       / 暗端 #3B8CF7（稿主体实测 (59,140,247)）。首版取 #5AA2F1/#3A83E0
        //       时右端 B 通道偏低 22（渲染 (61,134,225) vs 稿 (59,140,247)，偏灰），故上调。
        Brush.linearGradient(listOf(AppPalette.c_ff5ea8fb, AppPalette.c_ff3b8cf7))
}

/**
 * 当前是否为暗色「应用主题」。
 *
 * 取的是设置里的三档（跟随系统 / 亮 / 暗）经 JiaoziTheme 解算后的结果，
 * **不是** [isSystemInDarkTheme] 的原始系统值——用户手动选「暗」而系统为亮时，
 * 前者为 true、后者为 false，直接读系统值会渲染出与主题不符的颜色。
 * 任何需要按明暗分支取色的组件都应读这个 Local。
 */
val LocalAppDark = compositionLocalOf { false }

/**
 * 字体缩放护栏常量（2026-09-30 依用户实测反馈确立）。
 *
 * 🔴 背景（真实缺陷）：[scaleTypography] 只放大 Typography 的 fontSize/lineHeight，
 *    而 `sp` 在实际布局中还会再乘一次 [LocalDensity.fontScale]（即系统「字体大小」设置）。
 *    两者**相乘**：系统 1.3 × App 默认 lg 1.12 = 1.456；系统 2.0 × xl 1.28 = 2.56。
 *    而大量容器是**定高**的（`height(52.dp)` 主按钮 / Hero 定高 / `size(20.dp)` 图标），
 *    字号成倍放大、容器不放大 ⇒ 文字必然撑破容器（用户反馈「怎么你那显示那么大」的真因）。
 *
 * 护栏策略（保守、不剥夺可达性）：
 * - 系统缩放钳到 [MIN_SYS_FONT_SCALE] ~ [MAX_SYS_FONT_SCALE]（0.85 ~ 1.15），
 *   即允许用户在系统设置里做 ±15% 微调，但不允许系统档位把整站撑爆；
 * - 系统缩放 × App 档位的**总倍率**封顶 [MAX_TOTAL_FONT_SCALE]（1.60），确保极限组合下仍有可控布局。
 */
private const val MIN_SYS_FONT_SCALE = 0.85f
private const val MAX_SYS_FONT_SCALE = 1.15f
private const val MAX_TOTAL_FONT_SCALE = 1.60f

@Composable
fun JiaoziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: String = "md",
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> HyperDark
        else -> HyperLight
    }
    val colorScheme = baseScheme
    val scale = when (fontScale) {
        "sm" -> 0.9f
        "lg" -> 1.12f
        "xl" -> 1.28f
        else -> 1f
    }
    // 🔴 字体缩放护栏：Typography 已乘过 `scale`，故交给 LocalDensity 的系数要「除回去」，
    //    使最终生效倍率 = 钳制后的系统缩放（并保证总倍率不超上限），而非与 App 档位相乘。
    val srcDensity = LocalDensity.current
    val sysClamped = srcDensity.fontScale.coerceIn(MIN_SYS_FONT_SCALE, MAX_SYS_FONT_SCALE)
    val effectiveFontScale =
        if (sysClamped * scale > MAX_TOTAL_FONT_SCALE) MAX_TOTAL_FONT_SCALE / scale else sysClamped
    val guardedDensity = Density(density = srcDensity.density, fontScale = effectiveFontScale)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (scale == 1f) HyperTypography else scaleTypography(HyperTypography, scale),
        shapes = HyperShapes
    ) {
        // 语义辅助色（状态色 / 文本色 / 背景色）随明/暗主题切换，保证暗色下文字与卡片对比度合规
        AppColors.isDark = darkTheme
        // 供深层组件读取「应用主题」明暗（非系统值），用于渐变 / 状态色分支
        CompositionLocalProvider(
            LocalAppDark provides darkTheme,
            LocalDensity provides guardedDensity
        ) {
            content()
        }
    }
}

/** 按系数整体缩放排版字号（不影响布局结构，仅字号；与网页端 setFont 一致） */
private fun scaleTypography(t: androidx.compose.material3.Typography, scale: Float): androidx.compose.material3.Typography {
    fun TextStyle.scale() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return t.copy(
        displaySmall = t.displaySmall.scale(), headlineLarge = t.headlineLarge.scale(),
        headlineMedium = t.headlineMedium.scale(), headlineSmall = t.headlineSmall.scale(),
        titleLarge = t.titleLarge.scale(), titleMedium = t.titleMedium.scale(),
        titleSmall = t.titleSmall.scale(), bodyLarge = t.bodyLarge.scale(),
        bodyMedium = t.bodyMedium.scale(), bodySmall = t.bodySmall.scale(),
        labelLarge = t.labelLarge.scale(), labelMedium = t.labelMedium.scale(),
        labelSmall = t.labelSmall.scale()
    )
}
