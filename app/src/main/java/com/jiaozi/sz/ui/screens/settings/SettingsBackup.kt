@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 本地备份（路由 `settings_backup`）。
 *
 * 🔴 2026-09-26 方案 B 重写。相比旧 `SettingsBackupSection` 的三处变化：
 * 1. **去卡套卡**：旧实现把整块内容塞进 `SettingsSection`（自带 Card），而主页又把它展开在
 *    `SettingsCard` 内 ⇒ 卡套卡。现改为二级页，卡由 [SettingsCard] 统一承载。
 * 2. **操作入口行化**：原来的裸 `OutlinedButton` 网格改为 [SettingRow]（图标 + 标题 + 行尾说明），
 *    与其它设置页同一形态；按钮只保留在弹窗确认里。
 * 3. **launcher 自持**：原本 3 个 ActivityResultLauncher 由主页创建后层层传参（签名 7 个参数），
 *    现页面内 `rememberLauncherForActivityResult` 自持，签名收敛为 `(nav)`。
 *
 * 兼容红线：`BackupManager` / `exportEnvelopeTo` / `importEnvelopeFrom` 调用与存储结构**一字未改**。
 */

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BackupManager
import com.jiaozi.sz.data.exportEnvelopeTo
import com.jiaozi.sz.data.importEnvelopeFrom
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingRow
import com.jiaozi.sz.util.PATTERN_DATE_TIME
import com.jiaozi.sz.util.formatTs
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsBackupScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshots by remember { mutableStateOf(BackupManager.listSnapshots(ctx)) }
    fun refreshSnapshots() { snapshots = BackupManager.listSnapshots(ctx) }

    var msg by remember { mutableStateOf<String?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDirImportFiles by remember { mutableStateOf<List<Pair<String, Uri>>?>(null) }
    var pendingDirImportFile by remember { mutableStateOf<Uri?>(null) }

    val hasStorageAccess = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                exportEnvelopeTo(ctx, appVm.repo, uri)
                msg = "已导出数据"
            } catch (e: Exception) { msg = "导出失败：${e.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
    }
    val importDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                // 🔴 必须 withContext：写成作用域函数 `with(Dispatchers.IO){…}` 只会把 dispatcher 当 receiver，
                //    **不会切换协程上下文**（编译能过、IO 仍跑在主线程）。
                val files = withContext(kotlinx.coroutines.Dispatchers.IO) { scanDirDocuments(ctx, uri) }
                if (files.isEmpty()) { msg = "所选目录下未找到 .json 备份文件"; return@launch }
                pendingDirImportFiles = files
            } catch (e: Exception) { msg = "扫描目录失败：${e.message}" }
        }
    }
    // 全盘访问权限跳转（MANAGE_EXTERNAL_STORAGE）：返回后由用户重新触发导入操作，此处仅刷新界面
    val storageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    SettingsDetailPage(title = "本地备份", subtitle = "导出、导入与本地快照", icon = "cloud", nav = nav) {
        GroupTitle("数据文件", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingRow(
                icon = "upload",
                title = "导出数据",
                value = "存成 JSON 文件",
                iconBg = AppColors.blue
            ) { exportLauncher.launch("jiaozi_backup_${System.currentTimeMillis() / 1000}.json") }

            SettingRow(
                icon = "import",
                title = "导入合并",
                value = "较新者胜出",
                iconBg = AppColors.success
            ) { importLauncher.launch(arrayOf("application/json", "text/plain")) }

            // 目录导入（Android 11+ 用 SAF 递归扫描备份目录）
            if (Build.VERSION.SDK_INT >= 30) {
                SettingRow(
                    icon = "grid",
                    title = "从目录导入",
                    value = "扫描整个文件夹",
                    iconBg = AppColors.teal,
                    // 末行抑制：无授权时下面还有「授权全盘访问」行，故本行仍画线
                    showDivider = !hasStorageAccess
                ) { importDirLauncher.launch(null) }

                if (!hasStorageAccess) {
                    SettingRow(
                        icon = "alert",
                        title = "授权全盘访问",
                        value = "目录检索需要",
                        iconBg = AppColors.warning,
                        showDivider = false
                    ) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:" + ctx.packageName)
                        }
                        storageAccessLauncher.launch(intent)
                    }
                }
            }
        }

        GroupTitle("本地快照", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingRow(
                icon = "clock",
                title = "立即快照",
                value = "滚动保留 7 份",
                iconBg = AppColors.purple,
                showDivider = snapshots.isNotEmpty()
            ) {
                scope.launch {
                    try {
                        val f = BackupManager.takeSnapshot(ctx, appVm.repo)
                        refreshSnapshots()
                        msg = "已创建快照：${f.name}"
                    } catch (e: Exception) { msg = "快照失败：${e.message}" }
                }
            }

            snapshots.forEachIndexed { i, s ->
                val timeStr = formatTs(s.timeMillis, PATTERN_DATE_TIME)
                val kb = if (s.sizeBytes >= 1024) "${s.sizeBytes / 1024} KB" else "${s.sizeBytes} B"
                SettingRow(
                    icon = "cloud",
                    title = timeStr,
                    value = kb,
                    iconBg = AppColors.textSecondary,
                    showDivider = i < snapshots.lastIndex,
                    trailing = { Text("还原", style = MaterialTheme.typography.labelMedium, color = AppColors.blue) }
                ) { pendingRestoreFile = s.file }
            }

            if (snapshots.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "暂无本地快照",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.textSecondary
                    )
                    Text(
                        "下次打开 App 会自动生成；快照可作误操作后的「后悔药」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.textSecondary
                    )
                }
            }
        }
    }

    // ── 以下弹窗与旧版一致（行为未变）──
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { msg = null },
            title = { Text("提示") },
            text = { Text(msg!!) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好") } }
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("确认导入合并") },
            text = { Text("所选备份将与本地数据按「时间较新者胜出」合并，不会删除本地独有数据（错题/进度/自定义题库/备课）。确认继续？") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri!!
                    pendingImportUri = null
                    scope.launch {
                        try {
                            val report = importEnvelopeFrom(ctx, appVm.repo, uri)
                            msg = "已合并 ${report.total} 条数据"
                        } catch (e: Exception) { msg = "导入失败：${e.message}" }
                    }
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } }
        )
    }

    if (pendingRestoreFile != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreFile = null },
            title = { Text("确认还原快照") },
            text = { Text("将用该快照合并回本地数据（时间较新者胜出，不会删除本地独有数据）。若快照中存在本地已丢失的条目，会被重新加回。确认继续？") },
            confirmButton = {
                TextButton(onClick = {
                    val f = pendingRestoreFile!!
                    pendingRestoreFile = null
                    scope.launch {
                        try {
                            val n = BackupManager.restoreSnapshot(appVm.repo, f).total
                            msg = "已从快照还原（合并 $n 条）"
                            refreshSnapshots()
                        } catch (e: Exception) { msg = "还原失败：${e.message}" }
                    }
                }) { Text("还原") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreFile = null }) { Text("取消") } }
        )
    }

    if (pendingDirImportFiles != null) {
        val fileList = pendingDirImportFiles!!
        AlertDialog(
            onDismissRequest = { pendingDirImportFiles = null },
            title = { Text("选择备份文件") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (fileList.isEmpty()) {
                        Text("未找到 .json 备份文件")
                    } else {
                        fileList.forEach { (name, uri) ->
                            TextButton(
                                onClick = { pendingDirImportFile = uri; pendingDirImportFiles = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingDirImportFiles = null }) { Text("取消") } }
        )
    }

    if (pendingDirImportFile != null) {
        val importUri = pendingDirImportFile!!
        AlertDialog(
            onDismissRequest = { pendingDirImportFile = null },
            title = { Text("确认导入合并") },
            text = { Text("将用所选备份与本地数据按「时间较新者胜出」合并，不会删除本地独有数据。确认继续？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDirImportFile = null
                    scope.launch {
                        try {
                            val report = importEnvelopeFrom(ctx, appVm.repo, importUri)
                            msg = "已合并 ${report.total} 条数据"
                        } catch (e: Exception) { msg = "导入失败：${e.message}" }
                    }
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingDirImportFile = null }) { Text("取消") } }
        )
    }
}
