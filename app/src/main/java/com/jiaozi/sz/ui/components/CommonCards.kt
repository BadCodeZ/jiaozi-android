package com.jiaozi.sz.ui.components

import com.jiaozi.sz.ui.components.AppPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiaozi.sz.ui.theme.AppGradients
import com.jiaozi.sz.ui.theme.LocalAppDark

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
    /**
     * 🔴🔴 独立装饰图标位（2026-09-23 新增，修 CMP-HERO 组件级缺陷）。
     *
     * **背景（缺陷）**：本件左端 46dp 槽位自 2026-09-20 起是 `onBack` 与 `icon` **二选一**
     * （`if (onBack != null) 返回箭头 else icon`）。于是**凡是传了 `onBack` 的沉浸二级页，
     * 传进来的 `icon` 会被静默吞掉、根本不渲染**。受害 3 页：
     * - `SettingsScreen.kt` 的齿轮（稿面左端为齿轮）
     * - `LessonHub.kt` / `LessonTemplateLibrary.kt` 的装饰图标（稿面右端 56dp 白 30%）
     * - `ChaptersScreen.kt` 的 school 图标
     *
     * **修法**：装饰图标**不与返回箭抢左端槽**，改为独立承载位 —— 落在标题行右侧
     * （`Row { Text(title); Icon(decorIcon) }`）。左端槽仍恒定 46dp、仍只放返回箭，
     * 右端 action 槽仍只放功能键 ⇒ 三者互不侵占，跨页版式零跳动。
     *
     * ⚠️ 旧参数 `icon` 保留为「无返回键时的左端徽章」（12 个既有调用点零回归），
     * **新页面一律用 `decorIcon`**，不要再用 `icon` + `onBack` 组合。
     */
    decorIcon: Painter? = null,
    action: @Composable (() -> Unit)? = null,
    showPenguin: Boolean = false,
    /**
     * 🔴 沉浸模式（2026-09-19 按高保真稿新增）：通栏 + 顶部贴屏幕边 + **仅底部两角圆角**，
     * 状态栏白字直接压在 hero 渐变上。用于一级 Tab 的 Hero；二级页保持原「四角圆角卡片」形态。
     */
    immersive: Boolean = false,
    /**
     * 沉浸模式下为状态栏预留的高度：hero **背景**仍铺满到屏幕顶，仅**内容**下移避让。
     *
     * 🔴 实现要点（2026-09-19 修正）：整体高度确实要 +statusBarInset（内容才躲得开状态栏），
     * 但这会让 hero 的**布局盒**比**可见范围**高出一个 inset，从而把下方元素整体推低同样的量
     * —— 实测「今日目标」卡片被推低 24dp，hero 下沿与卡片之间凭空多出 24dp 死区（设计稿实测仅 ~3dp）。
     * （补充：原另有「`CollapsingTopBlocks` 过渡期裁剪 ⇒ 顶部闪出 24dp 底色」一层顾虑，
     * 该裁剪行为已随折叠机制于 2026-09-21 停用、2026-09-25 清除；下面的「上溢 + 少报」结论
     * **不依赖**该裁剪，单独成立。）
     *
     * 故此处用 [layout] 做「**向上溢出 + 对外少报**」：正常测量（含 inset），但
     * ① 把内容整体上移 insetPx（视觉从屏幕顶开始）；② 上报高度减去 insetPx（下方元素回到原位）。
     * 调用方因此**不需要**再写 `Modifier.offset(y = -statusBarTop)`。
     */
    statusBarInset: Dp = 0.dp,
    /**
     * 🔴🔴 内联返回键（2026-09-19 新增；**2026-09-20 收敛为水平单行统一版式**）：
     *
     * 沉浸 Hero 二级页（收集箱 / 校订）的 Hero 背景铺到屏幕顶，若仍由 `AppNav` 在**最上层**叠加
     * 全局悬浮返回件（44dp 磨砂白圆 + 标题胶囊），它会**正好压在 Hero 的 46dp 图标徽章上**
     * （实测两页均重叠）。处置 = 返回入口**内联进 Hero**。
     *
     * 🔴 版式：**与其余 10 个页面共用同一条水平单行骨架**——
     * `Row(sp.14)：[左端槽位] → Column(title/subtitle, weight 1f) → [action 槽]`。
     * 左端槽位**二选一**（**同一槽位、同为 46dp 白 18% 圆底**，跨页跳转不产生版式跳动）：
     * - `onBack != null` ⇒ 返回键（46dp 圆底 + **22dp** 白箭头）；
     * - `onBack == null` ⇒ 图标徽章（46dp 圆底 + **26dp** 白图标，= 改造前基线原值，零回归）。
     *
     * ⚠️ 2026-09-20 **撤回**此前的「垂直堆叠」实现：当时 `onBack != null` 会另起一套 `Column` 骨架
     * （首行 `[返回键] ←→ [action]`、次行 `[图标徽章 + 标题/副标题]`），造成沉浸页与非沉浸页
     * **两套 Hero 骨架**、Hero 内容高从 ~64dp 涨到 ~98dp，与其余 10 页观感割裂（杰哥 2026-09-20 反馈
     * 「呈现的效果不是很满意，感觉不好看」）。现全部收敛为单行，对齐 02 号 `CMP-HERO`。
     *
     * ✅ 零回归依据：改造前基线（`build/_bak_ui_20260918/…/CommonCards.kt` L95-113）本即
     * `Row(sp.14) → [46dp 圆底图标徽章] → Column(title/subtitle, weight 1f) → [action]`；
     * 本次仅把「`if (icon != null)` 才画徽章」放宽为「`icon != null || onBack != null`」，
     * **图标分支的尺寸与外观逐字未改** ⇒ 12 个既有调用点观感零变化。
     */
    onBack: (() -> Unit)? = null,
    /**
     * 🔴 2026-09-21 新增（按杰哥「Hero 默认以简洁形式展示、不占大面积」）：
     * **紧凑形态** —— 逐项收窄而不是砍信息：
     * 竖向内边距 20→10、左右 20→16、左端徽章 46→36、图标 26→20、
     * 标题 titleLarge→titleMedium、副标题 bodyMedium→bodySmall(单行)、行距 6→2。
     *
     * 整块高度约从 ~137dp 降到 ~80dp（沉浸页含状态栏 inset），**副标题等信息一条不丢**。
     * 默认 `false` 保持既有页面观感；要"默认简洁"的页面显式传 `true`。
     */
    compact: Boolean = false,
    /**
     * 🔴 2026-09-30 新增（修实测缺陷 P1：Hero 副标题被无差别截断）。
     *
     * **背景**：此前 subtitle **恒 `maxLines = 1`**，而大量调用方的文案远超一行
     * （今日页「点此选择目标日，开启备考倒计时」、下载页「先选报考学段，只下载你需要的题目」），
     * 真机实测渲染成「点此选择目标日，开启备考…」「先选报考学段，只下…」——
     * **关键信息（"开启什么"/"下载什么"）恰好落在省略号里**，比不显示更糟。
     *
     * **默认行为（`null`，推荐）＝ 自动**：
     * - 含硬换行 `'\n'` ⇒ 仍按行拆分、每行 1 行（保 2026-09-22 的 `books.main` 两行方案，零回归）；
     * - 不含硬换行 ⇒ 允许 **2 行**自动折行（本次修复目标）。
     *
     * 需要强制单行的调用方显式传 `1`。
     */
    subtitleMaxLines: Int? = null
) {
    val heroShape = if (immersive) {
        androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape20
    }
    // 沉浸：状态栏避让 —— 显式传值优先，为 0 时自动读取系统状态栏高度（新接入页只传 immersive 即可）
    val autoInset = if (immersive) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    val effectiveInset = if (immersive) maxOf(statusBarInset, autoInset) else 0.dp
    val insetPx = with(LocalDensity.current) { effectiveInset.roundToPx() }
val screenWpx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val maxPadPx = with(LocalDensity.current) { 32.dp.roundToPx() }
    Box(
        modifier
            .fillMaxWidth()
            .then(
                /**
                 * 🔴🔴 **沉浸破框（2026-09-22 统一收口，纵向 + 横向同步）**
                 *
                 * 目标：沉浸 Hero 的蓝色底从**屏幕左沿铺到右沿、顶边贴屏幕顶**，
                 * 而 Hero 的**内容**仍保持原有左右 20dp / 顶 20dp+状态栏 的视觉，
                 * 且**兄弟元素（下方卡片、筛选行）的宽度与纵向位置逐像素不变**。
                 *
                 * 难点：调用方外框情况不一 —— 既有 5 个沉浸页外框**无 padding**，
                 * 而卡片派二级页外框是 `padding(16.dp)`（四边）。后者若只按既有的
                 * 「上溢 statusBarInset」处理，背景顶边会停在「状态栏底 + 16dp」，
                 * 顶部露出 16dp 页面底色；水平方向更会退化成「蓝色圆角卡片」。
                 *
                 * 处置：**按父容器内边距实测反推**，不写死常量 ——
                 * - 本节点 `constraints.maxWidth` = 屏宽 − 父左右 padding；与屏宽相减即得 `padPx`；
                 * - **横向**：按 `availW + 2*padPx`（= 屏宽）重新定宽测量 ⇒ 蓝底铺满；
                 * - **纵向**：上移量 = `statusBarInset + padPx` ⇒ 顶边同时越过状态栏与父 top padding，贴到屏幕顶；
                 * - **对外上报**：宽只报 `availW`、高只报 `p.height − 上移量`
                 *   ⇒ 兄弟元素位置与宽度**零变化**。
                 *
                 * ✅ 零回归：既有 5 个沉浸页 `padPx = 0`（外框无 padding），
                 * 横向不生效、纵向上移量恰好 = 原 `statusBarInset`，与改造前**逐像素一致**；
                 * 非沉浸页整个 modifier 不挂载。
                 *
                 * ⚠️ `padPx` 上限 32dp：宽屏（≥720dp）限宽居中时左右留白可达数百 dp，
                 * 按上限截断避免 Hero 过度外溢。此时顶部破框亦不完整 —— 与一级 Tab 的
                 * 宽屏表现一致（既有行为，本次不改口径）。
                 */
                if (immersive) Modifier.layout { measurable, constraints ->
                    val availW = constraints.maxWidth
                    val rawW = if (availW == androidx.compose.ui.unit.Constraints.Infinity) constraints.maxWidth else availW
                    val padPx = if (rawW == androidx.compose.ui.unit.Constraints.Infinity) {
                        0
                    } else {
                        ((screenWpx - rawW) / 2).coerceIn(0, maxPadPx)
                    }
                    val liftPx = insetPx + padPx
                    val measureC = if (padPx > 0) {
                        val targetW = rawW + padPx * 2
                        constraints.copy(minWidth = targetW, maxWidth = targetW)
                    } else constraints
                    val p = measurable.measure(measureC)
                    val reportedH = (p.height - liftPx).coerceAtLeast(constraints.minHeight)
                    layout(rawW, reportedH) { p.place(-padPx, -liftPx) }
                } else Modifier
            )
            .background(AppGradients.hero(LocalAppDark.current), heroShape)
            .padding(
                start = if (compact) 16.dp else 20.dp,
                end = if (compact) 16.dp else 20.dp,
                top = (if (compact) 10.dp else 20.dp) + effectiveInset,
                bottom = if (compact) 10.dp else 20.dp
            )
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
                    drawPath(beak, AppPalette.c_ffe67e22.copy(alpha = 0.3f))
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
        // ── 水平单行（全局唯一骨架，对齐 02 号 CMP-HERO.layout）──
        // Row(sp.14)：[左端槽位] → Column(title / subtitle, weight 1f) → [action 槽]
        // 🔴 左端槽位**尺寸恒定 46dp**（与图标徽章同规格），仅内容二选一：
        //    onBack != null ⇒ 返回箭头（22dp）；否则 ⇒ 页面图标（26dp）。
        //    这样「有无返回键」不会改变 Hero 高度与左端占位，跨页跳转零版式跳动。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 🔴🔴 2026-10-01 九校二迭代（杰哥裁定）：左端槽的**返回键**改用 Liquid Glass 圆钮，44dp。
            //   原形态 = 46dp 白 18% 圆 + 22dp 白箭头（Hero 渐变上半透，刻意弱化）。
            //   杰哥要求全站返回/关闭入口「统一液化玻璃材质 + 统一大小（= 二级界面左上角返回键的 44dp）」，
            //   ⇒ 这里换成与全局悬浮返回件**完全同一个** [GlassIconButton]：
            //     同一套 drawBackdrop 玻璃、同一套按压果冻放大/回弹、同一档尺寸。
            //   槽宽同步 46 → 44dp（跨页零跳动仍成立：有/无返回键时都占 44dp）。
            //   ⚠️ 图标色由纯白改为主色 —— 玻璃底是浅色，白图标会看不见。
            //   ⚠️ **装饰图标徽章**（onBack == null 的 `icon` 分支）**不参与**本次统一：
            //     它是不可点的页面标识，不是"入口"，改玻璃会让它被误读为按钮（affordance 撒谎）。
            //     只把尺寸一并对齐到 44dp，保证左端槽宽度恒定。
            if (onBack != null) {
                GlassIconButton(
                    onClick = onBack,
                    icon = "back",
                    contentDescription = "返回",
                    size = if (compact) 36.dp else 44.dp
                )
            } else if (icon != null) {
                Box(
                    Modifier.size(if (compact) 36.dp else 44.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, Modifier.size(if (compact) 20.dp else 26.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                // 标题行：标题 + （可选）独立装饰图标位（见 decorIcon KDoc）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (decorIcon != null) {
                        Icon(
                            decorIcon,
                            contentDescription = null,
                            Modifier.size(if (compact) 18.dp else 22.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                // 🔴 2026-09-22 按 08 号 `books.main` 高保真：subtitle 支持**硬换行分行**。
                //   背景：08 号要求 subtitle 两行 —— 第 1 行静态说明「导入教材原文，结构化索引，随备课信封同步」、
                //   第 2 行动态覆盖度「3 本 · 覆盖科一/科二」。若整体交给单个 Text + maxLines=2，
                //   真机（MuMu 360dp）第 1 行 20 字 × 14sp = 280dp > 可用 228dp ⇒ **自动折行占满 2 行**，
                //   第 2 行动态行被完全丢弃（实测 OCR 只见「导入教材原文，结构化索」+「引，随备课信封同步」）。
                //   ⇒ 改为按 '\n' 拆分成多个 Text、每行 maxLines=1：第 2 行必显示；第 1 行超宽则省略号收尾。
                //   单行调用方（无 '\n'）走同一路径、渲染结果与改造前逐字一致 ⇒ 零回归。
                // 🔴 2026-09-30 补充（见 `subtitleMaxLines` KDoc）：上述"每行 1 行"对**无硬换行的长文案**
                //   是过度裁剪 —— 实测把「点此选择目标日，开启备考…」「先选报考学段，只下…」的关键信息切掉。
                //   ⇒ 无硬换行时默认放宽到 2 行自动折行；有硬换行时维持每行 1 行（零回归）。
                // subtitle 为空串时整块跳过（否则 split 会产出 [""] 渲染一个空行、白占一行高度）
                if (subtitle.isNotBlank()) {
                    val subLines = subtitle.split('\n')
                    val perLine = subtitleMaxLines ?: if (subLines.size > 1) 1 else 2
                    subLines.forEach { line ->
                        Text(
                            line,
                            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = perLine,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)) {
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
