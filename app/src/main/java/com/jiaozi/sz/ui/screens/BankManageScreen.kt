@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankPack
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.local.UserQuestionEntity
import com.jiaozi.sz.data.remote.BankRemote
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.CardTokens
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.HeroHeader
import kotlinx.coroutines.launch

/**
 * 题库管理页（2026-09-28 题库外置 · **学科 × 学段 双层拆分** + 功能三；晚 **紧凑批量化改版**）：
 * - 🔴 **学段感知**：页内可切换报考学段；列表随学段变化（初中显示科学/历史与社会，
 *   高中显示思想政治/通用技术），下载时自动取本学段版本（`_junior` / `_senior`）；
 *   学段与「已下载题库学段」不一致时，顶部提示「题库需更新」并可一键按新学段重下；
 * - 科目包管理：**按《考试大纲》官方科目分组**展示（《综合素质》/《教育知识与能力》/
 *   《学科知识与教学能力》），科目三下 17 个**子科目独立成包**，逐项下载 / 重新下载 / 移除；
 * - **效率改造**：① 每个科目包压成**单行紧凑条**（行高约 56dp）；② 顶部「已下载 n/N + 一键补齐缺失」；
 *   ③ 每组组头带「x/y」进度与「补下载本组」，科三 17 项无需逐点点开；
 * - 自加题库：用户手动录入题目（增）或删除（移），落 user_question 表，随练习/同步互通；
 *   科目三录入时须选定**具体子科目**，`disc` 存该子科目官方名（不再误存「科三」本身）。
 */
@Composable
fun BankManageScreen(nav: NavHostController) {
    val appVm = LocalAppVm.current
    val repo = appVm.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(appVm.examStage.value) }
    val chosenStage = stage.takeIf { it.isNotEmpty() }
    val groups = remember(stage) { BankStore.availablePacksByGroup(chosenStage) }
    val packs = remember(stage) { BankStore.availablePacks(chosenStage) }
    val total = packs.size

    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // code -> idle/doing/done/fail
    var userQs by remember { mutableStateOf<List<UserQuestionEntity>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var batchBusy by remember { mutableStateOf(false) }
    // 学段切换后重新计算「需更新」判定
    var outdated by remember(stage) { mutableStateOf(appVm.bankStageOutdated()) }
    // ── 未选学段时的下载引导（2026-09-28 修复「未选学段 ⇒ 学科题库静默下载失败」）──
    // 根因：`BankStore.remoteCode(code, null)` 对同名分卷科回退到**不存在的全量名**
    //       （远端只有 `ke3_<x>_junior.json` / `ke3_<x>_senior.json`，并无 `ke3_<x>.json`）
    //       ⇒ HTTP 404 ⇒ 该批下载全部失败，且界面只显示「还差 N 个」看不出原因。
    // 处置：凡含「同名分卷科」的下载，未选学段时先弹学段选择框，**选完自动续跑**，不再静默失败。
    var askStage by remember { mutableStateOf(false) }
    var pendingCodes by remember { mutableStateOf<List<String>?>(null) }
    var pendingForce by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) { userQs = repo.allUserQuestions() }

    val doneCount = packs.count { BankStore.isDownloaded(ctx, it.code) }

    /** 该包是否「必须先确定学段」才能定位远端文件（同名分卷科：初中/高中各一版） */
    fun needsStage(code: String): Boolean = BankStore.packMode(code) == "per-question"

    /** 实际执行下载（学段已定）：逐包拉取，结束时统一重载一次题库 */
    fun runDownload(codes: List<String>, force: Boolean, forStage: String?) {
        if (codes.isEmpty() || batchBusy) return
        batchBusy = true
        scope.launch {
            for (code in codes) {
                status = status + (code to "doing")
                val qs = BankRemote.fetchForStage(code, forStage)
                status = status + (code to if (qs != null) {
                    BankStore.persistQuestions(ctx, code, qs); "done"
                } else "fail")
            }
            repo.reloadBank(BankStore.loadLocal(ctx))
            if (forStage != null) appVm.setBankDownloadStage(forStage)
            outdated = appVm.bankStageOutdated()
            batchBusy = false
        }
    }

    /**
     * 下载统一入口（单包 / 批量共用）：
     * ① 先剔除已下载项（`force = false` 时）；
     * ② 若目标含**同名分卷科**而当前**未选报考学段** ⇒ 弹学段选择框并挂起本次目标，
     *    选完由 [resolveStage] 自动续跑（用户不必再点一次）。
     */
    fun requestDownload(codes: List<String>, force: Boolean) {
        if (codes.isEmpty() || batchBusy) return
        val missing = if (force) codes else codes.filter { !BankStore.isDownloaded(ctx, it) }
        if (missing.isEmpty()) return
        if (chosenStage == null && missing.any { needsStage(it) }) {
            pendingCodes = missing
            pendingForce = force
            askStage = true
            return
        }
        runDownload(missing, force, chosenStage)
    }

    /** 单包下载；force=true 时忽略「已下载」直接重拉 */
    fun downloadOne(code: String, force: Boolean) = requestDownload(listOf(code), force)

    /** 批量补齐：只拉尚未下载的包（已下载的跳过），完成后统一重载一次题库 */
    fun downloadMissing(targets: List<BankPack>) = requestDownload(targets.map { it.code }, force = false)

    /**
     * 按当前学段**全部重下**（学段切换后的「一键更新题库」）：
     * 先删掉本学段不可用的本地包（如切到高中后清掉初中独有的科学/历史与社会），
     * 再按本学段逐包重拉。
     */
    fun refreshAllForStage() {
        if (batchBusy) return
        batchBusy = true
        scope.launch {
            BankStore.pruneUnavailable(ctx, chosenStage)
            for (pack in packs) {
                status = status + (pack.code to "doing")
                val qs = BankRemote.fetchForStage(pack.code, chosenStage)
                status = status + (pack.code to if (qs != null) {
                    BankStore.persistQuestions(ctx, pack.code, qs); "done"
                } else "fail")
            }
            if (chosenStage != null) {
                appVm.setExamStage(chosenStage)
                appVm.setBankDownloadStage(chosenStage)
            }
            repo.reloadBank(BankStore.loadLocal(ctx))
            outdated = appVm.bankStageOutdated()
            batchBusy = false
        }
    }

    fun switchStage(newStage: String) {
        if (batchBusy || newStage == stage) return
        stage = newStage
        appVm.setExamStage(newStage)
    }

    /**
     * 学段选择框确认：写回学段后，**自动续跑**此前被 [requestDownload] 挂起的下载批次。
     * 语言刻意平实（面向备考用户），不出现「同名分卷科」等实现术语。
     */
    fun resolveStage(s: String) {
        askStage = false
        val codes = pendingCodes ?: emptyList()
        val force = pendingForce
        pendingCodes = null
        pendingForce = false
        switchStage(s)
        if (codes.isNotEmpty()) runDownload(codes, force, s)
    }

    fun removePack(code: String) {
        BankStore.deleteLocal(ctx, code)
        scope.launch { repo.reloadBank(BankStore.loadLocal(ctx)) }
        status = status - code
    }

    // 未选学段 ⇒ 学科题库无法定位远端版本：先引导选择，选完自动续下（不再静默失败）
    if (askStage) {
        AlertDialog(
            onDismissRequest = { askStage = false; pendingCodes = null; pendingForce = false },
            title = { Text("先选择报考学段") },
            text = {
                Text(
                    "《学科知识与教学能力》各科目分「初中 / 高中」两个版本，需先确定报考学段才能下载。"
                        + "选择后会自动开始下载。"
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { resolveStage(BankStore.STAGE_JUNIOR) }) { Text("初中") }
                    TextButton(onClick = { resolveStage(BankStore.STAGE_SENIOR) }) { Text("高中") }
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        HeroHeader(
            title = "题库管理",
            subtitle = "按学段下载 / 移除科目包，增删本地自加题",
            decorIcon = appPainter("book"),
            immersive = true,
            // 🔴 2026-09-28：补左端内联返回键（此前仅有右上角关闭键，无返回入口；
            // 关闭键与返回键同为 popBackStack，为避免「双出口」重复，一并移除关闭键）
            onBack = { nav.popBackStack() }
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 学段选择 ──
            item(key = "stage") {
                StagePicker(
                    stage = stage,
                    busy = batchBusy,
                    onSelect = { switchStage(it) }
                )
            }

            // ── 学段变更后：题库需更新提示 ──
            if (outdated) {
                item(key = "outdated") {
                    UpdateNotice(busy = batchBusy, onUpdate = { refreshAllForStage() })
                }
            }

            // ── 顶栏概览：全局进度 + 一键补齐 ──
            item(key = "overview") {
                OverviewCard(
                    done = doneCount,
                    total = total,
                    busy = batchBusy,
                    stageChosen = chosenStage != null,
                    onFillMissing = { downloadMissing(packs) }
                )
            }

            // ── 科目包管理（按官方科目分组）──
            groups.forEach { (group, groupPacks) ->
                item(key = "head_$group") {
                    GroupHeader(
                        group = group,
                        done = groupPacks.count { BankStore.isDownloaded(ctx, it.code) },
                        total = groupPacks.size,
                        busy = batchBusy,
                        onDownloadMissing = { downloadMissing(groupPacks) }
                    )
                }
                items(groupPacks, key = { it.code }) { pack ->
                    PackRow(
                        pack = pack,
                        downloaded = BankStore.isDownloaded(ctx, pack.code),
                        status = status[pack.code],
                        busy = batchBusy,
                        stageTag = if (BankStore.packMode(pack.code) == "per-question") stage else "",
                        onDownload = { force -> downloadOne(pack.code, force) },
                        onRemove = { removePack(pack.code) }
                    )
                }
            }

            // ── 自加题目管理 ──
            item(key = "userq_head") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Text(
                        "自加题目",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${userQs.size} 题",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary
                    )
                    Spacer(Modifier.weight(1f))
                    TinyPill(
                        text = if (showAdd) "收起" else "添加题目",
                        primary = true,
                        onClick = { showAdd = !showAdd }
                    )
                }
            }

            if (showAdd) {
                item(key = "userq_form") {
                    AddQuestionForm(
                        stage = chosenStage,
                        onSubmit = { entity ->
                            scope.launch {
                                repo.upsertUserQuestion(entity)
                                refreshKey++
                                showAdd = false
                            }
                        }
                    )
                }
            }

            if (userQs.isEmpty()) {
                item(key = "userq_empty") {
                    Text(
                        "还没有自加题目。点上方「添加题目」手动录入，或下载科目包获得内置题。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textSecondary
                    )
                }
            } else {
                items(userQs, key = { it.id }) { uq ->
                    UserQuestionRow(
                        uq = uq,
                        onDelete = { scope.launch { repo.deleteUserQuestion(uq.id); refreshKey++ } }
                    )
                }
            }
        }
    }
}

/**
 * 学段选择器（管理页顶部）：左右两枚等宽 chip。
 * 切换即写回 [MetaKeys.EXAM_STAGE]，列表随之过滤（独有学科出现/消失）。
 *
 * 🔴 2026-09-28：`stage` 为空（从未选择）时整卡转**警示态**并加「未选择」小标 —— 此前无任何
 *   未选提示，用户看不出「为什么学科题库点下载没反应」，是本轮真机缺陷的直接成因之一。
 */
@Composable
private fun StagePicker(stage: String, busy: Boolean, onSelect: (String) -> Unit) {
    val unset = stage.isEmpty()
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (unset) AppColors.warningBg else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("报考学段", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (unset) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.warning.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "未选择",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BankStore.STAGE_OPTIONS.forEach { opt ->
                    val sel = stage == opt
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) AppColors.blue else MaterialTheme.colorScheme.surfaceVariant)
                            .then(if (busy) Modifier else Modifier.clickable { onSelect(opt) })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt,
                            color = if (sel) Color.White else AppColors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Text(
                if (unset) "还没选报考学段：学科题库分「初中 / 高中」两个版本，需先选定才能下载对应题目。"
                else "切换学段后，初高中同名学科会显示对应版本；独有学科随学段出现或隐藏。",
                style = MaterialTheme.typography.labelSmall,
                color = if (unset) AppColors.warning else AppColors.textSecondary
            )
        }
    }
}

/**
 * 「题库需更新」提示条：报考学段与本地已下载题库学段不一致时出现，
 * 一键按新学段重下（会清掉本学段不可用的旧包）。
 */
@Composable
private fun UpdateNotice(busy: Boolean, onUpdate: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.warningBg),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "题库需更新",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "当前报考学段与已下载题库不一致，按新学段重新下载后练习才会出对应题目。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.blue)
            } else {
                TinyPill("一键更新", primary = true, onClick = onUpdate)
            }
        }
    }
}

/** 各类题目的展示名（官方口径）：科三附子科目 */
private fun subjectLabel(subject: String, disc: String?): String =
    if (subject == "科三" && !disc.isNullOrBlank()) "${BankStore.GROUP_3} · $disc"
    else BankStore.officialName(subject)

/**
 * 小号胶囊按钮：紧凑排布用（组头「补下载本组」/ 行内「下载」「移除」）。
 * [primary] 走企鹅蓝，否则为红色（危险动作，如「移除」）。
 */
@Composable
private fun TinyPill(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val fg = when {
        !enabled -> AppColors.textSecondary
        primary -> AppColors.blue
        else -> AppColors.danger
    }
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        primary -> AppColors.blueBg
        else -> AppColors.redBg
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * 顶部概览卡：全局「已下载 n/N」+ 一键补齐缺失。
 * 全部就绪时右侧动作位收起，仅留状态文案，避免出现无效按钮。
 * 🔴 未选报考学段时（[stageChosen] = false），副文案改为**明确指引**：
 *   学科题库分初高中两版，必须先定学段；点「一键补齐缺失」会先弹学段选择框。
 */
@Composable
private fun OverviewCard(
    done: Int,
    total: Int,
    busy: Boolean,
    stageChosen: Boolean,
    onFillMissing: () -> Unit
) {
    val allDone = done >= total
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "已下载 $done / $total 个科目包",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        busy -> "正在补齐中…"
                        allDone -> "题库已就绪，可直接开始练习"
                        !stageChosen -> "未选报考学段：学科题库分初高中两版，需先选学段"
                        else -> "还差 ${total - done} 个，可一键补齐"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary
                )
            }
            if (!allDone) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.blue)
                } else {
                    TinyPill("一键补齐缺失", primary = true, onClick = onFillMissing)
                }
            }
        }
    }
}

/**
 * 分组头：官方组名 + 「已下载 x/y」+ 右侧「补下载本组」（该组全部就绪时收起）。
 * `padding(top = 8.dp)` 用于在 12dp 卡片间距之上补足**区块间距 20dp**（项目间距双轨规范）。
 */
@Composable
private fun GroupHeader(
    group: String,
    done: Int,
    total: Int,
    busy: Boolean,
    onDownloadMissing: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(
            group,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary)
        Spacer(Modifier.weight(1f))
        if (done < total && !busy) {
            TinyPill("补下载本组", primary = true, onClick = onDownloadMissing)
        }
    }
}

/**
 * 单个科目包**紧凑条**（一行）：官方学科名 + 状态小字 + 右侧动作。
 * 科三子科目卡片标题为官方学科名 [BankPack.name]（分组名由 [GroupHeader] 给出）。
 * [stageTag] 非空时（同名分卷科）在科名后附「初中/高中」小标，明确本行是哪个学段版本。
 * 动作口径：未下载⇒「下载」；下载中⇒进度圈；失败⇒「重试」；已下载⇒「重新下载」+「移除」。
 */
@Composable
private fun PackRow(
    pack: BankPack,
    downloaded: Boolean,
    status: String?,
    busy: Boolean,
    onDownload: (force: Boolean) -> Unit,
    onRemove: () -> Unit,
    stageTag: String = ""
) {
    val st = status ?: if (downloaded) "done" else "idle"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pack.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stageTag.isNotEmpty()) {
                    Text(
                        stageTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.blue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.blueBg)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                when (st) {
                    "doing" -> "下载中…"
                    "done" -> "已下载"
                    "fail" -> "下载失败"
                    else -> "未下载"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (st == "fail") AppColors.danger else AppColors.textSecondary
            )
        }
        when (st) {
            "doing" -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.blue)
            "fail" -> TinyPill("重试", primary = true, enabled = !busy, onClick = { onDownload(true) })
            "done" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TinyPill("重新下载", primary = true, enabled = !busy, onClick = { onDownload(true) })
                TinyPill("移除", primary = false, enabled = !busy, onClick = onRemove)
            }
            else -> TinyPill("下载", primary = true, enabled = !busy, onClick = { onDownload(false) })
        }
    }
}

/** 自加题目**紧凑条**：题干单行省略 + 科目/章节小字 + 删除图标。 */
@Composable
private fun UserQuestionRow(uq: UserQuestionEntity, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                uq.q,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${subjectLabel(uq.subject, uq.disc)} · ${if (uq.chapter.isBlank()) "未分类" else uq.chapter}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDelete() }.padding(8.dp)
        ) {
            Icon(appPainter("trash"), contentDescription = "删除", tint = AppColors.danger, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 自加题目录入表单。
 * 科目以《考试大纲》官方名展示（三枚等宽 chip）；选「学科知识与教学能力」时须进一步选定**具体子科目**，
 * 保存时 `disc` 记该子科目官方名，与内置题库的 `disc` 判别键保持一致。
 * 🔴 子科目列表按 [stage] 过滤：初中不列「思想政治/通用技术」，高中不列「科学/历史与社会」。
 */
@Composable
private fun AddQuestionForm(stage: String?, onSubmit: (UserQuestionEntity) -> Unit) {
    // subject 数据键：科一 / 科二 / 科三；disc 仅科三填写（官方子科目名）
    val discPacks = remember(stage) {
        BankStore.DISC_PACKS.filter { BankStore.packAvailableIn(it.code, stage) }
    }
    var subject by remember { mutableStateOf("科一") }
    var disc by remember(stage) { mutableStateOf(discPacks.firstOrNull()?.name ?: BankStore.DISC_PACKS.first().name) }
    var chapter by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var opt by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf("") }

    val subjectOptions = listOf("科一", "科二", "科三")

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = CardTokens.Elevation)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 科目选择（官方名 · 三枚等宽 chip，取代原三行全宽块）
            Text("科目", style = MaterialTheme.typography.labelMedium, color = AppColors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subjectOptions.forEach { key ->
                    val sel = subject == key
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) AppColors.blue else MaterialTheme.colorScheme.surface)
                            .clickable { subject = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            BankStore.shortName(key),
                            color = if (sel) Color.White else AppColors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // 子科目选择（仅科三）：17 个子科目，官方名
            if (subject == "科三") {
                Text("子科目（${BankStore.GROUP_3}）", style = MaterialTheme.typography.labelMedium, color = AppColors.textSecondary)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discPacks.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { pack ->
                                val sel = disc == pack.name
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) AppColors.blue else MaterialTheme.colorScheme.surface)
                                        .clickable { disc = pack.name }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(pack.name, color = if (sel) Color.White else AppColors.textPrimary,
                                        fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                }
                            }
                            // 补齐末行空位，保持等宽
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            val tfColors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("题干 *") }, colors = tfColors, minLines = 2)
            OutlinedTextField(opt, { opt = it }, Modifier.fillMaxWidth(), label = { Text("选项（客观题，每选项一行，可空=主观题）") }, colors = tfColors, minLines = 2)
            OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), label = { Text("答案（客观题填 ABCD；主观题可空）") }, colors = tfColors)
            OutlinedTextField(chapter, { chapter = it }, Modifier.fillMaxWidth(), label = { Text("章节（可选，留空=未分类）") }, colors = tfColors)
            OutlinedTextField(analysis, { analysis = it }, Modifier.fillMaxWidth(), label = { Text("解析（可选）") }, colors = tfColors, minLines = 2)
            Button(
                onClick = {
                    if (q.isBlank()) return@Button
                    onSubmit(
                        UserQuestionEntity(
                            id = "UQ" + System.currentTimeMillis().toString(36),
                            subject = subject,
                            chapter = chapter.trim(),
                            q = q.trim(),
                            opt = opt.trim(),
                            answer = answer.trim(),
                            analysis = analysis.trim().ifBlank { null },
                            // 科三存具体子科目官方名；科一/科二无子科目
                            disc = if (subject == "科三") disc else null,
                            _mt = System.currentTimeMillis()
                        )
                    )
                },
                enabled = q.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                Text("保存到本地题库", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Text(
                if (opt.isBlank()) "当前为空选项 ⇒ 按主观题保存（提交时自评对错）。" else "当前有选项 ⇒ 按选择题保存。",
                style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary
            )
        }
    }
}