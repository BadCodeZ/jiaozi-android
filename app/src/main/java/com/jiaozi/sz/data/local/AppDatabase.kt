package com.jiaozi.sz.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [
        ProgressEntity::class, DailyStatEntity::class, MetaEntity::class, UserQuestionEntity::class,
        LessonEntity::class, InboxEntity::class, AiChatEntity::class, CurricEntity::class, BookEntity::class,
        ProofReviewEntity::class, WeaknessEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun metaDao(): MetaDao
    abstract fun userQuestionDao(): UserQuestionDao
    abstract fun lessonDao(): LessonDao
    abstract fun inboxDao(): InboxDao
    abstract fun aiChatDao(): AiChatDao
    abstract fun curricDao(): CurricDao
    abstract fun bookDao(): BookDao
    abstract fun docIndexDao(): DocIndexDao
    abstract fun proofReviewDao(): ProofReviewDao
    abstract fun weaknessDao(): WeaknessDao

    companion object {
        /** v2→v3：progress 表新增 draft 列（主观题草稿），保留既有进度 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3→v4：新增 lesson / inbox / aichat 三表（备课 / 收集箱 / AI 帮手），保留既有进度 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `lesson` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `subject` TEXT NOT NULL DEFAULT '',
                        `chapter` TEXT NOT NULL DEFAULT '', `content` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0, `_mt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `inbox` (
                        `id` TEXT NOT NULL, `type` TEXT NOT NULL DEFAULT 'text', `content` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL DEFAULT 0,
                        `_mt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `aichat` (
                        `id` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL,
                        `ts` INTEGER NOT NULL DEFAULT 0, `_mt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`))"""
                )
            }
        }

        /** v4→v5：user_question 表新增 flag / flagMsg 列（校订标记，对齐网页端 exam[].flag），保留既有用户题 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_question` ADD COLUMN `flag` TEXT")
                db.execSQL("ALTER TABLE `user_question` ADD COLUMN `flagMsg` TEXT")
            }
        }

        /** v5→v6：lesson 表新增 data 列（结构化十二要素 JSON），保留既有纯文本教案 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `lesson` ADD COLUMN `data` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v6→v7：新增 curric / books 两表（课标库 / 教材库全文），保留既有数据 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `curric` (
                        `id` TEXT NOT NULL, `grade` TEXT NOT NULL DEFAULT '', `subject` TEXT NOT NULL DEFAULT '',
                        `topic` TEXT NOT NULL DEFAULT '', `text` TEXT NOT NULL DEFAULT '', `_mt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`))"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `books` (
                        `id` TEXT NOT NULL, `grade` TEXT NOT NULL DEFAULT '', `book` TEXT NOT NULL DEFAULT '',
                        `unit` TEXT NOT NULL DEFAULT '', `lesson` TEXT NOT NULL DEFAULT '', `text` TEXT NOT NULL DEFAULT '',
                        `_mt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))"""
                )
            }
        }

        /**
         * v7→v8：新增 doc_index FTS4 虚表（全文检索 B 阶段），索引课标库 / 教材库 / 教案正文。
         * unicode61 分词器对中文按字建索引；保留既有数据（索引由应用层 rebuildDocIndex 回填）。
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE VIRTUAL TABLE IF NOT EXISTS `doc_index` USING fts4(
                        `source` TEXT, `sourceId` TEXT, `title` TEXT, `body` TEXT,
                        tokenize=unicode61)"""
                )
            }
        }

        /**
         * v8→v9：新增 proof_review 表（校订结构化，替代 meta `proof_reviewed` 逗号串）。
         * 存量用户数据无损：旧 proof_reviewed 逗号串在首次导入/启动时由 Repository 回填本表。
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `proof_review` (
                        `qid` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL DEFAULT 0,
                        `_mt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`qid`))"""
                )
            }
        }

        /**
         * v9→v10：books 表按 08 号高保真补齐 5 列（status / author / pages / sizeBytes / ext）。
         * 全部带默认值 ⇒ 存量教材数据零丢失；`status` 留空，由 UI 按 text 有无代理推断。
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `books` ADD COLUMN `status` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `author` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `pages` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `books` ADD COLUMN `ext` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v10→v11：新增 weakness 表（薄弱点攻坚笔记，独立模块，与备课教案分家）。
         * 全部列带默认值 ⇒ 存量数据无损；仅本地存储，不参与信封跨端同步。
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `weakness` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `subject` TEXT NOT NULL DEFAULT '',
                        `chapter` TEXT NOT NULL DEFAULT '', `disc` TEXT NOT NULL DEFAULT '',
                        `body` TEXT NOT NULL DEFAULT '', `fromExamId` TEXT NOT NULL DEFAULT '',
                        `createdAt` INTEGER NOT NULL DEFAULT 0, `_mt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`))"""
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun build(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jiaozi_exam.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        /** 全新安装（v8 直接建库）时，Room 不会建非实体表，这里补建 FTS 虚表 */
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                """CREATE VIRTUAL TABLE IF NOT EXISTS `doc_index` USING fts4(
                                    `source` TEXT, `sourceId` TEXT, `title` TEXT, `body` TEXT,
                                    tokenize=unicode61)"""
                            )
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
