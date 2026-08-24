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
        LessonEntity::class, InboxEntity::class, AiChatEntity::class
    ],
    version = 5,
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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun build(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jiaozi_exam.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
