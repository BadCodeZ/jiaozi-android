package com.jiaozi.sz.data

import com.jiaozi.sz.data.local.*
import com.jiaozi.sz.domain.MergeEngine
import com.jiaozi.sz.util.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/**
 * 同步信封模块：把 [AppRepository] 中「从本地 DB 构建信封」「把合并后信封映射回本地 DB」两大段
 * 抽出为 internal 扩展函数，使 Repository.kt 从上帝对象降级为可读的数据门面。
 * 行为与原实现逐字段一致，仅用手写 JSON 样板替换为 [com.jiaozi.sz.util] 的 opt* helper。
 */

/** 从本地 DB 构建信封（在 raw 基础上叠加 App 受管集合，只叠加不替换） */
internal suspend fun AppRepository.buildEnvelopeFromLocal(raw: JsonObject): JsonObject {
    val now = System.currentTimeMillis()
    val built = raw.toMutableMap()

    // exam：仅叠加 App 自有用户题（内置题由代码持有，不导出）
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
            // 🔴 2026-09-20：同时写网页端方言 n / c（n = 总作答次数、c = 答对次数），
            //      使两种方言在**同一个项内**自洽共存——理由见下方 built["qstat"] 处注释。
            put("n", p.right + p.wrong); put("c", p.right)
            if (!p.lastResult.isNullOrBlank()) put("lastResult", p.lastResult)
            if (p.cause != null) put("cause", JsonArray(p.cause.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
            if (p.draft.isNotBlank()) put("draft", p.draft)
        }
    }
    val rawQstat = raw["qstat"] as? JsonObject ?: JsonObject(emptyMap())
    // ── 🔴 2026-09-20 修复：qstat 同 qid 以本机 DB 为准（raw 独有的 qid 原样保留）───────
    // 旧写法 `MergeEngine.mergeMapCollection(rawQstat, qstatMap)` 存在两个实测缺陷：
    // ① 真机 `progress._mt` **全为 0**（30/30 行，用户从未在安卓端作答过该批题），而
    //    网页端 qstat 项本就不写 `_mt` ⇒ 命中 mergeMapCollection 的「双 0 平局 ⇒ 保留
    //    本地」分支 ⇒ 本机补出的 subject / chapter **永远写不进信封**（实测 30/30 项缺
    //    科/章归属）。
    // ② 反向「远端胜出」同样不可行：DB 项是安卓方言 {right,wrong,due,…}，raw 项是网页
    //    方言 {n,c}，整项覆盖会让网页端 `overallAcc()` 归零、`chapterAcc()` 返回
    //    {n:null, acc:-1}（已用网页端 mergeInto / overallAcc / chapterAcc 真实代码在
    //    Node 下复现：合并前 100 → 合并后 0）。
    // 修法：① 项内并存两种方言（上面已补 n / c），② 同 qid 由本机 DB 覆盖 —— 导出信封
    //    理应反映本机真实状态；raw 中本机没有的 qid 是网页端独有进度，原样保留不丢。
    val qstatOut = rawQstat.toMutableMap()
    qstatItems.forEach { qstatOut[it["id"]!!.jsonPrimitive.content] = it }
    built["qstat"] = JsonObject(qstatOut)

    // corrections：错题本（仅 wrongBook 的题目）
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

    // meta：仅覆盖受管字段，保留网页端独有 meta 键
    val rawMeta = raw["meta"] as? JsonObject ?: JsonObject(emptyMap())
    val meta = rawMeta.toMutableMap()
    fun metaPut(k: String, v: String?) { if (v != null) meta[k] = JsonPrimitive(v) }
    metaPut("theme", getMeta(MetaKeys.THEME))
    metaPut("pack", getMeta(MetaKeys.THEME_PACK))
    metaPut("font", getMeta(MetaKeys.FONT_SCALE))
    metaPut("targetDay", getMeta(MetaKeys.TARGET_DAY))
    metaPut("proof_reviewed", getMeta(PROOF_REVIEWED))
    val cc = serializeChapterConfig(getChapterConfig())
    if (cc != "{}") metaPut(MetaKeys.CHAPTER_CONFIG, cc)
    val metaManaged = listOf("theme", "pack", "font", "targetDay", "proof_reviewed")
    val metaChanged = metaManaged.any { meta[it] != rawMeta[it] }
    val metaMt = if (metaChanged) now else rawMeta.optLong("_mt", now)
    meta["_mt"] = JsonPrimitive(metaMt)
    built["meta"] = JsonObject(meta)

    // prefs：仅覆盖本端管理的两个字段，保留网页端独有键（如 proofTab）
    val rawPrefs = raw["prefs"] as? JsonObject ?: JsonObject(emptyMap())
    val prefs = rawPrefs.toMutableMap()
    getMeta(MetaKeys.PRACTICE_MODE)?.let { prefs["practiceMode"] = JsonPrimitive(it) }
    getMeta(MetaKeys.PRACTICE_SUBJ)?.let { prefs["lastSubject"] = JsonPrimitive(it) }
    val prefsChanged = prefs["practiceMode"] != rawPrefs["practiceMode"] || prefs["lastSubject"] != rawPrefs["lastSubject"]
    val prefsMt = if (prefsChanged) now else rawPrefs.optLong("_mt", now)
    prefs["_mt"] = JsonPrimitive(prefsMt)
    built["prefs"] = JsonObject(prefs)

    // lesson：备课教案（用户自建，全部导出）
    val lessons = lessonDao.all().first().map { l -> lessonToEnvelope(l) }
    val rawLesson = raw["lesson"] as? JsonArray ?: JsonArray(emptyList())
    built["lesson"] = MergeEngine.mergeArrayCollection("lesson", rawLesson, JsonArray(lessons))

    // curric / books：备课资源库
    val curric = curricDao.all().first().map { e ->
        buildJsonObject { put("id", e.id); put("grade", e.grade); put("subject", e.subject); put("topic", e.topic); put("text", e.text); put("_mt", e._mt) }
    }
    val books = bookDao.all().first().map { e ->
        buildJsonObject {
            put("id", e.id); put("grade", e.grade); put("book", e.book); put("unit", e.unit); put("lesson", e.lesson); put("text", e.text); put("_mt", e._mt)
            // 2026-09-22 Room v10 新增（跨端可选字段）：老端读不到会走各自默认值，旧信封零影响
            put("status", e.status); put("author", e.author); put("pages", e.pages); put("sizeBytes", e.sizeBytes); put("ext", e.ext)
        }
    }
    built["curric"] = MergeEngine.mergeArrayCollection("curric", raw["curric"] as? JsonArray ?: JsonArray(emptyList()), JsonArray(curric))
    built["books"] = MergeEngine.mergeArrayCollection("books", raw["books"] as? JsonArray ?: JsonArray(emptyList()), JsonArray(books))

    // 收集箱/AI 对话不写入同步包（留本地）
    built.remove("inbox")
    built.remove("aiHistory")

    built["v"] = JsonPrimitive(MergeEngine.ENVELOPE_VERSION)
    built["createdAt"] = JsonPrimitive(now)
    built["subj3"] = JsonPrimitive(subj3Disc())
    // 校订隐藏集以本地 proof_review 表为准刷新，避免 SYNC_ENV_RAW 冻结导致跨端陈旧
    val metaMut = (built["meta"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
    metaMut["proof_reviewed"] = JsonPrimitive(proofReviewedCsv())
    built["meta"] = JsonObject(metaMut)
    return JsonObject(built)
}

/** 把合并后信封的受管集合映射回本地 DB；返回详细 [MergeReport]（各集合增量 + 冲突 + 最大 _mt） */
internal suspend fun AppRepository.applyEnvelopeToLocal(env: JsonObject): MergeReport {
    val r = MergeReportBuilder()
    val builtinIds = bank.exam.map { it.id }.toSet()
    val existingUq = userQuestionDao.all().associateBy { it.id }

    // ── 🔴 2026-09-20 修复：进度反查补齐 subject / chapter ────────────────────────
    // 现象：真机 `progress` 表 subject / chapter 两列**全为空串**（30/30 行），随后导出信封
    //      的 qstat 项也随之带空值 → 跨端同步出去的进度**丢失科/章归属**。
    // 影响面（已实测核验，勿夸大）：手机端 `ProgressEntity.subject/chapter` **当前无任何 UI
    //      消费点**——图谱热力（GraphScreen.kt:88-97）、章节掌握度、统计页均按 qid 去
    //      `bank.exam` 反查科/章，不读本表两列。故本机页面表现不受影响；受损的是
    //      **信封内容正确性**（网页端/其它端拿到 qstat 时缺少科/章归属）。
    // 根因：本文件三处进度写入（qstat / corrections / exam 错题标记）在 `existing == null`
    //      时只 `ProgressEntity(qid = qid)`，`subject` / `chapter` 落默认空串；导出侧
    //      （L31 `put("subject", p.subject)`）虽正确，但源头已是空值。
    // 修法：以 qid 反查题库（内置题 → `bank.exam`；用户题 → `userQuestionDao`）取
    //      subject / chapter 补齐；已有值优先（不覆盖本地更权威的数据）。
    val builtinMeta = bank.exam.associate { it.id to (it.subject to it.chapter) }
    fun metaOf(qid: String): Pair<String, String>? =
        builtinMeta[qid] ?: existingUq[qid]?.let { it.subject to it.chapter }
    val existingLesson = lessonDao.all().first().associateBy { it.id }
    val existingCurric = curricDao.all().first().associateBy { it.id }
    val existingBook = bookDao.all().first().associateBy { it.id }
    val existingInbox = inboxDao.all().first().associateBy { it.id }
    val existingAi = aiChatDao.all().first().associateBy { it.id }

    // qstat → 进度统计（兼容手机端 {right,wrong,due} 与网页端 {n,c}）
    val qstat = env["qstat"] as? JsonObject
    if (qstat != null) {
        for ((qid, v) in qstat) {
            if (v !is JsonObject) continue
            if (v.optBool("_del")) continue
            val _mt = MergeEngine.mtOf(v)
            val existing = getProgress(qid)
            val right = v.optInt("right", v.optInt("c"))
            val wrong = v.optInt("wrong", run {
                val n = v.optInt("n"); val c = v.optInt("c")
                if (v.containsKey("n") && v.containsKey("c")) n - c else 0
            })
            val ent = (existing ?: ProgressEntity(qid = qid)).copy(
                right = right,
                wrong = wrong,
                due = v.optLong("due"),
                // 🔴 2026-09-20：qid 反查补 subject/chapter（已有值优先）
                subject = existing?.subject?.takeIf { it.isNotEmpty() } ?: metaOf(qid)?.first.orEmpty(),
                chapter = existing?.chapter?.takeIf { it.isNotEmpty() } ?: metaOf(qid)?.second.orEmpty(),
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
            if (v.optBool("_del")) continue
            val _mt = MergeEngine.mtOf(v)
            val existing = getProgress(qid) ?: ProgressEntity(qid = qid)
            val remoteCause = v.optArr("cause")?.strs() ?: emptyList()
            val localCause = existing.cause?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            val cause = (localCause + remoteCause).toSet().joinToString(",")
            upsertProgress(
                existing.copy(
                    wrongBook = true,
                    cause = cause.ifBlank { null },
                    // 🔴 2026-09-20：qid 反查补 subject/chapter（已有值优先）
                    subject = existing.subject.takeIf { it.isNotEmpty() } ?: metaOf(qid)?.first.orEmpty(),
                    chapter = existing.chapter.takeIf { it.isNotEmpty() } ?: metaOf(qid)?.second.orEmpty(),
                    _mt = maxOf(existing._mt, _mt)
                )
            )
            r.corrections++; r.max(_mt)
        }
    }

    // exam → 用户题入库（含 flag/flagMsg）；内置题只读但 flag 随信封到达，写入覆盖层
    val exam = env["exam"] as? JsonArray
    if (exam != null) {
        val ov = proofOverrides()
        for (e in exam) {
            if (e !is JsonObject) continue
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val flag = e.optStr("flag").takeIf { it.isNotEmpty() }
            if (id in builtinIds) {
                val fm = e.optStr("flagMsg").takeIf { it.isNotEmpty() }
                if (flag != null || fm != null) {
                    val cur = (ov[id]?.jsonObject?.toMutableMap() ?: mutableMapOf())
                    if (flag != null) cur["flag"] = JsonPrimitive(flag)
                    if (fm != null) cur["flagMsg"] = JsonPrimitive(fm)
                    ov[id] = JsonObject(cur)
                }
                continue
            }
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingUq.containsKey(id)) { deleteUserQuestion(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingUq.containsKey(id)
            val uqe = UserQuestionEntity(
                id = id,
                subject = e.optStr("subject"),
                chapter = e.optStr("chapter"),
                section = e.optStr("section").takeIf { it.isNotEmpty() },
                q = e.optStr("q"),
                opt = e.optStr("opt"),
                answer = e.optStr("answer"),
                analysis = e.optStr("analysis").takeIf { it.isNotEmpty() },
                disc = e.optStr("disc").takeIf { it.isNotEmpty() },
                flag = flag,
                flagMsg = e.optStr("flagMsg").takeIf { it.isNotEmpty() },
                _mt = _mt, _del = false
            )
            upsertUserQuestion(uqe)
            // 网页端错题标记：exam[].wrongBook=true 或含 cause → 同步到手机端错题本
            val wb = e.optBool("wrongBook")
            val causeArr = e.optArr("cause")?.strs() ?: emptyList()
            if (wb || causeArr.isNotEmpty()) {
                val pg = getProgress(id) ?: ProgressEntity(qid = id)
                val localCause = pg.cause?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                val cause = (localCause + causeArr).toSet().joinToString(",").ifBlank { null }
                // 🔴 2026-09-20：qid 反查补 subject/chapter（已有值优先；用户题直接用本次导入的 uqe）
                upsertProgress(
                    pg.copy(
                        wrongBook = true,
                        cause = cause,
                        subject = pg.subject.takeIf { it.isNotEmpty() } ?: uqe.subject,
                        chapter = pg.chapter.takeIf { it.isNotEmpty() } ?: uqe.chapter,
                        _mt = maxOf(pg._mt, _mt)
                    )
                )
                r.qstat++
            }
            if (existed) r.examUpdated++ else r.examAdded++
            r.max(_mt)
        }
        setMeta(PROOF_OVERRIDES, JsonObject(ov).toString())
    }

    // lesson → 备课
    val lessonArr = env["lesson"] as? JsonArray
    if (lessonArr != null) {
        for (e in lessonArr) {
            if (e !is JsonObject) continue
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingLesson.containsKey(id)) { deleteLesson(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingLesson.containsKey(id)
            upsertLesson(envelopeToLesson(e))
            if (existed) r.lessonUpdated++ else r.lessonAdded++
            r.max(_mt)
        }
    }

    // curric → 课标库
    val curricArr = env["curric"] as? JsonArray
    if (curricArr != null) {
        for (e in curricArr) {
            if (e !is JsonObject) continue
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingCurric.containsKey(id)) { deleteCurric(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingCurric.containsKey(id)
            upsertCurric(CurricEntity(
                id = id,
                grade = e.optStr("grade"),
                subject = e.optStr("subject"),
                topic = e.optStr("topic"),
                text = e.optStr("text"),
                _mt = _mt
            ))
            if (existed) r.curricUpdated++ else r.curricAdded++
            r.max(_mt)
        }
    }

    // books → 教材库
    val booksArr = env["books"] as? JsonArray
    if (booksArr != null) {
        for (e in booksArr) {
            if (e !is JsonObject) continue
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingBook.containsKey(id)) { deleteBook(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingBook.containsKey(id)
            upsertBook(BookEntity(
                id = id,
                grade = e.optStr("grade"),
                book = e.optStr("book"),
                unit = e.optStr("unit"),
                lesson = e.optStr("lesson"),
                text = e.optStr("text"),
                _mt = _mt,
                // 兼容旧信封：缺失时走默认值（optStr 空串 / 数值 0）
                status = e.optStr("status"),
                author = e.optStr("author"),
                pages = (e["pages"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                sizeBytes = (e["sizeBytes"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
                ext = e.optStr("ext")
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
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingInbox.containsKey(id)) { deleteInbox(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingInbox.containsKey(id)
            upsertInbox(InboxEntity(
                id = id,
                type = e.optStr("type", "text"),
                content = e.optStr("content"),
                note = e.optStr("note"),
                createdAt = e.optLong("createdAt"),
                _mt = _mt
            ))
            if (existed) r.inboxUpdated++ else r.inboxAdded++
            r.max(_mt)
        }
    }

    // aiHistory → AI 对话历史
    val aiArr = env["aiHistory"] as? JsonArray
    if (aiArr != null) {
        for (e in aiArr) {
            if (e !is JsonObject) continue
            val id = e.optStr("id"); if (id.isEmpty()) continue
            val _mt = MergeEngine.mtOf(e)
            if (e.optBool("_del")) {
                if (existingAi.containsKey(id)) { aiChatDao.delete(id); r.removed++; r.max(_mt) }
                continue
            }
            val existed = existingAi.containsKey(id)
            addAiChat(AiChatEntity(
                id = id,
                role = e.optStr("role", "assistant"),
                content = e.optStr("content"),
                ts = e.optLong("ts"),
                _mt = _mt
            ))
            if (existed) r.aiHistoryUpdated++ else r.aiHistoryAdded++
            r.max(_mt)
        }
    }

    // meta → theme / pack / font / targetDay / proof_reviewed / chapter_config
    val m = env["meta"] as? JsonObject
    if (m != null) {
        m.optStr("theme").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.THEME, it) }
        m.optStr("pack").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.THEME_PACK, it) }
        m.optStr("font").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.FONT_SCALE, it) }
        m.optStr("targetDay").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.TARGET_DAY, it) }
        m.optStr("proof_reviewed").takeIf { it.isNotEmpty() }?.let { setMeta(PROOF_REVIEWED, it) }
        m.optStr("chapter_config").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.CHAPTER_CONFIG, it) }
    }
    // prefs → practiceMode / lastSubject
    val p = env["prefs"] as? JsonObject
    if (p != null) {
        p.optStr("practiceMode").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.PRACTICE_MODE, it) }
        p.optStr("lastSubject").takeIf { it.isNotEmpty() }?.let { setMeta(MetaKeys.PRACTICE_SUBJ, it) }
    }
    return r.build()
}

/** 用户题 → 信封 exam JSON（仅被 buildEnvelopeFromLocal 使用，随模块抽出） */
private fun AppRepository.toExamJson(u: UserQuestionEntity): JsonObject = buildJsonObject {
    put("id", u.id); put("subject", u.subject); put("chapter", u.chapter)
    if (u.section != null) put("section", u.section)
    put("q", u.q); put("opt", u.opt); put("answer", u.answer)
    if (u.analysis != null) put("analysis", u.analysis)
    if (u.disc != null) put("disc", u.disc)
    if (u.flag != null) put("flag", u.flag)
    if (u.flagMsg != null) put("flagMsg", u.flagMsg)
    put("_mt", u._mt); put("_del", u._del)
}
