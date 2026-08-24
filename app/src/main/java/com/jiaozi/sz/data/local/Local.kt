package com.jiaozi.sz.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
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
