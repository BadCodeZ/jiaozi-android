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
    // 章节配置：显示名 + 模考权重（key = PracticeEngine.chapterKey(subject, disc, chapter)）
    const val CHAPTER_CONFIG = "chapter_config"
    // 备课用户模板库（JSON 数组：[{id,name,grade,type,fields}]）
    const val LESSON_TEMPLATES = "lesson_templates"
    // Pro 会员（诚信付费）激活状态："true" 表示已激活；不联网验单，靠用户自觉
    const val PRO_ACTIVATED = "pro_activated"
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
    private val aiChatDao: AiChatDao,
    private val curricDao: CurricDao,
    private val bookDao: BookDao,
    private val docIndexDao: DocIndexDao,
    private val proofReviewDao: ProofReviewDao
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
    private fun serializeChapterConfig(map: Map<String, ChapterCfg>): String = buildJsonObject {
        map.forEach { (k, v) ->
            put(k, buildJsonObject { put("name", v.name); put("weight", v.weight) })
        }
    }.toString()

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

    /** 已在校订页标记「通过」的题 id（P2-B：独立 proof_review 表，不再用 meta 逗号串） */
    private suspend fun proofReviewedSet(): MutableSet<String> =
        proofReviewDao.allQids().toMutableSet()
    suspend fun isProofReviewed(id: String): Boolean = proofReviewedSet().contains(id)
    suspend fun proofReviewedIds(): Set<String> = proofReviewedSet()
    /** 信封兼容：把本表序列化为 meta `proof_reviewed` 逗号串（导出/导入传输用） */
    private suspend fun proofReviewedCsv(): String = proofReviewDao.allQids().joinToString(",")

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
     * 4) 把受管集合映射回本地 DB；5) 校订表与信封并集。返回详细 [MergeReport]（各集合增量 + 冲突 + 最大 _mt）。
     */
    suspend fun importEnvelope(json: String): MergeReport {
        // 兼容网页端两种导出：v2 同步包信封 / 「导出备份」S 全量（无 v 自动补打成信封）
        val remote = MergeEngine.parseBackup(json)
        val local = loadRawEnv()
        val merged = MergeEngine.merge(local, remote)
        // 旧 `preserveLocalMetaKeys` 已移除——校订标记改为独立表，
        // 由 unionProofReviewFromMeta 在合并后做双端并集，比「本地强制覆盖远端」更正确。
        setMeta(MetaKeys.SYNC_ENV_RAW, MergeEngine.serialize(merged))
        val report = applyEnvelopeToLocal(merged)
        // 校订结构化：信封 meta `proof_reviewed` 与本地 proof_review 表并集，双端「已校订」标记均保全
        unionProofReviewFromMeta()
        return report
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
        // 离线样例（flagMsg=='离线样例'）非真实用户题，导出上行前过滤，避免污染网页端题库
        val uqs = allUserQuestions().filter { it.flagMsg != "离线样例" }.map { toExamJson(it) }
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
        // 章节配置（改名/权重）：非空才写入，避免清空信封体积；导入端仅在携带时覆盖本地
        val cc = serializeChapterConfig(getChapterConfig())
        if (cc != "{}") metaPut(MetaKeys.CHAPTER_CONFIG, cc)
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
        val lessons = lessonDao.all().first().map { l -> lessonToEnvelope(l) }
        val rawLesson = raw["lesson"] as? JsonArray ?: JsonArray(emptyList())
        built["lesson"] = MergeEngine.mergeArrayCollection("lesson", rawLesson, JsonArray(lessons))

        // curric / books：备课资源库（与网页端 S.curric / S.books 对齐；泛型合并对网页端无损）
        val curric = curricDao.all().first().map { e ->
            buildJsonObject { put("id", e.id); put("grade", e.grade); put("subject", e.subject); put("topic", e.topic); put("text", e.text); put("_mt", e._mt) }
        }
        val books = bookDao.all().first().map { e ->
            buildJsonObject { put("id", e.id); put("grade", e.grade); put("book", e.book); put("unit", e.unit); put("lesson", e.lesson); put("text", e.text); put("_mt", e._mt) }
        }
        built["curric"] = MergeEngine.mergeArrayCollection("curric", raw["curric"] as? JsonArray ?: JsonArray(emptyList()), JsonArray(curric))
        built["books"] = MergeEngine.mergeArrayCollection("books", raw["books"] as? JsonArray ?: JsonArray(emptyList()), JsonArray(books))

        // —— 收集箱(inbox) / AI 对话历史(aiHistory)：按同步契约「留本地」，不写入同步包 ——
        // 与网页端 SYNC_COLS=['exam','knowledge','lesson','corrections','qstat'] 严格对齐，
        // 避免手机端向共享空间写入网页端不识别的字段造成数据漂移。本地 DB 仍为唯一真源；
        // 若远端信封（如未来网页端扩展）携带这些集合，导入时仍会按 id 幂等写入本地，不丢数据。
        built.remove("inbox")
        built.remove("aiHistory")

        built["v"] = JsonPrimitive(MergeEngine.ENVELOPE_VERSION)
        built["createdAt"] = JsonPrimitive(now)
        built["subj3"] = JsonPrimitive(subj3Disc())   // 显式携带学科（科三方向），与网页端信封一致
        // 校订隐藏集以本地 proof_review 表为准刷新信封 meta，避免 SYNC_ENV_RAW 冻结导致跨端陈旧
        val metaMut = (built["meta"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        metaMut["proof_reviewed"] = JsonPrimitive(proofReviewedCsv())
        built["meta"] = JsonObject(metaMut)
        return JsonObject(built)
    }

    /** 把合并后信封的受管集合映射回本地 DB；返回详细 [MergeReport]（各集合增量 + 冲突 + 最大 _mt） */
    private suspend fun applyEnvelopeToLocal(env: JsonObject): MergeReport {
        val r = MergeReportBuilder()
        val builtinIds = bank.exam.map { it.id }.toSet()
        val existingUq = userQuestionDao.all().associateBy { it.id }
        val existingLesson = lessonDao.all().first().associateBy { it.id }
        val existingCurric = curricDao.all().first().associateBy { it.id }
        val existingBook = bookDao.all().first().associateBy { it.id }
        val existingInbox = inboxDao.all().first().associateBy { it.id }
        val existingAi = aiChatDao.all().first().associateBy { it.id }

        // qstat → 进度统计
        // 兼容两种格式：手机端标准 {right, wrong, due} 和网页端 {n, c}（n=做题次数, c=正确数）
        val qstat = env["qstat"] as? JsonObject
        if (qstat != null) {
            for ((qid, v) in qstat) {
                if (v !is JsonObject) continue
                if ((v["_del"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val _mt = MergeEngine.mtOf(v)
                val existing = getProgress(qid)
                val right = (v["right"] as? JsonPrimitive)?.intOrNull
                    ?: (v["c"] as? JsonPrimitive)?.intOrNull ?: 0
                val wrong = (v["wrong"] as? JsonPrimitive)?.intOrNull
                    ?: ((v["n"] as? JsonPrimitive)?.intOrNull?.let { n -> (v["c"] as? JsonPrimitive)?.intOrNull?.let { c -> n - c } })
                    ?: 0
                val ent = (existing ?: ProgressEntity(qid = qid)).copy(
                    right = right,
                    wrong = wrong,
                    due = (v["due"] as? JsonPrimitive)?.longOrNull ?: 0,
                    _mt = maxOf(existing?._mt ?: 0, _mt)
                )
                upsertProgress(ent); r.qstat++; r.max(_mt)
            }
        }

        // corrections → 错题本（wrongBook + 错因并集）
        val corr = env["corrections"] as? JsonObject
        if (corr != null) {
            for ((qid, v) in corr) {
                if (v !is JsonObject) continue
                if ((v["_del"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val _mt = MergeEngine.mtOf(v)
                val existing = getProgress(qid) ?: ProgressEntity(qid = qid)
                val remoteCause = (v["cause"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                val localCause = existing.cause?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val cause = (localCause + remoteCause).toSet().joinToString(",")
                upsertProgress(existing.copy(wrongBook = true, cause = cause.ifBlank { null }, _mt = maxOf(existing._mt, _mt)))
                r.corrections++; r.max(_mt)
            }
        }

        // exam → 用户题入库（含 flag/flagMsg）；内置题只读但 flag 随信封到达，写入覆盖层使其多端一致
        val exam = env["exam"] as? JsonArray
        if (exam != null) {
            val ov = proofOverrides()
            for (e in exam) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val flag = (e["flag"] as? JsonPrimitive)?.contentOrNull
                if (id in builtinIds) {
                    // 内置题：仅同步校订标记到覆盖层（内容由代码持有，不覆盖）
                    val fm = (e["flagMsg"] as? JsonPrimitive)?.contentOrNull
                    if (flag != null || fm != null) {
                        val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf())
                        if (flag != null) cur["flag"] = JsonPrimitive(flag)
                        if (fm != null) cur["flagMsg"] = JsonPrimitive(fm)
                        ov[id] = JsonObject(cur)
                    }
                    continue
                }
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingUq.containsKey(id)) { deleteUserQuestion(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingUq.containsKey(id)
                val uqe = UserQuestionEntity(
                    id = id,
                    subject = (e["subject"] as? JsonPrimitive)?.contentOrNull ?: "",
                    chapter = (e["chapter"] as? JsonPrimitive)?.contentOrNull ?: "",
                    section = (e["section"] as? JsonPrimitive)?.contentOrNull,
                    q = (e["q"] as? JsonPrimitive)?.contentOrNull ?: "",
                    opt = (e["opt"] as? JsonPrimitive)?.contentOrNull ?: "",
                    answer = (e["answer"] as? JsonPrimitive)?.contentOrNull ?: "",
                    analysis = (e["analysis"] as? JsonPrimitive)?.contentOrNull,
                    disc = (e["disc"] as? JsonPrimitive)?.contentOrNull,
                    flag = flag,
                    flagMsg = (e["flagMsg"] as? JsonPrimitive)?.contentOrNull,
                    _mt = _mt, _del = false
                )
                upsertUserQuestion(uqe)
                // 网页端错题标记：exam[].wrongBook=true 或含 cause → 同步到手机端错题本(ProgressEntity)
                val wb = (e["wrongBook"] as? JsonPrimitive)?.booleanOrNull == true
                val causeArr = (e["cause"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                if (wb || causeArr.isNotEmpty()) {
                    val pg = getProgress(id) ?: ProgressEntity(qid = id)
                    val localCause = pg.cause?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                    val cause = (localCause + causeArr).toSet().joinToString(",").ifBlank { null }
                    upsertProgress(pg.copy(wrongBook = true, cause = cause, _mt = maxOf(pg._mt, _mt)))
                    r.qstat++
                }
                if (existed) r.examUpdated++ else r.examAdded++
                r.max(_mt)
            }
            setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
        }

        // lesson → 备课（全部导入，非内置用户数据）
        val lessonArr = env["lesson"] as? JsonArray
        if (lessonArr != null) {
            for (e in lessonArr) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingLesson.containsKey(id)) { deleteLesson(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingLesson.containsKey(id)
                upsertLesson(envelopeToLesson(e))
                if (existed) r.lessonUpdated++ else r.lessonAdded++
                r.max(_mt)
            }
        }

        // curric → 课标库（备课资源，与网页端 S.curric 对齐；泛型合并无损）
        val curricArr = env["curric"] as? JsonArray
        if (curricArr != null) {
            for (e in curricArr) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingCurric.containsKey(id)) { deleteCurric(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingCurric.containsKey(id)
                upsertCurric(CurricEntity(
                    id = id,
                    grade = (e["grade"] as? JsonPrimitive)?.contentOrNull ?: "",
                    subject = (e["subject"] as? JsonPrimitive)?.contentOrNull ?: "",
                    topic = (e["topic"] as? JsonPrimitive)?.contentOrNull ?: "",
                    text = (e["text"] as? JsonPrimitive)?.contentOrNull ?: "",
                    _mt = _mt
                ))
                if (existed) r.curricUpdated++ else r.curricAdded++
                r.max(_mt)
            }
        }

        // books → 教材库（备课资源，与网页端 S.books 对齐；泛型合并无损）
        val booksArr = env["books"] as? JsonArray
        if (booksArr != null) {
            for (e in booksArr) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingBook.containsKey(id)) { deleteBook(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingBook.containsKey(id)
                upsertBook(BookEntity(
                    id = id,
                    grade = (e["grade"] as? JsonPrimitive)?.contentOrNull ?: "",
                    book = (e["book"] as? JsonPrimitive)?.contentOrNull ?: "",
                    unit = (e["unit"] as? JsonPrimitive)?.contentOrNull ?: "",
                    lesson = (e["lesson"] as? JsonPrimitive)?.contentOrNull ?: "",
                    text = (e["text"] as? JsonPrimitive)?.contentOrNull ?: "",
                    _mt = _mt
                ))
                if (existed) r.booksUpdated++ else r.booksAdded++
                r.max(_mt)
            }
        }

        // inbox → 收集箱
        val inboxArr = env["inbox"] as? JsonArray
        if (inboxArr != null) {
            for (e in inboxArr) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingInbox.containsKey(id)) { deleteInbox(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingInbox.containsKey(id)
                upsertInbox(InboxEntity(
                    id = id,
                    type = (e["type"] as? JsonPrimitive)?.contentOrNull ?: "text",
                    content = (e["content"] as? JsonPrimitive)?.contentOrNull ?: "",
                    note = (e["note"] as? JsonPrimitive)?.contentOrNull ?: "",
                    createdAt = (e["createdAt"] as? JsonPrimitive)?.longOrNull ?: 0,
                    _mt = _mt
                ))
                if (existed) r.inboxUpdated++ else r.inboxAdded++
                r.max(_mt)
            }
        }

        // aiHistory → AI 对话历史（按 id 幂等写入）
        val aiArr = env["aiHistory"] as? JsonArray
        if (aiArr != null) {
            for (e in aiArr) {
                if (e !is JsonObject) continue
                val id = (e["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                val _mt = MergeEngine.mtOf(e)
                if ((e["_del"] as? JsonPrimitive)?.booleanOrNull == true) {
                    if (existingAi.containsKey(id)) { aiChatDao.delete(id); r.removed++; r.max(_mt) }
                    continue
                }
                val existed = existingAi.containsKey(id)
                addAiChat(AiChatEntity(
                    id = id,
                    role = (e["role"] as? JsonPrimitive)?.contentOrNull ?: "assistant",
                    content = (e["content"] as? JsonPrimitive)?.contentOrNull ?: "",
                    ts = (e["ts"] as? JsonPrimitive)?.longOrNull ?: 0,
                    _mt = _mt
                ))
                if (existed) r.aiHistoryUpdated++ else r.aiHistoryAdded++
                r.max(_mt)
            }
        }

        // meta → theme / pack / font / targetDay / proof_reviewed（与网页端字段对齐）
        val m = env["meta"] as? JsonObject
        if (m != null) {
            (m["theme"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.THEME, it) }
            (m["pack"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.THEME_PACK, it) }
            (m["font"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.FONT_SCALE, it) }
            (m["targetDay"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.TARGET_DAY, it) }
            (m["proof_reviewed"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(PROOF_REVIEWED, it) }
            // 章节配置：信封携带时整体覆盖本地（配置类语义，与备份/同步一致）
            (m["chapter_config"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.CHAPTER_CONFIG, it) }
        }
        // prefs → practiceMode / lastSubject
        val p = env["prefs"] as? JsonObject
        if (p != null) {
            (p["practiceMode"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.PRACTICE_MODE, it) }
            (p["lastSubject"] as? JsonPrimitive)?.contentOrNull?.let { setMeta(MetaKeys.PRACTICE_SUBJ, it) }
        }
        return r.build()
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
private class MergeReportBuilder {
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
