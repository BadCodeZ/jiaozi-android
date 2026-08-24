package com.jiaozi.sz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.AppRepository
import com.jiaozi.sz.data.MetaKeys
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: AppRepository = (app as App).repository

    private val _subject3Disc = MutableStateFlow("美术")
    val subject3Disc: StateFlow<String> = _subject3Disc.asStateFlow()

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

    private val _theme = MutableStateFlow("system") // system / light / dark
    val theme: StateFlow<String> = _theme.asStateFlow()

    /** 美术主题包（风格增强）：默认 / 青 / 墨 / 锦，覆盖主色 token */
    private val _themePack = MutableStateFlow("默认")
    val themePack: StateFlow<String> = _themePack.asStateFlow()
    fun setThemePack(p: String) {
        _themePack.value = p
        viewModelScope.launch { repo.setMeta(MetaKeys.THEME_PACK, p) }
    }

    private val _dynamicColor = MutableStateFlow(false) // 跟随系统壁纸取色（小米适配）
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _islandEnabled = MutableStateFlow(false) // 灵动岛（上岛）开关
    val islandEnabled: StateFlow<Boolean> = _islandEnabled.asStateFlow()

    private val _fontScale = MutableStateFlow("md") // sm / md / lg / xl
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
    private val _webDavMode = MutableStateFlow("two-way")
    val webDavMode: StateFlow<String> = _webDavMode.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, ProgressEntity>>(emptyMap())
    val progressMap: StateFlow<Map<String, ProgressEntity>> = _progressMap.asStateFlow()

    /** 启动期数据加载错误（来自 Application 降级），供 UI 提示；正常为 null */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        // 搬运 Application 启动兜底时记录的错误信息
        _loadError.value = (app as? App)?.loadError

        viewModelScope.launch {
            try {
                val disc = repo.getMeta(MetaKeys.SUBJECT3_DISC) ?: repo.discList.firstOrNull() ?: "美术"
                _subject3Disc.value = disc
                _targetDay.value = repo.getMeta(MetaKeys.TARGET_DAY) ?: ""
                _targetScore.value = repo.getMeta(MetaKeys.TARGET_SCORE)?.toIntOrNull() ?: 90
                _knowledgeFav.value = parseFavSet(repo.getMeta(MetaKeys.KNOWLEDGE_FAV))
                _theme.value = repo.getMeta(MetaKeys.THEME) ?: "system"
                _themePack.value = repo.getMeta(MetaKeys.THEME_PACK) ?: "默认"
                _dynamicColor.value = repo.getMeta(MetaKeys.DYNAMIC_COLOR) == "true"
                _islandEnabled.value = repo.getMeta(MetaKeys.ISLAND_ENABLED) == "true"
                _onboarded.value = repo.getMeta(MetaKeys.ONBOARDED) == "true"
                _fontScale.value = repo.getMeta(MetaKeys.FONT_SCALE) ?: "md"
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

    fun setTargetDay(day: String) {
        _targetDay.value = day
        viewModelScope.launch { repo.setMeta(MetaKeys.TARGET_DAY, day) }
    }

    fun setOnboarded(v: Boolean) {
        _onboarded.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.ONBOARDED, v.toString()) }
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
                if (st is SyncState.Success) refreshProgress()
            }
        }
    }

    private fun todayStr() = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private fun yesterdayStr() =
        LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/** 解析收藏集合字符串（逗号分隔，容错空串） */
private fun parseFavSet(s: String?): Set<String> =
    if (s.isNullOrBlank()) emptySet() else s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

