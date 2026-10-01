package com.jiaozi.sz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.AppRepository
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.ChapterCfg
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.data.model.LessonTemplate
import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.remote.SyncState
import com.jiaozi.sz.data.remote.WebDavConfig
import com.jiaozi.sz.domain.AiProvider
import com.jiaozi.sz.domain.WebDavSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.jiaozi.sz.util.todayIso
import com.jiaozi.sz.util.yesterdayIso

/**
 * 薄弱点攻坚笔记种子：练习结算页「错题生成攻坚笔记」注入 WeaknessScreen 编辑器。
 * 与备课（LessonFields 十二要素）彻底分家——攻坚笔记是轻量「标题 + 科目 + 正文」结构，
 * 不需要教案骨架；故用独立载体跨导航传递，编辑器直接预填。
 */
data class PendingWeaknessSeed(
    val title: String,
    val subject: String = "",
    val disc: String = "",
    val body: String = "",
    val fromExamId: String = ""
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: AppRepository = (app as App).repository

    private val _subject3Disc = MutableStateFlow("美术")
    val subject3Disc: StateFlow<String> = _subject3Disc.asStateFlow()

    /**
     * 🔴 2026-09-28 学段筛题：用户**报考学段**（`"初中"` / `"高中"`，见 [BankStore.STAGE_OPTIONS]）。
     *
     * 语义（对齐用户裁定口径）：
     * - 空串 `""` = **未设置** ⇒ 不启用学段过滤，全部题目可见（兼容旧用户 / 首次安装未选）；
     * - `"初中"` / `"高中"` ⇒ 练习只出「本学段 + 通用题」（[Question.stage] 为 null 或等于该值）。
     *
     * 与 [subject3Disc]（科三学科）并列，同为主页偏好；落盘键 [MetaKeys.EXAM_STAGE]。
     */
    private val _examStage = MutableStateFlow("")
    val examStage: StateFlow<String> = _examStage.asStateFlow()

    private val _targetDay = MutableStateFlow("")
    val targetDay: StateFlow<String> = _targetDay.asStateFlow()

    /** 首开轻引导是否已展示（展示后置 true，永不重复弹） */
    private val _onboarded = MutableStateFlow(false)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    /** 首屏 meta 是否已加载完成（用于首开引导去抖，避免默认值 false 闪烁） */
    private val _metaLoaded = MutableStateFlow(false)
    val metaLoaded: StateFlow<Boolean> = _metaLoaded.asStateFlow()

    private val _knowledgeFav = MutableStateFlow<Set<String>>(emptySet())
    val knowledgeFav: StateFlow<Set<String>> = _knowledgeFav.asStateFlow()

    /** 目标估分（百分制，默认 90；对齐网页端统计「与目标分差」） */
    private val _targetScore = MutableStateFlow(90)
    val targetScore: StateFlow<Int> = _targetScore.asStateFlow()
    fun setTargetScore(v: Int) {
        _targetScore.value = v.coerceIn(0, 150)
        viewModelScope.launch { repo.setMeta(MetaKeys.TARGET_SCORE, _targetScore.value.toString()) }
    }

    /** 小米传送门：长按文本唤起时携带的搜索关键词（SearchScreen 消费后清空） */
    private val _pendingSearch = MutableStateFlow("")
    val pendingSearch: StateFlow<String> = _pendingSearch.asStateFlow()
    fun setPendingSearch(q: String) { _pendingSearch.value = q }

    /** 搜索结果直达：待打开文档（路由 + id），SearchScreen 写入、目标屏消费后定位 */
    data class PendingOpenDoc(val route: String, val id: String)
    private val _pendingOpenDoc = MutableStateFlow<PendingOpenDoc?>(null)
    val pendingOpenDoc: StateFlow<PendingOpenDoc?> = _pendingOpenDoc.asStateFlow()
    fun requestOpenDoc(route: String, id: String) { _pendingOpenDoc.value = PendingOpenDoc(route, id) }
    fun clearPendingOpenDoc() { _pendingOpenDoc.value = null }

    /** 小米桌面组件点击：请求进入练习页（AppRoot 消费后清空） */
    private val _pendingPractice = MutableStateFlow(false)
    val pendingPractice: StateFlow<Boolean> = _pendingPractice.asStateFlow()
    fun setPendingPractice(v: Boolean) { _pendingPractice.value = v }

    /** AI 就题追问：从练习/校订携带的题干上下文（AiChatScreen 消费后清空） */
    private val _pendingAiContext = MutableStateFlow("")
    val pendingAiContext: StateFlow<String> = _pendingAiContext.asStateFlow()
    fun setPendingAiContext(text: String) { _pendingAiContext.value = text }

    /** 章节健康度「去练该章」：携带 (科目, 章节) 进入练习页后由 PracticeScreen 消费 */
    private val _pendingChapterPractice = MutableStateFlow<Pair<String, String>?>(null)
    val pendingChapterPractice: StateFlow<Pair<String, String>?> = _pendingChapterPractice.asStateFlow()
    fun setPendingChapterPractice(subj: String, chapter: String) { _pendingChapterPractice.value = subj to chapter }
    fun clearPendingChapterPractice() { _pendingChapterPractice.value = null }

    /**
     * 🔴 2026-09-23（统计页 E7 / 走查 #34）：从统计页跳「校订」时指定落哪个 Tab。
     * 原实现「错题本」入口直接 `navigate("practice")`，与语义不符 —— 错题本是校订页的第 2 个 Tab。
     */
    private val _pendingProofTab = MutableStateFlow<String?>(null)
    val pendingProofTab: StateFlow<String?> = _pendingProofTab.asStateFlow()
    fun setPendingProofTab(tab: String) { _pendingProofTab.value = tab }
    fun clearPendingProofTab() { _pendingProofTab.value = null }

    /**
     * 🔴 2026-09-30（薄弱点攻坚独立模块）：练习结算页「生成攻坚笔记」把错题归集预填为
     * `PendingWeaknessSeed`，经本 pending 意图跨导航注入 WeaknessScreen 编辑器。
     * 落点 = `WeaknessScreen` 的 LaunchedEffect（消费后清空）。与备课模块完全解耦。
     */
    private val _pendingWeaknessSeed = MutableStateFlow<PendingWeaknessSeed?>(null)
    val pendingWeaknessSeed: StateFlow<PendingWeaknessSeed?> = _pendingWeaknessSeed.asStateFlow()
    fun setPendingWeaknessSeed(f: PendingWeaknessSeed?) { _pendingWeaknessSeed.value = f }
    fun clearPendingWeaknessSeed() { _pendingWeaknessSeed.value = null }

    private val _theme = MutableStateFlow("system") // system / light / dark
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false) // 跟随系统壁纸取色（小米适配）
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _islandEnabled = MutableStateFlow(false) // 灵动岛（上岛）开关
    val islandEnabled: StateFlow<Boolean> = _islandEnabled.asStateFlow()

    private val _fontScale = MutableStateFlow("lg") // sm / md / lg / xl（默认 lg：新用户大字可读性，老用户已存偏好不受影响）
    val fontScale: StateFlow<String> = _fontScale.asStateFlow()

    private val _checkinStreak = MutableStateFlow(0)
    val checkinStreak: StateFlow<Int> = _checkinStreak.asStateFlow()

    private val _syncEnabled = MutableStateFlow(false)
    val syncEnabled: StateFlow<Boolean> = _syncEnabled.asStateFlow()

    private val _aiKey = MutableStateFlow("")
    val aiKey: StateFlow<String> = _aiKey.asStateFlow()

    /** AI 服务商（deepseek / openai / moonshot），决定调用哪个端点 */
    private val _aiProvider = MutableStateFlow(AiProvider.DEFAULT)
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    /** 可选：覆盖该服务商默认模型；为空则用默认 */
    private val _aiModel = MutableStateFlow("")
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    // —— WebDAV 配置（内存镜像，init 时从 meta 载入）——
    private val _webDavUrl = MutableStateFlow("")
    val webDavUrl: StateFlow<String> = _webDavUrl.asStateFlow()
    private val _webDavUser = MutableStateFlow("")
    val webDavUser: StateFlow<String> = _webDavUser.asStateFlow()
    private val _webDavPass = MutableStateFlow("")
    val webDavPass: StateFlow<String> = _webDavPass.asStateFlow()
    private val _webDavDir = MutableStateFlow("artwb-default")
    val webDavDir: StateFlow<String> = _webDavDir.asStateFlow()
    private val _webDavEncrypt = MutableStateFlow(false)
    val webDavEncrypt: StateFlow<Boolean> = _webDavEncrypt.asStateFlow()
    private val _webDavSyncPass = MutableStateFlow("")
    val webDavSyncPass: StateFlow<String> = _webDavSyncPass.asStateFlow()

    // Pro 会员（诚信付费）状态：本地记录激活标记，不联网验单，靠用户自觉
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()
    private val _webDavMode = MutableStateFlow("two-way")
    val webDavMode: StateFlow<String> = _webDavMode.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** 上次成功同步时间戳（P2-C 增量水位展示用） */
    private val _lastSyncAt = MutableStateFlow(0L)
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, ProgressEntity>>(emptyMap())
    val progressMap: StateFlow<Map<String, ProgressEntity>> = _progressMap.asStateFlow()

    /** 章节配置（显示名 + 模考权重），供章节编辑页与模考蓝图消费 */
    private val _chapterConfig = MutableStateFlow<Map<String, ChapterCfg>>(emptyMap())
    val chapterConfig: StateFlow<Map<String, ChapterCfg>> = _chapterConfig.asStateFlow()

    /** 备课用户模板库（存 meta），供模板库页与编辑器消费 */
    private val _lessonTemplates = MutableStateFlow<List<LessonTemplate>>(emptyList())
    val lessonTemplates: StateFlow<List<LessonTemplate>> = _lessonTemplates.asStateFlow()

    /** 启动期数据加载错误（来自 Application 降级），供 UI 提示；正常为 null */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** 题库外置：首启下载引导是否已完成的判据（完成后不再强制弹下载页） */
    private val _bankInitDone = MutableStateFlow(false)
    val bankInitDone: StateFlow<Boolean> = _bankInitDone.asStateFlow()
    fun setBankInitDone(v: Boolean) {
        _bankInitDone.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.BANK_INIT_DONE, v.toString()) }
    }

    /**
     * 🔴 2026-09-28 学段真拆包：本地**已下载题库所属学段**（`"初中"` / `"高中"`）。
     *
     * 与 [examStage] 比对可判定「本地题库与当前报考学段是否一致」：
     * 不一致 ⇒ 本地存的是旧学段数据，需在题库管理页重下（见 [bankStageOutdated]）。
     * 空串 = 未记录（旧版本用户 / 从未下载）⇒ 不做一致性判定。
     */
    private val _bankDownloadStage = MutableStateFlow("")
    val bankDownloadStage: StateFlow<String> = _bankDownloadStage.asStateFlow()
    fun setBankDownloadStage(stage: String) {
        if (stage.isNotEmpty() && stage !in BankStore.STAGE_OPTIONS) return
        _bankDownloadStage.value = stage
        viewModelScope.launch { repo.setMeta(MetaKeys.BANK_DOWNLOAD_STAGE, stage) }
    }

    /**
     * 本地题库学段是否**已过期**（须重下）：
     * 仅当「已记录下载学段」且「已设置报考学段」且两者不等时为 true。
     */
    fun bankStageOutdated(): Boolean {
        val dl = _bankDownloadStage.value
        val cur = _examStage.value
        return dl.isNotEmpty() && cur.isNotEmpty() && dl != cur
    }

    init {
        // 搬运 Application 启动兜底时记录的错误信息
        _loadError.value = (app as? App)?.loadError

        viewModelScope.launch {
            try {
                val disc = repo.getMeta(MetaKeys.SUBJECT3_DISC) ?: repo.discList.firstOrNull() ?: "美术"
                _subject3Disc.value = disc
                // 🔴 学段：键缺失 ⇒ 空串（未设置，不过滤）。仅接受合法取值，脏数据回退未设置。
                _examStage.value = repo.getMeta(MetaKeys.EXAM_STAGE)
                    ?.takeIf { it in BankStore.STAGE_OPTIONS } ?: ""
                _targetDay.value = repo.getMeta(MetaKeys.TARGET_DAY) ?: ""
                _targetScore.value = repo.getMeta(MetaKeys.TARGET_SCORE)?.toIntOrNull() ?: 90
                _knowledgeFav.value = parseFavSet(repo.getMeta(MetaKeys.KNOWLEDGE_FAV))
                _theme.value = repo.getMeta(MetaKeys.THEME) ?: "system"
                _dynamicColor.value = repo.getMeta(MetaKeys.DYNAMIC_COLOR) == "true"
                _islandEnabled.value = repo.getMeta(MetaKeys.ISLAND_ENABLED) == "true"
                _onboarded.value = repo.getMeta(MetaKeys.ONBOARDED) == "true"
                _fontScale.value = repo.getMeta(MetaKeys.FONT_SCALE) ?: "lg"
                _checkinStreak.value = repo.getMeta(MetaKeys.CHECKIN_STREAK)?.toIntOrNull() ?: 0
                _syncEnabled.value = repo.getMeta(MetaKeys.SYNC_ENABLED) == "true"
                _aiKey.value = repo.getMeta(MetaKeys.AI_KEY) ?: ""
                _aiProvider.value = repo.getMeta(MetaKeys.AI_PROVIDER) ?: AiProvider.DEFAULT
                _aiModel.value = repo.getMeta(MetaKeys.AI_MODEL) ?: ""
                _webDavUrl.value = repo.getMeta(MetaKeys.WEBDAV_URL) ?: ""
                _webDavUser.value = repo.getMeta(MetaKeys.WEBDAV_USER) ?: ""
                _webDavPass.value = repo.getMeta(MetaKeys.WEBDAV_PASS) ?: ""
                _webDavDir.value = repo.getMeta(MetaKeys.WEBDAV_DIR) ?: "artwb-default"
                _webDavMode.value = repo.getMeta(MetaKeys.WEBDAV_DIRMODE) ?: "two-way"
                _webDavEncrypt.value = repo.getMeta(MetaKeys.SYNC_ENCRYPT) == "true"
                _webDavSyncPass.value = repo.getMeta(MetaKeys.SYNC_PASS) ?: ""
                _lastSyncAt.value = repo.getMeta(MetaKeys.LAST_SYNC_AT)?.toLongOrNull() ?: 0
                _isPro.value = repo.getMeta(MetaKeys.PRO_ACTIVATED) == "true"
                _bankInitDone.value = repo.getMeta(MetaKeys.BANK_INIT_DONE) == "true"
                _bankDownloadStage.value = repo.getMeta(MetaKeys.BANK_DOWNLOAD_STAGE)
                    ?.takeIf { it in BankStore.STAGE_OPTIONS } ?: ""
                _chapterConfig.value = repo.getChapterConfig()
                _lessonTemplates.value = repo.getLessonTemplates()

                // 进度增量订阅：Room 写入后自动推送，练习后今日/统计实时刷新
                repo.progressFlow()
                    .onEach { _progressMap.value = it }
                    .launchIn(viewModelScope)

                // 启用同步且已配置地址时，启动自动同步一次（静默，仅更新状态）
                if (_syncEnabled.value && _webDavUrl.value.isNotBlank()) {
                    doSync(loadWebDavConfig())
                }
                _metaLoaded.value = true
            } catch (e: Throwable) {
                // 首屏读取失败不应带走进程：记录后维持默认状态，App 仍可运行
                android.util.Log.e("AppViewModel", "init failed, app degraded", e)
            }
        }
    }

    fun setSubject3Disc(disc: String) {
        _subject3Disc.value = disc
        viewModelScope.launch { repo.setMeta(MetaKeys.SUBJECT3_DISC, disc) }
    }

    /**
     * 🔴 2026-09-28 学段筛题：设置报考学段。传空串 = 清除限制（不过滤）。
     * 非法取值忽略，避免脏数据写入。
     */
    fun setExamStage(stage: String) {
        if (stage.isNotEmpty() && stage !in BankStore.STAGE_OPTIONS) return
        _examStage.value = stage
        viewModelScope.launch { repo.setMeta(MetaKeys.EXAM_STAGE, stage) }
    }

    fun setTargetDay(day: String) {
        _targetDay.value = day
        viewModelScope.launch { repo.setMeta(MetaKeys.TARGET_DAY, day) }
    }

    fun setOnboarded(v: Boolean) {
        _onboarded.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.ONBOARDED, v.toString()) }
    }

    /** 诚信激活 Pro 会员：本地记录标记，不联网验单。具体解锁哪些功能由各处门禁（appVm.isPro）决定。 */
    fun activatePro() {
        _isPro.value = true
        viewModelScope.launch { repo.setMeta(MetaKeys.PRO_ACTIVATED, "true") }
    }

    /** 撤销 Pro 激活（调试/退款场景），仅本地清标记。 */
    fun deactivatePro() {
        _isPro.value = false
        viewModelScope.launch { repo.setMeta(MetaKeys.PRO_ACTIVATED, "false") }
    }

    /** 保存单章节配置（显示名 + 模考权重）。整体覆盖该 key 后写回 meta 并刷新 StateFlow。 */
    fun saveChapterConfig(key: String, name: String, weight: Double) {
        val next = _chapterConfig.value.toMutableMap()
        next[key] = ChapterCfg(name = name, weight = weight)
        _chapterConfig.value = next
        viewModelScope.launch { repo.saveChapterConfig(next) }
    }

    // —— 备课模板库 ——
    fun saveLessonTemplate(t: LessonTemplate) {
        val next = (_lessonTemplates.value.filter { it.id != t.id } + t).sortedBy { it.name }
        _lessonTemplates.value = next
        viewModelScope.launch { repo.saveLessonTemplates(next) }
    }
    fun deleteLessonTemplate(id: String) {
        val next = _lessonTemplates.value.filter { it.id != id }
        _lessonTemplates.value = next
        viewModelScope.launch { repo.saveLessonTemplates(next) }
    }

    fun toggleKnowledgeFav(id: String) {
        val next = if (_knowledgeFav.value.contains(id)) _knowledgeFav.value - id else _knowledgeFav.value + id
        _knowledgeFav.value = next
        viewModelScope.launch { repo.setMeta(MetaKeys.KNOWLEDGE_FAV, next.joinToString(",")) }
    }

    fun setTheme(t: String) {
        _theme.value = t
        viewModelScope.launch { repo.setMeta(MetaKeys.THEME, t) }
    }

    fun setDynamicColor(v: Boolean) {
        _dynamicColor.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.DYNAMIC_COLOR, v.toString()) }
    }

    fun setIslandEnabled(v: Boolean) {
        _islandEnabled.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.ISLAND_ENABLED, v.toString()) }
    }

    fun setFontScale(s: String) {
        _fontScale.value = s
        viewModelScope.launch { repo.setMeta(MetaKeys.FONT_SCALE, s) }
    }

    fun setSyncEnabled(v: Boolean) {
        _syncEnabled.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.SYNC_ENABLED, v.toString()) }
    }

    /** 打卡（每天一次；连续则 +1，断签则重置为 1） */
    fun checkIn() {
        viewModelScope.launch {
            val today = todayStr()
            val last = repo.getMeta(MetaKeys.LAST_CHECKIN)
            if (last == today) return@launch
            val streak = if (last == yesterdayStr()) (_checkinStreak.value + 1) else 1
            _checkinStreak.value = streak
            repo.setMeta(MetaKeys.CHECKIN_STREAK, streak.toString())
            repo.setMeta(MetaKeys.LAST_CHECKIN, today)
        }
    }

    fun refreshProgress() {
        viewModelScope.launch {
            _progressMap.value = repo.progressMap()
        }
    }

    fun saveAiKey(key: String) {
        _aiKey.value = key
        viewModelScope.launch { repo.setMeta(MetaKeys.AI_KEY, key) }
    }

    fun setAiProvider(id: String) {
        _aiProvider.value = if (AiProvider.get(id).id == id) id else AiProvider.DEFAULT
        viewModelScope.launch { repo.setMeta(MetaKeys.AI_PROVIDER, _aiProvider.value) }
        // 切换服务商时，清空自定义模型（避免旧厂商模型串到新厂商）
        if (_aiModel.value.isNotBlank()) {
            _aiModel.value = ""
            viewModelScope.launch { repo.setMeta(MetaKeys.AI_MODEL, "") }
        }
    }

    fun setAiModel(model: String) {
        _aiModel.value = model.trim()
        viewModelScope.launch { repo.setMeta(MetaKeys.AI_MODEL, _aiModel.value) }
    }

    /** 从当前内存镜像构建配置对象（非挂起，供同步调用） */
    fun loadWebDavConfig(): WebDavConfig = WebDavConfig(
        url = _webDavUrl.value.trim(),
        user = _webDavUser.value.trim(),
        pass = _webDavPass.value,
        remoteDir = _webDavDir.value.trim().ifBlank { "artwb-default" },
        direction = _webDavMode.value.ifBlank { "two-way" },
        encrypt = _webDavEncrypt.value,
        syncPass = _webDavSyncPass.value
    )

    /** 保存 WebDAV 配置到 meta（持久化） */
    fun saveWebDavConfig(cfg: WebDavConfig) {
        _webDavUrl.value = cfg.url
        _webDavUser.value = cfg.user
        _webDavPass.value = cfg.pass
        _webDavDir.value = cfg.remoteDir
        _webDavMode.value = cfg.direction
        _webDavEncrypt.value = cfg.encrypt
        _webDavSyncPass.value = cfg.syncPass
        viewModelScope.launch {
            repo.setMeta(MetaKeys.WEBDAV_URL, cfg.url)
            repo.setMeta(MetaKeys.WEBDAV_USER, cfg.user)
            repo.setMeta(MetaKeys.WEBDAV_PASS, cfg.pass)
            repo.setMeta(MetaKeys.WEBDAV_DIR, cfg.remoteDir)
            repo.setMeta(MetaKeys.WEBDAV_DIRMODE, cfg.direction)
            repo.setMeta(MetaKeys.SYNC_ENCRYPT, cfg.encrypt.toString())
            repo.setMeta(MetaKeys.SYNC_PASS, cfg.syncPass)
        }
    }

    /** 执行一次 WebDAV 同步；结果通过 syncState 暴露给 UI */
    fun doSync(cfg: WebDavConfig) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing("准备中…")
            WebDavSync.sync(cfg, repo) { st ->
                _syncState.value = st
                if (st is SyncState.Success) {
                    refreshProgress()
                    viewModelScope.launch { _lastSyncAt.value = repo.getMeta(MetaKeys.LAST_SYNC_AT)?.toLongOrNull() ?: 0L }
                }
            }
        }
    }

    private fun todayStr() = todayIso()
    private fun yesterdayStr() = yesterdayIso()
}

/** 解析收藏集合字符串（逗号分隔，容错空串） */
private fun parseFavSet(s: String?): Set<String> =
    if (s.isNullOrBlank()) emptySet() else s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

