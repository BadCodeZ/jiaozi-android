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
import com.jiaozi.sz.data.local.CurricDao
import com.jiaozi.sz.data.local.CurricEntity
import com.jiaozi.sz.data.local.BookDao
import com.jiaozi.sz.data.local.BookEntity
import com.jiaozi.sz.data.local.DocIndexDao
import com.jiaozi.sz.data.local.DocHit
import com.jiaozi.sz.data.local.ProofReviewDao
import com.jiaozi.sz.data.local.ProofReviewEntity
import androidx.sqlite.db.SimpleSQLiteQuery
import com.jiaozi.sz.data.local.InboxDao
import com.jiaozi.sz.data.local.InboxEntity
import com.jiaozi.sz.data.local.AiChatDao
import com.jiaozi.sz.data.local.AiChatEntity
import kotlinx.coroutines.flow.Flow
import com.jiaozi.sz.data.model.AutoSyllSubj
import com.jiaozi.sz.data.model.LessonFields
import com.jiaozi.sz.data.model.LessonTemplate
import com.jiaozi.sz.data.model.json
import com.jiaozi.sz.domain.MergeEngine
import com.jiaozi.sz.domain.BankNormalize
import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Knowledge
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.data.model.SyllabusSubject
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.ListSerializer

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
    // 同步增量水位（P2-C）：上次成功下载/双向合并后写入的最大 _mt，供增量判断与展示
    const val LAST_SYNC_MT = "last_sync_mt"
    const val LAST_SYNC_AT = "last_sync_at"
    // 练习偏好
    const val PRACTICE_MODE = "practice_mode"
    const val PRACTICE_SUBJ = "practice_subj"
    const val PRACTICE_NUM = "practice_num"
    const val PRACTICE_INTERLEAVE = "practice_interleave"
    // 以下三项为练习设置独立页（图 2-3）新增持久化键；旧版本无这些键，读取时按默认处理，完全兼容
    const val PRACTICE_SHOW_ANSWER = "practice_show_answer"   // 答案即时显示（true/false）
    const val PRACTICE_TIMED = "practice_timed"               // 限时模式（true/false，总时长 = num*60s）
    const val PRACTICE_CHAPTERS = "practice_chapters"         // 多选章节（| 分隔），为空表示不限定章节
    // 🔴 2026-09-25 补（G2）：单章「章节练习」通道专用键。
    //    背景：「继续练习」点卡无响应的根因 —— startChapter() 走的是单章 `chapter` 通道
    //    （PracticeEngine.build() 的 `mode == "章节练习"` 分支），但 start() 只持久化了 chapters 多选键，
    //    chapter/disc 落盘即丢 ⇒ 重启后 resumeLast() 读回 mode=章节练习 而 chapter=null ⇒ 题池恒空
    //    ⇒ start() 静默 return ⇒ 页面回落首页、无任何提示。此处补齐单章与科三学科两个键。
    const val PRACTICE_CHAPTER = "practice_chapter"           // 单章（章节练习通道，可空）
    const val PRACTICE_DISC = "practice_disc"                 // 科三学科（章节练习/错题本隔离用，可空）
    const val PRACTICE_SHUFFLE_OPTIONS = "practice_shuffle_options" // 选项乱序（04 号 F3，true/false）
    const val PRACTICE_INCLUDE_WRONG = "practice_include_wrong"     // 混入错题（04 号 F5，true/false）
    const val PRACTICE_FAV = "practice_fav"                   // 收藏题目 id（, 分隔）；旧版无此键 → 空集，兼容
    const val PRACTICE_TYPE = "practice_type"                 // 题型组合：choice / subjective / all（默认 choice）
    // 章节配置：显示名 + 模考权重（key = PracticeEngine.chapterKey(subject, disc, chapter)）
    const val CHAPTER_CONFIG = "chapter_config"
    // 备课用户模板库（JSON 数组：[{id,name,grade,type,fields}]）
    const val LESSON_TEMPLATES = "lesson_templates"
    // Pro 会员（诚信付费）激活状态："true" 表示已激活；不联网验单，靠用户自觉
    const val PRO_ACTIVATED = "pro_activated"
    // 题库外置：首次启动下载引导是否已完成的判据（"true" 后不再强制弹下载页）
    const val BANK_INIT_DONE = "bank_init_done"
    // 🔴 2026-09-28 学段筛题：用户报考学段（"初中" / "高中"）。
    //    取值与 BankStore.STAGE_OPTIONS 一致；键缺失 ⇒ null ⇒ 不启用学段过滤（兼容旧用户与未设置场景）。
    const val EXAM_STAGE = "exam_stage"
    // 🔴 2026-09-28 学段真拆包：本地题库**已下载版本对应的学段**。
    //    与 EXAM_STAGE 不一致 ⇒ 本地题包是旧学段数据，须重下（管理页据此提示「题库需更新」）。
    const val BANK_DOWNLOAD_STAGE = "bank_download_stage"
}

/**
 * 仓储：内置题库（只读，内存）+ Room（进度/每日统计/设置/用户题库）。
 * 等价原网页的 localStorage 数据层；新增 AI 用户题库与同步信封能力。
 *
 * 启动时预建索引，避免 UI 层每次全量扫描造成卡顿。
 */
class AppRepository(
    var bank: Bank,
    val syllabus: List<SyllabusSubject>,
    val autoSyll: List<AutoSyllSubj>,
    val knowledge: List<Knowledge>,
    internal val progressDao: ProgressDao,
    internal val dailyStatDao: DailyStatDao,
    internal val metaDao: MetaDao,
    internal val userQuestionDao: UserQuestionDao,
    internal val lessonDao: LessonDao,
    internal val inboxDao: InboxDao,
    internal val aiChatDao: AiChatDao,
    internal val curricDao: CurricDao,
    internal val bookDao: BookDao,
    internal val docIndexDao: DocIndexDao,
    internal val proofReviewDao: ProofReviewDao,
    /** 同步信封基线的落盘存储（替代 meta 大行；见 [RawEnvStore] 注释） */
    private val rawStore: RawEnvStore
) {
    /**
     * 🔴 P0 章名归一（2026-09-29）：**必须在任何索引/派生属性之前执行**。
     * Kotlin 中 init 块与属性初始化器按文本顺序运行，故本块置于 [discList]/[bySubject] 之前。
     * 归一改的是 `Question.chapter` 本身 ⇒ 下游 4 个直接扫 `repo.bank.exam` 的消费点
     * （BankScreen / GraphScreen / PracticeHomeParts / PracticeSetupSheet）一并生效。
     */
    init {
        bank = BankNormalize.bank(bank)
    }

    /** 科三学科列表（去重，保持出现顺序） */
    var discList: List<String> =
        bank.exam.filter { it.subject == "科三" }.mapNotNull { it.disc }.distinct()

    /** 预建索引：subject -> List<Question> */
    private var bySubject: Map<String, List<Question>> = bank.exam.groupBy { it.subject }

    /** 预建索引：(subject, chapter) -> List<Question> */
    private var byChapter: Map<Pair<String, String>, List<Question>> =
        bank.exam.groupBy { it.subject to it.chapter }

    /** 预建索引：科三 (disc, chapter) -> List<Question> */
    private var byDiscChapter: Map<Pair<String?, String>, List<Question>> =
        bank.exam.filter { it.subject == "科三" }.groupBy { it.disc to it.chapter }

    /**
     * 🔴 2026-09-28 题库外置：换库（首启下载 / 管理页增删科目包）后重建内存题库与全部索引。
     * 旧版 bank 为启动期一次性构造的只读 val；外置后 bank 随下载/移除动态变化，
     * 故改为 var 并在此集中重建，避免散落各处的 `repo.bank.exam` 读点遗漏刷新。
     */
    suspend fun reloadBank(newBank: Bank) {
        // 🔴 P0 章名归一：换库后新数据同样带脏章名（如外置 ke1.json 的「三、教师职业道德」）
        // ⇒ 与构造入口保持同一归一，避免「首启下载的包已归一、后续增删科目又退回脏名」。
        val nb = BankNormalize.bank(newBank)
        bank = nb
        discList = nb.exam.filter { it.subject == "科三" }.mapNotNull { it.disc }.distinct()
        bySubject = nb.exam.groupBy { it.subject }
        byChapter = nb.exam.groupBy { it.subject to it.chapter }
        byDiscChapter = nb.exam.filter { it.subject == "科三" }.groupBy { it.disc to it.chapter }
    }

    fun questionsByChapter(subject: String, chapter: String): List<Question> =
        byChapter[subject to chapter] ?: emptyList()

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
    fun dueFlow(now: Long) = progressDao.due(now)

    suspend fun getDailyStat(date: String): DailyStatEntity? = dailyStatDao.get(date)
    suspend fun upsertDailyStat(d: DailyStatEntity) = dailyStatDao.upsert(d)
    fun recentDailyStat(n: Int) = dailyStatDao.recent(n)

    /**
     * 最早一条每日统计的日期（yyyy-MM-dd）；无记录 → null。
     * 用于「备考天数」这类**累计制**指标（全工程禁用 streak）。
     */
    suspend fun earliestDailyStatDate(): String? = dailyStatDao.earliestDate()

    suspend fun getMeta(key: String): String? = metaDao.get(key)?.value
    suspend fun setMeta(key: String, value: String) = metaDao.upsert(MetaEntity(key, value))

    /** 进度映射（qid -> 进度），供练习引擎与首页读取 */
    suspend fun progressMap(): Map<String, ProgressEntity> =
        progressDao.all().first().associateBy { it.qid }

    /** 进度增量流：Room 写入后自动推送最新映射，首页/统计/练习偏好可订阅实时刷新 */
    fun progressFlow(): kotlinx.coroutines.flow.Flow<Map<String, ProgressEntity>> =
        progressDao.all().map { list -> list.associateBy { e -> e.qid } }

    // —— 章节配置（显示名 + 模考权重）——
    /** 读取章节配置（key = 章节键）。解析失败返回空映射，不阻断启动。 */
    suspend fun getChapterConfig(): Map<String, ChapterCfg> {
        val raw = getMeta(MetaKeys.CHAPTER_CONFIG) ?: return emptyMap()
        return try {
            Json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) ->
                val o = v.jsonObject
                ChapterCfg(
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                    weight = o["weight"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                )
            }
        } catch (_: Exception) { emptyMap() }
    }

    /** 持久化章节配置（整体覆盖，配置类语义） */
    suspend fun saveChapterConfig(map: Map<String, ChapterCfg>) {
        setMeta(MetaKeys.CHAPTER_CONFIG, serializeChapterConfig(map))
    }

    /** 序列化章节配置为 JSON 字符串 */
    internal fun serializeChapterConfig(map: Map<String, ChapterCfg>): String = buildJsonObject {
        map.forEach { (k, v) ->
            put(k, buildJsonObject { put("name", v.name); put("weight", v.weight) })
        }
    }.toString()

    /** 当前科三学科（与网页端 subj3 对齐） */
    internal suspend fun subj3Disc(): String =
        getMeta(MetaKeys.SUBJECT3_DISC) ?: discList.firstOrNull() ?: "美术"

    // —— 用户 AI 题库 ——
    suspend fun allUserQuestions(): List<UserQuestionEntity> = userQuestionDao.all()
    suspend fun upsertUserQuestion(q: UserQuestionEntity) = userQuestionDao.upsert(q)
    suspend fun deleteUserQuestion(id: String) = userQuestionDao.delete(id)

    // —— 备课（lesson）——
    fun allLessonsFlow(): Flow<List<LessonEntity>> = lessonDao.all()
    suspend fun upsertLesson(l: LessonEntity) {
        lessonDao.upsert(l)
        syncDoc("lesson", l.id, l.title, lessonSearchText(l))
    }
    suspend fun deleteLesson(id: String) {
        lessonDao.delete(id)
        unsyncDoc("lesson", id)
    }

    // —— 备课结构化（十二要素）序列化 ——
    /** 把本地 LessonEntity 还原为信封用的完整 lesson 对象（含顶层列 + data 内结构化字段） */
    fun lessonToEnvelope(l: LessonEntity): JsonObject {
        val base = if (l.data.isBlank()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(l.data).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        val m = base.toMutableMap()
        m["id"] = JsonPrimitive(l.id); m["title"] = JsonPrimitive(l.title)
        m["subject"] = JsonPrimitive(l.subject); m["chapter"] = JsonPrimitive(l.chapter)
        m["createdAt"] = JsonPrimitive(l.createdAt); m["_mt"] = JsonPrimitive(l._mt)
        // 旧版纯文本兼容：data 为空且有 content 时并入 body，避免历史教案丢失
        if (l.data.isBlank() && l.content.isNotBlank()) m["body"] = JsonPrimitive(l.content)
        return JsonObject(m)
    }

    /** 信封 lesson 对象 → 本地 LessonEntity（结构化字段整体落入 data，保留 rubric 等未知键） */
    fun envelopeToLesson(e: JsonObject): LessonEntity {
        val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: return LessonEntity(id = "", title = "")
        val m = e.toMutableMap()
        val title = (m.remove("title") as? JsonPrimitive)?.contentOrNull ?: ""
        val subject = (m.remove("subject") as? JsonPrimitive)?.contentOrNull ?: ""
        val chapter = (m.remove("chapter") as? JsonPrimitive)?.contentOrNull ?: ""
        val createdAt = (m.remove("createdAt") as? JsonPrimitive)?.longOrNull ?: 0
        val _mt = (m.remove("_mt") as? JsonPrimitive)?.longOrNull ?: 0
        val content = (m.remove("content") as? JsonPrimitive)?.contentOrNull ?: ""
        if (content.isNotBlank()) m["body"] = JsonPrimitive(content)
        return LessonEntity(id = id, title = title, subject = subject, chapter = chapter, data = JsonObject(m).toString(), content = "", createdAt = createdAt, _mt = _mt)
    }

    /** 解析 data JSON → (结构化字段, 未知键保留)；未知键用于信封无损回写 */
    fun parseLessonData(data: String): Pair<LessonFields, JsonObject> {
        if (data.isBlank()) return LessonFields() to JsonObject(emptyMap())
        return runCatching {
            val obj = Json.parseToJsonElement(data).jsonObject
            val fields = json.decodeFromJsonElement<LessonFields>(obj)
            val managed = setOf("grade", "type", "template", "curric", "textbook", "student", "objective", "keyPoints", "context", "processText", "questionsText", "diff", "method", "prep", "blackboard", "blackboardType", "homework", "reflect", "body", "disc", "tags", "source", "fromExamId")
            val extra = JsonObject(obj.filterKeys { it !in managed })
            fields to extra
        }.getOrElse { LessonFields() to JsonObject(emptyMap()) }
    }

    /** 结构化字段 + 保留未知键 → data JSON */
    fun serializeLessonData(fields: LessonFields, extra: JsonObject): String {
        val base = json.encodeToJsonElement(fields).jsonObject.toMutableMap()
        for ((k, v) in extra) base[k] = v
        return JsonObject(base).toString()
    }

    // —— 课标库 / 教材库（Room 实体，参与信封备份与同步）——
    fun allCurricFlow(): Flow<List<CurricEntity>> = curricDao.all()
    fun allBooksFlow(): Flow<List<BookEntity>> = bookDao.all()
    suspend fun upsertCurric(e: CurricEntity) {
        curricDao.upsert(e)
        syncDoc("curric", e.id, "${e.grade} ${e.subject} ${e.topic}".trim(), e.text)
    }
    suspend fun deleteCurric(id: String) {
        curricDao.delete(id)
        unsyncDoc("curric", id)
    }
    suspend fun upsertBook(e: BookEntity) {
        bookDao.upsert(e)
        syncDoc("books", e.id, "${e.grade} ${e.book} ${e.unit} ${e.lesson}".trim(), e.text)
    }
    suspend fun deleteBook(id: String) {
        bookDao.delete(id)
        unsyncDoc("books", id)
    }

    // —— 全文检索 FTS（B 阶段）——
    /**
     * 把教案实体还原为可检索正文：标题/学科/章节 + 结构化十二要素中的文本字段
     * （教学目标/重难点/情境/过程/提问链/分层/方法/准备/板书/作业/反思/正文）。
     * 既不索引 JSON 键名噪声，也能覆盖纯文本旧版 content。
     */
    private fun lessonSearchText(l: LessonEntity): String {
        val sb = StringBuilder()
        sb.append(l.title).append(' ').append(l.subject).append(' ').append(l.chapter)
        if (l.data.isNotBlank()) {
            val (f, _) = parseLessonData(l.data)
            sb.append(' ').append(f.objective)
            sb.append(' ').append(f.keyPoints.focus).append(' ').append(f.keyPoints.difficult)
            sb.append(' ').append(f.context)
            sb.append(' ').append(f.processText)
            sb.append(' ').append(f.questionsText)
            sb.append(' ').append(f.diff.basic).append(' ').append(f.diff.mid).append(' ').append(f.diff.top)
            sb.append(' ').append(f.method)
            sb.append(' ').append(f.prep)
            sb.append(' ').append(f.blackboard)
            sb.append(' ').append(f.homework)
            sb.append(' ').append(f.reflect)
            sb.append(' ').append(f.body)
        } else {
            sb.append(' ').append(l.content)
        }
        return sb.toString().trim()
    }

    /** 维护 FTS 索引：先删后插（幂等 upsert）；正文为空则不建索引 */
    private suspend fun syncDoc(source: String, sourceId: String, title: String, body: String) {
        docIndexDao.exec(SimpleSQLiteQuery("DELETE FROM doc_index WHERE source = ? AND sourceId = ?", arrayOf(source, sourceId)))
        if (body.isNotBlank()) {
            docIndexDao.insertQ(SimpleSQLiteQuery(
                "INSERT INTO doc_index (source, sourceId, title, body) VALUES (?, ?, ?, ?)",
                arrayOf(source, sourceId, title, body)
            ))
        }
    }

    /** 移除 FTS 索引条目 */
    private suspend fun unsyncDoc(source: String, sourceId: String) {
        docIndexDao.exec(SimpleSQLiteQuery("DELETE FROM doc_index WHERE source = ? AND sourceId = ?", arrayOf(source, sourceId)))
    }

    /**
     * 重建 FTS 索引（迁移/首启时调用）。
     * 仅当索引为空且源数据存在才重算，避免每次启动空跑；幂等、可重复安全调用。
     */
    suspend fun rebuildDocIndex() {
        if (docIndexDao.countQ(SimpleSQLiteQuery("SELECT COUNT(*) FROM doc_index")) > 0) return
        val curric = curricDao.all().first()
        val books = bookDao.all().first()
        val lessons = lessonDao.all().first()
        if (curric.isEmpty() && books.isEmpty() && lessons.isEmpty()) return
        docIndexDao.exec(SimpleSQLiteQuery("DELETE FROM doc_index"))
        for (e in curric) syncDoc("curric", e.id, "${e.grade} ${e.subject} ${e.topic}".trim(), e.text)
        for (e in books) syncDoc("books", e.id, "${e.grade} ${e.book} ${e.unit} ${e.lesson}".trim(), e.text)
        for (e in lessons) syncDoc("lesson", e.id, e.title, lessonSearchText(e))
    }

    /**
     * 构造 FTS MATCH 表达式：按空白分词，各词条加引号作短语检索。
     * 中文（无空格）整词成短语 → 字序邻接匹配（等价子串）；拉丁文按词邻接。
     */
    private fun ftsQuery(raw: String): String {
        val terms = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return ""
        return terms.joinToString(" ") { "\"${it.replace("\"", "")}\"" }
    }

    /** 全文检索：跨源命中（curric/books/lesson），调用方按 source 分流 */
    suspend fun searchDocs(raw: String): List<DocHit> {
        val q = ftsQuery(raw)
        if (q.isBlank()) return emptyList()
        return runCatching { docIndexDao.searchQ(SimpleSQLiteQuery("SELECT source, sourceId, title, body FROM doc_index WHERE doc_index MATCH ?", arrayOf(q))) }.getOrElse { emptyList() }
    }

    /** 全文检索：限定单一来源 */
    suspend fun searchDocs(raw: String, source: String): List<DocHit> {
        val q = ftsQuery(raw)
        if (q.isBlank()) return emptyList()
        return runCatching { docIndexDao.searchInQ(SimpleSQLiteQuery("SELECT source, sourceId, title, body FROM doc_index WHERE doc_index MATCH ? AND source = ?", arrayOf(q, source))) }.getOrElse { emptyList() }
    }

    /**
     * 读取用户选定文件文本：TXT/MD 直接按 UTF-8 读；PDF 用 PdfRenderer 逐页抽取；
     * DOCX 等非支持格式返回 null（UI 提示改用 txt/md/pdf）。
     */
    fun readFileText(ctx: android.content.Context, uri: android.net.Uri): String? {
        return try {
            val tp = ctx.contentResolver.getType(uri) ?: ""
            if (tp == "application/pdf") {
                ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val sb = StringBuilder()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i)?.use { page ->
                            // PdfRenderer.Page.getText() 仅 API 35+(Android 15) 可用；低版本用反射降级为 null（UI 提示改用 txt/md）
                            val t = if (android.os.Build.VERSION.SDK_INT >= 35) {
                                try { android.graphics.pdf.PdfRenderer.Page::class.java.getMethod("getText").invoke(page) as? String } catch (_: Exception) { null }
                            } else null
                            if (!t.isNullOrBlank()) sb.append(t).append("\n")
                        }
                    }
                    renderer.close()
                    sb.toString().trim().ifBlank { null }
                }
            } else {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }?.trim()
            }
        } catch (e: Exception) { null }
    }

    // —— 备课模板库（存 meta）——
    suspend fun getLessonTemplates(): List<LessonTemplate> {
        val s = getMeta(MetaKeys.LESSON_TEMPLATES) ?: return emptyList()
        if (s.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(LessonTemplate.serializer()), s) }.getOrElse { emptyList() }
    }
    suspend fun saveLessonTemplates(list: List<LessonTemplate>) {
        setMeta(MetaKeys.LESSON_TEMPLATES, json.encodeToString(ListSerializer(LessonTemplate.serializer()), list))
    }

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
    internal val PROOF_REVIEWED = "proof_reviewed"
    /** 内置题 flag 覆盖层（id -> {flag, flagMsg}），使内置题的校订状态也能跨端一致 */
    internal val PROOF_OVERRIDES = "proof_overrides"

    /** 解析内置题 flag 覆盖层 */
    internal suspend fun proofOverrides(): MutableMap<String, JsonElement> {
        val raw = getMeta(PROOF_OVERRIDES) ?: return mutableMapOf()
        return try { Json.parseToJsonElement(raw).jsonObject.toMutableMap() }
        catch (_: Exception) { mutableMapOf() }
    }

    /** 待校订池：flag ∈ ('待审','需修正') 的题（内置 + 用户），覆盖层优先于题自身 flag */
    suspend fun pendingProofQuestions(): List<Question> {
        val ov = proofOverrides()
        val userQs = userQuestionDao.all().map { it.toQuestion() }
        val all = bank.exam + userQs
        return all.filter {
            val f = ov[it.id]?.jsonObject?.get("flag")?.jsonPrimitive?.contentOrNull ?: it.flag
            f == "待审" || f == "需修正"
        }
    }

    /**
     * 设置待审题的校订状态（2026-09-19 新增，承载高保真「采纳 / 修正 / 丢弃」三键）。
     *
     * 🔴 实现口径：**复用既有 `proof_overrides` 覆盖层，不新增表、不改 DAO、不动 Room version**。
     *  - `已校订`（采纳）→ 走 [markProofReviewed]（双写 proof_review 表 + 覆盖层），不放这里；
     *  - `需修正`（修正）→ 覆盖层 flag = '需修正'，**仍在待审池内**（[pendingProofQuestions] 已放行该值），
     *    并写入 `flagMsg` 供 UI 显示提示；
     *  - `已丢弃`（丢弃）→ 覆盖层 flag = '已丢弃'，自动移出待审池（池过滤只认 '待审' / '需修正'）。
     *
     * 同步口径：覆盖层经 `PROOF_OVERRIDES` meta 上行（与 markProofReviewed 同一条通路），
     * 故网页端导入导出天然互通，无需改信封结构。
     *
     * @param msg 可选校订提示（仅 flag='需修正' 时使用）
     */
    suspend fun setProofFlag(id: String, flag: String, msg: String? = null) {
        val ov = proofOverrides()
        val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf()).apply {
            put("flag", JsonPrimitive(flag))
            if (msg != null) put("flagMsg", JsonPrimitive(msg))
        }
        ov[id] = JsonObject(cur)
        setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
        val uq = userQuestionDao.all().firstOrNull { it.id == id }
        if (uq != null) userQuestionDao.upsert(uq.copy(flag = flag, flagMsg = msg ?: uq.flagMsg))
    }

    /** 校订页覆盖层标记（供 UI 显示「需修正」等态；键 = qid，值 = flag） */
    suspend fun proofFlagMap(): Map<String, String> =
        proofOverrides().mapNotNull { (k, v) ->
            v.jsonObject["flag"]?.jsonPrimitive?.contentOrNull?.let { k to it }
        }.toMap()

    /** 已在校订页标记「通过」的题 id（P2-B：独立 proof_review 表，不再用 meta 逗号串） */
    private suspend fun proofReviewedSet(): MutableSet<String> =
        proofReviewDao.allQids().toMutableSet()
    suspend fun proofReviewedIds(): Set<String> = proofReviewedSet()
    /** 信封兼容：把本表序列化为 meta `proof_reviewed` 逗号串（导出/导入传输用） */
    internal suspend fun proofReviewedCsv(): String = proofReviewDao.allQids().joinToString(",")

    /** 标记已校订（双写，App→Web 对称）：① 设 flag='已校订'（覆盖层/用户实体，经 exam 集合上行）；② 写入 proof_review 表（本地真源）+ 派生 meta 逗号串 */
    suspend fun markProofReviewed(id: String) {
        val ov = proofOverrides()
        val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf()).apply { put("flag", JsonPrimitive("已校订")) }
        ov[id] = JsonObject(cur)
        setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
        val uq = userQuestionDao.all().firstOrNull { it.id == id }
        if (uq != null) userQuestionDao.upsert(uq.copy(flag = "已校订"))
        val now = System.currentTimeMillis()
        proofReviewDao.upsert(ProofReviewEntity(qid = id, reviewedAt = now, _mt = now))
        setMeta(PROOF_REVIEWED, proofReviewedCsv())
    }

    /** 校订结构化（P2-B 同步）：信封 meta `proof_reviewed` 与本地 proof_review 表并集，双端「已校订」标记均保全 */
    private suspend fun unionProofReviewFromMeta() {
        val remoteIds = (getMeta(PROOF_REVIEWED) ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val localIds = proofReviewDao.allQids().toSet()
        val union = localIds + remoteIds
        val now = System.currentTimeMillis()
        union.forEach { proofReviewDao.upsert(ProofReviewEntity(qid = it, reviewedAt = now, _mt = now)) }
        setMeta(PROOF_REVIEWED, union.joinToString(","))
    }

    /** v8→v9 存量回填：把旧 meta `proof_reviewed` 逗号串迁入新 proof_review 表（仅当表空，避免覆盖新数据） */
    suspend fun migrateProofReviewFromMetaIfNeeded() {
        if (proofReviewDao.allQids().isEmpty()) {
            val ids = (getMeta(PROOF_REVIEWED) ?: "").split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (ids.isNotEmpty()) {
                val now = System.currentTimeMillis()
                ids.forEach { proofReviewDao.upsert(ProofReviewEntity(qid = it, reviewedAt = now, _mt = now)) }
            }
        }
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
     * 4) 把受管集合映射回本地 DB；5) 校订表与信封并集。返回详细 [MergeReport]（各集合增量 + 冲突 + 最大 _mt）。
     */
    suspend fun importEnvelope(json: String): MergeReport {
        // 兼容网页端两种导出：v2 同步包信封 / 「导出备份」S 全量（无 v 自动补打成信封）
        val remote = MergeEngine.parseBackup(json)
        val local = loadRawEnv()
        val merged = MergeEngine.merge(local, remote)
        // 旧 `preserveLocalMetaKeys` 已移除——校订标记改为独立表，
        // 由 unionProofReviewFromMeta 在合并后做双端并集，比「本地强制覆盖远端」更正确。
        // 基线落盘（原先写 meta 单行，信封涨大后会超 CursorWindow 上限导致后续读取抛异常）
        rawStore.write(MergeEngine.serialize(merged))
        val report = applyEnvelopeToLocal(merged)
        // 校订结构化：信封 meta `proof_reviewed` 与本地 proof_review 表并集，双端「已校订」标记均保全
        unionProofReviewFromMeta()
        return report
    }

    /**
     * 读取上次原样信封（缺失则用空信封）。
     *
     * 存储演进：早期存在 meta 表 `sync_env_raw` **单行**里；信封随题量/进度增长到约 1.26 MB 后
     * 触发 `SQLiteBlobTooBigException`（Row too big to fit into CursorWindow），使 exportEnvelope
     * 全线失败。现改为**文件优先**：
     * ① 文件（新路径）有 → 直接用；
     * ② 否则回退读 meta（老路径，兼容未迁移设备），**读成功即迁入文件**并删除 meta 行；
     * ③ 老行**读不出来**（正是该 bug 本身）也删掉它，避免持续占空间、反复抛错。
     * 信封格式与内容均未改动，故**跨端互通不受影响**。
     */
    private suspend fun loadRawEnv(): JsonObject {
        rawStore.read()?.let { s ->
            return runCatching { MergeEngine.parse(s) }
                .getOrElse { MergeEngine.emptyEnvelope(subj3Disc()) }
        }
        // 兼容旧数据：meta 里可能还留着基线（也可能大到读不出来）
        val legacy = runCatching { getMeta(MetaKeys.SYNC_ENV_RAW) }.getOrNull()
        if (!legacy.isNullOrBlank()) {
            rawStore.write(legacy)
            return runCatching { MergeEngine.parse(legacy) }
                .getOrElse { MergeEngine.emptyEnvelope(subj3Disc()) }
        }
        runCatching { metaDao.delete(MetaKeys.SYNC_ENV_RAW) }  // 老行已无用，删掉释放空间
        return MergeEngine.emptyEnvelope(subj3Disc())
    }
}

/** 章节配置：显示名（改名，仅影响 UI 展示，不改题自身 chapter 字段）+ 模考权重（影响蓝图抽题配额） */
data class ChapterCfg(val name: String = "", val weight: Double = 1.0)

/**
 * 同步合并报告（P2-C）：导入信封后各集合的新增/更新/移除与冲突统计，以及本次合并的最大 _mt 水位。
 * - added：本端不存在的 id，新增；
 * - updated：本端已存在且远端 _mt 更新，远端覆盖（即「冲突已按 _mt 较新者胜出」）；
 * - removed：远端墓碑 _del 触发的本地移除；
 * - maxMt：合并涉及的最大 _mt，供增量水位与展示。
 * 注：信封为单一 JSON 文件，仍做全量导出以保证跨端正确；增量体现在「按 _mt 合并 + 本报告 + lastSyncMt 水位」。
 */
data class MergeReport(
    val examAdded: Int = 0, val examUpdated: Int = 0,
    val lessonAdded: Int = 0, val lessonUpdated: Int = 0,
    val curricAdded: Int = 0, val curricUpdated: Int = 0,
    val booksAdded: Int = 0, val booksUpdated: Int = 0,
    val qstat: Int = 0,
    val corrections: Int = 0,
    val inboxAdded: Int = 0, val inboxUpdated: Int = 0,
    val aiHistoryAdded: Int = 0, val aiHistoryUpdated: Int = 0,
    val removed: Int = 0,
    val maxMt: Long = 0
) {
    /** 冲突已解决数 = 各集合「远端覆盖本地」的条目数（_mt 较新者胜出） */
    val conflicts: Int
        get() = examUpdated + lessonUpdated + curricUpdated + booksUpdated +
                inboxUpdated + aiHistoryUpdated + qstat + corrections
    /** 合并总条数（不含移除单独计） */
    val total: Int
        get() = examAdded + examUpdated + lessonAdded + lessonUpdated + curricAdded + curricUpdated +
                booksAdded + booksUpdated + qstat + corrections + inboxAdded + inboxUpdated +
                aiHistoryAdded + aiHistoryUpdated
}

/** [MergeReport] 的内部可变累加器 */
internal class MergeReportBuilder {
    var examAdded = 0; var examUpdated = 0
    var lessonAdded = 0; var lessonUpdated = 0
    var curricAdded = 0; var curricUpdated = 0
    var booksAdded = 0; var booksUpdated = 0
    var qstat = 0; var corrections = 0
    var inboxAdded = 0; var inboxUpdated = 0
    var aiHistoryAdded = 0; var aiHistoryUpdated = 0
    var removed = 0
    var maxMt = 0L
    fun max(m: Long) { if (m > maxMt) maxMt = m }
    fun build() = MergeReport(
        examAdded, examUpdated, lessonAdded, lessonUpdated, curricAdded, curricUpdated,
        booksAdded, booksUpdated, qstat, corrections, inboxAdded, inboxUpdated,
        aiHistoryAdded, aiHistoryUpdated, removed, maxMt
    )
}

/** 兼容别名：部分模块以 `Repository` 指代 `AppRepository`（类型/构造均可用） */
typealias Repository = AppRepository
