@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 答题会话：题目视图、选项行、计时器、模考倒计时。
 *
 * 从 PracticeScreen.kt 拆分而来（纯物理拆分，逻辑未改）。
 */

import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.PracticeState
import kotlin.math.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jiaozi.sz.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.jiaozi.sz.domain.answerIndex
import com.jiaozi.sz.domain.parseOptions
import com.jiaozi.sz.ui.CAUSE_OPTIONS
import com.jiaozi.sz.ui.Motion
import com.jiaozi.sz.ui.reduceMotionNow
import com.jiaozi.sz.ui.PracticeViewModel
import com.jiaozi.sz.xiaomi.Haptic
import com.jiaozi.sz.xiaomi.StudyTimerService
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun SessionView(vm: PracticeViewModel, st: com.jiaozi.sz.ui.PracticeState) {
    val ctx = LocalContext.current
    val rm = reduceMotionNow(ctx)
    val q = st.current ?: return
    val isLast = st.isLast

    // 模考·全屏考场沉浸：限时套卷期间隐藏状态栏+导航栏，进入纯考场视野；
    // 系统返回手势拦截为「二次确认退出」，防手滑中断模考。
    val isMock = st.timeLimitSec != null && st.timeLimitSec!! > 0
    var showExitConfirm by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { showExitConfirm = true }
    DisposableEffect(isMock) {
        if (isMock) {
            ctx.findActivity()?.window?.let { w ->
                val ctrl = WindowInsetsControllerCompat(w, w.decorView)
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            ctx.findActivity()?.window?.let { w ->
                WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(if (isMock) "离开模考？" else "退出练习？") },
            text = {
                Text(
                    if (isMock) "离场将按当前进度交卷结束。"
                    else "退出不自动保存，已答进度计入错题本。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; vm.exitSession() }) { Text(if (isMock) "交卷离开" else "退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text(if (isMock) "继续答题" else "继续练习") }
            }
        )
    }

    // 专注计时由练习「会话」生命周期管理（begin 开 / exitSession、末题、超时、ViewModel 销毁 关）。
    // 视图只负责读秒显示，不持有计时状态。
    //
    // 🔴 2026-09-24 计时口径变更（杰哥拍板「后台暂停 / 超时归零」）：
    //   ① 站内切 Tab **不**触发 onStop ⇒ 继续累计；
    //   ② 真正退到后台（锁屏 / 切走 App）⇒ `MainActivity.onStop` 暂停读秒，读数冻结；
    //   ③ 回前台时若离开 > 30 分钟 ⇒ `StudyTimerService` 整段归零从零重计（防跨夜失真）。
    //   详见 `xiaomi/StudyTimerService.BACKGROUND_RESET_TIMEOUT_MS`。

    // 练习页内顶部计时：专注时长与模考倒计时各自收进独立小 Composable，
    // 每秒的读秒只重绘那一小块，不再触发整个 SessionView（含 AnimatedContent + 选项列表）重组，消除卡顿。

    // 当前题收藏态：原挂在底部工具条，2026-09-24 底栏删除后上提至顶栏（常驻高频动作）
    val favIds by vm.favIds.collectAsStateWithLifecycle()
    val isFav = q.id in favIds

    Column(Modifier.fillMaxSize().background(AppColors.bg).displayCutoutPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 🔴 2026-09-24 顶栏收口（杰哥：『调整练习界面的其他功能键的屏幕占比，做题才是关键』）
        //   背景：原底部「更多 ··· / 收藏 / 草稿」整条工具条（48dp 控件 + 14dp 间距 ≈ 62dp）常驻屏底，
        //        但实测其中【收藏】≡ ⋯菜单「收藏本题」、【草稿】≡ ⋯菜单「存到收集箱」——两键两功能，纯冗余。
        //   处置（方案 A）：
        //     ① 删除整条底部工具条 ⇒ 题目视口（AnimatedContent weight 1f）净增 62dp，376dp → 438dp（+16.5%）。
        //     ② 收藏上提为顶栏常驻键，对齐 04 号规范 practice.session E1「… → 计时 → 收藏键 40dp」。
        //     ③ 「存到收集箱」为低频动作，下沉至答题卡面板内（见 showCard 分支），不再占常驻位。
        //     ④ 顶栏高度锁定 44dp（原 IconButton 撑到 48dp），另省 4dp。
        //   🔴 2026-09-24 二次精简（杰哥：『继续精简，上方显示的信息可以再减少一些，比如减少汉字的出现，
        //      题目只显示 1/n 即可，然后该有的功能图标继续存在』）：
        //      ① 「✕ 退出」→ 纯 close 图标（汉字归零，图标语义自明 + contentDescription 保无障碍）。
        //      ② 「${st.mode} · 第 7/20 题」→「7/20」（去 mode 标签 + 去「第/题」汉字，数字自解释）。
        //      ③ 右侧计时/收藏/答题卡三枚功能图标一律保留（杰哥明确「该有的功能图标继续存在」）。
        //      左端由「图标+双行文案」收成「图标+短数字」，顶栏视觉重量大幅下降，视线直达题目。
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showExitConfirm = true }, modifier = Modifier.size(40.dp)) {
                    Icon(appPainter("close"), contentDescription = "退出练习", modifier = Modifier.size(20.dp))
                }
                Text("${st.index + 1}/${st.total}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (st.timeLimitSec == null) PracticeTimer()
                // 收藏键：常驻顶栏（原底栏重复项，现唯一入口）
                IconButton(onClick = { vm.toggleFav(q.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(appPainter("star"), contentDescription = "收藏", tint = if (isFav) AppColors.warning else AppColors.textSecondary, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showCard = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.GridOn, contentDescription = "答题卡", modifier = Modifier.size(22.dp))
                }
            }
        }

        // 🔴 2026-09-24 三轮精简（杰哥勾选「去掉 4dp 进度条」）：
        //   顶栏左端已有 `${index+1}/${total}` 数字进度，4dp LinearProgressIndicator 属重复表达。
        //   摘除后省下「4dp 条 + 14dp 间距 = 18dp」全部归还题目视口（Column spacedBy(14.dp) 自动收敛）。
        //   去掉的是顶栏下方的进度条；模考倒计时（MockCountdown）是独立元素，仍保留其自身进度语义。
        // 模考倒计时进度条（红色越界告警）——独立 Composable，每秒仅重绘自身
        if (st.timeLimitSec != null && st.timeLimitSec!! > 0) {
            MockCountdown(st.timeLimitSec!!) { vm.onTimeout() }
        }

        // 切题平滑过渡：按题目 id 取快照，避免题目瞬间硬切打断心流（低端机也不显突兀）
        AnimatedContent(
            targetState = q.id,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn(tween(Motion.duration(rm, Motion.BASE))))
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(tween(Motion.duration(rm, Motion.BASE))))
            },
            label = "questionSwap"
        ) { id ->
            val qq = st.questions.firstOrNull { it.id == id } ?: q
            val opts = parseOptions(qq.opt)
            val scrollState = rememberScrollState()
            // 反馈卡在滚动内容中的顶部偏移（提交后据此把卡顶滚到视口顶部）
            var feedbackOffset by remember { mutableStateOf(0) }
            // 提交后自动滚动到反馈卡**顶部**（判定行「答对了 / 答错了」所在处）。
            // 🔴 此前是 animateScrollTo(maxValue) 滚到绝对底部，真机复现缺陷：
            //    解析块是可见了，但排在最前的判定行被顶出视口上方——用户提交后第一眼
            //    看不到「答错了」，只看到「为什么错了？」，主反馈反而丢了（主观题/客观题均复现）。
            //    改为滚到卡顶：判定行置顶，错因 / 解析顺次向下；内容超出仍可继续滚动查看。
            LaunchedEffect(st.answered) {
                if (st.answered) {
                    delay(80) // 等一帧布局完成，确保卡位置已测量
                    scrollState.animateScrollTo(maxOf(0, feedbackOffset))
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AppColors.blueBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(if (qq.isSubjective) "主观题" else "单选题", style = MaterialTheme.typography.labelSmall, color = AppColors.blue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(qq.q, style = MaterialTheme.typography.bodyLarge, fontSize = 16.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // ── 选项 / 主观题列表 ──
                // 🔴 2026-09-23 根治（真机+无障碍树+APK/DEX 三重实证）：整页内容区改为**单一垂直滚动 Column**。
                //   原结构 `Column{ 题干卡; 反馈卡(含解析块); LazyColumn(weight(1f)) }`：
                //   内容区可用高 ≈1248px，题干卡占 ~334px 后，反馈卡被赋予的 maxHeight ≈866px
                //   < 其自然高（判定行+错因卡+解析块 ≈1000px）⇒ `Card`(Surface) 裁剪溢出内容，
                //   排在最后的「解析块」永远不合成（树中查无「解析」节点、像素无蓝底），
                //   同时 `LazyColumn(weight 1f)` 只剩 ~48px 塌成空白。
                //   反证：APK 的 DEX 中 `暂无解析` 命中 2 次（=L270 解析块 + L309 参考答案）⇒ 代码已编入包，
                //   确系布局裁剪而非陈旧编译。
                //   正解 = 题干→选项→反馈卡 顺序排布 + 整页 `verticalScroll`（无 height 上限、无裁剪），
                //   全部内容随页滚动即可见 —— 契合杰哥既定「去掉所有收缩、正常滚动」。
                if (qq.isSubjective) {
                    if (!st.showAnswer) {
                        // 第一步：写草稿
                        OutlinedTextField(
                            value = st.draft,
                            onValueChange = { vm.setDraft(it) },
                            label = { Text("写下答案（草稿）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8
                        )
                        Button(
                            onClick = { vm.revealAnswer() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("对答案") }
                    } else {
                        // 第二步：展示参考答案，自评
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("参考答案 / 解析", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text(qq.analysis ?: "暂无解析", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        // 错题本重练主观题时，展示当初作答（与当前草稿不同才提示，避免和预填重复）
                        if (!st.historyDraft.isNullOrBlank() && st.historyDraft != st.draft) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("你上次作答", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(st.historyDraft ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("对照后自评：", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(
                                    if (st.subjectiveResult == "right") "✓ 我答对了" else "我答对了",
                                    Modifier.weight(1f)
                                ) { vm.markSubjective("right") }
                                ActionButton(
                                    if (st.subjectiveResult == "wrong") "✓ 我还不会" else "我还不会",
                                    Modifier.weight(1f)
                                ) { vm.markSubjective("wrong") }
                            }
                        }
                    }
                } else {
                    val correctIdx = answerIndex(qq.answer)
                    opts.forEachIndexed { i, _ ->
                        val letter = ('A' + i).toString()
                        val isCorrect = i == correctIdx
                        // 答错后：用户选错的那项标红✗，正确项标绿✓；未作答前只显示选中态
                        val wrongSel = st.answered && (st.selected == i) && !isCorrect
                        OptionRow(
                            letter = letter,
                            text = opts[i],
                            selected = st.selected == i,
                            answered = st.answered,
                            correct = isCorrect,
                            wrongSelected = wrongSel
                        ) { vm.selectOption(i) }
                    }
                }

                // ── 一体化反馈卡（04 号 E5 / SP-OPTION-STATE）：置于选项之后，随整页滚动，永不裁剪 ──
                // 🔴 2026-09-23 定案：答对/答错互斥渲染同一张卡。
                // 🔴 2026-09-24 重排（杰哥：错因占比不应压过解析、本末倒置）：
                //   顺序改为 **判定行 → 解析块 → 折叠错因入口**，解析成为反馈卡主内容。
                //   原结构把「为什么错了？」白底嵌套卡（两级标题 + 5 chips）排在解析**之前**，
                //   视觉重量远超解析块 ⇒ 本末倒置。
                //   错因同时降级为**可标可不标**（`canNext` 不再要求选中，见底部动作栏）。
                if (st.answered) {
                    val ok = st.correct
                    val okFg = if (ok) AppColors.success else AppColors.danger
                    // 错因折叠展开态：答错默认收起，仅留一行轻量入口
                    var causeExpanded by remember(q.id) { mutableStateOf(false) }
                    // 🔴 2026-09-24 真机复现缺陷（uiautomator bounds 实证）：
                    //   折叠入口位于反馈卡最末、紧邻底部动作栏（行 y≈1982–2174，视口底 2560、
                    //   动作栏自 2272 起）。展开后 5 枚 chips 自然高 ≈420px（两行 FlowRow + 提示语）
                    //   ⇒ 必然落出视口下沿，用户点完「标记错因（可选）」**看起来毫无反应**
                    //   （实测连点 6 次每次其实都 toggle 成功，只因 chips 在视口外而误判为失效）。
                    //   正解 = 展开后把页面滚到底，让 chips 进入视口。
                    LaunchedEffect(causeExpanded) {
                        if (causeExpanded) {
                            // AnimatedVisibility 默认 spring 展开约 300–400ms，期间 maxValue 持续增大，
                            // 单次 animateScrollTo 只会滚到「动画中途」的较小值 ⇒ 跟随重定位数次直到稳定。
                            repeat(5) {
                                delay(90)
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    }
                    Card(
                        // 记录卡顶在滚动内容中的偏移，供提交后「滚到判定行」使用
                        Modifier.fillMaxWidth().onGloballyPositioned { feedbackOffset = it.positionInParent().y.toInt() },
                        colors = CardDefaults.cardColors(containerColor = if (ok) AppColors.greenBg else AppColors.redBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // ① 判定行
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    appPainter(if (ok) "check" else "close"),
                                    contentDescription = null,
                                    Modifier.size(20.dp),
                                    tint = okFg
                                )
                                Text(
                                    if (ok) "答对了" else "答错了",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = okFg,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // ② 解析块（主内容；整页可滚后不被裁剪）
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.blueBg).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(appPainter("bulb"), contentDescription = null, Modifier.size(15.dp), tint = AppColors.blue)
                                    Text("解析", style = MaterialTheme.typography.labelMedium, color = AppColors.blue, fontWeight = FontWeight.SemiBold)
                                }
                                Text(q.analysis ?: "暂无解析", style = MaterialTheme.typography.bodyMedium, color = AppColors.textPrimary)
                            }
                            // ③ 错因（可选，折叠；置于解析之后 ⇒ 默认高度不含 chips，占比稳压解析）
                            if (!ok) {
                                val causeText = when {
                                    st.causeSelected.isEmpty() -> "标记错因（可选）"
                                    else -> "已标记：${st.causeSelected.joinToString("、")}"
                                }
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .clickable { causeExpanded = !causeExpanded }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        appPainter(if (st.causeSelected.isEmpty()) "plus" else "flag"),
                                        contentDescription = null,
                                        Modifier.size(15.dp),
                                        tint = AppColors.textSecondary
                                    )
                                    Text(
                                        causeText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppColors.textSecondary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        appPainter("chevron"),
                                        contentDescription = null,
                                        Modifier.size(16.dp).rotate(if (causeExpanded) 90f else 0f),
                                        tint = AppColors.textSecondary
                                    )
                                }
                                AnimatedVisibility(visible = causeExpanded) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(top = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FlowRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                                            CAUSE_OPTIONS.forEach { cause ->
                                                FilterChip(
                                                    selected = cause in st.causeSelected,
                                                    onClick = { vm.toggleCause(cause) },
                                                    label = { Text(cause) }
                                                )
                                            }
                                        }
                                        Text(
                                            "凭当时的直觉选，不选也不影响继续",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        // 🔴 2026-09-24 已删除原「底部工具条（更多 ··· / 收藏 / 草稿）」整条 Row（见顶栏注释）。
        //    该条 48dp 控件 + 上一区块 14dp 间距 = 62dp 常驻占用，而三键只承载两个动作：
        //    收藏 ≡ ⋯菜单「收藏本题」、草稿 ≡ ⋯菜单「存到收集箱」。
        //    处置：收藏上提顶栏；「存到收集箱」下沉答题卡面板；菜单整体废止。

        // 🔴 2026-09-23（04 号 E7 / SP-BOTTOM-ACTION）：底部行动栏 48dp/r14 → **56dp/r20**
        // 🔴🔴 2026-09-24 去掉「避让底栏」的 76dp 死空间：
        //    AppNav 自 2026-09-19 起在答题中【整体隐藏】GlassNavBar（navHidden ⇒ 下移 160dp + alpha 0），
        //    这里再留 bottom = 76dp 去避让一个已经不在屏上的底栏 ⇒ 变成会话内部死空间。
        //    真机实测代价（MuMu 192.168.0.130:5555）：动作栏下沿 2192 / 屏高 2560，白占约 76dp，
        //    把 AnimatedContent(weight 1f) 的内容视口压到仅 480→1696 = 304dp ⇒
        //    4 选 1 长题干时选项 C/D 被挤出视口（用户以为只有两个选项）、
        //    主观题「对答案」按钮只剩 19px 露头。去掉后内容区实增 76dp(≈304px)，上述全部回到视口内。
        Row(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 上一题
            // 🔴 2026-09-24 修「上一题」被挤成两行、「题」字被切（真机截图重现）：
            //    M3 Button 默认 contentPadding = 水平 24dp×2 = 48dp，380px(95dp) 宽的按钮被吃掉一半，
            //    再减去 22dp 返回图标后文字只剩 ~21dp ⇒ 3 个字放不下被迫折行（TextView 实测 h=160=40dp=两行）。
            //    两处收口：① contentPadding 收到 8dp ② 图标 22→20dp；再以 maxLines=1 + softWrap=false 兜底硬保证单行。
            OutlinedButton(
                onClick = { if (st.index > 0) vm.goto(st.index - 1) },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = st.index > 0,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(appPainter("back"), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("上一题", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
            // 提交按钮
            val canSubmit = if (q.isSubjective) (st.showAnswer && st.subjectiveResult != null) else st.selected >= 0
            Button(
                onClick = { if (!st.answered) { vm.submit(); Haptic.tick(ctx) } },
                modifier = Modifier.weight(1.2f).height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                enabled = !st.answered && canSubmit
            ) { Text(if (st.answered) "已提交" else "提交", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, softWrap = false) }
            // 下一题
            // 🔴 2026-09-24：错因改为「可选」⇒ 解除原「答错必须选 ≥1 项错因才能下一题」的硬门禁。
            //    答对、或已作答（无论是否标错因）均可继续；错因标记改由反馈卡内折叠入口自由补标。
            val canNext = st.answered
            OutlinedButton(
                onClick = { if (canNext) vm.next() },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = canNext,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(if (isLast) "查看结果" else "下一题", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(4.dp))
                Icon(appPainter("chevron"), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }

    // 答题卡：题号宫格，按作答态着色，点击跳题；当前题描边高亮 + 图例
    if (showCard) {
        ModalBottomSheet(onDismissRequest = { showCard = false }, sheetState = rememberModalBottomSheetState()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("答题卡", style = MaterialTheme.typography.titleLarge)
                    Text("${st.results.size}/${st.total} 已答", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                LazyVerticalGrid(columns = GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(st.total) { i ->
                        val itemQ = st.questions[i]
                        val r = st.results[itemQ.id]
                        val isCurrent = i == st.index
                        val (bg, fg) = when {
                            r?.correct == true -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            r != null -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
                        }
                        val statusColor = when {
                            r?.correct == true -> MaterialTheme.colorScheme.primary
                            r != null -> MaterialTheme.colorScheme.error
                            else -> null
                        }
                        val border = when {
                            isCurrent -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
                            statusColor != null -> BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                            else -> null
                        }
                        Card(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clickable { vm.goto(i); showCard = false },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            border = border
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = fg)
                                    if (isCurrent) Text("当前", style = MaterialTheme.typography.labelSmall, color = AppColors.blue, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                // 图例
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnswerCardLegend(MaterialTheme.colorScheme.primaryContainer, "答对")
                    AnswerCardLegend(MaterialTheme.colorScheme.errorContainer, "答错")
                    AnswerCardLegend(MaterialTheme.colorScheme.surfaceVariant, "未答")
                }
                // 🔴 2026-09-24：原底部工具条低频动作「存到收集箱」下沉至此（答题卡面板）。
                //    理由：不占常驻屏位，且「看答题卡」与「收题待复习」发生在同一心智动作内（复盘时顺手收题）。
                OutlinedButton(
                    onClick = { vm.saveToInbox(q, st.draft); showCard = false },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(appPainter("note"), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("把本题存到收集箱", fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
internal fun OptionRow(
    letter: String,
    text: String,
    selected: Boolean,
    answered: Boolean,
    correct: Boolean,
    wrongSelected: Boolean,
    onClick: () -> Unit
) {
    // 选项字母配色：四色收敛为语义色（蓝/绿/红/灰），随明暗主题自动切换；
    // 原先用 Tailwind 原色（#3B82F6/#10B981/#EF4444/#6B7280）+ 固定浅底，在暗色主题下会留下死白底
    val letterPairs = mapOf(
        "A" to (AppColors.blue to AppColors.blueLight),
        "B" to (AppColors.success to AppColors.greenBg),
        "C" to (AppColors.danger to AppColors.redBg),
        "D" to (AppColors.textSecondary to AppColors.trackGray)
    )
    val (letterColor, letterBg) = letterPairs[letter] ?: (AppColors.blue to AppColors.blueLight)
    val correctContainer = AppColors.greenBg
    val correctOn = AppColors.success
    val correctBorder = AppColors.success
    val bg = when {
        answered && correct -> correctContainer
        answered && wrongSelected -> MaterialTheme.colorScheme.errorContainer
        selected -> letterBg
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        answered && correct -> correctOn
        answered && wrongSelected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        answered && correct -> correctBorder
        answered && wrongSelected -> MaterialTheme.colorScheme.error
        selected -> letterColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val mark = when {
        answered && correct -> "✓"
        answered && wrongSelected -> "✗"
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = !answered) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            // 🔴 2026-09-25 修正 B2：选项前导字母圆位按规定 `list_leading = 24dp`（02 号 atoms.size），
            //   原 28dp 比明文大 4dp。视觉上圆底与 15sp 字母仍居中自适应，不影响命中区（整行可点）。
            modifier = Modifier.size(24.dp).clip(CircleShape).background(if (mark != null) Color.Transparent else letterBg),
            contentAlignment = Alignment.Center
        ) {
            if (mark != null) {
                Text(mark, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold)
            } else {
                Text(letter, style = MaterialTheme.typography.labelLarge, color = letterColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f), fontSize = 15.sp)
    }
}

/** 专注计时：独立状态，每秒只重绘本 Composable，不触发整页重组 */
@Composable
internal fun PracticeTimer() {
    var elapsedSec by remember { mutableStateOf(StudyTimerService.elapsedSeconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSec = StudyTimerService.elapsedSeconds()
        }
    }
    // 🔴 2026-09-24：原 `%02d:%02d`(分:秒) 在超 100 分钟时失真——真机截图出现「专注 346:06」
    //    （实为跨夜累计 5 小时 46 分），肉眼读起来像坏了。≥1 小时改为 H:MM:SS。
    //    ⚠️ 计时语义（StudyTimerService 前台服务，离开 App / 跨夜持续累计）为既定设计，本次只修显示格式。
    val h = elapsedSec / 3600
    val mm = (elapsedSec % 3600) / 60
    val ss = elapsedSec % 60
    val clock = if (h > 0) "%d:%02d:%02d".format(h, mm, ss) else "%02d:%02d".format(mm, ss)
    // 🔴 2026-09-24 二次精简：「专注 12:34」→ 时钟图标 +「12:34」，去汉字（图标语义自明）。
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(appPainter("clock"), contentDescription = "专注时长", tint = AppColors.blue, modifier = Modifier.size(13.dp))
        Text(clock, style = MaterialTheme.typography.labelMedium, color = AppColors.blue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 模考倒计时：独立状态，每秒重绘本 Composable；时间到回调交卷 */
@Composable
internal fun MockCountdown(timeLimitSec: Int, onTimeout: () -> Unit) {
    var remainSec by remember(timeLimitSec) { mutableStateOf(timeLimitSec) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remainSec = (remainSec - 1).coerceAtLeast(0)
            // 最后 10 秒每读秒连续轻微震动（紧迫感）；30 秒时单下提醒半程
            if (remainSec in 1..10) Haptic.tick(ctx)
            else if (remainSec == 30) Haptic.tick(ctx)
            if (remainSec == 0) { onTimeout(); break }
        }
    }
    val mm = remainSec / 60
    val ss = remainSec % 60
    val urgent = remainSec <= 60
    val ratio = if (timeLimitSec > 0) remainSec.toFloat() / timeLimitSec.toFloat() else 0f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 🔴 2026-09-24 二次精简：「⏱ 剩余 12:34」→ 时钟图标 +「12:34」。
        //    ① 去 emoji `⏱`（项目红线：UI 禁用 emoji）；② 去「剩余」汉字（红色即告警语义自明）。
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(appPainter("clock"), contentDescription = "剩余时间",
                tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp))
            Text("%02d:%02d".format(mm, ss),
                style = MaterialTheme.typography.labelMedium,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (remainSec <= 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/** 结算页错题回顾条目 */
internal data class WrongView(
    val q: com.jiaozi.sz.data.model.Question,
    val cause: String,
    val draft: String?,
    val selected: Int
)
