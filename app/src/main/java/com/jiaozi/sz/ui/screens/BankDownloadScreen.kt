@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankPack
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.remote.BankRemote
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SectionTitleDot
import kotlinx.coroutines.launch

/**
 * 首启题库下载引导页（2026-09-28 题库外置 · **学科 × 学段 双层拆分**）：
 * - 首次启动（bank_init_done=false）由 AppRoot 强制跳转至此；
 * - 🔴 **先选报考学段**：学段决定「哪些学科可选」与「下载哪一版题目」——
 *   初中独有科（科学/历史与社会）仅在初中出现，高中独有科（思想政治/通用技术）仅在高中出现；
 *   13 个初高中同名分卷科各按学段下载 `_junior` / `_senior` 版本（只下载本学段数据）；
 * - 用户勾选所需科目包（默认全选），联网从 GitHub(raw) 拉取到本地；
 * - **按《考试大纲》官方科目分组展示**：科目一《综合素质》/ 科目二《教育知识与能力》/
 *   科目三《学科知识与教学能力》（其下子科目**独立成包**，互不混装）；
 * - 下载完成后记录「已下载学段」，合并进内存题库并标记已完成，进入主界面；失败可重试，也可跳过先空题库。
 */
@Composable
fun BankDownloadScreen(nav: NavHostController) {
    val appVm = LocalAppVm.current
    val repo = appVm.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(appVm.examStage.value) }
    val groups = remember(stage) { BankStore.availablePacksByGroup(stage.takeIf { it.isNotEmpty() }) }
    val packs = remember(stage) { BankStore.availablePacks(stage.takeIf { it.isNotEmpty() }) }

    var selected by remember(stage) { mutableStateOf(packs.map { it.code }.toSet()) }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // code -> idle/doing/done/fail
    var downloading by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    // 初始：已下载过的科目包标记为 done
    remember(stage) {
        status = packs.associate { p -> p.code to if (BankStore.isDownloaded(ctx, p.code)) "done" else "idle" }
        Unit
    }

    fun toggle(code: String) {
        if (downloading) return
        selected = if (code in selected) selected - code else selected + code
    }

    fun startDownload() {
        if (downloading) return
        downloading = true
        errMsg = null
        val chosenStage = stage.takeIf { it.isNotEmpty() }
        scope.launch {
            // 清理旧版单文件科三包（banks/ke3.json），避免与子科目包重复
            BankStore.pruneLegacy(ctx)
            // 先清掉本学段不可用的本地包（如初中独有的科学/历史与社会），避免残留在题库里
            BankStore.pruneUnavailable(ctx, chosenStage)
            val failed = mutableListOf<String>()
            for (pack in packs) {
                if (pack.code !in selected) continue
                status = status + (pack.code to "doing")
                val qs = BankRemote.fetchForStage(pack.code, chosenStage)
                if (qs == null) {
                    status = status + (pack.code to "fail")
                    failed.add(pack.code)
                } else {
                    BankStore.persistQuestions(ctx, pack.code, qs)
                    status = status + (pack.code to "done")
                }
            }
            if (failed.isEmpty()) {
                appVm.setExamStage(stage)
                if (chosenStage != null) appVm.setBankDownloadStage(chosenStage)
                repo.reloadBank(BankStore.loadLocal(ctx))
                appVm.setBankInitDone(true)
                nav.navigate("today") { popUpTo("bankdownload") { inclusive = true } }
            } else {
                errMsg = "以下科目包下载失败：${
                    failed.joinToString { BankStore.byCode(it)?.let { p -> BankStore.displayName(p) } ?: it }
                }，请检查网络后重试，或先跳过使用空题库。"
            }
            downloading = false
        }
    }

    fun skip() {
        // 跳过也要记住学段选择，否则后续练习无法按学段过滤
        if (stage.isNotEmpty()) appVm.setExamStage(stage)
        appVm.setBankInitDone(true)
        nav.navigate("today") { popUpTo("bankdownload") { inclusive = true } }
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        HeroHeader(
            title = "下载题库",
            subtitle = "先选报考学段，只下载本学段题目",
            icon = appPainter("download"),
            immersive = true,
            action = {}
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 报考学段（决定可选学科与下载版本）──
            SectionTitleDot("报考学段")
            Text(
                "选择你报考的学段：初高中同名学科只会下载本学段题目；独有学科仅在本学段出现。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BankStore.STAGE_OPTIONS.forEach { opt ->
                    val sel = stage == opt
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) AppColors.blue else MaterialTheme.colorScheme.surface)
                            .then(if (downloading) Modifier else Modifier.clickable { stage = opt })
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt,
                            color = if (sel) Color.White else AppColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (stage.isEmpty()) {
                Text(
                    "尚未选择学段。未设置时下载全部题目（含初高中两版），建议先选定学段以节省流量。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.warning
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "选择需要下载的科目包（可多选，建议全选以获得完整题库）：",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.textSecondary
            )

            groups.forEach { (group, groupPacks) ->
                SectionTitleDot(group)
                groupPacks.forEach { pack ->
                    PackRow(
                        pack = pack,
                        selected = pack.code in selected,
                        status = status[pack.code] ?: "idle",
                        downloading = downloading,
                        showStage = stage.isNotEmpty() && BankStore.packMode(pack.code) == "per-question",
                        stage = stage,
                        onToggle = { toggle(pack.code) }
                    )
                }
            }

            if (errMsg != null) {
                Text(errMsg ?: "", style = MaterialTheme.typography.bodyMedium, color = AppColors.danger)
            }
        }

        // 底部 CTA
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { startDownload() },
                enabled = !downloading && selected.isNotEmpty() && stage.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(appPainter("download"), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (downloading) "下载中…" else if (stage.isEmpty()) "请先选择报考学段" else "开始下载所选科目",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                )
            }
            if (!downloading) {
                Box(
                    Modifier.fillMaxWidth().clickable { skip() }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("跳过，先使用空题库", style = MaterialTheme.typography.bodyMedium, color = AppColors.textSecondary)
                }
            }
        }
    }
}

/**
 * 单个科目包勾选行。科三子科目在 [BankPack.group] 分组下以官方学科名 [BankPack.name] 展示，
 * 不拼接分组前缀（分组已由 SectionTitleDot 给出），避免「学科知识与教学能力 · 美术」重复冗长。
 * 同名分卷科（[showStage]）右侧附「初中/高中」小标，明确本行下载的是哪一版。
 */
@Composable
private fun PackRow(
    pack: BankPack,
    selected: Boolean,
    status: String,
    downloading: Boolean,
    showStage: Boolean = false,
    stage: String = "",
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) AppColors.blue else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .background(if (selected) AppColors.blueBg else MaterialTheme.colorScheme.surface)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = !downloading,
            colors = CheckboxDefaults.colors(checkedColor = AppColors.blue)
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(pack.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (showStage && stage.isNotEmpty()) {
                    Text(
                        stage,
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
                when (status) {
                    "doing" -> "下载中…"
                    "done" -> "已下载"
                    "fail" -> "下载失败，可重试"
                    else -> "未下载"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (status == "fail") AppColors.danger else AppColors.textSecondary
            )
        }
        if (status == "doing") {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.blue)
        } else {
            Box(Modifier.size(20.dp))
        }
    }
}