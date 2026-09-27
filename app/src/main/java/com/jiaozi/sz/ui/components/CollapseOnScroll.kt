package com.jiaozi.sz.ui.components
import com.jiaozi.sz.ui.components.HubBar
import com.jiaozi.sz.ui.components.CollapsingTopBlocks

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 列表页「顶部重区块」基础设施。
 *
 * ## 沿革（判据）
 * - 2026-09-18：定「外框挂折叠监听 + 固定带手势直通」范式（真机普查 5 个一级页）；
 * - 2026-09-21：**折叠机制整体停用**（观感不佳）⇒ 外框监听退回 no-op、顶部容器退回普通 Column；
 * - 2026-09-25：**死代码清理**（杰哥「一并清掉」）—— 折叠遗留物全数删除：
 *   `Modifier.collapseOnScroll`、`Modifier.scrollAndCollapse`、`TopCollapseState.nested`、
 *   连续进度 / 行程 / 滚动增量等驱动字段、6 个失活常量；全站 15 处 `.collapseOnScroll(top)` 调用链同步移除；
 * - **2026-09-25 晚：折叠状态机彻底退场**（杰哥「D 清掉」）—— 复查发现上一轮清理后
 *   `TopCollapseState.settled` 已无任何**置真**写入源（见下），`collapsed` 退化为**结构性恒 false**：
 *   * `Saver.restore` 只还原旧值、`locked` setter 与 `expand()` 都只置假 ⇒ 无一能把它置真；
 *   * 两处构造点均不传 `initiallyCollapsed = true`（默认 false） ⇒ 与运行时条件无关，恒 false。
 *   由此产生 3 处**永不生效**的死分支，本文件一并删除：
 *   ① `CollapsedHubBar` 的 `AnimatedVisibility(visible = top.collapsed)` ⇒ **12 处页面调用永不渲染**；
 *   ② `ProofScreen` 的收起态返回键；
 *   ③ `ProofScreen` 的 chip 内边距「收起态 8dp」分支。
 *
 * ## 本文件现存的**真实行为**（只有两条）
 * 1. [HubBar] —— 顶部常驻栏（分区标题 + 主操作），页面可见；
 * 2. [hubDragToScroll] —— 固定带「手势直通滚动」。
 * 另有 [CollapsingTopBlocks]：便于阅读的顶部常驻块标签（等价 `Column(spacedBy)`）。
 *
 * 🔴 [hubDragToScroll] 是本文件**唯一不可删**的东西：固定带（Hero / 统计卡 / 搜索胶囊）自身是
 * 普通 `Column`，内部没有任何滚动节点 ⇒ 不给它挂滚动节点，手指落上去**既不滚列表也无任何反应**
 * （2026-09-18 真机普查：题库页固定带占屏 88% / 564dp，被放大成「整页不可用」）。
 * ⚠️ 曾一度把它与 `collapseOnScroll` / `CollapsingTopBlocks` 并列为「零行为三件套」，**该判断是错的**：
 * 它行为真实，全工程 **26 处调用点**（`Modifier.hubDragToScroll`；连同定义与注释共 62 处文本匹配）。
 *
 * 布局铁律（与折叠无关，长期有效）：外框只放 Hero 与常驻栏；唯一滚动容器 `weight(1f)`；
 * 块闭合用括号计数、避免嵌套错位。
 */

/**
 * 顶部常驻块容器（**普通纵向 Column**）。
 *
 * 高度恒等于内容自然高度：不缩高、不视差、不裁剪。
 * 保留此名字只为给「Hero / 统计卡 / 搜索胶囊」这一组顶部常驻内容一个可读的语义标签；
 * 行为完全等价于 [Column] + [Arrangement.spacedBy]。`spacing` 真实生效（全站 14 页在用）。
 */
@Composable
fun CollapsingTopBlocks(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/**
 * 顶部固定带「手势直通滚动」：让手指落在**非滚动**的固定带（Hero / 统计卡 / 搜索胶囊 /
 * 快捷入口组）上时也能滚动列表。
 *
 * 🔴 背景（2026-09-18 真机普查 5 个一级页）：两段式布局的固定带是普通 `Column`，
 * 内部没有任何滚动节点 ⇒ 手指落上去**既不滚列表、也不触发折叠**，表现为「整页点下去毫无反应」。
 * 题库页因固定带占屏 88%（564dp / 640dp）被放大成「完全不可用」——实际是全站通病，
 * 只是其余 4 页固定带较矮（249~306dp）、列表区够大才没暴露。
 *
 * 做法：给固定带挂一个**指向列表状态**的纵向 `scrollable` 节点。落在固定带上的手势由它消费，
 * 直接推进同一个 `LazyListState`；落在列表上的手势仍由 `LazyColumn` 自己的滚动节点消费。
 * 两者**互斥命中**（Compose 一次手势只驱动命中的那一个 scrollable 节点）⇒ 不会双倍滚动。
 *
 * ⚠️ 本 modifier **自身不含**折叠监听，故意如此：只做「手势直通」一件事，职责单一。
 * 🔴 **折叠停用不影响本 modifier 的有效性**——「固定带点下去没反应」是滚动可达性问题，
 * 与折叠无关；删掉它题库页会立刻回归「整页不可用」。
 */
fun Modifier.hubDragToScroll(
    state: ScrollableState,
    enabled: Boolean = true
): Modifier = this.scrollable(
    state = state,
    orientation = Orientation.Vertical,
    enabled = enabled
)

/**
 * 顶部常驻栏（约 52dp）：**分区标题 + 主操作**，展开态即常驻。
 *
 * - 左：`title`（建议带条数，如「备课组 · 12」）；
 * - 其后：`actions`（图标按钮，如「新建教案」）。
 *
 * ## 沿革
 * - 2026-09-19：原为「收起态紧凑栏」（`CollapsedHubBar`），靠 `top.collapsed` 门控显隐；
 * - 2026-09-21：`persistent = true` 版改为展开态也常驻（高保真稿在统计卡可见时同样画出本栏），
 *   语义升级为「分区标题 + 主操作」；
 * - **2026-09-25 晚**：复查确认 `top.collapsed` 结构性恒 false ⇒ 非 persistent 版**12 处页面调用
 *   永不渲染**（已逐页核查，全部有等价替代入口，无功能丢失）⇒ 调用点删除；组件自身也去掉
 *   `top` / `icon` / `iconDesc` / `onExpand` / `leading` / `chips` / `persistent` 全部死参数，
 *   更名为 [HubBar]，**恒常驻**。
 *
 * 原 `chips` 槽已删除：`persistent` 态本就不补 chips（筛选栏在滚动区首行，补了会与首行重复——
 * 即设置页截图那条「重复筛选行」的历史事故成因）。
 */
@Composable
fun HubBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.primary
        )
        actions()
    }
}