package com.jiaozi.sz.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.xiaomi.Haptic
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * 玻璃图标钮原子（2026-10-01 九校重写）—— 返回键 / 关闭键 / 其它圆形浮空件的**唯一**实现。
 *
 * ## 缘起
 * 底部导航在八校完成了 iOS 26 Liquid Glass 化（[GlassPanel] 真折射 + 双弹簧 + 悬停/按压交互）后，
 * 全站出现**两种玻璃语言**：导航栏是带折射的液体玻璃，而返回键/关闭键还是 2026-09-18 的
 * 「半透实色圆 + 羽化扩散 + 发丝边」旧磨砂方案。杰哥裁定**统一**：
 * 「可以根据目前的导航栏所用的 glass 进行其他组件的优化，比如返回键、关闭件，也用相同的透明玻璃、
 *   交互反馈、动画效果」。
 *
 * ## 三层统一（与导航栏逐项同源）
 * ① **材质**：走同一个 [GlassPanel] + `drawBackdrop`（blur / lens 边缘折射 / vibrancy / 内阴影 /
 *    曲面受光 / 外投影），参数取 [LiquidGlass.ButtonSpec] ——
 *    🔴 2026-10-01 十四校起该档**直接等同 [LiquidGlass.NavSpec]**（九校的「按尺幅 0.5× 收缩」已取消），
 *    即设置 / 返回 / 关闭三键与底部导航胶囊**逐位同参数**，全站玻璃材质只此一份；
 * ②③ **交互反馈 = 动画本身**（2026-10-01 九校二次迭代，杰哥裁定）：
 *    按压/悬停经 `MutableInteractionSource` 合成单一按压量 `press`，**直接驱动组件自身尺寸** ——
 *    按下瞬间**整体放大** [LiquidGlass.PressScaleUp]（+10%），同时**横向拉伸**
 *    [LiquidGlass.PressJellyStretch] / **纵向压扁** [LiquidGlass.PressJellySquash]（+6% / −6%），
 *    松手切 [LiquidGlass.PressReleaseSpring] 自由衰减 ⇒ **反向过冲 → 回弹 → 落定**。
 *    🔴 刻意**不用**环形描边做反馈 —— 环是"标记"而非"触感"，杰哥原话：「不是一圈蓝色搞定的，
 *    是需要该组件进行果冻式按压导致的放大，像是被手指所按压触碰的感觉」；
 *    也不叠 Material ripple（`indication = null`，灰色涟漪画在玻璃上会破坏通透感）。
 *
 * ## 与旧实现的关键差异
 * | 项 | 旧（2026-09-18） | 新（九校） |
 * |---|---|---|
 * | 模糊 | `Modifier.blur` 模糊**自身**实色圆 ⇒ 只是一圈羽化，**不是**毛玻璃 | `drawBackdrop` 采样**背后内容**真模糊 |
 * | 边缘 | 1dp 发丝实线 | 曲面受光带（Highlight）+ 边缘折射 `lens` |
 * | 体积 | 无 | 内阴影（顶暗底亮） |
 * | 反馈 | 仅 `Haptic.tick` | 图标放大 + 聚焦环 + 按压果冻 + 触感 |
 *
 * ## 使用约束
 * @param size 钮径（圆形）。**同时决定**图标大小（`size × 0.5`）、聚焦环内缩（`size × 10%`）
 *   与命中区 —— 三者随尺寸自洽，不需要调用方各自调参。
 *   常用档：44dp（全局悬浮返回件/关闭件）· 40dp（紧凑顶栏）· 36dp（卡片内关闭）。
 * @param backdrop 背景捕获层。**只有在「本组件不在该 backdrop 的录制范围内」时才可传入共享实例** ——
 *   kyant 的 `layerBackdrop` 录的是所在节点的整棵子树，若钮本身被录进去，它就会采到
 *   **含自己上一帧**的画面 ⇒ 越叠越糊、玻璃发白。
 *   · AppNav 的全局悬浮返回件：**在** `layerBackdrop` 节点之外（见 AppNav 的内容层结构）⇒ 传 `navBackdrop`，真折射；
 *   · 各屏**内联**的返回/关闭件：位于 NavHost 内部、在录制范围内 ⇒ **留 null**，
 *     此时 [GlassPanel] 退回自建空 backdrop ⇒ 保留半透填充 + 受光 + 内阴影 + 投影（无折射），
 *     观感仍与导航栏同族（该降级路径已由导航栏早前实测验证：采空层是安全的，不会发黑）。
 *
 * ⚠️🔴 形变**必须走真实 layout 尺寸**，且必须用 `Modifier.requiredSize(w, h)`：
 *   ① **不能用** `graphicsLayer { scaleX/scaleY }` —— `drawBackdrop` 按屏幕坐标采样 backdrop，
 *      缩放会把**已捕获的背景**一起缩放 ⇒ 折射错位（红线同 AppNav 的主胶囊，见 [LiquidGlass.BubbleStretchX]）；
 *   ② **不能用** `Modifier.size(w, h)` —— 它受父约束（宿主 Box 的 maxWidth = [size]）**coerce**，
 *      放大后的宽会被静默截断回 [size]（实测：X 边界按压前后一个像素不变、只有 Y 变化）。
 *      `requiredSize` 忽略父约束，是最省事又唯一正确的写法。
 *   宿主 Box 恒为 [size] 且 `contentAlignment = Center` ⇒ 形变后的玻璃块自动居中，无需手工换算；
 *   命中区挂在**宿主**上 ⇒ 形变期间点击可达性完全不变（不会出现「按下去反而点不到」）。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "back",
    contentDescription: String = "返回",
    size: Dp = 44.dp,
    backdrop: LayerBackdrop? = null,
    spec: GlassSpec = LiquidGlass.ButtonSpec,
    // 🔴🔴 2026-10-01 十五校（杰哥：「设置按钮、返回按钮、关闭按钮我认为是图标颜色不对导致的，
    //   与导航栏的图标颜色统一一下」）——**默认色由 `primary` 改为 `onSurface`**。
    //
    // 病根：九校把三键从「白 18% 实色圆 + 纯白图标」换成玻璃钮时，为救 Hero 渐变上「白图标看不见」，
    //   顺手把 tint 定成了 `primary`。但这一改把圆钮的**语义**改错了：
    //   底部导航的图标色是 `lerp(onSurface, primary, sel)` —— 只有**选中**那一格才是主色，
    //   其余四格恒为 `onSurface`。于是全站出现「圆钮＝蓝、导航图标＝黑白」的**两套图标语言**，
    //   而且圆钮用的恰是导航栏里**最少见**的那个色（选中态专用）。
    //   杰哥一眼看出「颜色不对」，就是这条。
    //
    // 为什么 `onSurface` 才是正解（而不是别的中性色）：它是全站**内容前景色**的唯一定义，
    //   黑底近白 / 白底近黑，自动跟随明暗 —— 圆钮图标与导航未选中图标取同一个 token，
    //   从此「图标在玻璃上用什么色」只有一条规则。
    //   ⚠️ 顺带修正旧注释里那句「玻璃底已是浅色，白图标会看不见」：该理由**只在浅色侧成立**，
    //     而浅色侧的正解本就是 `onSurface`（近黑，不是白）。深色侧玻璃底是暗的，
    //     亮图标反而最清晰 —— 两侧用同一个 `onSurface` 全都对。
    //   参考实现亦如此：UFIPanel 深色下导航未选中图标 #F2F2F2、右上圆钮图标同为中性灰，
    //     全站**没有一个圆钮图标是蓝色的**。
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)

    // ---- ②③ 交互反馈 = 动画本身：按压量 `press` ∈ [0, 1] 直接驱动组件尺寸 ----
    //
    // 🔴🔴 2026-10-01 九校二次迭代（杰哥裁定）：**废弃「聚焦环」，改为组件自身的果冻式按压放大**。
    //   初版把反馈做成「按下时在玻璃圆内画一圈主色描边 + 淡填充」（环形标记）。杰哥否掉：
    //   「我所要的手指悬停的感觉不是一圈蓝色搞定的，是需要该组件进行**果冻式按压导致的放大**，
    //     像是被手指所按压触碰的感觉」。
    //   判断成立 —— 环是**标记**（告诉你"这里被选中"），而触感应当是**材质自己的形变**：
    //   手指压在玻璃上，被按的是这块玻璃，它该胀大、变形、松手再弹回来。
    //   ⇒ 环相关代码与 token（含 `RingInsetRatio`）全部删除；导航栏的「预览轮廓」**保留不动** ——
    //     那里表达的是「选中胶囊将要移动到这一格」的**占位预告**，与"触感"是两回事。
    //
    //   现在只有**一个**驱动量 `press`：
    //     按下 → tween([LiquidGlass.PressDownMs]) 到 1.0（快、跟手、不弹）
    //     松手 → [LiquidGlass.PressReleaseSpring] 自由衰减（ζ 0.30 ⇒ 反向过冲 37.2%）
    //   由它派生三个量：整体放大 [LiquidGlass.PressScaleUp]、横向拉伸 [PressJellyStretch]、
    //   纵向压扁 [PressJellySquash] —— 松手时三者**同时**反向过冲再回弹，
    //   即「果冻被按下去又弹回来」的完整一拍半。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()

    // 目标按压量：按下 1.0 / 悬停 0.6（[LiquidGlass.HoverPressLevel]）/ 无 0
    val pressTarget = when {
        pressed -> 1f
        hovered -> LiquidGlass.HoverPressLevel
        else -> 0f
    }

    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed, hovered, rm) {
        if (rm) {
            // 系统「减少动态效果」：不放大、不形变、不回落（瞬时切换，零动画）
            press.snapTo(0f)
            return@LaunchedEffect
        }
        if (pressTarget > press.value) {
            press.animateTo(
                pressTarget,
                tween(LiquidGlass.PressDownMs, easing = LinearOutSlowInEasing)
            )
        } else if (press.value != pressTarget) {
            // 松手 / 指针离开：走欠阻尼弹簧 ⇒ 过冲 → 回弹
            // （首帧 press=0 且 target=0 时条件不成立，天然短路，无空动画）
            press.animateTo(pressTarget, LiquidGlass.PressReleaseSpring)
        }
    }

    val p = press.value
    // 回弹期（p < 0）额外放大：正向幅度受「不遮住邻元素」约束，反向没有（见 [PressReboundGain]）
    val shaped = if (p >= 0f) p else p * LiquidGlass.PressReboundGain
    // 三个派生量（乘法叠加）：整体放大 × 横向拉伸 × 纵向压扁
    val scale = 1f + LiquidGlass.PressScaleUp * shaped
    val glassW = size * scale * (1f + LiquidGlass.PressJellyStretch * shaped)
    val glassH = size * scale * (1f - LiquidGlass.PressJellySquash * shaped)

    Box(
        modifier
            // 🔴 宿主尺寸恒为 [size]：形变只发生在内部的玻璃块上 ⇒ 命中区/布局占位不随动画抖动
            .size(size)
            .clickable(
                interactionSource = interaction,
                // 关掉 Material ripple：灰色涟漪画在玻璃上会破坏通透感，且与聚焦环语义重复
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription
            ) {
                Haptic.tick(ctx)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // ① 玻璃本体（唯一会形变的层）：尺寸走**真实 layout**
        //   🔴 红线：必须用 `requiredSize` 而**不是** `size` —— 宿主 Box 固定 [size]（44dp），
        //     按下瞬间玻璃宽会到 44 × 1.10 × 1.06 ≈ **51.3dp**，而 `size` 会被父约束
        //     （maxWidth = 44dp）**coerce 回 44dp** ⇒ 放大与横向拉伸被静默吃掉，只剩纵向压缩生效。
        //     （实测证据：X 边界按压前后一个像素都没变、Y 高度正常变化。）
        //     `requiredSize` 忽略父约束按自身尺寸布局，由 `contentAlignment = Center` 居中，
        //     溢出部分正常绘制（本组件不做裁剪，与导航胶囊"可溢出栏边"同一手法）。
        //   🔴 也不可用 `graphicsLayer { scaleX/scaleY }`：`drawBackdrop` 按屏幕坐标采样 backdrop，
        //     缩放会把**已捕获的背景**一起缩放 ⇒ 折射错位（详见 [LiquidGlass.BubbleStretchX]）。
        Box(Modifier.requiredSize(glassW, glassH)) {
            GlassPanel(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                backdrop = backdrop,
                spec = spec
            ) {}
        }

        // ② 图标：钉在**宿主**中心，不随按压缩放。
        //   理由（这是本版与"整体缩放"方案的关键差别）：若图标跟着玻璃一起放大，
        //   整颗钮就只是"变大了"，读起来是**缩放**而不是**形变**；
        //   玻璃胀大而图标保持原尺寸才读得出「玻璃被顶起来、图标还压在原位」的果冻感。
        //   附带好处：图标尺寸恒定 ⇒ 不逐帧 remeasure、笔画不抖、小尺寸下始终清晰。
        //   ⚠️ 必须最后声明 —— 沉到玻璃层下会被 drawBackdrop 的折射糊掉。
        Icon(
            appPainter(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * 统一返回/关闭键（2026-09-18 立，2026-10-01 九校改为 Liquid Glass）。
 *
 * 保持**签名与语义不变**（原 `(onClick, modifier, icon, contentDescription, diameter)`，
 * 仅追加 `backdrop`）⇒ 全站既有调用点（AppNav 全局件 / DocReader / BookScreen / LessonEditor /
 * WeaknessScreen / PracticeSummary / SettingsGoal）**零改动**自动升级到新玻璃语言。
 *
 * 形态沿革：
 *  - 2026-09-18：44dp 圆形玻璃件 = 羽化雾面层 + 半透明底 + 发丝高光边 + 投影（旧磨砂方案）；
 *  - 2026-10-01 九校：改为 [GlassIconButton]，走与底部导航同一套 `drawBackdrop` 真折射玻璃
 *    （[LiquidGlass.ButtonSpec]），并补齐**按压果冻动画**（整体放大 + 横向拉伸 / 纵向压扁，松手回弹）；
 *  - 2026-10-01 十四校：材质档位与导航胶囊**完全合一**（[LiquidGlass.ButtonSpec] ≡ [LiquidGlass.NavSpec]）。
 *
 * @param icon 图标名（见 [appPainter]）：返回用 `"back"`，关闭用 `"close"`。
 * @param diameter 直径，默认 44dp（无障碍最小点击尺寸）。
 * @param backdrop 见 [GlassIconButton] 的使用约束 —— 内联使用请留 null。
 */
@Composable
fun GlassBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "back",
    contentDescription: String = "返回",
    diameter: Dp = 44.dp,
    backdrop: LayerBackdrop? = null
) = GlassIconButton(
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    contentDescription = contentDescription,
    size = diameter,
    backdrop = backdrop
)

/**
 * 悬浮返回条（二级界面统一入口）：【左上角圆形玻璃件 + 标题玻璃胶囊】。
 *
 * 设计要点：
 * - 不占用 Scaffold 固定顶栏高度，作为 overlay 叠加在内容之上（AppRoot 已给二级页预留 top padding 56dp）；
 * - 返回键为左上角【圆形玻璃悬浮件】（[GlassBackButton]），玻璃语言与底部导航一致；
 *   🔴 标题紧随其右，但**刻意不做玻璃**（纯静态标签：极淡底 + 发丝边）—— 理由见下方内联注释；
 * - 整体高度 = 44 + 6×2 = 56dp，与 AppRoot 为二级页预留的 `padding(top = 56.dp)` 完全吻合，挂载点无需改动；
 * - 沉浸式答题（练习页）为一 Tab 且由自身渲染返回键，不经过此处。
 *
 * 🔴 2026-10-01 九校：**唯一**一个可以拿到真折射的返回件 ——
 *   本组件由 AppNav 挂在 `layerBackdrop` 节点的**同级之后**（不在其子树内），
 *   因此可以把共享的 `navBackdrop` 传进来；各屏**内联**的返回/关闭件在 NavHost 内部（在录制范围内），
 *   传共享 backdrop 会自采样，一律留 null（见 [GlassIconButton] 的约束说明）。
 *
 * @param backdrop AppRoot 的共享背景捕获层；传 null 时退化为「无折射的半透玻璃」（视觉仍成立）。
 */
@Composable
fun FloatingBackButton(
    nav: NavHostController,
    title: String,
    showTitle: Boolean = true,
    backdrop: LayerBackdrop? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassBackButton(onClick = { nav.navigateUp() }, backdrop = backdrop)
        if (showTitle && title.isNotBlank()) {
            // 🔴🔴 2026-10-01 九校 · 杰哥裁定：**标题不用玻璃**。
            //
            // 我曾把标题胶囊也换成 Liquid Glass（`LiquidGlass.TagSpec`），被杰哥当场否掉：
            // 「还有标题为什么会用同样的玻璃质感」。理由成立，且是**材质语义**层面的错误：
            //  ① 玻璃在本设计系统里承担的是**「可交互的浮空控件」**语义 —— 导航胶囊（可点）、
            //     选中指示底（跟随位置）、返回/关闭圆钮（可按）无一例外；用户已经由此学会
            //     「玻璃 = 可操作」。标题是**纯静态文本**，给它同一材质就是在暗示可点，
            //     属于 affordance 撒谎（点了没反应）。
            //  ② 视觉层级：两块相邻玻璃会互相争夺重心。实测放大图上，标题胶囊面积是返回钮的
            //     2.5 倍、且同为白色，读起来像"标题才是主操作"，而真正可点的返回钮反而被压住。
            //  ③ 参考实现（UFIPanel / iOS 26）的标题一律是**裸文字**，不做玻璃底。
            // ⇒ 恢复为「极淡半透明胶囊 + 发丝边」的**静态标签**形态：有底以保证在浅色内容上的
            //   可读性，但**不提亮、不折射、不投影**（三个玻璃特征一个都不给），与圆钮明确分层。
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
                        shape = RoundedCornerShape(50)
                    )
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
