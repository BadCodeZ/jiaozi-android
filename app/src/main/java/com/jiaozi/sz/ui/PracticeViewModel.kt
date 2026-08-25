package com.jiaozi.sz.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.xiaomi.StudyTimerService
import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.local.DailyStatEntity
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.domain.PracticeConfig
import com.jiaozi.sz.domain.PracticeEngine
import com.jiaozi.sz.domain.SpacedRepetition
import com.jiaozi.sz.domain.answerIndex
import com.jiaozi.sz.domain.parseOptions
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AnswerRecord(
    val correct: Boolean,
    val cause: List<String>,
    val subject: String = "",   // 所属科目（模考分科报告用）
    val draft: String? = null,  // 主观题作答草稿（复盘可见）
    val selected: Int = -1      // 客观题所选下标（复盘显示"我选了 X"）
)

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

    init {
        viewModelScope.launch {
            val mode = repo.getMeta(MetaKeys.PRACTICE_MODE) ?: "随机全科"
            val subj = repo.getMeta(MetaKeys.PRACTICE_SUBJ)?.takeIf { it.isNotBlank() }
            val num = repo.getMeta(MetaKeys.PRACTICE_NUM)?.toIntOrNull() ?: 20
            val interleave = repo.getMeta(MetaKeys.PRACTICE_INTERLEAVE) == "true"
            _config.value = PracticeConfig(mode = mode, subj = subj, num = num, interleave = interleave)
        }
    }

    private suspend fun loadProgress(): Map<String, ProgressEntity> = repo.progressMap()

    /** 统一入口：按配置抽题并开始 */
    fun start(cfg: PracticeConfig) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        val progress = loadProgress()
        val all = repo.bank.exam
        val qs = PracticeEngine.build(all, cfg, progress)
        if (qs.isEmpty()) {
            _state.value = PracticeState(mode = cfg.mode, questions = emptyList())
            return@launch
        }
        // 持久化偏好（对齐网页端 S.prefs）
        repo.setMeta(MetaKeys.PRACTICE_MODE, cfg.mode)
        cfg.subj?.let { repo.setMeta(MetaKeys.PRACTICE_SUBJ, it) }
        repo.setMeta(MetaKeys.PRACTICE_NUM, cfg.num.toString())
        repo.setMeta(MetaKeys.PRACTICE_INTERLEAVE, cfg.interleave.toString())
        _config.value = cfg
        begin(cfg.mode, qs)
    }

    /** 继续上次练习 */
    fun resumeLast() = viewModelScope.launch {
        val mode = repo.getMeta(MetaKeys.PRACTICE_MODE) ?: "随机全科"
        val subj = repo.getMeta(MetaKeys.PRACTICE_SUBJ)
        val num = repo.getMeta(MetaKeys.PRACTICE_NUM)?.toIntOrNull() ?: 20
        val interleave = repo.getMeta(MetaKeys.PRACTICE_INTERLEAVE) == "true"
        start(PracticeConfig(mode = mode, subj = subj, num = num, interleave = interleave))
    }

    /** 清除偏好 */
    fun clearPrefs() = viewModelScope.launch {
        repo.setMeta(MetaKeys.PRACTICE_MODE, "随机全科")
        repo.setMeta(MetaKeys.PRACTICE_SUBJ, "")
        repo.setMeta(MetaKeys.PRACTICE_NUM, "20")
        repo.setMeta(MetaKeys.PRACTICE_INTERLEAVE, "false")
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
            val wrongs = PracticeEngine.wrong(repo.bank.exam, progress)
                .filter { it.subject != "科三" || it.disc == disc }
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
            val qs = PracticeEngine.blueprint(repo.bank.exam, disc, count, weights)
            // 题库不足时提示实际抽取数量，避免用户以为满额开考
            if (qs.size < count) {
                Toast.makeText(
                    getApplication(),
                    "题库可用 ${qs.size} 题，已按实际抽取（少于请求 $count 题）",
                    Toast.LENGTH_LONG
                ).show()
            }
            begin("全科模考", qs, timeLimitSec = timeLimitSec)
        }
    }

    fun startCause(cause: String, disc: String) {
        start(PracticeConfig(mode = "错因强化", cause = cause, disc = disc))
    }

    /** 从题库点击某题进入练习：用该题同章节组一套题，避免单题太单薄 */
    fun startByQuestion(q: Question) = viewModelScope.launch {
        val pool = repo.questionsByChapter(q.subject, q.chapter).filter {
            q.subject != "科三" || it.disc == q.disc
        }
        val ordered = listOf(q) + pool.filter { it.id != q.id }.shuffled()
        begin("章节练习", ordered.take(20))
    }

    /** AI 题库练习：优先用用户生成的题（按学科/章节过滤），为空则回落到该科目内置题 */
    fun startUserBank(subject: String, scope: String) = viewModelScope.launch {
        val user = repo.allUserQuestions().map { it.toQuestion() }
        val pool = if (user.isEmpty()) {
            repo.bank.exam.filter { it.subject == subject }
        } else {
            user.filter { it.subject == subject && (scope.isBlank() || it.disc == scope || it.chapter == scope) }
                .ifEmpty { user }
        }
        begin("AI 题库", PracticeEngine.weak(pool, loadProgress()))
    }

    private fun begin(mode: String, questions: List<Question>, timeLimitSec: Int? = null) {
        StudyTimerService.resetAll() // 新开一套练习，专注计时从头计
        _state.value = PracticeState(mode = mode, questions = questions, index = 0, timeLimitSec = timeLimitSec)
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

    fun toggleCause(cause: String) {
        val s = _state.value.causeSelected
        _state.value = _state.value.copy(causeSelected = if (cause in s) s - cause else s + cause)
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
        repo.upsertProgress(
            prev.copy(
                right = right, wrong = wrong, due = due, wrongBook = wrongBook,
                cause = if (correct) prev.cause else cause.joinToString(","),
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
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val prev = repo.getDailyStat(date) ?: DailyStatEntity(date = date)
        repo.upsertDailyStat(
            prev.copy(
                right = prev.right + if (correct) 1 else 0,
                wrong = prev.wrong + if (correct) 0 else 1
            )
        )
    }

    /** 结算：正确率 + 主要错因 */
    fun summary(): Pair<Float, Map<String, Int>> {
        val rs = _state.value.results.values
        val total = rs.size
        val right = rs.count { it.correct }
        val acc = if (total == 0) 0f else right.toFloat() / total
        val cause = mutableMapOf<String, Int>()
        for (r in rs) for (c in r.cause) cause[c] = cause.getOrDefault(c, 0) + 1
        return acc to cause
    }

    /** 模考分科报告：科一/科二/科三 各自 (正确, 总数) */
    fun summaryBySubject(): Map<String, Pair<Int, Int>> {
        val out = mutableMapOf<String, Pair<Int, Int>>()
        for ((id, r) in _state.value.results) {
            val subj = r.subject.ifBlank { _state.value.questions.find { it.id == id }?.subject ?: "" }
            if (subj.isBlank()) continue
            val (rt, tot) = out.getOrDefault(subj, 0 to 0)
            out[subj] = (rt + if (r.correct) 1 else 0) to (tot + 1)
        }
        return out
    }

    /** 分数预估（百分制）：正确率 × 100，模考用 */
    fun scoreEstimate(): Int {
        val (acc, _) = summary()
        return (acc * 100).toInt()
    }

    /** 错题清单（题 + 错因），供 AI 讲评使用 */
    fun wrongItems(): List<Pair<Question, String>> {
        val st = _state.value
        return st.questions.mapNotNull { q ->
            val r = st.results[q.id] ?: return@mapNotNull null
            if (r.correct) null else q to r.cause.joinToString("、")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCapsule() // 退出练习页（含配置/结算）停止胶囊，避免残留前台通知
    }
}
