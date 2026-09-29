package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Question

/**
 * 题库章名归一（P0 脏数据治理）。
 *
 * ## 为什么必须在数据层做
 * 全站有 **4 个章节消费点**，它们都**直接扫 `repo.bank.exam`** 并按字面比较章名，
 * 不经过 `AppRepository.byChapter` 索引：
 * ```
 * BankScreen.kt:127        repo.bank.exam.filter { it.subject == subj && it.chapter == ch.name && … }
 * GraphScreen.kt:100       repo.bank.exam.filter { … it.chapter == ch.name … }
 * PracticeHomeParts.kt:174 repo.bank.exam.filter { … it.chapter == ch.name … }
 * PracticeSetupSheet.kt:101 repo.bank.exam.filter { … it.chapter == ch.name … }
 * ```
 * 官方章名取自 `syllabus`（`default_syllabus.json`）。凡题库章名与官方章名**差一个字**，
 * 该章下这些题就会**从「章节练习 / 章节正确率 / 薄弱章节 / 题库浏览」四处集体消失**
 * ——题还在库里、还能被全库随机抽到，但「按章打开」永远少一截，属静默丢失。
 *
 * 因此归一必须落在**题库数据本身**（`bank.exam` 的 `Question.chapter` 值），
 * 而非某个消费点的兼容分支——一处修好，四处同时生效。
 *
 * ## 🔴 科三的关键前提（2026-09-29 核实）
 * `default_syllabus.json` **只登记了「美术」一套科三章名**
 * （`一、学科知识 / 二、教学设计 / 三、教学实施 / 四、教学评价`），
 * 而 `auto_syll.json` **只覆盖科一 / 科二**（len=2）。
 * ⇒ 科三**所有 17 个学科的章清单都套用美术那 4 章**，
 *   凡题库章名与这 4 章对不上，就集体判为「非官方章」而漏题。
 *
 * ## 实测脏数据（2026-09-29 全库 6669 题扫描）
 * - 科一：`三、教师职业道德` **20 题**，漏「规范」。
 *   官方第三章为 `三、教师职业道德规范`，其 sections 恰为
 *   `["教师职业道德","教师职业行为"]`，与这 20 题的 section 分布（15 + 5）完全吻合
 *   ⇒ 确认是**同章异名**，可安全并入，非独立章。
 * - 科三：非官方章名共 **797 题** = 别名章 595 + `一、学科知识·XXX` 细分章 202。
 *   别名章 595 的 section 实证：
 *   - `三、学科素养`(358) → 文学常识 / 中外美术史 / 古诗文 / 力学 / 国情 / 哲学 / 世界史 /
 *     法律常识 / 中国地理 ⇒ **学科知识类**，并入 `一、学科知识`。
 *   - `二、学科教学知识`(126) / `二、教学理论与实践`(110) → 核心素养 / 评价 / 教学设计 /
 *     课标 / 教学原则 ⇒ **教学类**，并入 `二、教学设计`
 *     （交叉证据：官方 `二、教学设计` 的 section 正是「教学目标设计/教学过程设计/
 *     教学评价设计/教学方法选择」，与这两章同源）。
 *   - `三、教学设计`(1) → section=教学目标 ⇒ 并入 `二、教学设计`。
 *   细分章 202（数学 数与代数/图形与几何…、英语 语法/词汇…、美术 中国美术史/造型基础…）
 *   均以 `一、学科知识·` 打头 ⇒ 按**前缀规则**整体并入 `一、学科知识`
 *   （原始细分词仍保留在 `section` 字段，可按节下钻，信息不丢）。
 */
object BankNormalize {

    /** 科三细分章前缀：`一、学科知识·XXX` ⇒ 归入 [KE3_KNOWLEDGE] */
    private const val KE3_KNOWLEDGE = "一、学科知识"
    private const val KE3_KNOWLEDGE_PREFIX = "一、学科知识·"
    private const val KE3_DESIGN = "二、教学设计"

    /**
     * 科一：脏章名 → 官方大纲章名。
     * 依据 `default_syllabus.json` 科一 5 章官方名。
     */
    private val KE1_ALIAS: Map<String, String> = mapOf(
        "三、教师职业道德" to "三、教师职业道德规范",
    )

    /**
     * 科三：脏章名 → 官方大纲章名。
     *
     * 🔴 仅登记**有 section 实证**的别名章；`一、学科知识·XXX` 细分章走**前缀规则**
     * （见 [KE3_KNOWLEDGE_PREFIX]），不在此表逐条登记（46 个细分名，前缀更稳）。
     * 表为空时 [chapter] 对科三零开销原样返回。
     */
    private val KE3_ALIAS: Map<String, String> = mapOf(
        // 学科知识类
        "三、学科素养" to KE3_KNOWLEDGE,
        // 教学类（两条路径在数据中并存，section 高度同源，均归入教学设计）
        "二、学科教学知识" to KE3_DESIGN,
        "二、教学理论与实践" to KE3_DESIGN,
        "三、教学设计" to KE3_DESIGN,
    )

    /** 按章节取别名表（未登记科目直接返回 null ⇒ 调用方短路，避免无谓查表） */
    private fun aliasTable(subject: String): Map<String, String>? = when (subject) {
        "科一" -> KE1_ALIAS
        "科三" -> KE3_ALIAS
        else -> null
    }

    /** 单章名归一：命中别名或前缀规则则返回官方名，否则原样返回 */
    fun chapter(subject: String, chapter: String): String {
        if (subject == "科三" && chapter.startsWith(KE3_KNOWLEDGE_PREFIX)) return KE3_KNOWLEDGE
        val table = aliasTable(subject) ?: return chapter
        return table[chapter] ?: chapter
    }

    /** 单题归一：章名无需改动时**返回原对象**（零分配，便于上游做同引用判断） */
    fun question(q: Question): Question {
        val c = chapter(q.subject, q.chapter)
        return if (c == q.chapter) q else q.copy(chapter = c)
    }

    /**
     * 整库归一。**全局脏题数为 0 时原样返回入参**，
     * 使正常启动与每类科目包下载都不产生额外拷贝开销。
     */
    fun bank(b: Bank): Bank {
        val dirty = b.exam.any { chapter(it.subject, it.chapter) != it.chapter }
        if (!dirty) return b
        return b.copy(exam = b.exam.map { question(it) })
    }
}
