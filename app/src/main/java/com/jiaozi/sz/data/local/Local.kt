package com.jiaozi.sz.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** 单题练习进度（按 qid 持久化；内置题库本身只读，不在此存储题目内容） */
@Serializable
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val qid: String,
    val subject: String = "",
    val chapter: String = "",
    val right: Int = 0,
    val wrong: Int = 0,
    val due: Long = 0,             // 下次复习时间戳(ms)；0 表示未安排
    val wrongBook: Boolean = false,
    val cause: String? = null,     // 错因
    val lastResult: String? = null, // "right" / "wrong"
    val draft: String = "",        // 主观题作答草稿（复盘可见）
    val _mt: Long = 0,
    val _del: Boolean = false
)

/** 每日统计（趋势 / 打卡） */
@Serializable
@Entity(tableName = "daily_stat")
data class DailyStatEntity(
    @PrimaryKey val date: String,  // yyyy-MM-dd
    val right: Int = 0,
    val wrong: Int = 0,
    val minutes: Int = 0
)

/** 键值设置（主题 / 科三学科 / 打卡 / AI Key 等） */
@Serializable
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE qid = :qid")
    suspend fun get(qid: String): ProgressEntity?

    @Upsert
    suspend fun upsert(p: ProgressEntity)

    @Query("SELECT * FROM progress WHERE wrongBook = 1")
    fun wrongBook(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress")
    fun all(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM progress WHERE due <= :now AND due > 0")
    fun due(now: Long): Flow<List<ProgressEntity>>
}

@Dao
interface DailyStatDao {
    @Query("SELECT * FROM daily_stat WHERE date = :date")
    suspend fun get(date: String): DailyStatEntity?

    @Upsert
    suspend fun upsert(d: DailyStatEntity)

    @Query("SELECT * FROM daily_stat")
    fun all(): Flow<List<DailyStatEntity>>

    @Query("SELECT * FROM daily_stat ORDER BY date DESC LIMIT :n")
    fun recent(n: Int): Flow<List<DailyStatEntity>>
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM meta WHERE `key` = :k")
    suspend fun get(k: String): MetaEntity?

    @Upsert
    suspend fun upsert(m: MetaEntity)

    @Query("SELECT * FROM meta")
    fun all(): Flow<List<MetaEntity>>
}

/**
 * 用户 AI 生成的题库（与内置题库分离存储）。
 * 仅保存题目内容 + 元数据；练习时映射回 Question 与内置题统一处理。
 */
@Serializable
@Entity(tableName = "user_question")
data class UserQuestionEntity(
    @PrimaryKey val id: String,
    val subject: String = "",
    val chapter: String = "",
    val section: String? = null,
    val q: String,
    val opt: String = "",          // 选项文本；主观题为空
    val answer: String = "",       // 客观题答案；主观题可空
    val analysis: String? = null,
    val disc: String? = null,       // 科三学科
    val flag: String? = null,       // 校订标记：待审 / 已校订（与网页端 exam[].flag 对齐，单一真源）
    val flagMsg: String? = null,    // 校订提示
    val _mt: Long = 0,
    val _del: Boolean = false
) {
    fun toQuestion(): com.jiaozi.sz.data.model.Question = com.jiaozi.sz.data.model.Question(
        id = id, subject = subject, chapter = chapter, section = section,
        q = q, opt = opt, answer = answer, analysis = analysis,
        disc = disc, flag = flag, flagMsg = flagMsg, _init = false, _mt = _mt, _del = _del
    )
}

@Dao
interface UserQuestionDao {
    @Query("SELECT * FROM user_question")
    suspend fun all(): List<UserQuestionEntity>

    @Query("SELECT * FROM user_question")
    fun allFlow(): Flow<List<UserQuestionEntity>>

    @Upsert
    suspend fun upsert(q: UserQuestionEntity)

    @Query("DELETE FROM user_question WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * 备课教案（用户自建，可同步到网页端 `lesson` 集合）。
 */
@Serializable
@Entity(tableName = "lesson")
data class LessonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subject: String = "",
    val chapter: String = "",
    /** 结构化十二要素 JSON（与网页端 lesson 对象形状一致）；旧版纯文本迁至 body 字段 */
    val data: String = "",
    val content: String = "",
    val createdAt: Long = 0,
    val _mt: Long = 0
)

@Dao
interface LessonDao {
    @Query("SELECT * FROM lesson ORDER BY createdAt DESC")
    fun all(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lesson WHERE id = :id")
    suspend fun get(id: String): LessonEntity?

    @Upsert
    suspend fun upsert(l: LessonEntity)

    @Query("DELETE FROM lesson WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * 课标库条目（用户导入的结构化索引：学段 / 学科 / 主题 / 原文）。
 * 与网页端 `S.curric` 集合对齐，参与信封备份与同步。
 */
@Serializable
@Entity(tableName = "curric")
data class CurricEntity(
    @PrimaryKey val id: String,
    val grade: String = "",
    val subject: String = "",
    val topic: String = "",
    val text: String = "",
    val _mt: Long = 0
)

@Dao
interface CurricDao {
    @Query("SELECT * FROM curric ORDER BY _mt DESC")
    fun all(): Flow<List<CurricEntity>>

    @Query("SELECT * FROM curric WHERE id = :id")
    suspend fun get(id: String): CurricEntity?

    @Upsert
    suspend fun upsert(e: CurricEntity)

    @Query("DELETE FROM curric WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * 教材库条目（用户导入的结构化索引：年级 / 册 / 单元 / 课文 / 原文）。
 * 与网页端 `S.books` 集合对齐，全文检索（B 阶段）在此之上建 FTS 虚表。
 */
@Serializable
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val grade: String = "",
    val book: String = "",
    val unit: String = "",
    val lesson: String = "",
    val text: String = "",
    val _mt: Long = 0
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY _mt DESC")
    fun all(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Upsert
    suspend fun upsert(e: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * 收集箱（临时素材：文本 / 链接 / 题干；可同步到网页端 `inbox` 集合）。
 */
@Serializable
@Entity(tableName = "inbox")
data class InboxEntity(
    @PrimaryKey val id: String,
    val type: String = "text",   // text / link / question
    val content: String,
    val note: String = "",
    val createdAt: Long = 0,
    val _mt: Long = 0
)

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox ORDER BY createdAt DESC")
    fun all(): Flow<List<InboxEntity>>

    @Upsert
    suspend fun upsert(e: InboxEntity)

    @Query("DELETE FROM inbox WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * AI 帮手对话历史（user/assistant 轮次；可同步到网页端 `aiHistory` 集合）。
 */
@Serializable
@Entity(tableName = "aichat")
data class AiChatEntity(
    @PrimaryKey val id: String,
    val role: String,            // user / assistant
    val content: String,
    val ts: Long = 0,
    val _mt: Long = 0
)

@Dao
interface AiChatDao {
    @Query("SELECT * FROM aichat ORDER BY ts ASC")
    fun all(): Flow<List<AiChatEntity>>

    @Upsert
    suspend fun upsert(m: AiChatEntity)

    @Query("DELETE FROM aichat WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM aichat")
    suspend fun clear()
}

/**
 * 校订结构化（P2-B）：待审页「已校订」本地标记。
 * 原为 meta `proof_reviewed` 逗号串（单格、不可索引、易随同步被整体替换），
 * 现独立成表，支持按 qid 索引、与信封 meta 双向同步、可扩展记录校订时间。
 * 本地真源 = 本表；信封 meta `proof_reviewed` 仅作为跨端传输序列化形式。
 */
@Entity(tableName = "proof_review")
data class ProofReviewEntity(
    @PrimaryKey val qid: String,
    val reviewedAt: Long = 0,
    val _mt: Long = 0
)

@Dao
interface ProofReviewDao {
    @Query("SELECT qid FROM proof_review")
    suspend fun allQids(): List<String>

    @Query("SELECT * FROM proof_review")
    fun allFlow(): Flow<List<ProofReviewEntity>>

    @Upsert
    suspend fun upsert(e: ProofReviewEntity)

    @Query("DELETE FROM proof_review WHERE qid = :qid")
    suspend fun delete(qid: String)

    @Query("DELETE FROM proof_review")
    suspend fun clear()
}

/**
 * 全文检索索引（B 阶段）：对课标库 / 教材库 / 教案正文建 FTS4 虚表。
 *
 * 设计说明：Room 的 `@Fts4` 注解实体在部分 Kotlin/Room 版本组合下会因 tokenizer 常量
 * 解析触发 KSP `MissingType` 失败，故此处**不把 doc_index 注册为 Room 实体**——
 * 表由 `MIGRATION_7_8`（升级）与 `RoomDatabase.Callback.onCreate`（全新安装）显式
 * `CREATE VIRTUAL TABLE ... USING fts4(..., tokenize=unicode61)` 建立，`DocIndexDao`
 * 用 `@RawQuery` 访问（绕过 Room 编译期校验，运行时直接走 SQLite FTS）。
 *
 * - `source` ∈ {curric, books, lesson}：关联原实体类型；
 * - `sourceId`：关联原实体主键（非唯一，靠 source+sourceId 定位删除）；
 * - `title` / `body`：可检索标题与正文（body 含课标/教材原文或教案结构化文本）。
 *
 * `unicode61` 分词器对中文按单字建索引，配合短语查询（词条加引号）可实现可用的中文
 * 全文检索（字序邻接，等价于子串匹配）。rowid 由 SQLite 自赋（FTS4 隐式主键）。
 */
/** 检索命中（非实体 POJO，由 DocIndexDao 的 SELECT 直接映射） */
data class DocHit(
    val source: String = "",
    val sourceId: String = "",
    val title: String = "",
    val body: String = ""
)

@Dao
interface DocIndexDao {
    /** 写入一条索引（返回自赋 rowid）；参数经 SimpleSQLiteQuery 绑定 */
    @RawQuery
    suspend fun insertQ(query: SupportSQLiteQuery): Long

    /** 执行 DELETE / CREATE 等语句，返回受影响行数（CREATE 返回 0） */
    @RawQuery
    suspend fun exec(query: SupportSQLiteQuery): Int

    /** 跨源全文检索（MATCH 短语，由调用方分源过滤） */
    @RawQuery
    suspend fun searchQ(query: SupportSQLiteQuery): List<DocHit>

    /** 限定单一来源的全文检索 */
    @RawQuery
    suspend fun searchInQ(query: SupportSQLiteQuery): List<DocHit>

    /** 当前索引条数 */
    @RawQuery
    suspend fun countQ(query: SupportSQLiteQuery): Int
}
