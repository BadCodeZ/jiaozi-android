package com.jiaozi.sz.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.xiaomi.StudyTimerService
import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.local.DailyStatEntity
import com.jiaozi.sz.data.local.InboxEntity
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.domain.PracticeConfig
import com.jiaozi.sz.domain.PracticeEngine
import com.jiaozi.sz.domain.PracticeReport
import com.jiaozi.sz.domain.SpacedRepetition
import com.jiaozi.sz.domain.answerIndex
import com.jiaozi.sz.domain.parseOptions
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.jiaozi.sz.util.todayIso

/** 单题作答记录已上移到 domain（纯数据、被结算逻辑需要）；此别名保持 ui 侧引用零改动。 */
typealias AnswerRecord = com.jiaozi.sz.domain.AnswerRecord

data class PracticeState(
    val mode: String = "",
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val answered: Boolean = false,
    val selected: Int = -1,
    val subjectiveResult: String? = null, // "right" / "wrong"
    val correct: Boolean = false,
    val showAnalysis: Boolean = false,
    val causeSelected: Set<String> = emptySet(),
    val finished: Boolean = false,
    val results: Map<String, AnswerRecord> = emptyMap(),
    val timeLimitSec: Int? = null,        // 模考限时（秒）
    val draft: String = "",               // 主观题草稿
    val showAnswer: Boolean = false,      // 主观题是否已"对答案"
    val historyDraft: String? = null,     // 历史草稿（来自错题本进度，复盘可见）
    val loading: Boolean = false           // 抽题/加载中：错题本等异步入口的感知反馈
) {
    val current: Question? get() = questions.getOrNull(index)
    val total: Int get() = questions.size
    val isLast: Boolean get() = index >= questions.lastIndex
    val options: List<String> get() = current?.let { parseOptions(it.opt) } ?: emptyList()
}

/** 错因选项（与原网页一致） */
val CAUSE_OPTIONS = listOf("概念不清", "审题偏差", "记忆模糊", "理解偏差", "其他")

class PracticeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: Repository = (app as App).repository

    private val _state = MutableStateFlow(PracticeState())
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    /** 当前练习配置（供 PracticeHome 回显） */
    private val _config = MutableStateFlow(PracticeConfig())
    val config: StateFlow<PracticeConfig> = _config.asStateFlow()

    /** 收藏题目 id 集合（meta 持久化；旧版无此键 → 空集，天然兼容旧用户） */
    private val _favIds = MutableStateFlow<Set<String>>(emptySet())
    val favIds: StateFlow<Set<String>> = _favIds.asStateFlow()

    /** 题型组合偏好（choice / subjective / all，默认 choice） */
    private val _practiceType = MutableStateFlow("choice")
    val practiceType: StateFlow<String> = _practiceType.asStateFlow()
    fun setPracticeType(v: String) {
        if (v !in listOf("choice", "subjective", "all")) return
        _practiceType.value = v
        viewModelScope.launch { repo.setMeta(MetaKeys.PRACTICE_TYPE, v) }
    }

    init {
        viewModelScope.launch {
            val mode = repo.getMeta(MetaKeys.PRACTICE_MODE) ?: "随机全科"
            val subj = repo.getMeta(MetaKeys.PRACTICE_SUBJ)?.takeIf { it.isNotBlank() }
            val num = repo.getMeta(MetaKeys.PRACTICE_NUM)?.toIntOrNull() ?: 20
            val interleave = repo.getMeta(MetaKeys.PRACTICE_INTERLEAVE) == "true"
            val showAnswer = repo.getMeta(MetaKeys.PRACTICE_SHOW_ANSWER) == "true"
            val chapters = repo.getMeta(MetaKeys.PRACTICE_CHAPTERS)?.takeIf { it.isNotBlank() }?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            val chapter = repo.getMeta(MetaKeys.PRACTICE_CHAPTER)?.takeIf { it.isNotBlank() }
            val disc = repo.getMeta(MetaKeys.PRACTICE_DISC)?.takeIf { it.isNotBlank() }
            val shuffleOptions = repo.getMeta(MetaKeys.PRACTICE_SHUFFLE_OPTIONS) == "true"
            val includeWrong = repo.getMeta(MetaKeys.PRACTICE_INCLUDE_WRONG) == "true"
            val typeCombo = repo.getMeta(MetaKeys.PRACTICE_TYPE)?.takeIf { it.isNotBlank() }
                ?.takeIf { it in listOf("choice", "subjective", "all") } ?: "choice"
            _practiceType.value = typeCombo
            _config.value = PracticeConfig(
                mode = mode, subj = subj, num = num, interleave = interleave,
                showAnswer = showAnswer, chapters = chapters,
                chapter = chapter, disc = disc,
                shuffleOptions = shuffleOptions, includeWrong = includeWrong,
                typeCombo = typeCombo
            )
            _favIds.value = repo.getMeta(MetaKeys.PRACTICE_FAV)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()
        }
    }

    private suspend fun loadProgress(): Map<String, ProgressEntity> = repo.progressMap()

    /**
     * 🔴 2026-09-28 学段筛题：当前用户报考学段（meta `EXAM_STAGE` 持久化）。
     * 未设置 / 空串 ⇒ null ⇒ 不启用学段过滤（兼容旧用户与未答题场景）。
     */
    private suspend fun currentStage(): String? =
        repo.getMeta(MetaKeys.EXAM_STAGE)?.takeIf { it.isNotBlank() }

    /**
     * 空题池归因文案（🔴 2026-09-28 学段筛题的空状态处理）。
     *
     * 判定依据：把同一配置的 `stage` 摘掉再抽一次 ——
     * - 摘掉后**能抽出题** ⇒ 空池完全由学段过滤造成 ⇒ 提示「当前学段暂无题目」并给出切学段的可操作指引；
     * - 摘掉后仍为空 ⇒ 与学段无关（范围本身没题、章节名丢失等）⇒ 沿用原有文案。
     */
    private fun emptyMessage(
        cfg: PracticeConfig,
        all: List<Question>,
        progress: Map<String, ProgressEntity>
    ): String {
        val stage = cfg.stage
        if (stage != null && PracticeEngine.build(all, cfg.copy(stage = null), progress).isNotEmpty()) {
            val other = if (stage == BankStore.STAGE_JUNIOR) BankStore.STAGE_SENIOR else BankStore.STAGE_JUNIOR
            return "当前学段「$stage」在该范围暂无题目，可在「我的」里切到「$other」，或换个范围"
        }
        return "这个范围抽不出题目，换个范围试试"
    }

    /** 统一入口：按配置抽题并开始 */
    fun start(cfg: PracticeConfig) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val progress = loadProgress()
        val all = repo.bank.exam
        // 题型组合全局偏好优先：保证从任何入口（章节/薄弱/错因/随机）进入练习都尊重用户设定
        // 🔴 学段口径：显式配置优先，未指定则回落用户报考学段偏好 ⇒ 七条入口全部自动受限。
        val effective = cfg.copy(
            typeCombo = _practiceType.value,
            stage = cfg.stage ?: currentStage()
        )
        val qs = PracticeEngine.build(all, effective, progress)
        if (qs.isEmpty()) {
            // 🔴 2026-09-25 补（G2）：此前空集静默 return，用户点「继续练习」看到的是「页面纹丝不动」。
            //    空集的两个真实来源：①单章通道重启后章名丢失（已由 PRACTICE_CHAPTER 键修复）
            //    ②该范围确实没题（如科三学科下该章无题）。无论哪种，都必须给出可见反馈。
            // 🔴 2026-09-28 追加第三个来源：学段过滤后无题 ⇒ 文案改为可操作的切学段指引。
            _state.value = PracticeState(mode = cfg.mode, questions = emptyList())
            Toast.makeText(getApplication(), emptyMessage(effective, all, progress), Toast.LENGTH_SHORT).show()
            return@launch
        }
        begin(cfg.mode, qs, timeLimitSec = cfg.timeLimitSec, showAnswer = cfg.showAnswer)
        // 持久化偏好（对齐网页端 S.prefs）
        repo.setMeta(MetaKeys.PRACTICE_MODE, cfg.mode)
        cfg.subj?.let { repo.setMeta(MetaKeys.PRACTICE_SUBJ, it) }
        repo.setMeta(MetaKeys.PRACTICE_NUM, cfg.num.toString())
        repo.setMeta(MetaKeys.PRACTICE_INTERLEAVE, cfg.interleave.toString())
        repo.setMeta(MetaKeys.PRACTICE_SHOW_ANSWER, cfg.showAnswer.toString())
        repo.setMeta(MetaKeys.PRACTICE_CHAPTERS, cfg.chapters.joinToString("|"))
        // 🔴 G2 修复核心：单章通道（startChapter）的 chapter/section/disc 此前不落盘，
        //    重启后 resumeLast() 拿不到章名 ⇒ 题池恒空。空串表示「无此维度」，回读时转 null。
        repo.setMeta(MetaKeys.PRACTICE_CHAPTER, cfg.chapter ?: "")
        repo.setMeta(MetaKeys.PRACTICE_DISC, cfg.disc ?: "")
        repo.setMeta(MetaKeys.PRACTICE_SHUFFLE_OPTIONS, cfg.shuffleOptions.toString())
        repo.setMeta(MetaKeys.PRACTICE_INCLUDE_WRONG, cfg.includeWrong.toString())
        repo.setMeta(MetaKeys.PRACTICE_TYPE, effective.typeCombo)
        _config.value = effective
    }

    /** 继续上次练习 */
    fun resumeLast() = viewModelScope.launch {
        val mode = repo.getMeta(MetaKeys.PRACTICE_MODE) ?: "随机全科"
        val subj = repo.getMeta(MetaKeys.PRACTICE_SUBJ)
        val num = repo.getMeta(MetaKeys.PRACTICE_NUM)?.toIntOrNull() ?: 20
        val interleave = repo.getMeta(MetaKeys.PRACTICE_INTERLEAVE) == "true"
        val showAnswer = repo.getMeta(MetaKeys.PRACTICE_SHOW_ANSWER) == "true"
        val chapters = repo.getMeta(MetaKeys.PRACTICE_CHAPTERS)?.takeIf { it.isNotBlank() }?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        // 🔴 G2 修复核心：回读单章 / 学科。空串 → null，避免把「无此维度」误判成「章名 = 空串」
        //    （题池过滤 it.chapter == "" 仍恒空，等于没修）。
        val chapter = repo.getMeta(MetaKeys.PRACTICE_CHAPTER)?.takeIf { it.isNotBlank() }
        val disc = repo.getMeta(MetaKeys.PRACTICE_DISC)?.takeIf { it.isNotBlank() }
        val shuffleOptions = repo.getMeta(MetaKeys.PRACTICE_SHUFFLE_OPTIONS) == "true"
        val includeWrong = repo.getMeta(MetaKeys.PRACTICE_INCLUDE_WRONG) == "true"
        val typeCombo = repo.getMeta(MetaKeys.PRACTICE_TYPE)?.takeIf { it.isNotBlank() }
            ?.takeIf { it in listOf("choice", "subjective", "all") } ?: "choice"
        _practiceType.value = typeCombo
        start(PracticeConfig(mode = mode, subj = subj, num = num, interleave = interleave, showAnswer = showAnswer, chapters = chapters,
            chapter = chapter, disc = disc,
            shuffleOptions = shuffleOptions, includeWrong = includeWrong))
    }

    /** 清除偏好 */
    fun clearPrefs() = viewModelScope.launch {
        repo.setMeta(MetaKeys.PRACTICE_MODE, "随机全科")
        repo.setMeta(MetaKeys.PRACTICE_SUBJ, "")
        repo.setMeta(MetaKeys.PRACTICE_NUM, "20")
        repo.setMeta(MetaKeys.PRACTICE_INTERLEAVE, "false")
        repo.setMeta(MetaKeys.PRACTICE_SHOW_ANSWER, "false")
        repo.setMeta(MetaKeys.PRACTICE_CHAPTERS, "")
        repo.setMeta(MetaKeys.PRACTICE_CHAPTER, "")
        repo.setMeta(MetaKeys.PRACTICE_DISC, "")
        repo.setMeta(MetaKeys.PRACTICE_SHUFFLE_OPTIONS, "false")
        repo.setMeta(MetaKeys.PRACTICE_INCLUDE_WRONG, "false")
        repo.setMeta(MetaKeys.PRACTICE_TYPE, "choice")
        _practiceType.value = "choice"
        _config.value = PracticeConfig()
    }

    fun startChapter(subject: String, chapter: String, section: String? = null, num: Int = 30, disc: String? = null) {
        start(PracticeConfig(mode = "章节练习", subj = subject, chapter = chapter, section = section, num = num, disc = disc))
    }

    fun startWeak(disc: String) {
        start(PracticeConfig(mode = "薄弱优先", disc = disc))
    }

    fun startWrong(disc: String) {
        // 错题本：取 wrongBook 标记的题（科三按学科隔离）。空集合时提示，避免「点击无反应」。
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val progress = loadProgress()
            // 🔴 学段筛题：错题本同样只显示与报考学段匹配（含通用）的题
            val wrongs = PracticeEngine.wrong(repo.bank.exam, progress)
                .filter { it.subject != "科三" || it.disc == disc }
                .filter { BankStore.stageMatches(it, currentStage()) }
            if (wrongs.isEmpty()) {
                _state.value = _state.value.copy(loading = false)
                Toast.makeText(getApplication(), "当前没有错题，先去练习里标记吧", Toast.LENGTH_SHORT).show()
                return@launch
            }
            start(PracticeConfig(mode = "错题本", disc = disc))
        }
    }

    /** 模考：支持短模考（20/30/50 题 → 40/60/90 分钟） */
    fun startBlueprint(disc: String, count: Int = 50, timeLimitSec: Int = 90 * 60) {
        viewModelScope.launch {
            // 章节权重（来自章节编辑页配置）；为空时蓝图退化为均匀抽取
            val weights = repo.getChapterConfig().mapValues { it.value.weight }
            val stage = currentStage()
            // 🔴 学段筛题：模考蓝图三科题源统一受限（本学段 + 通用题）
            val qs = PracticeEngine.blueprint(repo.bank.exam, disc, count, weights, stage)
            // 题库不足时提示实际抽取数量，避免用户以为满额开考
            if (qs.size < count) {
                Toast.makeText(
                    getApplication(),
                    "题库可用 ${qs.size} 题，已按实际抽取（少于请求 $count 题）",
                    Toast.LENGTH_LONG
                ).show()
            }
            // 🔴 学段筛空（蓝图链路）：直接提示可操作的切学段指引，避免空卷开考
            if (qs.isEmpty()) {
                val other = if (stage == BankStore.STAGE_JUNIOR) BankStore.STAGE_SENIOR else BankStore.STAGE_JUNIOR
                val msg = if (stage != null) {
                    "当前学段「$stage」暂无可用于模考的题目，可在「我的」里切到「$other」"
                } else {
                    "题库暂无可用于模考的题目，请先下载题库"
                }
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                return@launch
            }
            begin("全科模考", qs, timeLimitSec = timeLimitSec)
        }
    }

    fun startCause(cause: String, disc: String) {
        start(PracticeConfig(mode = "错因强化", cause = cause, disc = disc))
    }

    /** 从题库点击某题进入练习：用该题同章节组一套题，避免单题太单薄 */
    fun startByQuestion(q: Question) = viewModelScope.launch {
        // 🔴 学段筛题：若被点题目本身与报考学段不符（历史数据/搜索命中），仍保留该题本身，
        //    但同章节扩充的题池必须受限，避免顺带混入不匹配学段的大量题目。
        val stage = currentStage()
        val pool = repo.questionsByChapter(q.subject, q.chapter).filter {
            q.subject != "科三" || it.disc == q.disc
        }
        val combined = (listOf(q) + pool.filter { it.id != q.id && BankStore.stageMatches(it, stage) }).shuffled()
        val filtered = PracticeEngine.filterByType(combined, _practiceType.value, stage)
        val ordered = (if (filtered.isEmpty()) combined else filtered).take(20)
        begin("章节练习", ordered)
    }

    /** AI 题库练习：优先用用户生成的题（按学科/章节过滤），为空则回落到该科目内置题 */
    fun startUserBank(subject: String, scope: String) = viewModelScope.launch {
        // 🔴 学段筛题：内置题回落后受限（用户自建题无学段字段，恒视作通用题）。
        val stage = currentStage()
        val user = repo.allUserQuestions().map { it.toQuestion() }
        val pool = if (user.isEmpty()) {
            repo.bank.exam.filter { it.subject == subject }
                .filter { BankStore.stageMatches(it, stage) }
        } else {
            user.filter { it.subject == subject && (scope.isBlank() || it.disc == scope || it.chapter == scope) }
                .ifEmpty { user }
        }
        val filtered = PracticeEngine.filterByType(pool, _practiceType.value, stage)
        begin("AI 题库", PracticeEngine.weak(if (filtered.isEmpty()) pool else filtered, loadProgress()))
    }

    private fun begin(mode: String, questions: List<Question>, timeLimitSec: Int? = null, showAnswer: Boolean = false) {
        StudyTimerService.resetAll() // 新开一套练习，专注计时从头计
        _state.value = PracticeState(mode = mode, questions = questions, index = 0, timeLimitSec = timeLimitSec, showAnswer = showAnswer)
        refreshHistoryDraft(0)
        startCapsule() // 小米灵动胶囊：练习开始即上岛（前台计时）
    }

    /** 退出当前练习：停止计时并回到练习首页（已作答进度已在 submit 时逐题落盘，不丢失） */
    fun exitSession() {
        stopCapsule()
        StudyTimerService.resetAll()
        _state.value = PracticeState()
    }

    /** 小米灵动胶囊：启动前台计时服务（系统折叠为顶部胶囊）；异常静默不阻断练习 */
    private fun startCapsule() {
        try {
            val ctx = getApplication<Application>()
            ctx.startForegroundService(Intent(ctx, StudyTimerService::class.java))
        } catch (_: Throwable) { /* 胶囊为增强项，失败不影响练习 */ }
    }

    /** 停止胶囊（练习结束/退出时） */
    private fun stopCapsule() {
        try {
            val ctx = getApplication<Application>()
            ctx.stopService(Intent(ctx, StudyTimerService::class.java))
        } catch (_: Throwable) { /* 同上 */ }
    }

    /** 进入某题时载入历史草稿（来自错题本进度）；错题本重练主观题时预填，避免提交覆盖丢失 */
    private fun refreshHistoryDraft(idx: Int) {
        val q = _state.value.questions.getOrNull(idx) ?: return
        viewModelScope.launch {
            val p = repo.getProgress(q.id)
            val d = p?.draft
            _state.value = _state.value.copy(historyDraft = d)
            if (q.isSubjective && d != null && _state.value.draft.isBlank()) {
                _state.value = _state.value.copy(draft = d)
            }
        }
    }

    // ===== 主观题草稿 / 对答案 =====
    fun setDraft(text: String) {
        if (_state.value.answered) return
        _state.value = _state.value.copy(draft = text)
    }

    /** 收藏 / 取消收藏当前题（写 meta，跨会话保留；与旧版共用 meta 表，互不影响） */
    fun toggleFav(qid: String) = viewModelScope.launch {
        val cur = _favIds.value.toMutableSet()
        if (!cur.add(qid)) cur.remove(qid)
        _favIds.value = cur
        repo.setMeta(MetaKeys.PRACTICE_FAV, cur.joinToString(","))
    }

    /** 把当前题（题干+笔记）存进「收集箱」，复用现有 inbox 表（type=question） */
    fun saveToInbox(q: Question, note: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        repo.upsertInbox(
            InboxEntity(
                id = "q_" + q.id,
                type = "question",
                content = q.q,
                note = note,
                createdAt = now,
                _mt = now
            )
        )
    }

    fun revealAnswer() {
        if (_state.value.answered) return
        _state.value = _state.value.copy(showAnswer = true)
    }

    // ===== 模考倒计时超时：强制交卷 =====
    fun onTimeout() {
        val st = _state.value
        if (st.finished) return
        val q = st.current
        if (q != null && !st.answered) {
            // 未答的当前题记错并落盘（suspend 调用需协程）
            viewModelScope.launch {
                val prev = repo.getProgress(q.id) ?: ProgressEntity(qid = q.id, subject = q.subject, chapter = q.chapter)
                val due = SpacedRepetition.nextDue("wrong", 0)
                repo.upsertProgress(
                    prev.copy(wrong = prev.wrong + 1, due = due, wrongBook = true,
                        lastResult = "wrong", _mt = System.currentTimeMillis())
                )
                updateDaily(false)
            }
        }
        _state.value = st.copy(finished = true)
        stopCapsule()
    }

    fun selectOption(idx: Int) {
        if (_state.value.answered) return
        _state.value = _state.value.copy(selected = idx)
    }

    fun markSubjective(result: String) {
        if (_state.value.answered) return
        _state.value = _state.value.copy(subjectiveResult = result)
    }

    /**
     * 错因标记（**可选动作**，不阻塞「下一题」）。
     *
     * 🔴 2026-09-24 口径变更：原实现要求「答错必须选 ≥1 项错因才能下一题」，
     *   且错因入口在反馈卡中排在解析**之前**、占一整张白底嵌套卡 ⇒ 视觉重量压过解析（本末倒置）。
     *   现改为：错因折叠在解析**之后**、可标可不标；解析成为反馈卡主内容。
     *
     * 因此提交后仍可能再点 chip 补标 ⇒ 必须**实时落库**（提交时的 `submit()` 已过去，
     * 错因不在那一刻的快照里，故此处直接写回进度，避免「标了却没存」）。
     */
    fun toggleCause(cause: String) {
        val st = _state.value
        val now = if (cause in st.causeSelected) st.causeSelected - cause else st.causeSelected + cause
        val q = st.current
        _state.value = st.copy(
            causeSelected = now,
            // 同步内存作答记录 ⇒ 结算页「错因分布」即时准确（答对不记错因）
            results = if (st.answered && q != null && !st.correct) {
                val r = st.results[q.id]
                if (r != null) st.results + (q.id to r.copy(cause = now.toList())) else st.results
            } else st.results
        )
        // 已作答（且答错）后的补标：立刻更新进度中的 cause
        if (st.answered && !st.correct && q != null) {
            viewModelScope.launch {
                val prev = repo.getProgress(q.id) ?: return@launch
                repo.upsertProgress(
                    prev.copy(cause = now.joinToString(","), _mt = System.currentTimeMillis())
                )
            }
        }
    }

    /** 提交当前题：判定对错、落盘进度、更新每日统计 */
    fun submit() = viewModelScope.launch {
        val st = _state.value
        val q = st.current ?: return@launch
        val correct = if (q.isSubjective) {
            st.subjectiveResult == "right"
        } else {
            st.selected == answerIndex(q.answer)
        }
        val cause = if (correct) emptyList() else st.causeSelected.toList()

        // 落盘进度
        val prev = repo.getProgress(q.id) ?: ProgressEntity(qid = q.id, subject = q.subject, chapter = q.chapter)
        val right = prev.right + if (correct) 1 else 0
        val wrong = prev.wrong + if (correct) 0 else 1
        val due = SpacedRepetition.nextDue(if (correct) "right" else "wrong", 0)
        val wrongBook = !correct
        // 主观题草稿随进度落盘，复盘可见当初作答
        val draftToSave = if (q.isSubjective) st.draft else prev.draft
        // 🔴 2026-09-24：错因改为「可选」后，提交时通常**尚未标注**（chips 折叠在解析之后，
        //   提交瞬间 causeSelected 必为空）。此时**不可写空串**，否则会把该题历史错因抹掉——
        //   错因的实际落盘改由 `toggleCause()` 在用户点选时实时完成。
        val causeStr = if (correct || cause.isEmpty()) prev.cause else cause.joinToString(",")
        repo.upsertProgress(
            prev.copy(
                right = right, wrong = wrong, due = due, wrongBook = wrongBook,
                cause = causeStr,
                lastResult = if (correct) "right" else "wrong",
                draft = draftToSave, _mt = System.currentTimeMillis()
            )
        )
        updateDaily(correct)

        _state.value = st.copy(
            answered = true, correct = correct, showAnalysis = true,
            results = st.results + (q.id to AnswerRecord(
                correct = correct, cause = cause, subject = q.subject,
                draft = if (q.isSubjective) st.draft.ifBlank { null } else null,
                selected = if (q.isSubjective) -1 else st.selected
            ))
        )
    }

    /** 下一步：末题则结束 */
    fun next() {
        val st = _state.value
        if (!st.answered) return
        if (st.isLast) {
            _state.value = st.copy(finished = true)
            stopCapsule()
        } else {
            _state.value = st.copy(
                index = st.index + 1, answered = false, selected = -1,
                subjectiveResult = null, correct = false, showAnalysis = false, causeSelected = emptySet(),
                draft = "", showAnswer = false, historyDraft = null
            )
            refreshHistoryDraft(st.index + 1)
        }
    }

    /** 答题卡跳题：跳转到指定下标并还原该题作答态（已答则回显，未答则重置） */
    fun goto(idx: Int) {
        val st = _state.value
        if (idx == st.index || idx !in st.questions.indices) return
        val q = st.questions[idx]
        val r = st.results[q.id]
        val subjectiveResult = if (q.isSubjective) {
            when { r?.correct == true -> "right"; r != null -> "wrong"; else -> null }
        } else null
        _state.value = st.copy(
            index = idx,
            answered = r != null,
            correct = r?.correct ?: false,
            selected = r?.selected ?: -1,
            subjectiveResult = subjectiveResult,
            showAnalysis = r != null,
            causeSelected = if (r != null) r.cause.toSet() else emptySet(),
            draft = if (q.isSubjective) r?.draft ?: "" else "",
            showAnswer = if (q.isSubjective) r != null else false,
            historyDraft = null
        )
        refreshHistoryDraft(idx)
    }

    fun restart() {
        val st = _state.value
        StudyTimerService.resetAll()
        _state.value = st.copy(index = 0, answered = false, selected = -1, subjectiveResult = null,
            correct = false, showAnalysis = false, causeSelected = emptySet(), finished = false,
            results = emptyMap(), draft = "", showAnswer = false)
    }

    private fun updateDaily(correct: Boolean) = viewModelScope.launch {
        val date = todayIso()
        val prev = repo.getDailyStat(date) ?: DailyStatEntity(date = date)
        repo.upsertDailyStat(
            prev.copy(
                right = prev.right + if (correct) 1 else 0,
                wrong = prev.wrong + if (correct) 0 else 1
            )
        )
    }

    /** 结算：正确率 + 主要错因。计算口径下沉 domain/PracticeReport。 */
    fun summary(): Pair<Float, Map<String, Int>> {
        val s = PracticeReport.summary(_state.value.results.values)
        return s.accuracy to s.causeCounts
    }

    /** 模考分科报告：科一/科二/科三 各自 (正确, 总数)。计算口径下沉 domain/PracticeReport。 */
    fun summaryBySubject(): Map<String, Pair<Int, Int>> =
        PracticeReport.bySubject(_state.value.results, _state.value.questions)

    /** 分数预估（百分制）：正确率 × 100，模考用。计算口径下沉 domain/PracticeReport。 */
    fun scoreEstimate(): Int =
        PracticeReport.scoreEstimate(PracticeReport.summary(_state.value.results.values))

    /** 错题清单（题 + 错因），供 AI 讲评使用。计算口径下沉 domain/PracticeReport。 */
    fun wrongItems(): List<Pair<Question, String>> =
        PracticeReport.wrongItems(_state.value.questions, _state.value.results)

    override fun onCleared() {
        super.onCleared()
        stopCapsule() // 退出练习页（含配置/结算）停止胶囊，避免残留前台通知
    }
}
