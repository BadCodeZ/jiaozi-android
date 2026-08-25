package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.remote.SyncState
import com.jiaozi.sz.data.remote.WebDavConfig
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.components.InlineExpandSelect
import com.jiaozi.sz.ui.components.NavPref
import com.jiaozi.sz.ui.components.SettingsSection
import com.jiaozi.sz.ui.components.SwitchPref
import com.jiaozi.sz.xiaomi.FloatingIslandService
import com.jiaozi.sz.domain.AiProvider
import com.jiaozi.sz.domain.UpdateChecker
import com.jiaozi.sz.data.BackupManager
import java.io.File
import java.io.FileFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val theme by appVm.theme.collectAsStateWithLifecycle()
    val dynamicColor by appVm.dynamicColor.collectAsStateWithLifecycle()
    val fontScale by appVm.fontScale.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val discList = appVm.repo.discList
    val syncEnabled by appVm.syncEnabled.collectAsStateWithLifecycle()
    val syncState by appVm.syncState.collectAsStateWithLifecycle()
    val lastSyncAt by appVm.lastSyncAt.collectAsStateWithLifecycle()

    var aiKeyText by remember { mutableStateOf(appVm.aiKey.value) }
    var showAiDialog by remember { mutableStateOf(false) }
    val aiProvider by appVm.aiProvider.collectAsStateWithLifecycle()
    val aiModel by appVm.aiModel.collectAsStateWithLifecycle()
    var msg by remember { mutableStateOf<String?>(null) }

    // 本地备份：导入确认挂起的 Uri、还原确认挂起的文件、快照列表
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var snapshots by remember { mutableStateOf(BackupManager.listSnapshots(ctx)) }
    fun refreshSnapshots() { snapshots = BackupManager.listSnapshots(ctx) }

    // 全盘访问权限（MANAGE_EXTERNAL_STORAGE）：目录导入所需
    val hasStorageAccess = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    // 目录导入：从用户选定目录扫描 JSON 文件
    var pendingDirImportFiles by remember { mutableStateOf<List<Pair<String, android.net.Uri>>?>(null) }
    var pendingDirImportFile by remember { mutableStateOf<android.net.Uri?>(null) }

    // —— WebDAV 配置本地编辑态（初始从已保存配置载入一次）——
    var wdUrl by remember { mutableStateOf("") }
    var wdUser by remember { mutableStateOf("") }
    var wdPass by remember { mutableStateOf("") }
    var wdDir by remember { mutableStateOf("artwb-default") }
    var wdMode by remember { mutableStateOf("two-way") }
    var wdEncrypt by remember { mutableStateOf(false) }
    var wdSyncPass by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }
    var wdLoaded by remember { mutableStateOf(false) }

    if (!wdLoaded) {
        wdUrl = appVm.webDavUrl.value
        wdUser = appVm.webDavUser.value
        wdPass = appVm.webDavPass.value
        wdDir = appVm.webDavDir.value.ifBlank { "artwb-default" }
        wdMode = appVm.webDavMode.value.ifBlank { "two-way" }
        wdEncrypt = appVm.webDavEncrypt.value
        wdSyncPass = appVm.webDavSyncPass.value
        wdLoaded = true
    }

    // 导出：写 JSON 到用户选定文件
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = appVm.repo.exportEnvelope()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                msg = "已导出数据"
            } catch (e: Exception) { msg = "导出失败：${e.message}" }
        }
    }
    // 导入：读取用户选定文件并合并（先挂起待确认，避免误合并）
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
    }

    // 目录导入：用户选定目录后扫描所有 .json 备份文件
    val importDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) {
                    scanDirDocuments(ctx, uri)
                }
                if (files.isEmpty()) { msg = "所选目录下未找到 .json 备份文件"; return@launch }
                pendingDirImportFiles = files
            } catch (e: Exception) { msg = "扫描目录失败：${e.message}" }
        }
    }

    // 全盘访问权限跳转（MANAGE_EXTERNAL_STORAGE）
    val storageAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 从授权页返回后刷新状态（实际由用户重新触发导入操作时判断）
    }

    // 灵动岛：授予悬浮窗权限后启动前台服务（未授权则跳系统设置页引导）
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 从授权页返回后尝试启动：MIUI/澎湃 OS 上 Settings.canDrawOverlays 可能误报 false，
        // 故交给 FloatingIslandService 内部最终兜底（无权限则先 startForeground 再自停，不崩）。
        // 必须门控权限，否则无权限时启动前台服务会闪退。
        IslandBus.setError(null)
        if (Settings.canDrawOverlays(ctx) && IslandBus.state.value != null) {
            ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
        }
    }

    /**
     * 跳转悬浮窗授权页。
     * 小米/澎湃 OS 上标准 ACTION_MANAGE_OVERLAY_PERMISSION 跳的是「特殊应用权限」列表，
     * 该列表不收录第三方应用（用户反馈“找不到 app”）；故小米系直接跳本应用详情页，
     * 用户在「权限管理 → 显示悬浮窗」中开启即可，一定能定位到本应用。
     */
    fun openOverlaySettings(c: Context) {
        val isXiaomi = Build.BRAND.equals("xiaomi", ignoreCase = true)
            || Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)
            || Build.MODEL.contains("POCO", ignoreCase = true)
            || Build.MODEL.contains("Redmi", ignoreCase = true)
        val intent = if (isXiaomi) {
            // 小米/澎湃 OS：标准悬浮窗权限页不收录第三方应用，直接跳本应用详情页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))
        }
        // 部分 OEM（三星/ColorOS 等）的「悬浮窗」列表同样不收录第三方应用，
        // 若目标 Intent 无可解析的 Activity，则回退到本应用详情页，保证一定能定位到本应用权限入口。
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        val target = if (intent.resolveActivity(c.packageManager) != null) intent else fallback
        // 用 launcher 启动，授权/设置页返回后由回调自动尝试启动服务
        overlayLauncher.launch(target)
    }

    val isSyncing = syncState is SyncState.Syncing

    fun currentWebDavConfig() = WebDavConfig(
        url = wdUrl.trim(), user = wdUser.trim(), pass = wdPass,
        remoteDir = wdDir.trim().ifBlank { "artwb-default" }, direction = wdMode.ifBlank { "two-way" },
        encrypt = wdEncrypt, syncPass = wdSyncPass
    )

    fun handleIsland(v: Boolean) {
        appVm.setIslandEnabled(v)
        if (v) {
            // 开启后由业务屏（练习/首页/AI 帮手）按需自动拉起服务；
            // 若当前已处于某业务场景（已有 IslandState），则立即启动让胶囊直接出现。
            if (IslandBus.state.value != null) {
                if (Settings.canDrawOverlays(ctx)) {
                    IslandBus.setError(null)
                    ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
                } else {
                    openOverlaySettings(ctx)
                }
            }
        } else {
            ctx.stopService(Intent(ctx, FloatingIslandService::class.java))
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 76.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // —— 外观（复刻 Miuix 设置页：分组标题 + 偏好行）——
        val themePack by appVm.themePack.collectAsStateWithLifecycle()
        SettingsSection("外观") {
            InlineExpandSelect(
                title = "主题模式",
                options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                selected = theme,
                onSelect = { appVm.setTheme(it) }
            )
            InlineExpandSelect(
                title = "主题配色",
                summary = "默认墨绿（网页端同款）· 小米蓝/青/墨/锦/企鹅可选",
                options = listOf("默认" to "墨绿", "小米蓝" to "小米蓝", "青" to "青", "墨" to "墨", "锦" to "锦", "企鹅" to "企鹅"),
                selected = themePack,
                onSelect = { appVm.setThemePack(it) }
            )
            InlineExpandSelect(
                title = "字体大小",
                summary = "对齐网页端 setFont",
                options = listOf("sm" to "小", "md" to "标准", "lg" to "大", "xl" to "特大"),
                selected = fontScale,
                onSelect = { appVm.setFontScale(it) },
                showDivider = false
            )
            SwitchPref(
                title = "跟随系统壁纸取色",
                summary = "Android 12 以上生效；关闭则使用默认墨绿主题。优先级高于主题配色。",
                checked = dynamicColor,
                onCheckedChange = { appVm.setDynamicColor(it) },
                showDivider = false
            )
        }

        // 灵动岛（上岛）：全局悬浮胶囊 —— 学习会话期顶部常驻（仿小米 HyperOS 上岛），仅会话期间显示以省电
        val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()
        val islandErr by IslandBus.error.collectAsStateWithLifecycle()
        SettingsSection("灵动岛（上岛）") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("灵动岛（上岛）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "开启后，在「练习 / AI 帮手」等学习会话期间，进度会常驻显示在屏幕顶部胶囊（需授予「悬浮窗」权限），离开 App 也能看到实时状态。仅在学习的会话期间显示，平时不占用资源与电量。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
                SwitchPref(
                    title = "启用灵动岛",
                    summary = "学习会话期顶部胶囊（需悬浮窗权限）· 离开应用也能看实时进度",
                    checked = islandEnabled,
                    onCheckedChange = { handleIsland(it) },
                    showDivider = false
                )
                // 未授予悬浮窗权限时显示引导卡（小米/澎湃 OS 上标准入口找不到本应用，需手动在应用设置开启）
                if (islandEnabled && (!Settings.canDrawOverlays(ctx) || islandErr != null)) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "灵动岛无法显示",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val err = islandErr
                            if (err != null) {
                                Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Text(
                                "请按以下路径手动开启「悬浮窗」权限：\n设置 → 应用 → 综合教资备考平台 → 权限管理 → 显示悬浮窗\n开启后返回本页重新打开开关即可。",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = { openOverlaySettings(ctx) }, Modifier.fillMaxWidth().height(44.dp)) { Text("去授予悬浮窗权限") }
                        }
                    }
                }
            }
        }

        // 目标考试日（倒计时锚点）—— 对齐网页端：用原生 DatePicker，避免手敲格式错误被静默吞掉
        val todayMillis = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        var picked by remember {
            mutableStateOf(runCatching { java.time.LocalDate.parse(targetDay) }.getOrNull())
        }
        var showDatePicker by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = picked?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: todayMillis
        )
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    Button(onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            picked = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        // 目标考试日（倒计时锚点）—— 对齐网页端：用原生 DatePicker，避免手敲格式错误被静默吞掉
        SettingsSection("学习目标") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标考试日（倒计时锚点）", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDatePicker = true }, Modifier.weight(1f).height(44.dp)) {
                        Text(if (picked != null) "已选：${picked!!.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)}" else "选择日期")
                    }
                    if (picked != null) {
                        OutlinedButton(onClick = { picked = null }, Modifier.weight(1f).height(44.dp)) { Text("清除") }
                    }
                }
                Button(
                    onClick = {
                        if (picked != null) {
                            val iso = picked!!.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                            appVm.setTargetDay(iso)
                            android.widget.Toast.makeText(ctx, "已保存目标日：$iso", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            appVm.setTargetDay("")
                            android.widget.Toast.makeText(ctx, "已清除目标日", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(if (picked != null) "保存目标日" else "清除目标日")
                }
            }
        }

        // 科三学科（展开式滑动选择）
        SettingsSection("科三学科") {
            InlineExpandSelect(
                title = "当前科三学科",
                summary = "影响科三出题与模考 · 当前：$disc",
                options = discList.map { it to it },
                selected = disc,
                onSelect = { appVm.setSubject3Disc(it) },
                showDivider = false
            )
        }

        // AI 出题 / 讲评（需 API Key）
        SettingsSection("AI 出题 / 讲评") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 出题 / 讲评（需 API Key）", style = MaterialTheme.typography.titleMedium)
                // 服务商选择（deepseek / openai / moonshot）
                InlineExpandSelect(
                    title = "服务商",
                    summary = "默认模型：${AiProvider.get(aiProvider).defaultModel}",
                    options = AiProvider.options(),
                    selected = aiProvider,
                    onSelect = { appVm.setAiProvider(it) },
                    showDivider = false
                )
                OutlinedTextField(
                    value = aiKeyText,
                    onValueChange = { aiKeyText = it },
                    label = { Text("API Key") },
                    placeholder = { Text(AiProvider.get(aiProvider).hint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 模型（可选，覆盖该服务商默认模型）
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = { appVm.setAiModel(it) },
                    label = { Text("模型（可选，留空用默认）") },
                    placeholder = { Text("默认：${AiProvider.get(aiProvider).defaultModel}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        appVm.saveAiKey(aiKeyText.trim())
                        showAiDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("保存 Key 并去出题") }
                if (appVm.aiKey.value.isNotBlank()) {
                    Text(
                        "已保存 Key · 当前服务商：${AiProvider.get(aiProvider).label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 本地备份（安全网）：手动导出/导入合并 + 本地滚动快照（与 WebDAV 互不替代）
        SettingsSection("本地备份（安全网）") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本地备份（设备内安全网）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导出：把全部学习数据（错题/进度/自定义题库/备课）存成 JSON 文件，可用系统分享或文件管理器拷到电脑、云盘。导入：把备份合并回本地（时间较新者胜出，不会删除本地独有数据）。本地快照约每 24 小时自动留存一份（最多 7 份），可作「后悔药」。以上均为单机操作，与下方 WebDAV 远程同步互不替代。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("jiaozi_backup_${System.currentTimeMillis() / 1000}.json") }, Modifier.weight(1f).height(44.dp)) { Text("导出数据") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f).height(44.dp)) { Text("导入合并") }
                }

                // 全盘访问权限授权 + 目录导入（Android 11+ 用于检索备份目录）
                if (Build.VERSION.SDK_INT >= 30) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasStorageAccess) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:" + ctx.packageName)
                                    }
                                    storageAccessLauncher.launch(intent)
                                },
                                Modifier.weight(1f).height(44.dp)
                            ) { Text("授权全盘访问") }
                        }
                        OutlinedButton(
                            onClick = { importDirLauncher.launch(null) },
                            Modifier.weight(1f).height(44.dp)
                        ) { Text("从目录导入") }
                    }
                }

                // —— 本地自动快照（滚动保留 7 份）——
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("本地快照（最多 7 份）", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                val f = BackupManager.takeSnapshot(ctx, appVm.repo)
                                refreshSnapshots()
                                msg = "已创建快照：${f.name}"
                            } catch (e: Exception) { msg = "快照失败：${e.message}" }
                        }
                    }, Modifier.height(44.dp)) { Text("立即快照") }
                }
                if (snapshots.isEmpty()) {
                    Text("暂无本地快照（下次打开 App 会自动生成）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        snapshots.forEach { s ->
                            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(s.timeMillis))
                            val kb = if (s.sizeBytes >= 1024) "${s.sizeBytes / 1024} KB" else "${s.sizeBytes} B"
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(timeStr, style = MaterialTheme.typography.bodyMedium)
                                    Text(kb, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                TextButton(onClick = { pendingRestoreFile = s.file }) { Text("还原") }
                            }
                        }
                    }
                }
            }
        }

        // WebDAV 远程同步
        SettingsSection("WebDAV 远程同步") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WebDAV 远程同步", style = MaterialTheme.typography.titleMedium)
                Text(
                    "配置 WebDAV 服务器（Nextcloud / 群晖 NAS 等），在设备间自动同步学习进度、错题与 AI 题库。同步文件名为 sync.json，与网页端共用同一「空间目录」和「同步口令」即可互通。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("启用同步（打开 App 与后台定期自动 WebDAV 同步）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = syncEnabled, onCheckedChange = { appVm.setSyncEnabled(it) })
                }

                // 服务商预设：就地展开选择（对齐椒盐笔记设置展开范式），选常用服务自动填好地址模板
                val wdPresetDefs = listOf(
                    "坚果云" to "https://dav.jianguoyun.com/dav/",
                    "Nextcloud" to "https://你的域名/remote.php/dav/files/用户名/",
                    "群晖 NAS" to "https://你的NAS:5006/remote.php/dav/files/用户名/",
                    "其它" to ""
                )
                InlineExpandSelect(
                    title = "服务商预设",
                    options = wdPresetDefs.map { (name, _) -> name to name },
                    selected = wdPresetDefs.firstOrNull { it.second == wdUrl }?.first ?: "其它",
                    onSelect = { name ->
                        val tpl = wdPresetDefs.firstOrNull { it.first == name }?.second ?: ""
                        wdUrl = tpl
                        if (tpl.isNotEmpty()) wdDir = "artwb-default"
                    }
                )

                OutlinedTextField(
                    value = wdUrl, onValueChange = { wdUrl = it },
                    label = { Text("服务器地址（含协议，如 https://dav.example.com）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wdUser, onValueChange = { wdUser = it },
                        label = { Text("用户名") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = wdPass, onValueChange = { wdPass = it },
                        label = { Text("密码") }, modifier = Modifier.weight(1f), singleLine = true,
                        visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(if (pwVisible) appPainter("eye_off") else appPainter("eye"), contentDescription = "切换密码可见")
                            }
                        }
                    )
                }
                OutlinedTextField(
                    value = wdDir, onValueChange = { wdDir = it },
                    label = { Text("空间目录（不含首尾斜杠，如 artwb-default）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // 加密同步
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("同步加密（AES-GCM 零知识）", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后数据以密文存到服务器（服务器不可读）；需所有设备用 https/localhost 打开且口令一致。",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = wdEncrypt, onCheckedChange = { wdEncrypt = it })
                }
                if (wdEncrypt) {
                    OutlinedTextField(
                        value = wdSyncPass, onValueChange = { wdSyncPass = it },
                        label = { Text("同步口令（两端须一致）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(if (pwVisible) appPainter("eye_off") else appPainter("eye"), contentDescription = "切换口令可见")
                            }
                        }
                    )
                }

                InlineExpandSelect(
                    title = "同步方向",
                    options = listOf("upload" to "仅上传", "download" to "仅下载", "two-way" to "双向合并"),
                    selected = wdMode,
                    onSelect = { wdMode = it },
                    showDivider = false
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { appVm.saveWebDavConfig(currentWebDavConfig()) },
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) { Text("保存配置") }
                    Button(
                        onClick = {
                            val cfg = currentWebDavConfig()
                            appVm.saveWebDavConfig(cfg)
                            appVm.doSync(cfg)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        enabled = !isSyncing && wdUrl.isNotBlank()
                    ) { Text(if (isSyncing) "同步中…" else "立即同步") }
                }

                // 上次同步时间（P2-C 增量水位展示）
                if (lastSyncAt > 0) {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    Text("上次同步：${fmt.format(Date(lastSyncAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                // 同步状态提示
                when (val s = syncState) {
                    is SyncState.Syncing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(0.dp), strokeWidth = 2.dp)
                        Text(s.phase, style = MaterialTheme.typography.bodyMedium)
                    }
                    is SyncState.Success -> {
                        Text("✓ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        s.report?.let { r ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(12.dp), Arrangement.spacedBy(4.dp)) {
                                    Text("本次合并报告", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("合计 ${r.total} 条 · 冲突已解决 ${r.conflicts} · 数据水位 ${r.maxMt}", style = MaterialTheme.typography.bodySmall)
                                    val lines = buildList {
                                        if (r.examAdded > 0 || r.examUpdated > 0) add("题库：新增 ${r.examAdded} / 更新 ${r.examUpdated}")
                                        if (r.lessonAdded > 0 || r.lessonUpdated > 0) add("教案：新增 ${r.lessonAdded} / 更新 ${r.lessonUpdated}")
                                        if (r.curricAdded > 0 || r.curricUpdated > 0) add("课标：新增 ${r.curricAdded} / 更新 ${r.curricUpdated}")
                                        if (r.booksAdded > 0 || r.booksUpdated > 0) add("教材：新增 ${r.booksAdded} / 更新 ${r.booksUpdated}")
                                        if (r.qstat > 0) add("进度统计：${r.qstat}")
                                        if (r.corrections > 0) add("错题本：${r.corrections}")
                                        if (r.inboxAdded > 0 || r.inboxUpdated > 0) add("收集箱：新增 ${r.inboxAdded} / 更新 ${r.inboxUpdated}")
                                        if (r.aiHistoryAdded > 0 || r.aiHistoryUpdated > 0) add("AI 对话：新增 ${r.aiHistoryAdded} / 更新 ${r.aiHistoryUpdated}")
                                        if (r.removed > 0) add("移除：${r.removed}")
                                    }
                                    lines.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    is SyncState.Error -> Text("✗ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    is SyncState.Idle -> Text("尚未同步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 新手引导：重置后可重新显示首开轻引导弹窗（默认一次性，onboarded=true 后不再弹）
        SettingsSection("新手引导") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("重置首开引导", style = MaterialTheme.typography.titleMedium)
                Text(
                    "首开轻引导（欢迎语 + 设置目标日）默认只显示一次。重置后，下次进入首页会重新弹出。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
                Button(
                    onClick = {
                        appVm.setOnboarded(false)
                        android.widget.Toast.makeText(ctx, "已重置，返回首页将重新显示引导", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) { Text("重置新手引导") }
            }
        }
    }
        }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("去 AI 出题") },
            text = { Text("Key 已保存。到「练习」页底部的「AI 题库」选择科目与数量即可生成。") },
            confirmButton = { TextButton(onClick = { showAiDialog = false; nav.navigate("practice") }) { Text("去练习") } },
            dismissButton = { TextButton(onClick = { showAiDialog = false }) { Text("稍后") } }
        )
    }

    if (msg != null) {
        AlertDialog(
            onDismissRequest = { msg = null },
            title = { Text("提示") },
            text = { Text(msg!!) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好") } }
        )
    }

    // 导入合并确认：避免误选文件直接覆盖/合并本地数据
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
                            val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                            val report = appVm.repo.importEnvelope(json)
                            msg = "已合并 ${report.total} 条数据"
                        } catch (e: Exception) { msg = "导入失败：${e.message}" }
                    }
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } }
        )
    }

    // 快照还原确认
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
                        } catch (e: Exception) { msg = "还原失败：${e.message}" }
                    }
                }) { Text("还原") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreFile = null }) { Text("取消") } }
        )
    }

    // 目录导入：列出来自所选目录的 JSON 文件，让用户选择
    if (pendingDirImportFiles != null) {
        val fileList = pendingDirImportFiles!!
        AlertDialog(
            onDismissRequest = { pendingDirImportFiles = null },
            title = { Text("选择备份文件") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
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

    // 目录导入：选定文件后的合并确认
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
                            val json = ctx.contentResolver.openInputStream(importUri)?.bufferedReader()?.readText() ?: ""
                            val report = appVm.repo.importEnvelope(json)
                            msg = "已合并 ${report.total} 条数据"
                        } catch (e: Exception) { msg = "导入失败：${e.message}" }
                    }
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingDirImportFile = null }) { Text("取消") } }
        )
    }
}

// ==================== 目录导入工具函数 ====================

/**
 * 通过 SAF ContentResolver 递归扫描目录下的所有 .json 文件。
 * 返回 Pair<显示名, Uri> 列表，用于用户选择后通过 contentResolver 读取。
 */
private fun scanDirDocuments(ctx: android.content.Context, treeUri: android.net.Uri): List<Pair<String, android.net.Uri>> {
    val results = mutableListOf<Pair<String, android.net.Uri>>()
    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri))
    val projection = arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    scanChildren(ctx, childrenUri, projection, results)
    return results
}

private fun scanChildren(
    ctx: android.content.Context,
    parentUri: android.net.Uri,
    projection: Array<String>,
    results: MutableList<Pair<String, android.net.Uri>>
) {
    var cursor: android.database.Cursor? = null
    try {
        cursor = ctx.contentResolver.query(parentUri, projection, null, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    // 递归扫描子目录
                    val childUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
                    scanChildren(ctx, childUri, projection, results)
                } else if (name.endsWith(".json", ignoreCase = true)) {
                    results.add(name to docUri)
                }
            }
        }
    } catch (_: Exception) {
        // 遇到无权限访问的子目录静默跳过
    } finally {
        cursor?.close()
    }
}

// ==================== 关于页面（独立路由 "about"）====================

/**
 * 关于页面 —— 卡片式布局（参考 Miuix 风格暗色卡片）。
 * 包含：版本/检查更新、赞助支持、社交链接（GitHub/小红书/抖音）、开源信息。
 */
@Composable
fun AboutScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val appVm = LocalAppVm.current
    val isPro by appVm.isPro.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showProDialog by remember { mutableStateOf(false) }

    // 尝试从 PackageManager 取当前版本名
    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.58" }
        catch (_: Exception) { "2.58" }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题栏
        Row(Modifier.fillMaxWidth(), Arrangement.Start, Alignment.CenterVertically) {
            IconButton(onClick = nav::popBackStack) { Icon(appPainter("back"), contentDescription = "返回") }
            Text("关于", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // ── 卡片 1：版本 & 检查更新 ──
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (checking) return@clickable
                    checking = true
                    updateMsg = null
                    pendingUpdate = null
                    scope.launch {
                        val info = UpdateChecker.checkUpdate()
                        checking = false
                        when {
                            info == null -> updateMsg = "检查失败，请检查网络后重试"
                            info.hasUpdate -> pendingUpdate = info
                            else -> updateMsg = "已是最新版本（$versionName）"
                        }
                    }
                }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("检查更新", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("当前版本 $versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("点击检查", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(appPainter("chevron"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── 卡片 2：Pro 会员（诚信付费）──
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { showProDialog = true }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isPro) "Pro 会员 · 已激活" else "Pro 会员",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isPro) "感谢支持，已解锁全部 Pro 权益" else "微信/支付宝扫码开通，解锁作者原创内容",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isPro) "管理" else "去开通", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(appPainter("chevron"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── 卡片 3：GitHub ──
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BadCodeZ"))) }
                    catch (_: Exception) {}
                }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("GitHub", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("开源代码与 Issue 反馈", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@BadCodeZ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(appPainter("chevron"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── 卡片 4：小红书 ──
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xhslink.cn/m/8D9WQaseSL7"))) }
                    catch (_: Exception) {}
                }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("小红书", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点点关注喵", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@决明子", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(appPainter("chevron"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // ── 卡片 5：抖音 ──
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    // 抖音通过短链打开
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://v.douyin.com/qQmtgRmVY6A/"))) }
                    catch (_: Exception) {}
                }.padding(16.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("抖音", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点点关注喵", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@BadCodeZ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(appPainter("chevron"), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // 底部版权
        Text(
            "综合教资备考平台 · $versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    // 更新结果提示（无更新 / 检查失败）
    if (updateMsg != null) {
        AlertDialog(
            onDismissRequest = { updateMsg = null },
            title = { Text("检查更新") },
            text = { Text(updateMsg!!) },
            confirmButton = { TextButton(onClick = { updateMsg = null }) { Text("好") } }
        )
    }

    // 发现新版本：前往下载
    if (pendingUpdate != null) {
        val info = pendingUpdate!!
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text("发现新版本 V${info.latestVersionName}") },
            text = { Text(if (info.changelog.isBlank()) "作者已发布新版本，建议更新以获得最新题库与修复。" else info.changelog) },
            confirmButton = {
                TextButton(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) } catch (_: Exception) {}
                    pendingUpdate = null
                }) { Text("前往下载") }
            },
            dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text("稍后") } }
        )
    }

    // Pro 会员对话框（诚信激活）
    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text(if (isPro) "Pro 会员" else "开通 Pro 会员") },
            text = {
                if (isPro) {
                    Text("你已激活 Pro 会员，感谢支持！解锁的权益将持续扩充。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pro 权益（持续扩充）：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("· 知识卡片高级模板\\n· 备课模板库\\n· 课标精讲\\n（具体范围后续版本逐步开放）", style = MaterialTheme.typography.bodySmall)
                        Text("微信 / 支付宝收款码（图待补）。扫码付费后点下方按钮诚信激活——本应用不联网验单，靠你的自觉。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            confirmButton = {
                if (isPro) {
                    TextButton(onClick = { appVm.deactivatePro(); showProDialog = false }) { Text("撤销激活") }
                } else {
                    TextButton(onClick = { appVm.activatePro(); showProDialog = false }) { Text("我已付费 · 诚信激活") }
                }
            },
            dismissButton = { TextButton(onClick = { showProDialog = false }) { Text("关闭") } }
        )
    }
}
