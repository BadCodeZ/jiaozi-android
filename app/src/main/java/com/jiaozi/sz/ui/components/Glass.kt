package com.jiaozi.sz.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
// ⚠️ 必须用别名：本文件的 `LiquidGlass` 里有一个同名 token `val Highlight: Dp`（发丝边宽），
// 会在该对象作用域内**遮蔽**本类 ⇒ `Highlight.Default` 报 "Unresolved reference 'Default'"。
import com.kyant.backdrop.highlight.Highlight as GlassHighlight
import com.kyant.backdrop.highlight.HighlightStyle as GlassHighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 悬浮导航容器（2026-08-16，V2.35.1 对齐设计稿）。
 *
 * 设计来源：UI重设计预览.html 第56-57行底部导航规范：
 *   background: surface-2（实色，非透明）；box-shadow: shadow-md；圆角 28px。
 * 不使用 RenderEffect / BlurMaskFilter / 半透明 / 高光渐变 / 发丝边框。
 * 目标：轻量、不卡、在所有机型上视觉一致。
 *
 * 🔴 2026-09-30 起本组 token 仅供「旧实色方案」的向后兼容保留；底部导航改走
 *    iOS 26 Liquid Glass（见 [LiquidGlass] / [GlassPanel]）。新页请优先用新 token。
 */
object NavTokens {
    /** 导航药丸圆角 */
    val Radius: Dp = 28.dp
    /** 导航药丸高度 */
    val Height: Dp = 60.dp
    /** 投影高度（柔和悬浮感） */
    val Elevation: Dp = 8.dp
    /**
     * 一级页面内容底部留白：保证列表最后一项不被浮空导航遮挡。
     *
     * 🔴 2026-09-30 取缔散落 `bottom = 76.dp`：旧值按旧实色导航（高 60 + 离屏 16 = 76）倒推，
     * 现导航改 iOS 26 浮空玻璃（高 60 + 离屏 10 = 70）+ 玻璃上方还需 12dp 呼吸 ⇒ 取 **82dp**。
     * 🔴 2026-10-01 二校：玻璃高 64 + 离屏 14（含系统手势 inset ≈14.5 ⇒ 实际占位约 92.5）+ 呼吸
     * ⇒ **90dp**（旧 88dp 随 BottomInset 12→14 同步上调）。
     * 全站 8 处（Bank/Mine/Settings/SettingsAbout/Today/Stats/PracticeHomeParts/SettingsPageScaffold）
     * 统一引用本 token，替换手写魔数。
     */
    val ContentBottomPad: Dp = 92.dp
}

/**
 * 卡片阴影 token（2026-09-28 新增）。
 *
 * 背景：此前全站约 40 处 `Card` 用 `cardElevation(defaultElevation = 0.dp)`，
 * 靠「卡片底色（surfaceContainer 纯白）与页面底（background 灰）的色差」区分层级。
 * 杰哥 2026-09-28 裁定「**卡片统一加轻阴影**」⇒ 收敛为本 token，与悬浮件
 * （[NavTokens.Elevation] = 8dp）拉开**两级层级**：
 *   卡片级 2dp（轻贴底） < 悬浮件级 8dp（明显浮起）。
 *
 * 用法：`elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)`。
 * ⚠️ 仅「卡片」语义用它；底部导航 / 悬浮返回件 / 上岛胶囊等**浮起件**仍用 [NavTokens.Elevation]。
 */
object CardTokens {
    /** 卡片统一轻阴影（与悬浮件 8dp 拉开层级） */
    val Elevation: Dp = 2.dp
}

/**
 * 圆角 token 体系（2026-09-30 收敛「圆角 17 种」散落魔数）。
 *
 * 背景：全站 `RoundedCornerShape(N.dp)` 取值多达 17 种，其中 20/12/14/16 是稳定主流（占 ~96 处），
 * 真正「随手乱填」的是 9/11/13 等低频孤值。本对象提供归一档位，新代码统一引用；
 * 既有的 `MaterialTheme.shapes`（large=24/extraLarge=28）与「依高保真图像素实证」的刻意值（如
 * KnowledgeScreen 的 9dp 方徽章）**保留不动**，避免违反设计稿。
 *
 * 档位：tiny=4（分割线/极小点）· xs=8（芯片/小徽章）· sm=12（卡片内元素/按钮）·
 * md=16（卡片）· lg=20（大卡片/容器）· xl=28（导航药丸等大幅面）· pill=999（全圆）。
 */
object Radius {
    val tiny: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 28.dp
    /** 全圆（胶囊/圆形），等价于 RoundedCornerShape(50%) */
    val pill: Dp = 999.dp
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

/** @deprecated 使用 [NavSurface]（旧实色）或 [GlassPanel]（iOS 26 玻璃）替代。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(NavTokens.Radius),
    content: @Composable () () -> Unit
) = NavSurface(modifier, shape, content)

// =====================================================================================
// iOS 26 Liquid Glass（2026-09-30 三次重做 · kyant `drawBackdrop` 真折射 + 固定展开态）
// =====================================================================================

/**
 * iOS 26 Liquid Glass 导航 token。
 *
 * 设计依据（WWDC25 / Apple HIG + 真实渲染拆解）：
 *  - **浮空胶囊**：tab bar 浮在内容之上、四周离屏边留白，内容可从其下方滚过；
 *  - **半透明玻璃**：材质半透，颜色由周围内容决定，随明暗环境自适应（亮色乳白 / 暗色墨色）；
 *  - **镜面高光（specular）**：顶部一道受光亮带 + 全圈发丝亮边（顶部最亮、底/侧渐隐），
 *    模拟曲面玻璃边缘受光——这是 Liquid Glass 最主导的视觉语言，不依赖背景捕获；
 *  - **玻璃厚度**：底缘压一道内阴影，顶部亮、底部暗，形成玻璃的体积感；
 *  - **克制选中**：仅以 tint（主色）标记 + 一层极淡玻璃高亮胶囊，不用大面积实色块；
 *  - **固定展开**：栏高恒为 [LiquidGlass.Height]（64dp），图标 + 文字标签常驻；
 *    2026-09-30 晚 杰哥裁定**去掉 scroll-to-minimize**（原先向下滚动会缩成仅图标）。
 *
 * ⚠️ 关于"真实背景折射/模糊"：Apple 真机用 Metal 着色器实时折射背后内容。本工程已升
 * Compose BOM 2025.08.00（UI 1.9.x）+ 引 kyant `AndroidLiquidGlass:1.0.0-rc01`，
 * [GlassPanel] 现通过 `drawBackdrop{ vibrancy(); blur(); lens() }` 在 `GraphicsLayer` 上
 * 实时捕获背后内容并 AGSL/RenderEffect 折射，得到真 iOS 26 液体玻璃（无需旧版内容淡出遮罩）。
 */
object LiquidGlass {
    /** 左右离屏留白（浮空感来源）= **容器外缘**到屏幕边的距离。
     *
     *  🔴🔴 2026-10-01 四校（本次修正 —— 推翻二校的「图标反推法」）：
     *  二校用「图标中心 166/353/540/727/914 反推胶囊左右缘 = 72.5 / 1007.5」是**错的**，
     *  因为它默认「容器宽 = 5 等分内容带」——而 UFI 的**容器在内容带之外还各留了约 10.75px 内缩**
     *  （见 [ContentInset]）。
     *
     *  正确量法＝**在容器纵向中心行逐像素找「边缘高光 + 外侧投影」的跃变点**（1dp = 2.75px）：
     *    · UFI  左缘 **x=62.5**（62:230 投影 → 63:251 边缘高光）、右缘 **x=1017**（1015~1018 高光 → 1019:243 外投影）
     *      ⇒ 容器宽 **954px = 347dp**，单侧离屏 **22.9dp**（左右对称，验证通过）；
     *    · 本工程旧值 26dp ⇒ 容器 936px = 340dp ⇒ **窄了 18px ≈ 6.5dp**
     *      （杰哥肉眼即看出「我的导航栏左右宽度比 UFIPanel 窄一丢丢」——实测证实）。
     *
     *  ⇒ 改取 **23dp**：393.45dp 屏上容器 = 393.45 − 46 = **347.45dp ≈ 955px** ✓ 与参考 954px 对齐。
     *  ⚠️ 必须与 [ContentInset] 联动：改大 SideInset 会同时压缩格宽。 */
    val SideInset: Dp = 23.dp

    /** 容器**内侧**水平留白（容器内壁 ↔ 首/末格的格子边界）。
     *
     *  🔴🔴 2026-10-01 新增（杰哥本轮指出的「导航栏边缘与左右两端选中胶囊外弧**没有间距**」）：
     *  参考的 tab 内容带**比容器窄**。由两条实测联立解出：
     *    · 首个图标中心距容器左缘 **104px**、末个距右缘 **103px**（图标中心 166.5 / 914，容器 62.5 / 1017）；
     *    · 图标 pitch = 186.9px（5 格内容带 ⇒ 内容带宽 = 4×186.9 + 格宽 ≈ 934.4px）。
     *  设内缩 P：`62.5 + P + (954 − 2P)/10 = 166.5` ⇒ **P = 10.75px = 3.9dp**。
     *
     *  含义＝**选中底（贴满格宽）外弧 ↔ 容器边缘之间那圈呼吸位**：
     *    · 参考 **≈3.9dp**；本工程旧值 **0**（选中底直接压在容器圆角上，视觉上「挤在一起」）。
     *  ⇒ 取 **4dp**。
     *  ⚠️ 联动公式：`格宽 = (屏宽 − 2×SideInset − 2×ContentInset) / 5`
     *  ⇒ 改后格宽 = (393.45 − 46 − 8)/5 = **67.9dp ≈ 186.7px**，与参考 186.5px 一致（不变窄）。 */
    val ContentInset: Dp = 4.dp
    /** 底部离屏留白。实测 UFIPanel 胶囊下缘 y=2322（屏高 2400）⇒ 距屏底 78px = **28.4dp**；
     *  本 App 系统手势 inset 实测约 14.5dp ⇒ 本 token 取 **14dp**（14 + 14.5 ≈ 28.5）。
     *  🔴 2026-10-01 二校：旧值 12dp 实测落点 26.5dp，比参考低 2dp。 */
    val BottomInset: Dp = 14.dp
    /** 导航栏高度。
     *  🔴 2026-10-01 修正：旧注释按 density=3 换算得「60dp」有误——**小米 13 实际
     *  `wm density = 440`（1dp = 2.75px）**，UFIPanel 胶囊实测 179px(1080 尺度) ⇒ **65dp**，
     *  本 token 取 **64dp**（比参考矮 1dp，避免与 8dp 网格冲突）。
     *  换算铁律：**先 `adb shell wm density` 拿真实 density，再谈 px→dp**。 */
    val Height: Dp = 64.dp
    /** 收缩态高度（仅图标）。
     *  @deprecated 2026-09-30 晚 杰哥裁定去掉 scroll-to-minimize 后已不再用于导航栏，
     *  仅 [radius] 的向后兼容分支保留。 */
    val HeightCollapsed: Dp = 52.dp
    /** 玻璃柔和投影 */
    val Elevation: Dp = 16.dp
    /** 玻璃半透明填充不透明度（亮）。
     *  🔴 2026-10-01 三校：0.45 偏薄——真机同列对比「页面底色 vs 胶囊内底色」：
     *  UFI 实测 244 → **255（+11）**，本工程仅 237 → 240（**+3**）⇒ 玻璃「提亮感」明显不足，
     *  视觉上比参考更灰、更「没玻璃味」。按 UFI 的 +11 反推所需白填充 ≈ 0.6（0.6×255+0.4×237≈248）。
     *  取 **0.60**（仍保留 40% 背景透光，blur/lens 光学效果不被盖住）。 */
    const val ALPHA_LIGHT = 0.60f
    /** 玻璃半透明填充不透明度（暗）。
     *  🔴🔴 2026-10-01 六校（杰哥对稿：「现在的压暗反而让组件不明显了」）：
     *  深色下玻璃**必须提亮、不能压暗**。真机同列对比「盘内 vs 页面底」：
     *  浅色 +52（168→220，提亮）／深色 **−11（18→7，压暗）** ⇒ 控件在近黑页面上几乎融化。
     *  行业口径一致：深色模式下玻璃控件是「比 background 亮一档的浮层」
     *  （iOS 26 深色 TabBar ≈ #2C2C2E on #1C1C1E；Material 深色 surfaceContainer ≈ #1E1E1E on #121212），
     *  **不是**比背景更黑的凹陷块。
     *  ⇒ 深色 fill 改用**低 alpha 白色**叠加（见 [GlassPanel]），本 token 即该白色的不透明度。
     *  取值推导：页底（十七校起 #0A0A0A = 10 阶）为底、白 a 叠加后 = 10 + 245a；
     *  取 a = 0.10 ⇒ ≈ 34（**+24**），与浅色的 +52 同数量级，控件从暗底「浮起」而非「陷下去」。
     *  注：a 值本身与页底无关，页底压深后「浮起量」保持不变（+24），G-4「玻璃必比页底亮」依然成立。 */
    const val ALPHA_DARK = 0.10f
    /** 发丝高光边宽 */
    val Highlight: Dp = 1.25.dp

    /**
     * 浅色主题下**受光带宽度/柔化半径**的缩放系数。
     *
     * 🔴🔴 2026-10-01 十三校：**由 0.6f 改回 1.0f —— 两侧不再分档，用同一套窄带。**
     *
     * 十二校设 0.6 的前提是「深色档=5dp、浅色折回 3dp 才等于改前」，那是为了**保住浅色零回归**。
     * 十三校把深色档从 5dp 收到 1.5dp 后，这个前提消失了：若仍乘 0.6，浅色会变成 **0.9dp**
     * —— 在 440dpi 上只有 2.5px，比库默认（0.5dp）强不了多少，等于把浅色的边缘受光也一起抹掉。
     *
     * 为什么浅色**不需要**分档（真机同场景扫描，浅色首页导航胶囊 x=446）：
     *   · 剖面差异本就极小：受光带 3dp → 0.3dp 的全过程中，上缘剖面只在
     *     `242 / 240 / 238 / 235` 之间浮动（**≤7 个灰阶**），远低于可辨识阈值；
     *   · 真正的可观测量是**饱和宽度**：`≥254` 像素占比 `13.1% → 6.8% → 4.0% → 1.4%`
     *     —— 也就是旧档在浅色下并没有"更亮"，只是**边缘多出一圈死白**。
     *   ⇒ 统一到 1.5dp 后浅色落在 **6.8%**（旧 13.1%），是**变得更干净**，不是回归。
     *
     * 保留该常量（=1.0）而非删除，是为了日后再遇到"两侧表达空间不同"时有个现成的旋钮。
     */
    const val HighlightLightScale = 1.0f

    // ---- 选中项指示器（2026-09-30 五次重做 · 严格对齐 UFIPanel）----
    // 🔴 实测结论：UFIPanel 的选中态是「一枚**中性色**大圆角底托住『图标 + 文字』整体」，
    //    **不是**主色药丸、也**不是**只在图标后的圆点。底色实测 (229,229,229)
    //    = 10% 黑叠在白色玻璃上 ⇒ 用中性 10% 黑 / 10% 白，不掺主色。
    /** 选中项指示器高度。
     *  🔴 2026-10-01 二校：UFI 实测选中底 y2158..2316 = **158px = 57.5dp**（旧值 56dp 偏小）⇒ 取 58dp；
     *  胶囊高 64dp 时上下各留 **3dp**。
     *  量法：在选中格中心列做纵向逐像素扫描，找「中性灰带」的上下缘（见 _nav_geo 脚本）。 */
    val IndicatorHeight: Dp = 58.dp
    /** 选中项指示器圆角 = 半高（圆角端）。用 UFI 下缘宽度序列反解：W=82px、d=8px 时 w=77px
     *  ⇒ R≈78px ≈ 半高 ⇒ 取 29dp。 */
    val IndicatorRadius: Dp = 29.dp
    /** 🔴 选中项指示器**左右内缩**（相对格宽）。
     *  🔴🔴 2026-10-01 三校（本次修正）：**UFI 的选中底是「贴满整格」的，不内缩**。
     *  用 y2235 行（灰底最宽处）横扫实测：UFI 灰底 x818..1010 = **192px = 70dp**，
     *  而 5 等分格宽 = 935px/5 = **187px = 68dp** ⇒ 灰底比格还宽 ≈2.5px（边缘折射外溢）。
     *  旧值 4dp 来自一次误测（把 @2x 参考图与 1080 截图混算），真机放大图上表现为
     *  「我这边灰底明显比 UFI 窄一圈」——即杰哥指出的「底色胶囊与导航胶囊的间距不对」。
     *  ⇒ 内缩归零（保留参数以便暗色/特殊形态微调）。 */
    val IndicatorInset: Dp = 0.dp
    /** 选中项指示器底色（亮色主题）= 10% 黑 ⇒ 叠白玻璃实测 #E5E5E5 */
    val IndicatorLight: Color = Color(0x1A000000)
    /** 选中项指示器底色（暗色主题）= 10% 白 */
    val IndicatorDark: Color = Color(0x1AFFFFFF)

    /**
     * 深色主题下，选中胶囊**在玻璃基底之上**额外叠加的白 —— 唯一作用是把选中态调成
     * 「比导航栏灰一些」，**不许靠加厚不透明度来区分**。
     *
     * 🔴🔴 2026-10-01 十四校（杰哥：「深色模式下被选中态的胶囊透光只是比导航栏灰一些，
     *   而不是现在闷闷的不透」）：
     *
     * 病根 = **总不透明度被叠成了两倍**。旧写法把选中色 `compositeOver` 玻璃基底白：
     *   导航胶囊 = 白 [ALPHA_DARK] (0.10)            ⇒ 透光 90%、内部灰度 **42**
     *   选中胶囊 = 白 0.10 ⊕ 白 0.10 = **白 0.19**   ⇒ 透光 81%、内部灰度 **63**
     * 真机实测（深色首页，页底 18）与公式 `18 + 237×α` 完全吻合：
     *   0.10 → 42 ✓（导航）　0.19 → 63 ✓（选中，实测 63）
     * ⇒ 选中态不但**亮度高出导航栏 50%**（21 个灰阶），**透光率还低了 9 个百分点**：
     *   背后内容被多出的一层白雾罩住，读起来是"一块实心浅灰板"而不是"一块玻璃"——就是"闷"。
     *
     * 修法＝把选中态按**导航栏的薄度**来做，差异只留一点点色偏：
     *   取 **0.015** ⇒ 总白 = 0.10 ⊕ 0.015 = **0.1135**
     *   ⇒ 透光 **88.7%**（旧 81%）、内部灰度 **45**（旧 63）—— 比导航栏(42)只亮 **3**，
     *     差异收到"一眼看得出是选中的那一格、但不是一块白板"的程度。
     *
     *   档位谱（真机实测均落在 `18 + 237×α` 上，可放心外推）：
     *     | SELECT | 总白 α | 内部灰度 | 与导航(42)差 | 观感 |
     *     |---|---|---|---|---|
     *     | 0.10（旧的叠加写法） | 0.19 | 63 | +21 | 实心浅灰板，闷 |
     *     | 0.03 | 0.127 | 49 | +7 | 仍偏白 |
     *     | **0.015（当前）** | **0.1135** | **45** | **+3** | **灰而透光** |
     *     | 0.00 | 0.10 | 42 | 0 | 与导航栏完全同色（选中只靠图标变色） |
     *   ⇒ 若杰哥还要"再灰"，下一档直接取 **0.00**；此时选中块在颜色上与导航栏完全一致，
     *     区分只剩「图标转主色 + 图标放大」，色块退化为纯粹的玻璃层。
     *
     * ⚠️ 语义仍保持"**基底 + 选中色**"两层结构（不改调用处的 compositeOver 写法）——
     *   这样浅色侧继续走 `IndicatorLight`（10% 黑叠白玻璃，实测 226 vs 导航 237），
     *   两侧共用同一段合成代码，只有本条 token 分深浅。
     * ⚠️ 之所以还要**保留**这 +7 的亮度：选中态必须一眼可辨；但它的主要识别手段其实是
     *   **图标转主色 + 图标放大**（见 AppNav 的 `sel` 驱动），色块只承担辅助层级。
     */
    const val ALPHA_DARK_SELECT = 0.015f

    // ---- 玻璃档位（2026-10-01 二校新增）----
    /** 导航主胶囊：厚玻璃。边缘强折射 + 中磨砂 + 明显内阴影（体积感）。 */
    val NavSpec: GlassSpec = GlassSpec(
        blur = 22.dp,
        lens = 28.dp,
        innerShadowRadius = 7.dp,
        innerShadowAlpha = 0.70f,
        // 🔴🔴 2026-10-01 十校（杰哥：「整体还缺少了一点液化的感觉，深色模式尤为明显」）
        //   —— 根因实证（反编译库 `Highlight` 的默认参数解析）：
        //     width      = **0.5dp**（440dpi 上仅 1.4px 的一条发丝）
        //     blurRadius = width / 2f = 0.25dp
        //     alpha      = 1f
        //  ⇒ 库给的现成档（Default / Ambient / Plain）**只差 style 这个 shader**，
        //    而 1.4px 的线宽上 shader 差异被压到亚像素 ⇒ 换档"等于没换"
        //    （实测：换 Ambient 后齿轮钮纵向剖面 93/97/104/117/118/98/98 **一位不差**）。
        //  ⇒ 真正决定"液化感"的是 **受光带的宽度与柔和度**，必须显式给：
        //    `width 0.5 → 3dp`（能把"液面反光"读出来的最小宽度）、
        //    `blurRadius → 7dp`（让亮带向内外柔和衰减，而不是一圈硬描边）、
        //    `style` 见下条（**十一校重大修正**）。
        //
        //  🔴🔴 2026-10-01 十一校（杰哥：「参考 UFIPanel 的液化进行优化」）——
        //    **十校把 style 换成 `Ambient` 是方向性错误，本轮推翻。** 反编译 `HighlightStyle`
        //    拿到三档 shader 的**真实数学**（库源码 1.0.0-rc01，`HighlightStyle$*` 字节码内嵌 AGSL）：
        //      · `Default`：`intensity = pow(abs(dot(grad, normal)), falloff)`，**取绝对值**
        //        ⇒ 沿光照轴**两端同时起光**（角法线与光同向 d=+1、反向 d=−1，二者亮度相同）；
        //      · `Ambient`：`half4(step(0.0, d)) * pow(abs(d), falloff)`，**只取 d ≥ 0 的一侧**
        //        ⇒ **单侧光**，法线背光的那半边完全无光。
        //    ⇒ 切成 `Ambient` 等于**把玻璃底部那道反射光整条抹掉**，玻璃从"有厚度的液体凸起"
        //      退化成"被从单一方向打光的一块塑料"。这正是"缺少液化感"的一条确切来源。
        //
        //  ⇒ 用 **`Default` + `angle = −90°`**（屏幕坐标 y 朝下，`normal = (cos−90°, sin−90°) = (0,−1)` 即朝上）：
        //      顶部边缘法线朝上 ⇒ d = +1 ⇒ 亮；
        //      底部边缘法线朝下 ⇒ d = −1 ⇒ |d| = 1 ⇒ **同样亮**；
        //      左右边缘法线与光垂直 ⇒ d = 0 ⇒ 不亮。
        //    ⇒ 得到的正是 iOS 26 / UFI 的「**上缘主光 + 下缘反射光**」双高光。
        //    ⚠️ 库默认的 `Default` 用的是 `angle = 45°`（对角光，亮在"右下 + 左上"），
        //      与导航胶囊的水平长条形态不搭 ⇒ **必须显式指定 angle**，不能靠默认。
        //
        //  🔴🔴 2026-10-01 十二校（杰哥：「参考 UFIPanel 的液化进行优化」· 实机扫描定档）——
        //    **受光带再加宽一档，且该加宽只对深色生效。**
        //    真机同场景扫描（深色首页导航胶囊，x=446 列，见 build/_probe/glassprobe.py）：
        //      · `hlw 3 → 5dp / hlb 7 → 9dp`：上缘峰 **98 → 105**、下缘峰 **100 → 114**（+7% / +14%）；
        //      · 再加宽到 5dp 以上收益递减，且 44dp 圆钮上会让亮点占满圆面 ⇒ 5/9dp 为拐点。
        //    浅色侧**不跟随**（在 [GlassPanel] 里按主题缩回，理由见该处注释）。
        //
        //  🔴🔴 2026-10-01 十三校（杰哥：「对比之前 UFIPanel 的导航栏截图，现在的效果有些太刻意」）——
        //    **十二校把受光带加宽到 5dp / 9dp 是过头了，本轮大幅收回。**
        //
        //    「刻意」的确切量化含义 = **受光带被做成了「渐变坡」而不是「边缘跃变」**。
        //    同场景逐像素剖面（深色首页导航胶囊 x=446 列，脚本 build/_probe/glassprobe.py）：
        //
        //      · 十二校（5dp / 9dp / α.85）：105 → 100 → 89 → 76 → 65 → 55 → 49 → 45 → 43
        //        —— 距上缘 **45px（16.4dp）内一直在衰减**，没有任何台阶。
        //        人眼读到的不是"玻璃边缘的一线反光"，而是"整块上半部被蒙了一层光"。
        //      · UFIPanel 参考（亮色，1080×2400@440 的放大图逐像素）：
        //        投影谷 232 → 边缘高光 255 **只用 1.5 个原始像素 = 0.55dp**，
        //        其后胶囊内部**恒定 255、零渐变**。
        //      ⇒ 我们的受光带宽度是参考的 **约 30 倍**。这就是"刻意"。
        //
        //    本档实测（同场景扫描，见下「扫描口径」）：
        //      上缘峰 **84**（旧 105）、峰-内部余量 **42**（旧 63）、
        //      有效衰减宽度 **14px = 5.1dp**（旧 45px = 16.4dp，收窄 3.2 倍）。
        //
        //  🔴 扫描口径（避免"看着变了"的主观判断）：
        //    · 同场景判据 = 玻璃上方内容区 diff 必须为 0.0（本次 12 组全 0.0）；
        //    · 宽度判据   = 从峰值衰减到 `内部基线 + 30%余量` 的像素数；
        //    · 过曝判据   = 区域 `≥254` 像素占比（浅色玻璃填充易饱和）。
        //    ⚠️ 不要再靠"峰值更高 = 更明显"来加宽 —— 峰值够用后**唯一的负面就是宽度**。
        highlight = GlassHighlight.Default.copy(
            width = 1.5.dp,
            blurRadius = 2.5.dp,
            alpha = 0.5f,
            style = GlassHighlightStyle.Default(
                intensity = 0.5f,
                angle = -90f,
                falloff = 1f
            )
        ),
        shadow = Shadow.Default
    )

    /**
     * 选中指示器：**叠在导航胶囊之上的第二层薄玻璃**。
     *
     * 🔴🔴 2026-10-01 关键认知：UFI 的选中底**不是一层平涂的半透明灰**，而是一块**独立玻璃**——
     * 真机 3× 放大可见背景的暗色糊块在它的圆角边缘被**弯折绕行**（折射），且自带内阴影/边缘光。
     * 旧实现用 `Modifier.background(10% 黑)` 平涂 ⇒ 「被选中时的透光形态」与参考完全不同。
     *
     * 参数取向（对齐 UFIPanel 第二层 `innerBlurRadius` 更小的 AGSL 玻璃）：
     *  - `blur` 与主胶囊同档 ⇒ 同屏不出现「一块清晰一块糊」；
     *  - `lens` 收敛到 16dp ⇒ 小块玻璃的边厚按比例更薄；
     *  - `Highlight.Plain` ⇒ 平面高光（不是大曲面的弧形受光带）；
     *  - 外投影降到 10% ⇒ 只留一丝「抬起」感。
     */
    val IndicatorSpec: GlassSpec = GlassSpec(
        blur = 22.dp,
        lens = 16.dp,
        innerShadowRadius = 4.dp,
        innerShadowAlpha = 0.55f,
        // 🔴🔴 2026-10-01 十四校（杰哥：「导航栏被选状态的胶囊不用有根细线在外围」）：
        //   **去掉选中胶囊的整圈轮廓光**（alpha 归零）。
        //
        //   旧值 `GlassHighlight.Plain` 的语义是**平面填充光 ⇒ 四条边都亮**（库默认 width 0.5dp、alpha 1f）。
        //   真机实测（深色首页「今日」格，小米13 1080×2400@440）：
        //     · 胶囊**左缘** y=2235：x74/76 = **103 / 112**，而同列的内部仅 63、外侧导航玻璃 43
        //       ⇒ 一圈 +40~+50 的亮线，就是杰哥指的那根"外围细线"；
        //     · 胶囊**上缘** x=167：y2150 = **129**（内部 60），同样是一根。
        //   用 `Settings.Global[jzglass]` 扫 `hla` 定量确认：`0.35 → 0.15 → 0` 时
        //     左缘依次 `68/80 → 56/71 → 48/63`、上缘 `85 → 71 → 60`（归零后与内部**完全齐平**）。
        //
        //   ⚠️ 它与主胶囊的光**不是一回事**（这是本轮容易看走眼的地方）：
        //     导航胶囊用 `Default + angle = −90°` —— 按边缘法线分布，**只有上/下缘起光、左右不亮**；
        //     而 `Plain` 是平面填充、**四边等亮**。选中底是"叠在主胶囊之上的一层**选中色块**"，
        //     语义上并非一枚独立玻璃钮 ⇒ 不该自带轮廓光（有光就会被读成"浮起来的第二个控件"）。
        //     主胶囊的玻璃本身仍完整保留，选中底照旧借它透光、只是不再自己发光。
        //
        //   ⚠️ 保留 `.copy()` 形态而**不是**删掉整个 highlight 参数：`GlassSpec.highlight` 是必填项；
        //     且日后若想给选中底补一点"仅下缘"的浮起光，改回 `Default.copy(angle = 90f, …)` 即可。
        highlight = GlassHighlight.Plain.copy(alpha = 0f),
        shadow = Shadow.Default.copy(alpha = 0.10f)
    )

    /**
     * 选中胶囊**位移动画**弹簧（2026-10-01 新增 / 八校调整）。
     *
     * 🔴 八校调整（杰哥需求：「惯性效果减小」）：**过冲从 12% 收到 ~2%**。
     *   · 过冲量 = `exp(-πζ/√(1-ζ²))`：ζ 0.56 ⇒ 12.0%；**ζ 0.78 ⇒ 2.0%**（跨 4 格 745px 时约 +15px）；
     *   · 稳定时间 ≈ `4/(ζω)`，ω = √520 ≈ 22.8 ⇒ **≈0.23s**（旧 ζ=0.56/stiffness=500 实测 397~445ms）。
     *   ⇒ 位移变得**干脆利落、不再"冲过头再荡回来"**，不再有旧版那种大惯性摆尾。
     *
     * ⚠️ 那「气泡/果冻的回弹感」去哪了？**移交给了形变**（[JellySpring]）——
     *   位移负责「稳、准、快」，形变负责「弹、荡、余韵」。两者分工是刻意的：
     *   位移过冲会让**图标与文字跟着整块来回扫**（内容层被拖拽，可读性差），
     *   而形变过冲只动**玻璃块自身的宽高**，图标始终钉在格中心 ⇒ 既有弹性又不晕。
     *
     * ⚠️ 与 `Motion.springSteady`（DampingRatioNoBouncy）仍**刻意区分**：本 spec 保留 2% 过冲，
     *   让到位瞬间有一丝「吸附」的手感；而导航栏显隐/页面切换用稳弹簧。
     * ⚠️ 系统「减少动态效果」开启时由调用方改用 `snapTo`，不读本 spec（见 AppNav 的 `rm` 分支）。
     */
    val IndicatorSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.78f,
        stiffness = 520f,
        // 🔴🔴 2026-10-01 关键：visibilityThreshold 必须**极小**，否则过冲回弹会被整体吃掉。
        //   Compose 由它推导速度阈值 `vThr = visibilityThreshold × 0.75 × 2π × √stiffness`：
        //     · 初版误取 0.4f（把「格」当 px 量级）⇒ 实测 p 从 3.0 **单调**衰减到 0，
        //       1414ms 全程无过冲（逐帧日志实证），气泡回弹完全消失。
        //     · 取 0.002f 后实测完整曲线：**峰 3.357（+11.9%）→ 谷 2.957（−1.4%）→ 二峰 3.005 → 落定**
        //       （与 `exp(-πζ/√(1-ζ²))` 理论值 12.2% 吻合）。
        //     · 生产取 0.02f：仍远小于任何可见位移（≈0.02 格），但把总时长从 695ms 收到 ~430ms。
        //       ⚠️ 位置阈值必须与「速度阈值」同时满足才判平衡，故小阈值不会让动画停在过冲峰值上。
        visibilityThreshold = 0.02f
    )

    /**
     * 气泡动效的**速度参考值**（2026-10-01 新增）。
     *
     * 🔴 用途：把「速度强度」归一化，驱动漂浮上浮量与拖尾可见度。
     *   必须用 **Dp** 而非裸 px —— 同样的物理运动在 640dpi 真机上的 px/s 是 320dpi 设备的 2 倍，
     *   用 px 常量会导致**高密度机型上动效瞬间饱和、低密度机型上几乎不动**。
     *
     * 取值依据：跨 1 格（[ContentInset] 下格宽 ≈67.9dp）时弹簧峰值速度
     *   `≈ 67.9dp × √500 × 0.6 ≈ 911 dp/s` ⇒ 取 1200dp/s 使「单格切换」达到约 0.76 强度、
     *   「跨 3 格以上」饱和（=1），保证短距离也有明显漂浮感、长距离不过冲夸张。
     *
     * 🔴🔴 2026-10-01 六校 · 配套红线（**调用方必读**）：
     *   本 token 的量纲是 **dp/s**，而 `Animatable.velocity` 的量纲是 **「格/秒」**（progress ∈ [0, n-1]）。
     *   ⇒ 调用方**必须**先换成真实像素速度再比：
     *   ```kotlin
     *   val speedRefCells = BubbleSpeedRef.toPx() / cellW.toPx()   // 满强度对应的「格/秒」
     *   val vc = v.coerceIn(-speedRefCells, speedRefCells)         // 顺手饱和，防跨多格时拖尾甩太远
     *   val intensity = abs(vc) / speedRefCells
     *   ```
     *   直接写 `abs(v) / BubbleSpeedRef.toPx()` 会漏掉 `× 格宽`，在 440dpi 真机上把强度
     *   **低估 186.7 倍**（恰好 = cellW.toPx()）⇒ 上浮 0.01dp、拖尾 alpha 5e-6，视觉上等同于没有动效。
     *   （真机录屏实测「y 段全程不变」就是这条，**不是** graphicsLayer 失效。）
     */
    val BubbleSpeedRef: Dp = 1200.dp

    // ---- 「果冻」形变（2026-10-01 七校新增 / 八校改为弹簧驱动）----
    /**
     * 选中胶囊在形变中的**横向拉伸**幅度（相对宽度）—— 由 [JellySpring] 的当前值 `jelly` 缩放。
     *
     * 语义：`jelly = 1`（被甩到最大变形）时 —— **宽 +7.5% / 高 −5%**（[BubbleSquashY]）。
     *   · 横向被「甩」长 + 纵向收窄 ⇒ 面积近似守恒，读起来像一颗被甩出去的气泡在空气里被拉长，
     *     而不是一块被生拉伸的矩形（后者会很「塑料」）；
     *   · 🔴 **`jelly` 会过冲到负值**（[JellySpring] 的 20.5%）⇒ 胶囊**反向形变**：
     *     宽 1−0.075×0.205 ≈ **98.5%**、高 ≈ 101% ⇒ 从"横着拉长"翻成"竖着收窄"，
     *     这一帧就是「果冻被弹回来」的观感来源。公式无须特判，`jelly` 带符号直接乘即可。
     *   · **静止态恒为 0 形变**（精确回到 1:1）⇒ 不改动已验收的静态形态，零回归。
     *
     * ⚠️ 驱动量已从「速度的即时函数」换成**独立弹簧**（八校）。旧写法结构上不可能有回弹，
     *   详见 [JellySpring] 的注释。
     *
     * 🔴🔴 实现红线：形变**必须走真实布局尺寸**（`Modifier.width/height`），
     *   **禁止**用 `Modifier.graphicsLayer { scaleX/scaleY }` —— `drawBackdrop` 按屏幕坐标
     *   采样 backdrop，而 graphicsLayer 的 scale 会把**已捕获的背景**一起缩放 ⇒ 折射内容
     *   与玻璃块尺寸错配（边缘 lens 的弯折位置也随之整体外移）。改尺寸则每次都以**实际 bounds**
     *   重新采样，光学效果永远正确。
     *   代价是动画期每帧一次 relayout —— 本子树只有 1 块玻璃 + 2 条尾迹，实测可接受。
     *
     * ⚠️ 幅度上限还受「格宽」约束：186.7px × 7.5% ≈ 14px 总增宽（左右各 7px），
     *   不会盖到相邻格的图标（格距 186.7px、图标半径 33px）。
     */
    const val BubbleStretchX = 0.075f

    /** 选中胶囊在位移中的**纵向压扁**幅度（[JellySpring] 峰值 = 1 时）；与 [BubbleStretchX] 配对实现体积守恒感。 */
    const val BubbleSquashY = 0.05f

    /**
     * 「果冻」形变弹簧（2026-10-01 八校新增）—— 本轮**弹性回弹的唯一来源**。
     *
     * 与 [IndicatorSpring] 的分工（这是本轮最核心的一条设计决定）：
     *   · [IndicatorSpring] 管**胶囊中心的位置**：ζ 0.78 ⇒ 稳、准、快，几乎不过冲；
     *   · **本 spec 管胶囊自身的宽高**：ζ 0.45 ⇒ 明显欠阻尼，**过冲 20.5%**、自由振荡两拍。
     *
     * 为什么必须把「位移」与「形变」拆成两个独立的物理量：
     *   七校版把形变量写成 `速度的即时函数`（`bubbleW = indW × (1 + k·intensity)`）—— 它**只能**
     *   跟着速度单峰起落，速度归零形变就归零，**结构上不可能有回弹**（没有独立的二阶动力学）。
     *   八校改为：形变量由一个**自己的弹簧**驱动（[JellySpring]），产生
     *   `拉满 → 反向过冲(压扁) → 回弹(再膨胀) → 落定` 的果冻曲线。
     *
     * 参数推导（ζ = 0.38, stiffness = 520 ⇒ ω = √520 ≈ 22.8）：
     *   · 过冲 = `exp(-πζ/√(1-ζ²))` = **27.5%** ⇒ 峰值 1.0 之后会反向冲到 **−0.275**
     *     （形变由正转负 ⇒ 胶囊**从"拉长"翻成"收窄"**，这正是果冻被弹回来的那一帧）；
     *   · 峰间比 = 同一指数 = 0.275 ⇒ **第二拍 +7.6%**、第三拍 −2.1%（肉眼可见的两拍半）；
     *   · 周期 T = `2π/(ω√(1-ζ²))` ≈ **0.29s**，衰减 `4/(ζω)` ≈ **0.46s** ⇒ 看得清两拍、不拖沓；
     *   · 与 [JellyKickMs] 的 120ms 上升合起来，整个形变过程 ≈ 0.58s，
     *     比位移（0.23s）**长出一截** —— 刻意如此：「停下来了但还在晃」才是果冻。
     *   · ζ 从 0.45 降到 0.38 是**为"看得见"服务**的：0.45 时第二拍只有 +4.2%（≈0.4dp，白做）。
     *   · `visibilityThreshold` 取 0.002（同 [IndicatorSpring] 的道理：阈值过大 ⇒ 提前判平衡 ⇒
     *     振荡被整体吃掉。本 token 的量纲是「无量纲形变量」，0.002 相当于 0.2% 形变，刚好在不可见门槛下）。
     */
    val JellySpring: SpringSpec<Float> = spring(
        dampingRatio = 0.38f,
        stiffness = 520f,
        visibilityThreshold = 0.002f
    )

    /**
     * 「回弹」阶段的额外形变增益 —— `jelly < 0` 时把形变幅度放大这么多倍。
     *
     * 为什么需要**不对称**：
     *   · 正向（拉伸）幅度被**硬约束**在 +7.5% —— 再宽就会盖到相邻格的图标（格距 186.7px、
     *     图标半径 33px，总增宽 14px 已是安全上限）；
     *   · 反向（收窄/压扁）**没有这个约束**（胶囊变小不会遮到任何东西）。
     * ⇒ 单靠 27.5% 的过冲，反向只有 −2.1%（≈4px，运动中几乎看不见）。
     *   放大 1.6 倍后反向达 **−3.3%（≈6px）**，「被弹回来」那一下才真的读得出来。
     */
    const val JellyReboundGain = 1.6f

    /**
     * 果冻形变的**上升时长**（ms）—— 即「胶囊被甩出去」的那一下。
     *
     * 取 120ms（用 `LinearOutSlowInEasing` 减速收尾）：
     *   · 明显快于位移的 230ms ⇒ 视觉上「先被甩变形，再滑到位」，符合"受力瞬间变形最大"的直觉；
     *   · 若取到 200ms 以上，形变会与位移几乎同时到顶，退化成七校那种"平移中的静态拉伸"。
     */
    const val JellyKickMs = 120

    // ---- 悬停 / 按压交互（2026-10-01 八校新增）----
    /**
     * 悬停（hover，鼠标 / 触控笔）时图标的放大倍率 —— 相对「未选中」的 0.916 基线。
     * 取 1.08：**略大于选中态的 1.0**，这样指针停在某个 tab 上时，
     * 「我要点这个」的提示强度会盖过「这个当前被选中」的静态大小，指向性明确。
     */
    const val HoverIconScale = 1.08f

    /**
     * 按压（press，手指按住未松）时图标的放大倍率。
     * 取 1.10（略强于 [HoverIconScale]）—— 手指按下是**更强的意图**，反馈也更强。
     */
    const val PressIconScale = 1.10f

    /**
     * 「预览轮廓」的**主色填充**不透明度 —— 按压/悬停在**非当前选中项**上时，
     * 在该格画一枚低对比度圆角块，明确预示「选中胶囊将要移动到这里」。
     *
     * 取 0.10：足够让人注意到"这里有个东西"，但**必须显著弱于**真胶囊
     * （其 `IndicatorLight` 全不透明 + 玻璃层 + 折射）—— 否则会被误读成"已经切过去了"。
     */
    const val PreviewFillAlpha = 0.10f

    /**
     * 「预览轮廓」的**主色描边**不透明度。
     * 取 0.45：靠**边**而不是靠"面"来表意 —— 描边在玻璃上比填充更清晰，也更不容易与真胶囊混淆。
     */
    const val PreviewStrokeAlpha = 0.45f

    /** 「预览轮廓」描边宽度。 */
    val PreviewStrokeWidth: Dp = 1.5.dp

    /**
     * 「预览轮廓」的**内缩量** —— 比真胶囊小一圈（4dp），
     * 让它在视觉上明确是「轮廓/占位」而非「实心胶囊」，避免与真胶囊混淆。
     */
    val PreviewInset: Dp = 5.dp

    // =================================================================================
    // 「玻璃钮」族（2026-10-01 九校新增）—— 把底部导航的 Liquid Glass 语言推广到
    //   返回键 / 关闭键 / 其它圆形浮空件。
    //
    // 设计取向：**同一套 token、同一套交互、同一套弹簧**，只把「尺幅」参数按控件大小分档
    //   （玻璃的边厚/模糊必须随控件尺寸收敛 —— 44dp 圆钮上套 28dp 的 lens 会整块糊成一个洞）。
    // 三者对应关系：
    //   导航胶囊  → [NavSpec]        （64dp 高，厚玻璃）
    //   选中指示底 → [IndicatorSpec]  （58dp 高，薄玻璃）
    //   图标钮    → **本段 [ButtonSpec]**（36~44dp，袖珍玻璃）
    // =================================================================================

    /**
     * 玻璃图标钮（设置键 / 返回键 / 关闭键 / 圆形浮空按钮）的效果档位。
     *
     * 🔴🔴 2026-10-01 十四校（杰哥：「设置按钮、返回按钮和关闭按钮都统一导航栏的元素和参数设置」）：
     *   **取消九校的「按尺幅分档」，本档直接等同 [NavSpec] —— 玻璃圆钮与导航胶囊取同一套渲染参数，
     *   全站玻璃材质只此一份。**
     *
     * 被推翻的旧方案（九校）按「玻璃边厚 ∝ 控件最小边」做了约 0.5× 的收缩
     * （`lens 28→14` / `blur 22→18` / `innerShadow 7dp,α.70→5dp,α.55` / 投影 α`0.18`）。
     * 那条推理只在**单看某一块玻璃**时成立，放进真实界面就露馅：
     *   · 首页 Hero 的设置钮与底部导航**同屏可见**，一块 18dp 模糊、一块 22dp，
     *     边缘受光带的衰减宽度也不同（1.25dp vs 1.5dp）⇒ 两块玻璃**读起来是两种材质**；
     *   · 「设置 / 返回 / 关闭」三者之间尺寸在九校二迭代已经统一到 44dp，
     *     材质却还留着这一档差异 —— 等于统一只做了一半。
     * ⇒ 本轮把这档抹平：**按钮与导航栏参数逐位相同**，日后调参只需动 [NavSpec] 一处。
     *
     * ⚠️ 尺幅带来的真实差异（44dp 圆钮的边缘弧长只有导航胶囊的约 1/4，同样 28dp 的折射带
     *   在圆面上覆盖的相对比例更大）**交由几何自然表达**，不再用改材质参数的方式人为补偿。
     */
    val ButtonSpec: GlassSpec = NavSpec

    /**
     * 圆形钮按下时的**果冻形变**幅度（相对钮径）：**宽 +6% / 高 −6%**（面积近似守恒 ⇒ "被按扁"）。
     *
     * 为什么是「拉伸宽、压扁高」而不是整体缩小：手指按在一颗玻璃钮上，物理直觉是**受压力方向被压扁**
     * （高变小、材料往两侧挤 ⇒ 宽变大），这也正是导航胶囊 [BubbleStretchX] 的正向语义 ⇒
     * 两处控件在"受力瞬间"的表现完全同源，用户不需要学两套语汇。
     *
     * 幅度 6% 的取值：44dp 钮上 = 宽 +2.6dp / 高 −2.6dp —— 静止时看不出、按下时**能看出**
     * 的那条线（低于 4% 即 ≈1.7dp，小钮上几乎读不出来）。
     */
    const val PressJellyStretch = 0.06f

    /** 圆形钮按下时的**纵向压扁**幅度（与 [PressJellyStretch] 配对，共同实现"体积守恒"的按压感）。 */
    const val PressJellySquash = 0.06f

    /**
     * 圆形钮**按压到位**的时长（ms）。
     *
     * 取 90 —— 比导航胶囊的 [JellyKickMs]（120ms）更快：按钮的行程只有 2~3dp，
     * 反馈必须**跟手**；而导航胶囊要跨 190dp 的格距，一下甩变形才有"被甩"的观感。
     * （口诀：**行程越长，形变上升越慢**。）
     */
    const val PressDownMs = 90

    /**
     * 圆形钮**松手回弹**弹簧 —— 本族「果冻回弹」的来源，与导航胶囊的 [JellySpring] 同族。
     *
     * 「按下」走 [PressDownMs] 的 tween（快、准、不弹），「松手」切到本弹簧自由衰减：
     *   · ζ = 0.30 ⇒ 过冲 `exp(-πζ/√(1-ζ²))` = **37.2%**（比 [JellySpring] 的 27.5% 更活跃 ——
     *     小控件需要更夸张的相对过冲才读得出来）；
     *   · ω = √560 ≈ 23.7 ⇒ 周期 ≈ 0.28s、衰减 ≈ 0.56s ⇒ **看得清一拍半，不拖沓**；
     *   · 松手瞬间 `jelly` 从 +1 衰减到 0 并**反向冲到 −0.372** ⇒ 由"压扁"翻成"竖向回弹"，
     *     这一帧就是「果冻被弹回来」。
     *
     * ⚠️ 与导航栏一致的红线：`visibilityThreshold` 必须**极小**（0.002），
     *   阈值过大会让 Compose 提前判定平衡 ⇒ 整个振荡被吃掉（详见 [IndicatorSpring] 的实测记录）。
     */
    val PressReleaseSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.30f,
        stiffness = 560f,
        visibilityThreshold = 0.002f
    )

    /**
     * 圆形钮**回弹期**（`jelly < 0`）的形变增益。
     *
     * 与导航栏 [JellyReboundGain] 同因：正向（按下拉伸）幅度同时被「不能盖到相邻控件」与
     * 「按钮本体只有 36~44dp」两条约束卡死，而**反向回弹没有约束**（钮变小不会遮到任何东西）。
     * 37.2% 的裸过冲换算到 44dp 上只有 −1.6dp（≈肉眼极限），×2.6 后达 **−4.3%（≈1.9dp）**，
     * 「弹回来」那一下才真的看得见。
     */
    const val PressReboundGain = 2.0f

    /**
     * 圆形钮按下时的**整体放大**倍率 —— 2026-10-01 九校二次迭代（杰哥裁定）新增。
     *
     * 🔴🔴 杰哥原话：「我所要的手指悬停的感觉不是一圈蓝色搞定的，是需要该组件进行**果冻式按压导致的放大**，
     *   像是被手指所按压触碰的感觉」—— 这条否掉了初版的「聚焦环」方案（当时按下只是在圆内画一圈
     *   主色描边 + 淡填充）。**环形反馈是"标记"，不是"触感"**：它只告诉你"这里被选中了"，
     *   而手指按在玻璃上真正该发生的是**这块玻璃自己被按到变形、胀大、再弹回来**。
     *   ⇒ 聚焦环全部删除（[PreviewFillAlpha]/[PreviewStrokeAlpha]/[PreviewInset]/`RingInsetRatio` 不再被
     *   按钮使用，导航栏的「预览轮廓」仍保留 —— 那里是"胶囊将移动到这里"的占位语义，不是触感）；
     *   改由本 token 把「按下 = 整块放大」直接作用在**组件自身尺寸**上。
     *
     * 取值 0.10（44dp → 48.4dp）：
     *   · 低于 0.06 在 44dp 上只有 2.6dp，静止/按压两帧几乎看不出差别；
     *   · 高于 0.14 会与相邻元素打架（Hero 右上角的按钮距屏幕边只有 16~20dp）；
     *   · 0.10 恰好让「按下」这一帧**一眼可辨**，同时溢出量（每侧 2.2dp）仍在安全区内。
     *
     * ⚠️ 它与 [PressJellyStretch] / [PressJellySquash] **同时生效**（乘法叠加）：
     *   先整体胀大 10%，再在这个基础上横向多 6% / 纵向少 6% ⇒
     *   视觉上是「一颗圆玻璃被手指顶起来、同时被稍稍压扁」的果冻感 —— 这正是杰哥要的"被触碰"。
     *   44dp 上按下瞬间实测 **宽 51.3dp / 高 45.5dp**。
     */
    const val PressScaleUp = 0.10f

    /**
     * 悬停（鼠标 / 触控笔）时的按压强度 —— 相对「完全按下」（= 1.0）的比例。
     *
     * 取 0.6：手机上不存在真 hover，本档位只服务桌面模式 / DeX / 平板 + 触控笔。
     * 悬停给出**六成**的按压量（放大 6%、形变 3.6%）—— 足以让指针下的控件"浮起来回应"，
     * 又明显弱于按下的 100%，保留"按下"这个动作的最后一段增量。
     */
    const val HoverPressLevel = 0.6f

    fun radius(expanded: Boolean): Dp = (if (expanded) Height else HeightCollapsed) / 2
}

/**
 * 玻璃效果档位（2026-10-01 新增）。
 *
 * 把原先硬编码在 [GlassPanel] 里的效果强度提出来，让**同一 backdrop 上的多层玻璃**各自取档：
 * 主胶囊（厚玻璃）与选中指示器（薄玻璃）用的是同一套 AGSL 管线，只是参数不同——
 * 这正是 UFIPanel 的做法（它的 AGSL 链由 `innerBlurRadius` / `strokeWidth` / `lightIntensity*`
 * 等 uniform 驱动，不同组件传不同值）。
 *
 * @param blur 背景模糊半径（磨砂强度）
 * @param lens 边缘折射位移（液体玻璃的「厚边」，越大边缘弯折越强）
 * @param innerShadowRadius / @param innerShadowAlpha 内阴影（顶部暗、底部亮的体积感）
 * @param vibrancy 是否做色调提升（对应 UFIPanel 的 in_saturation / in_brightness 段）
 * @param highlight 边缘受光带（[Highlight.Default] 曲面弧光 / [Highlight.Plain] 平面光）
 * @param shadow 外投影（浮起感）
 */
data class GlassSpec(
    val blur: Dp = 22.dp,
    val lens: Dp = 28.dp,
    val innerShadowRadius: Dp = 10.dp,
    val innerShadowAlpha: Float = 0.5f,
    val vibrancy: Boolean = true,
    val highlight: GlassHighlight = GlassHighlight.Default,
    val shadow: Shadow = Shadow.Default
)

/**
 * 🔧 **玻璃参数运行时通道（仅本轮「液化」调参使用，交付前删除）**。
 *
 * 动机：玻璃的折射 / 高光 / 内阴影参数对观感极其敏感，靠"改一次常量 + 编译 + 装机"盲调，
 * 一轮约 3 分钟且无法横向对比。本通道把全部旋钮暴露到 `Settings.Global`，
 * 一次编译后可任意组合扫描：
 *
 * ```bash
 * adb shell settings put global jzglass "blur=14;lensh=20;lensa=10;depth=1;hlw=3;hlang=-90"
 * adb shell am force-stop com.jiaozi.sz && adb shell am start -n com.jiaozi.sz/.MainActivity
 * ```
 *
 * 键表（缺省 = 用 [GlassSpec] 里的常量，**行为与未加通道时逐位一致**）：
 * | key | 含义 |
 * |---|---|
 * | `blur` | 背景模糊半径 dp |
 * | `lensh` | 折射**带宽度** dp（库默认与 `lensa` 同值） |
 * | `lensa` | 折射**位移量** dp（库默认与 `lensh` 同值） |
 * | `depth` | 1 = 折射叠加径向分量（凸透镜感） |
 * | `disp` | 1 = 色散折射（`RefractionWithDispersion`） |
 * | `hlw` / `hlb` / `hla` | 边缘受光带的 宽 dp / 柔化 dp / 强度 |
 * | `hlang` / `hlint` / `hlfall` | 高光光照角（度）/ 强度 / 衰减指数 |
 * | `isr` / `isa` / `isoff` | 内阴影 半径 dp / 强度 / 垂直偏移 dp |
 * | `isblack` | 1 = 内阴影用纯黑（默认库色为 `Black@0.15`，会被 alpha 二次相乘压暗） |
 *
 * ⚠️ 进程级缓存，改 `Settings.Global` 后**必须 `force-stop` 重启**才生效。
 * ⚠️ 交付前连同 [GlassPanel] 里的 `GlassTune` 分支一并移除。
 */
internal object GlassTune {
    private const val KEY = "jzglass"

    @Volatile
    private var cache: Map<String, String>? = null

    fun ensure(ctx: android.content.Context) {
        if (cache != null) return
        // 仅 debug 包（可调试）生效；release 包恒定走常量，零生产风险。
        val debuggable =
            (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            cache = emptyMap()
            return
        }
        cache = runCatching {
            val raw = android.provider.Settings.Global
                .getString(ctx.contentResolver, KEY).orEmpty()
            if (raw.isBlank()) {
                emptyMap()
            } else {
                buildMap {
                    raw.split(';').forEach { seg ->
                        val i = seg.indexOf('=')
                        if (i > 0) put(seg.substring(0, i).trim(), seg.substring(i + 1).trim())
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** 是否处于被覆盖状态（无人写入时为 false ⇒ 全走常量）。 */
    val active: Boolean get() = !cache.isNullOrEmpty()

    fun f(k: String, d: Float): Float = cache?.get(k)?.toFloatOrNull() ?: d
    fun b(k: String, d: Boolean): Boolean =
        cache?.get(k)?.let { it == "1" || it == "true" } ?: d
}

/**
 * 通用 Liquid Glass 面板：基于 kyant AndroidLiquidGlass（com.github.Kyant0:AndroidLiquidGlass:1.0.0-rc01）
 * 的 `drawBackdrop` 真折射——在 Compose 1.9.x 上通过 `GraphicsLayer` 背景捕获 + AGSL/RenderEffect
 * 实时模糊 / 折射 / 高光，得到与 iOS 26 / 小米 HyperOS 一致的液体玻璃。
 *
 * 视觉语言：浮空、半透、曲面受光；背后滚动内容被折射「化开」（不再需要旧版的内容淡出遮罩）。
 *
 * 降级：API < 33 设备 RenderEffect 不可用，`drawBackdrop` 内的 `updateEffects()` 仅在
 * `Build.VERSION.SDK_INT >= S` 时挂着色器，低于此自动退化为无折射（仅半透填充），视觉仍成立
 * （见 [LiquidGlass] 的 fill alpha token）。
 *
 * @param shape 面板形状（默认按展开态高度取胶囊圆角；导航栏会随收缩态传入动态圆角）
 * @param containerColor 玻璃半透填充色；默认走 [LiquidGlass] 亮/暗 token。
 * @param rimBrush 发丝外描边笔刷（可选）；传 null 不描边。
 *   ⚠️ 实测 UFIPanel 导航胶囊**无**描边 ⇒ 底部导航当前**不传**本参数（保留能力备用）。
 * @param backdrop 共享的背景捕获层（可选）。
 *   🔴🔴 2026-10-01 关键修复：kyant 的 `LayerBackdrop` **必须由被采样的内容侧提供**——
 *   内容树要挂 `Modifier.layerBackdrop(backdrop)` 把自身录进 backdrop 的 GraphicsLayer，
 *   本面板的 `drawBackdrop(backdrop, …)` 才能采到像素。此前 [GlassPanel] 内部自建
 *   `rememberLayerBackdrop()`（无人往里面录内容）⇒ 采到的是**空层**，真机上表现为
 *   「只有半透明白填充、背后内容原样清晰穿透」（无 blur / 无 lens / 无 vibrancy）——
 *   即真机截图里字从导航栏下面直接透出来的根因。
 *   ⇒ 调用方（如 AppNav）应在外层 `rememberLayerBackdrop()` 并传给内容树与本面板；
 *   留 null 时退回自建（仅供「内容就在面板内部、无需采样」的兼容场景）。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LiquidGlass.radius(true)),
    containerColor: Color? = null,
    rimBrush: Brush? = null,
    backdrop: LayerBackdrop? = null,
    spec: GlassSpec = LiquidGlass.NavSpec,
    content: @Composable () -> Unit
) {
    val dark = AppColors.isDark
    val ownBackdrop = rememberLayerBackdrop()
    val layerBackdrop = backdrop ?: ownBackdrop
    // 🔧 调试通道（无覆盖时下列取值逐位等于 [GlassSpec] 常量）
    GlassTune.ensure(LocalContext.current)
    val eBlur = GlassTune.f("blur", spec.blur.value).dp
    val eLensH = GlassTune.f("lensh", spec.lens.value).dp
    val eLensA = GlassTune.f("lensa", spec.lens.value).dp
    val eDepth = GlassTune.b("depth", false)
    val eDisp = GlassTune.b("disp", false)
    val eHlW = GlassTune.f("hlw", spec.highlight.width.value).dp
    val eHlB = GlassTune.f("hlb", spec.highlight.blurRadius.value).dp
    val eHlA = GlassTune.f("hla", spec.highlight.alpha)
    val eHlAng = GlassTune.f("hlang", Float.NaN)
    val eHlInt = GlassTune.f("hlint", Float.NaN)
    val eHlFall = GlassTune.f("hlfall", Float.NaN)
    val eIsR = GlassTune.f("isr", spec.innerShadowRadius.value).dp
    val eIsA = GlassTune.f("isa", spec.innerShadowAlpha)
    val eIsOff = GlassTune.f("isoff", Float.NaN)
    val eIsBlack = GlassTune.b("isblack", false)
    // 🔴🔴 2026-10-01 六校：深浅两模式必须**同向**（都提亮），不能靠切主题色「自动反相」。
    //  旧写法一律取 `surfaceContainerLowest`：浅色主题里它是 **Color.White** ⇒ 白 60% 叠加 = 提亮 ✓；
    //  深色主题里它是 **Color.Black**（Theme.kt:111）⇒ 同一个 60% 变成「黑 60% 叠加」= 压暗 ✗。
    //  真机实测：深色下玻璃盘 7 vs 页面底 18（差 −11），圆钮/导航胶囊几乎与背景融成一片。
    //  ⇒ 深色改用**低 alpha 白色**（[LiquidGlass.ALPHA_DARK]），与浅色同为「提亮」，方向一致。
    val fill = containerColor ?: if (dark) {
        Color.White.copy(alpha = LiquidGlass.ALPHA_DARK)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = LiquidGlass.ALPHA_LIGHT)
    }
    Box(
        modifier
            .drawBackdrop(
                backdrop = layerBackdrop,
                shape = { shape },
                effects = {
                    // 逐段对齐 UFIPanel.apk 导航栏的 AGSL 玻璃管线（见 参考_UFIPanel玻璃着色器.md）：
                    //  ① 色调调整 —— 对应它的 in_brightness / in_contrast / in_saturation 段。
                    //     kyant 的 `vibrancy()` 就是该三参数的库预设（提饱和度 + 微提亮/对比），
                    //     让模糊后的背景仍保有色相，而不是灰糊一片。
                    if (spec.vibrancy) vibrancy()
                    //  ② 模糊 —— UFIPanel 是「多 tap 盒式预模糊（±0.25 / ±0.75px）+ RenderEffect 高斯」，
                    //     整体属**轻量级**而非重磨砂。2026-10-01 真机对比：18dp 时背后卡片仍可辨出
                    //     文字轮廓，参考的「化开」程度更高 ⇒ 22dp。
                    blur(eBlur.toPx())
                    //  ③ 边缘折射（液体玻璃的灵魂）—— UFIPanel 的 AGSL `Refraction` 段实测参数语义：
                    //     `refractionHeight` = 折射**带宽度**（边缘向内多深之内参与弯折）、
                    //     `refractionAmount` = 最大**位移量**、`depthEffect` = 叠加径向分量、`dispersion` = 色散。
                    //     库的签名默认把两者当同一个值用，本轮起**显式分离**（见上方 `lens` 参数注释）。
                    lens(eLensH.toPx(), eLensA.toPx(), eDepth, eDisp)
                },
                highlight = {
                    val st = spec.highlight.style
                    val style = if (eHlAng.isNaN() && eHlInt.isNaN() && eHlFall.isNaN()) {
                        st
                    } else {
                        val d = st as? GlassHighlightStyle.Default
                        GlassHighlightStyle.Default(
                            intensity = if (eHlInt.isNaN()) (d?.intensity ?: 0.5f) else eHlInt,
                            angle = if (eHlAng.isNaN()) (d?.angle ?: 45f) else eHlAng,
                            falloff = if (eHlFall.isNaN()) (d?.falloff ?: 1f) else eHlFall
                        )
                    }
                    // 🔴🔴 2026-10-01 十二校：受光带的**加宽只对深色生效**，浅色按
                    //   [LiquidGlass.HighlightLightScale] 缩回原档。真机实测（浅色首页导航胶囊，x=446）：
                    //     把 `hlw 3 → 5dp / hlb 7 → 9dp` 后，上缘剖面 242/240 **一位不变**、
                    //     下缘 255 **一位不变** —— 边缘观感**零变化**；
                    //     而玻璃内部「≥254 的饱和像素占比」从 **15.9% 涨到 23.8%**（+50%）。
                    //   根因：浅色玻璃填充本身就是 `0.60 × 白`，在页底 237 上已叠加到 ≈250~255，
                    //     受光带再亮也只是**把饱和区撑大**（读作"边上一圈死白"），而不产生层次。
                    //   深色侧则相反：页底 18、玻璃内 42，距 255 有巨大余量，受光带能完整表达
                    //     （上缘峰 98→105、下缘峰 100→114，实测同场景）⇒ 只该在深色加宽。
                    val hlScale = if (dark) 1f else LiquidGlass.HighlightLightScale
                    spec.highlight.copy(
                        width = (eHlW.value * hlScale).dp,
                        blurRadius = (eHlB.value * hlScale).dp,
                        alpha = eHlA,
                        style = style
                    )
                },
                shadow = { spec.shadow },
                // ④ 内阴影 —— 玻璃的「厚度」：顶部压暗、底部透亮，做出凸起感。
                //    UFIPanel 对应段用 `innerBlurRadius` + 边缘法线 `getNormal()` 算高光/压暗。
                //    🔴 库默认 `color = Black.copy(alpha = 0.15f)`，会被本参数 alpha **二次相乘**
                //       （0.70 × 0.15 ≈ 0.105）⇒ 实际强度只有名义值的 1/7。需要真实厚度时显式给色。
                innerShadow = {
                    InnerShadow(
                        radius = eIsR,
                        offset = DpOffset(0.dp, if (eIsOff.isNaN()) eIsR else eIsOff.dp),
                        color = if (eIsBlack) Color.Black else Color.Black.copy(alpha = 0.15f),
                        alpha = eIsA
                    )
                }
            )
            .clip(shape)
            .background(fill)
            // 发丝外描边（可选）：一圈贴边细亮线，给玻璃胶囊明确的边缘轮廓
            // （UFIPanel 导航栏在暗底上即有一圈浅灰细边；不传 rimBrush 则完全不画）。
            .then(
                if (rimBrush != null) Modifier.border(LiquidGlass.Highlight, rimBrush, shape)
                else Modifier
            )
    ) { content() }
}
