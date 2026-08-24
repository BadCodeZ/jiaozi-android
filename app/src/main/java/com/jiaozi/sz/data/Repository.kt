package com.jiaozi.sz.data

import com.jiaozi.sz.data.local.DailyStatDao
import kotlinx.coroutines.flow.first
import com.jiaozi.sz.data.local.MetaDao
import com.jiaozi.sz.data.local.ProgressDao
import com.jiaozi.sz.data.local.UserQuestionDao
import com.jiaozi.sz.data.local.DailyStatEntity
import kotlinx.coroutines.flow.map
import com.jiaozi.sz.data.local.MetaEntity
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.local.UserQuestionEntity
import com.jiaozi.sz.data.local.LessonDao
import com.jiaozi.sz.data.local.LessonEntity
import com.jiaozi.sz.data.local.InboxDao
import com.jiaozi.sz.data.local.InboxEntity
import com.jiaozi.sz.data.local.AiChatDao
import com.jiaozi.sz.data.local.AiChatEntity
import kotlinx.coroutines.flow.Flow
import com.jiaozi.sz.data.model.AutoSyllSubj
import com.jiaozi.sz.domain.MergeEngine
import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.data.model.SyllabusSubject
import kotlinx.serialization.json.*

/** 已知设置键 */
object MetaKeys {
    const val THEME = "theme"                 // light / dark / system
    const val THEME_PACK = "theme_pack"        // 美术主题包：默认 / 青 / 墨 / 锦
    const val DYNAMIC_COLOR = "dynamic_color" // 跟随系统壁纸动态取色（小米适配）
    const val FONT_SCALE = "font_scale"       // 字体大小 sm/md/lg/xl
    const val SUBJECT3_DISC = "subject3_disc" // 当前科三学科
    const val CHECKIN_STREAK = "checkin_streak"
    const val LAST_CHECKIN = "last_checkin"  // yyyy-MM-dd
    const val TARGET_DAY = "target_day"      // 教资考试目标日 yyyy-MM-dd（倒计时锚点）
    const val TARGET_SCORE = "target_score"  // 目标估分（百分制，默认 90）
    const val KNOWLEDGE_FAV = "knowledge_fav" // 收藏知识卡 id 集合（逗号分隔）
    const val AI_KEY = "ai_key"
    const val AI_PROVIDER = "ai_provider"     // deepseek / openai / moonshot
    const val AI_MODEL = "ai_model"           // 可选，覆盖默认模型
    const val AI_EXPLAIN_CACHE = "ai_explain_cache" // AI 讲评错因聚合缓存（JSON：ts + causes[{c,n}]）
    const val ISLAND_ENABLED = "island_enabled"      // 灵动岛（上岛）开关
    const val ONBOARDED = "onboarded"                // 首开轻引导是否已展示
    const val SYNC_ENABLED = "sync_enabled"
    // WebDAV 远程同步
    const val WEBDAV_URL = "webdav_url"
    const val WEBDAV_USER = "webdav_user"
    const val WEBDAV_PASS = "webdav_pass"
    const val WEBDAV_DIR = "webdav_dir"
    const val WEBDAV_DIRMODE = "webdav_dirmode"
    // 同步加密与口令（密码仅本地存储，等同 AI Key 处理）
    const val SYNC_ENCRYPT = "sync_encrypt"
    const val SYNC_PASS = "sync_pass"
    // 上次同步信封原样（保活网页端独有集合/字段，避免 App 吞数据）
    const val SYNC_ENV_RAW = "sync_env_raw"
    // 练习偏好
    const val PRACTICE_MODE = "practice_mode"
    const val PRACTICE_SUBJ = "practice_subj"
    const val PRACTICE_NUM = "practice_num"
    const val PRACTICE_INTERLEAVE = "practice_interleave"
}

/**
 * 仓储：内置题库（只读，内存）+ Room（进度/每日统计/设置/用户题库）。
 * 等价原网页的 localStorage 数据层；新增 AI 用户题库与同步信封能力。
 *
 * 启动时预建索引，避免 UI 层每次全量扫描造成卡顿。
 */
class AppRepository(
    val bank: Bank,
    val syllabus: List<SyllabusSubject>,
    val autoSyll: List<AutoSyllSubj>,
    val knowledge: List<Knowledge>,
    private val progressDao: ProgressDao,
    private val dailyStatDao: DailyStatDao,
    private val metaDao: MetaDao,
    private val userQuestionDao: UserQuestionDao,
    private val lessonDao: LessonDao,
    private val inboxDao: InboxDao,
    private val aiChatDao: AiChatDao
) {
    /** 科三学科列表（去重，保持出现顺序） */
    val discList: List<String> =
        bank.exam.filter { it.subject == "科三" }.mapNotNull { it.disc }.distinct()

    /** 预建索引：subject -> List<Question> */
    private val bySubject: Map<String, List<Question>> = bank.exam.groupBy { it.subject }

    /** 预建索引：(subject, chapter) -> List<Question> */
    private val byChapter: Map<Pair<String, String>, List<Question>> =
        bank.exam.groupBy { it.subject to it.chapter }

    /** 预建索引：(subject, chapter, section) -> List<Question> */
    private val bySection: Map<Triple<String, String, String?>, List<Question>> =
        bank.exam.groupBy { Triple(it.subject, it.chapter, it.section) }

    /** 预建索引：科三 (disc, chapter) -> List<Question> */
    private val byDiscChapter: Map<Pair<String?, String>, List<Question>> =
        bank.exam.filter { it.subject == "科三" }.groupBy { it.disc to it.chapter }

    /** 预建索引：id -> Question */
    private val byId: Map<String, Question> = bank.exam.associateBy { it.id }

    fun questionById(id: String): Question? = byId[id]
    fun questionsBySubject(subject: String): List<Question> = bySubject[subject] ?: emptyList()
    fun questionsByChapter(subject: String, chapter: String): List<Question> =
        byChapter[subject to chapter] ?: emptyList()

    fun questionsBySection(subject: String, chapter: String, section: String?): List<Question> =
        bySection[Triple(subject, chapter, section)] ?: emptyList()

    /** 搜索（移动端规模上限固化：最多返回 200，防大数据量卡顿） */
    fun search(query: String, limit: Int = 200): List<Question> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val low = q.lowercase()
        return bank.exam.filter {
            it.q.lowercase().contains(low) ||
                (it.analysis?.lowercase()?.contains(low) == true) ||
                (it.chapter.lowercase().contains(low)) ||
                (it.section?.lowercase()?.contains(low) == true) ||
                (it.opt.lowercase().contains(low))
        }.take(limit)
    }

    /** 按科三学科过滤后的题数 */
    fun countBySubject(subject: String, disc: String? = null): Int =
        if (subject == "科三") {
            bank.exam.count { it.subject == "科三" && it.disc == disc }
        } else {
            bySubject[subject]?.size ?: 0
        }

    /** 某章节（可选节）题数；O(1) 查索引 */
    fun countChapter(subject: String, chapter: String, section: String? = null, disc: String? = null): Int {
        val pool = if (subject == "科三" && disc != null) {
            byDiscChapter[disc to chapter] ?: emptyList()
        } else {
            byChapter[subject to chapter] ?: emptyList()
        }
        return if (section == null) pool.size else pool.count { it.section == section }
    }

    suspend fun getProgress(qid: String): ProgressEntity? = progressDao.get(qid)
    suspend fun upsertProgress(p: ProgressEntity) = progressDao.upsert(p)
    fun wrongBookFlow() = progressDao.wrongBook()
    fun dueFlow(now: Long) = progressDao.due(now)

    suspend fun getDailyStat(date: String): DailyStatEntity? = dailyStatDao.get(date)
    suspend fun upsertDailyStat(d: DailyStatEntity) = dailyStatDao.upsert(d)
    fun recentDailyStat(n: Int) = dailyStatDao.recent(n)

    suspend fun getMeta(key: String): String? = metaDao.get(key)?.value
    suspend fun setMeta(key: String, value: String) = metaDao.upsert(MetaEntity(key, value))

    /** 进度映射（qid -> 进度），供练习引擎与首页读取 */
    suspend fun progressMap(): Map<String, ProgressEntity> =
        progressDao.all().first().associateBy { it.qid }

    /** 进度增量流：Room 写入后自动推送最新映射，首页/统计/练习偏好可订阅实时刷新 */
    fun progressFlow(): kotlinx.coroutines.flow.Flow<Map<String, ProgressEntity>> =
        progressDao.all().map { list -> list.associateBy { e -> e.qid } }

    /** 当前科三学科（与网页端 subj3 对齐） */
    private suspend fun subj3Disc(): String =
        getMeta(MetaKeys.SUBJECT3_DISC) ?: discList.firstOrNull() ?: "美术"

    // —— 用户 AI 题库 ——
    suspend fun allUserQuestions(): List<UserQuestionEntity> = userQuestionDao.all()
    fun userQuestionsFlow() = userQuestionDao.allFlow()
    suspend fun upsertUserQuestion(q: UserQuestionEntity) = userQuestionDao.upsert(q)
    suspend fun deleteUserQuestion(id: String) = userQuestionDao.delete(id)

    // —— 备课（lesson）——
    fun allLessonsFlow(): Flow<List<LessonEntity>> = lessonDao.all()
    suspend fun getLesson(id: String): LessonEntity? = lessonDao.get(id)
    suspend fun upsertLesson(l: LessonEntity) = lessonDao.upsert(l)
    suspend fun deleteLesson(id: String) = lessonDao.delete(id)

    // —— 收集箱（inbox）——
    fun allInboxFlow(): Flow<List<InboxEntity>> = inboxDao.all()
    suspend fun upsertInbox(e: InboxEntity) = inboxDao.upsert(e)
    suspend fun deleteInbox(id: String) = inboxDao.delete(id)

    /**
     * 收集箱 → 练习题（一键引用）。
     * content 支持「题干|~|选项|~|答案」三段结构化；否则整段作为主观题题干。
     * id = "QI" + 收集箱 id（同一收集箱条目重复转换覆盖同一条，不无限增长）。
     * 落为用户题入库 → 可被「题库/练习」抽取，并随 exam 集合上行同步。
     * @param subject 目标科目（科一/科二/科三），默认「未分类」；移动端收集箱转题可选学科（对齐网页端）。
     * @param chapter 目标章节，默认「收集箱」。
     * @param disc 科三学科（仅 subject==科三 时生效），默认 null。
     */
    suspend fun inboxToQuestion(
        e: InboxEntity,
        subject: String = "未分类",
        chapter: String = "收集箱",
        disc: String? = null
    ): String {
        val (q, opt, answer) = parseInboxQuestion(e.content)
        val now = System.currentTimeMillis()
        val id = "QI" + e.id
        upsertUserQuestion(
            UserQuestionEntity(
                id = id,
                subject = subject.ifBlank { "未分类" },
                chapter = chapter.ifBlank { "收集箱" },
                disc = if (subject == "科三") disc else null,
                q = q,
                opt = opt,
                answer = answer,
                analysis = e.note.ifBlank { null },
                _mt = now,
                _del = false
            )
        )
        return id
    }

    private fun parseInboxQuestion(content: String): Triple<String, String, String> {
        val parts = content.split("|~|")
        return if (parts.size >= 3) {
            Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
        } else {
            Triple(content.trim(), "", "")
        }
    }

    // —— AI 帮手对话历史（aichat）——
    fun allAiChatFlow(): Flow<List<AiChatEntity>> = aiChatDao.all()
    suspend fun addAiChat(m: AiChatEntity) = aiChatDao.upsert(m)
    suspend fun clearAiChat() = aiChatDao.clear()

    // —— 校订（待审题，v5.17 质量护栏产物）——
    /**
     * 校订真源（P4-1 闭环 R1）：「已校订」= exam[].flag != '待审'（网页端权威字段）∪ id∈proof_reviewed（App 本地完成标记）双源并集。
     * 内置题只读、不在 user_question 表，其 flag 随信封 exam 集合到达，存于 meta `proof_overrides` 覆盖层。
     */
    private val PROOF_REVIEWED = "proof_reviewed"
    /** 内置题 flag 覆盖层（id -> {flag, flagMsg}），使内置题的校订状态也能跨端一致 */
    private val PROOF_OVERRIDES = "proof_overrides"

    /** 解析内置题 flag 覆盖层 */
    private suspend fun proofOverrides(): MutableMap<String, JsonElement> {
        val raw = getMeta(PROOF_OVERRIDES) ?: return mutableMapOf()
        return try { Json.parseToJsonElement(raw).jsonObject.toMutableMap() }
        catch (_: Exception) { mutableMapOf() }
    }

    /** 待校订池：flag == '待审' 的题（内置 + 用户），覆盖层优先于题自身 flag */
    suspend fun pendingProofQuestions(): List<Question> {
        val ov = proofOverrides()
        val userQs = userQuestionDao.all().map { it.toQuestion() }
        val all = bank.exam + userQs
        return all.filter { (ov[it.id]?.jsonObject?.get("flag")?.jsonPrimitive?.contentOrNull ?: it.flag) == "待审" }
    }

    /** 已在校订页标记「通过」的题 id（本地元数据存储，逗号分隔） */
    private suspend fun proofReviewedSet(): MutableSet<String> =
        (getMeta(PROOF_REVIEWED) ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
    suspend fun isProofReviewed(id: String): Boolean = proofReviewedSet().contains(id)
    suspend fun proofReviewedIds(): Set<String> = proofReviewedSet()

    /** 标记已校订（双写，App→Web 对称）：① 设 flag='已校订'（覆盖层/用户实体，经 exam 集合上行）；② 追加 proof_reviewed 本地缓存 */
    suspend fun markProofReviewed(id: String) {
        val ov = proofOverrides()
        val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf()).apply { put("flag", JsonPrimitive("已校订")) }
        ov[id] = JsonObject(cur)
        setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
        val uq = userQuestionDao.all().firstOrNull { it.id == id }
        if (uq != null) userQuestionDao.upsert(uq.copy(flag = "已校订"))
        val s = proofReviewedSet().apply { add(id) }
        setMeta(PROOF_REVIEWED, s.joinToString(","))
    }

    /** 导入侧：某题 flag 已解决（!= '待审'）时，剪除本地 proof_reviewed 该 id，保持单一真源无 stale */
    private suspend fun pruneProofReviewed(id: String) {
        val s = proofReviewedSet()
        if (s.remove(id)) setMeta(PROOF_REVIEWED, s.joinToString(","))
    }

    suspend fun pendingProofUnreviewed(): List<Question> {
        val done = proofReviewedSet()
        return pendingProofQuestions().filter { it.id !in done }
    }

    /** 错题本：wrongBook 标记的进度对应的题目（科三按学科隔离） */
    suspend fun wrongBookQuestions(disc: String? = null): List<Pair<Question, ProgressEntity>> {
        val wb = progressDao.wrongBook().first()
        val byId = (bank.exam + userQuestionDao.all().map { it.toQuestion() }).associateBy { it.id }
        return wb.mapNotNull { p -> byId[p.qid]?.let { q -> if (disc == null || q.subject != "科三" || q.disc == disc) q to p else null } }
    }

    /** 章节归类：未归类（chapter 为空/未分类/收集箱）的用户题，供校订页手动指派 */
    suspend fun unclassifiedUserQuestions(): List<UserQuestionEntity> =
        userQuestionDao.all().filter { it.chapter.isBlank() || it.chapter == "未分类" || it.chapter == "收集箱" }

    /** 手动指派用户题的科目/章节（与网页端 setCh 一致） */
    suspend fun setUserQuestionChapter(id: String, subject: String, chapter: String) {
        val e = userQuestionDao.all().firstOrNull { it.id == id } ?: return
        userQuestionDao.upsert(e.copy(subject = subject, chapter = chapter, _mt = System.currentTimeMillis()))
    }

    /** 本地质量复核（AI 复核降级版）：挑出解析过短/缺答案的待审题，返回其 id 列表 */
    suspend fun localQualityCheck(limit: Int = 10): List<String> {
        return pendingProofQuestions().filter { q ->
            q.analysis.isNullOrBlank() || q.analysis.length < 6 || q.answer.isBlank()
        }.take(limit).map { it.id }
    }

    // —— 多端同步信封（导出/导入/合并，v2 与网页端互通）——
    suspend fun exportEnvelope(): String {
        val raw = loadRawEnv()
        val built = buildEnvelopeFromLocal(raw)
        return MergeEngine.serialize(built)
    }

    /**
     * 导入远端/文件信封并合并到本地。
     * 1) 解析（校验版本）；2) 与上次原样信封合并（保活未知集合）；3) 持久化合并结果为新原样；
     * 4) 把受管集合映射回本地 DB。返回新增/更新条数。
     */
    suspend fun importEnvelope(json: String): Int {
        val remote = MergeEngine.parse(json)
        val local = loadRawEnv()
        val merged = MergeEngine.merge(local, remote)
        // 回滚防护：若远端 meta 整体替换后丢失本地独有键（如 proof_reviewed 已校订集合），
        // 用本地 Room meta 兜底补回，确保「已校订」状态永不因同步被静默抹掉。
        val fixed = preserveLocalMetaKeys(merged)
        setMeta(MetaKeys.SYNC_ENV_RAW, MergeEngine.serialize(fixed))
        return applyEnvelopeToLocal(fixed)
    }

    /** 合并后，若最终 meta 缺失本地已有的 App 独有键（proof_reviewed 等），用本地值补回 */
    private suspend fun preserveLocalMetaKeys(env: JsonObject): JsonObject {
        val localProof = getMeta(PROOF_REVIEWED) ?: return env
        val meta = (env["meta"] as? JsonObject)?.toMutableMap() ?: return env
        if (!meta.containsKey("proof_reviewed")) {
            meta["proof_reviewed"] = JsonPrimitive(localProof)
            val m = env.toMutableMap()
            m["meta"] = JsonObject(meta)
            return JsonObject(m)
        }
        return env
    }

    /** 读取上次原样信封（缺失则用空信封） */
    private suspend fun loadRawEnv(): JsonObject {
        val raw = getMeta(MetaKeys.SYNC_ENV_RAW)
        return if (raw != null) {
            try { MergeEngine.parse(raw) } catch (_: Exception) { MergeEngine.emptyEnvelope(subj3Disc()) }
        } else MergeEngine.emptyEnvelope(subj3Disc())
    }

    /**
     * 从本地 DB 构建信封：在 raw 基础上叠加 App 受管集合（exam 用户题 / qstat 进度 / corrections 错题本 / meta / prefs），
     * 用 MergeEngine 的集合合并保证「只叠加、不替换」——raw 中网页端独有的 exam/knowledge/lesson 等全部保留。
     */
    private suspend fun buildEnvelopeFromLocal(raw: JsonObject): JsonObject {
        val now = System.currentTimeMillis()
        val built = raw.toMutableMap()

        // exam：仅叠加 App 自有用户题（内置题由代码持有，不导出，避免误删网页端内置题）
        val uqs = allUserQuestions().map { toExamJson(it) }
        val rawExam = raw["exam"] as? JsonArray ?: JsonArray(emptyList())
        built["exam"] = MergeEngine.mergeArrayCollection("exam", rawExam, JsonArray(uqs))

        // qstat：进度统计
        val prog = progressDao.all().first()
        val qstatItems = prog.map { p ->
            buildJsonObject {
                put("id", p.qid); put("right", p.right); put("wrong", p.wrong); put("due", p.due)
                put("_mt", p._mt); put("_del", p._del)
                put("subject", p.subject); put("chapter", p.chapter)
                if (!p.lastResult.isNullOrBlank()) put("lastResult", p.lastResult)
                if (p.cause != null) put("cause", JsonArray(p.cause.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
                if (p.draft.isNotBlank()) put("draft", p.draft)
            }
        }
        val rawQstat = raw["qstat"] as? JsonObject ?: JsonObject(emptyMap())
        val qstatMap = buildJsonObject { qstatItems.forEach { put(it["id"]!!.jsonPrimitive.content, it) } }
        built["qstat"] = MergeEngine.mergeMapCollection(rawQstat, qstatMap)

        // corrections：错题本（仅 wrongBook 的题目）；以 qid 为 key（条目内不含 id 字段）
        val corrMap = prog.filter { it.wrongBook }.associate { p ->
            p.qid to buildJsonObject {
                put("_mt", p._mt); put("wrongBook", true)
                if (p.cause != null) put("cause", JsonArray(p.cause.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
                if (!p.lastResult.isNullOrBlank()) put("lastResult", p.lastResult)
            }
        }
        val rawCorr = raw["corrections"] as? JsonObject ?: JsonObject(emptyMap())
        val corrJson = buildJsonObject { corrMap.forEach { (k, v) -> put(k, v) } }
        built["corrections"] = MergeEngine.mergeMapCollection(rawCorr, corrJson)

        // meta：与网页端逐字段对齐（theme/pack/font/targetDay/_mt + 本地 proof_reviewed）。
        // 从 raw 起步，仅覆盖受管字段，确保网页端独有 meta 键（如未来扩展）无损保留。
        val rawMeta = raw["meta"] as? JsonObject ?: JsonObject(emptyMap())
        val meta = rawMeta.toMutableMap()
        fun metaPut(k: String, v: String?) { if (v != null) meta[k] = JsonPrimitive(v) }
        metaPut("theme", getMeta(MetaKeys.THEME))
        metaPut("pack", getMeta(MetaKeys.THEME_PACK))   // 美术主题包（墨绿/小米蓝/青/墨/锦）
        metaPut("font", getMeta(MetaKeys.FONT_SCALE))   // 字号 sm/md/lg/xl
        metaPut("targetDay", getMeta(MetaKeys.TARGET_DAY))
        metaPut("proof_reviewed", getMeta(PROOF_REVIEWED))
        // 内容级 _mt：仅当受管字段相对 raw 变化时才刷新为 now，否则沿用 raw._mt。
        // 否则每次导出都打 now 会让本端 meta 永远「较新」，导致网页端改了 meta 时手机端无法采纳。
        val metaManaged = listOf("theme", "pack", "font", "targetDay", "proof_reviewed")
        val metaChanged = metaManaged.any { meta[it] != rawMeta[it] }
        val metaMt = if (metaChanged) now else (rawMeta["_mt"] as? JsonPrimitive)?.longOrNull ?: now
        meta["_mt"] = JsonPrimitive(metaMt)
        built["meta"] = JsonObject(meta)

        // prefs：与网页端对齐（practiceMode/lastSubject/_mt）。
        // 从 raw 起步保留网页端独有键（如 proofTab），仅覆盖本端管理的两个字段；
        // _mt 内容级（仅当本端字段变化才刷新），避免本端永远「较新」而覆盖掉网页端的 proofTab。
        val rawPrefs = raw["prefs"] as? JsonObject ?: JsonObject(emptyMap())
        val prefs = rawPrefs.toMutableMap()
        getMeta(MetaKeys.PRACTICE_MODE)?.let { prefs["practiceMode"] = JsonPrimitive(it) }
        getMeta(MetaKeys.PRACTICE_SUBJ)?.let { prefs["lastSubject"] = JsonPrimitive(it) }
        val prefsChanged = prefs["practiceMode"] != rawPrefs["practiceMode"] || prefs["lastSubject"] != rawPrefs["lastSubject"]
        val prefsMt = if (prefsChanged) now else (rawPrefs["_mt"] as? JsonPrimitive)?.longOrNull ?: now
        prefs["_mt"] = JsonPrimitive(prefsMt)
        built["prefs"] = JsonObject(prefs)

        // lesson：备课教案（用户自建，全部导出，按 id+_mt 合并保活网页端独有）
        val lessons = lessonDao.all().first().map { l ->
            buildJsonObject {
                put("id", l.id); put("title", l.title); put("subject", l.subject)
                put("chapter", l.chapter); put("content", l.content)
                put("createdAt", l.createdAt); put("_mt", l._mt)
            }
        }
        val rawLesson = raw["lesson"] as? JsonArray ?: JsonArray(emptyList())
        built["lesson"] = MergeEngine.mergeArrayCollection("lesson", rawLesson, JsonArray(lessons))

        // —— 收集箱(inbox) / AI 对话历史(aiHistory)：按同步契约「留本地」，不写入同步包 ——
        // 与网页端 SYNC_COLS=['exam','knowledge','lesson','corrections','qstat'] 严格对齐，
        // 避免手机端向共享空间写入网页端不识别的字段造成数据漂移。本地 DB 仍为唯一真源；
        // 若远端信封（如未来网页端扩展）携带这些集合，导入时仍会按 id 幂等写入本地，不丢数据。
        built.remove("inbox")
        built.remove("aiHistory")

        built["v"] = JsonPrimitive(MergeEngine.ENVELOPE_VERSION)
        built["createdAt"] = JsonPrimitive(now)
        built["subj3"] = JsonPrimitive(subj3Disc())   // 显式携带学科（科三方向），与网页端信封一致
        return JsonObject(built)
    }

    /** 把合并后信封的受管集合映射回本地 DB；返回新增/更新条数 */
    private suspend fun applyEnvelopeToLocal(env: JsonObject): Int {
        var n = 0
        val builtinIds = bank.exam.map { it.id }.toSet()

        // qstat → 进度统计
        val qstat = env["qstat"] as? JsonObject
        if (qstat != null) {
            for ((qid, v) in qstat) {
                if (v !is JsonObject) continue
                if ((v["_del"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val right = v["right"]?.jsonPrimitive?.intOrNull ?: 0
                val wrong = v["wrong"]?.jsonPrimitive?.intOrNull ?: 0
                val due = v["due"]?.jsonPrimitive?.longOrNull ?: 0
                val _mt = MergeEngine.mtOf(v)
                val existing = getProgress(qid)
                val ent = (existing ?: ProgressEntity(qid = qid)).copy(
                    right = right, wrong = wrong, due = due, _mt = maxOf(existing?._mt ?: 0, _mt)
                )
                upsertProgress(ent); n++
            }
        }

        // corrections → 错题本（wrongBook + 错因并集）
        val corr = env["corrections"] as? JsonObject
        if (corr != null) {
            for ((qid, v) in corr) {
                if (v !is JsonObject) continue
                if ((v["_del"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val existing = getProgress(qid) ?: ProgressEntity(qid = qid)
                val remoteCause = (v["cause"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                val localCause = existing.cause?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val cause = (localCause + remoteCause).toSet().joinToString(",")
                upsertProgress(existing.copy(wrongBook = true, cause = cause.ifBlank { null }, _mt = maxOf(existing._mt, MergeEngine.mtOf(v))))
                n++
            }
        }

        // exam → 用户题入库（含 flag/flagMsg）；内置题只读但 flag 随信封到达，写入覆盖层使其多端一致
        val exam = env["exam"] as? JsonArray
        if (exam != null) {
            val ov = proofOverrides()
            for (e in exam) {
                if (e !is JsonObject) continue
                val id = e["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val flag = e["flag"]?.jsonPrimitive?.contentOrNull
                if (id in builtinIds) {
                    // 内置题：仅同步校订标记到覆盖层（内容由代码持有，不覆盖）
                    val fm = e["flagMsg"]?.jsonPrimitive?.contentOrNull
                    if (flag != null || fm != null) {
                        val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf())
                        if (flag != null) cur["flag"] = JsonPrimitive(flag)
                        if (fm != null) cur["flagMsg"] = JsonPrimitive(fm)
                        ov[id] = JsonObject(cur)
                    }
                    if (flag != null && flag != "待审") pruneProofReviewed(id)
                    continue
                }
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) { deleteUserQuestion(id); n++; continue }
                val uqe = UserQuestionEntity(
                    id = id,
                    subject = e["subject"]?.jsonPrimitive?.contentOrNull ?: "",
                    chapter = e["chapter"]?.jsonPrimitive?.contentOrNull ?: "",
                    section = e["section"]?.jsonPrimitive?.contentOrNull,
                    q = e["q"]?.jsonPrimitive?.contentOrNull ?: "",
                    opt = e["opt"]?.jsonPrimitive?.contentOrNull ?: "",
                    answer = e["answer"]?.jsonPrimitive?.contentOrNull ?: "",
                    analysis = e["analysis"]?.jsonPrimitive?.contentOrNull,
                    disc = e["disc"]?.jsonPrimitive?.contentOrNull,
                    flag = flag,
                    flagMsg = e["flagMsg"]?.jsonPrimitive?.contentOrNull,
                    _mt = MergeEngine.mtOf(e), _del = false
                )
                upsertUserQuestion(uqe); n++
                if (flag != null && flag != "待审") pruneProofReviewed(id)
            }
            setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
        }

        // lesson → 备课（全部导入，非内置用户数据）
        val lessonArr = env["lesson"] as? JsonArray
        if (lessonArr != null) {
            for (e in lessonArr) {
                if (e !is JsonObject) continue
                val id = e["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) { deleteLesson(id); n++; continue }
                upsertLesson(LessonEntity(
                    id = id,
                    title = e["title"]?.jsonPrimitive?.contentOrNull ?: "",
                    subject = e["subject"]?.jsonPrimitive?.contentOrNull ?: "",
                    chapter = e["chapter"]?.jsonPrimitive?.contentOrNull ?: "",
                    content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    createdAt = (e["createdAt"]?.jsonPrimitive?.longOrNull ?: 0),
                    _mt = MergeEngine.mtOf(e)
                )); n++
            }
        }

        // inbox → 收集箱
        val inboxArr = env["inbox"] as? JsonArray
        if (inboxArr != null) {
            for (e in inboxArr) {
                if (e !is JsonObject) continue
                val id = e["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) { deleteInbox(id); n++; continue }
                upsertInbox(InboxEntity(
                    id = id,
                    type = e["type"]?.jsonPrimitive?.contentOrNull ?: "text",
                    content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    note = e["note"]?.jsonPrimitive?.contentOrNull ?: "",
                    createdAt = (e["createdAt"]?.jsonPrimitive?.longOrNull ?: 0),
                    _mt = MergeEngine.mtOf(e)
                )); n++
            }
        }

        // aiHistory → AI 对话历史（按 id 幂等写入）
        val aiArr = env["aiHistory"] as? JsonArray
        if (aiArr != null) {
            for (e in aiArr) {
                if (e !is JsonObject) continue
                val id = e["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) { aiChatDao.delete(id); n++; continue }
                addAiChat(AiChatEntity(
                    id = id,
                    role = e["role"]?.jsonPrimitive?.contentOrNull ?: "assistant",
                    content = e["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    ts = (e["ts"]?.jsonPrimitive?.longOrNull ?: 0),
                    _mt = MergeEngine.mtOf(e)
                )); n++
            }
        }

        // meta → theme / pack / font / targetDay / proof_reviewed（与网页端字段对齐）
        val m = env["meta"] as? JsonObject
        if (m != null) {
            val theme = m["theme"]?.jsonPrimitive?.contentOrNull
            if (theme != null) setMeta(MetaKeys.THEME, theme)
            val pack = m["pack"]?.jsonPrimitive?.contentOrNull
            if (pack != null) setMeta(MetaKeys.THEME_PACK, pack)
            val font = m["font"]?.jsonPrimitive?.contentOrNull
            if (font != null) setMeta(MetaKeys.FONT_SCALE, font)
            val targetDay = m["targetDay"]?.jsonPrimitive?.contentOrNull
            if (targetDay != null) setMeta(MetaKeys.TARGET_DAY, targetDay)
            val pr = m["proof_reviewed"]?.jsonPrimitive?.contentOrNull
            if (pr != null) setMeta(PROOF_REVIEWED, pr)
        }
        // prefs → practiceMode / lastSubject
        val p = env["prefs"] as? JsonObject
        if (p != null) {
            val pm = p["practiceMode"]?.jsonPrimitive?.contentOrNull
            if (pm != null) setMeta(MetaKeys.PRACTICE_MODE, pm)
            val ls = p["lastSubject"]?.jsonPrimitive?.contentOrNull
            if (ls != null) setMeta(MetaKeys.PRACTICE_SUBJ, ls)
        }
        return n
    }

    private fun toExamJson(u: UserQuestionEntity): JsonObject = buildJsonObject {
        put("id", u.id); put("subject", u.subject); put("chapter", u.chapter)
        if (u.section != null) put("section", u.section)
        put("q", u.q); put("opt", u.opt); put("answer", u.answer)
        if (u.analysis != null) put("analysis", u.analysis)
        if (u.disc != null) put("disc", u.disc)
        if (u.flag != null) put("flag", u.flag)
        if (u.flagMsg != null) put("flagMsg", u.flagMsg)
        put("_mt", u._mt); put("_del", u._del)
    }
}

/** 兼容别名：部分模块以 `Repository` 指代 `AppRepository`（类型/构造均可用） */
typealias Repository = AppRepository
