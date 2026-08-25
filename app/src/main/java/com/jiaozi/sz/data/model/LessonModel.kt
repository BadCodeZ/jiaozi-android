package com.jiaozi.sz.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 备课模块结构化数据模型（对标网页端「骨-肉-皮 十二要素」）。
 *
 * 设计要点：
 * - [LessonFields] 与网页端 `lessonDefaults()` 字段形状一致（含嵌套 keyPoints / diff），
 *   保证信封（SYNCPKG1）在移动端与网页端之间无损互通。
 * - 序列化用 [json]（ignoreUnknownKeys=true），从网页端导入的教案即使携带 rubric / _chk 等
 *   移动端不识别的字段也不会解析失败。
 * - 持久化：Room 仅存顶层索引列（id/title/subject/chapter）+ 一个 `data` TEXT 列承载本对象 JSON；
 *   未知键（rubric 等）由 Repository 在序列化时整体保留，避免同步吞数据。
 */
val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = false }

/** 重难点（网页端 keyPoints） */
@Serializable
data class LKeyPoints(
    val focus: String = "",
    val difficult: String = ""
)

/** 分层任务（网页端 diff） */
@Serializable
data class LDiff(
    val basic: String = "",
    val mid: String = "",
    val top: String = ""
)

/**
 * 结构化教案字段。与网页端一一对应：
 * 骨（目标与依据）：curric / textbook / student / objective / keyPoints
 * 肉（过程与活动）：context / processText / questionsText / diff / method / prep
 * 皮（呈现与反思）：blackboard(+blackboardType) / homework / reflect
 */
@Serializable
data class LessonFields(
    val grade: String = "初中",
    val type: String = "新授",
    val template: String = "std",
    val curric: String = "",
    val textbook: String = "",
    val student: String = "",
    val objective: String = "",
    val keyPoints: LKeyPoints = LKeyPoints(),
    val context: String = "",
    val processText: String = "",
    val questionsText: String = "",
    val diff: LDiff = LDiff(),
    val method: String = "",
    val prep: String = "",
    val blackboard: String = "",
    val blackboardType: String = "提纲",
    val homework: String = "",
    val reflect: String = "",
    val body: String = "",
    val disc: String = "",
    val tags: String = "",
    val source: String = "",
    val fromExamId: String = ""
)

/** 用户自建模板 */
@Serializable
data class LessonTemplate(
    val id: String,
    val name: String,
    val grade: String = "初中",
    val type: String = "新授",
    val fields: LessonFields
)

/** 维度选项（与网页端 LESSON_* 一致） */
object LessonDims {
    val GRADE = listOf("小学", "初中", "高中")
    val SUBJ = listOf("美术", "语文", "数学", "英语", "音乐", "体育", "幼教", "其他")
    val TYPE = listOf("新授", "复习", "实验探究", "公开课", "教学设计题")
    val BLACKBOARD = listOf("提纲", "图表公式", "概念网络")
}

/**
 * 十二要素编辑器字段元数据：flatKey → (中文标签, 占位提示, 多行行数)。
 * 顺序即编辑器渲染顺序（骨→肉→皮）。
 */
val LESSON_FIELD_META: List<Triple<String, Pair<String, String>, Int>> = listOf(
    Triple("curric", "课标依据" to "锚定核心素养 + 条目号 / 原文", 2),
    Triple("textbook", "教材分析" to "是什么 / 从哪来 / 往哪去 / 核心矛盾 / 独特价值", 3),
    Triple("student", "学情分析" to "起点 / 认知特点 / 困难兴趣点", 2),
    Triple("objective", "教学目标" to "学科化·可观测行为动词（如美术四大素养）", 2),
    Triple("keyFocus", "重难点·重点" to "本课最核心的达成点", 1),
    Triple("keyDiff", "重难点·难点" to "学生最难突破处，含成因", 2),
    Triple("context", "情境导入" to "真实性四检验：去掉情境是否仍可成立", 2),
    Triple("processText", "教学过程" to "每行一个环节，用 → 写设计意图", 4),
    Triple("questionsText", "课堂提问链" to "≥3 条，标注层级 L1–L5 与追问", 3),
    Triple("diffBasic", "分层任务·基础" to "全体可达成的保底任务", 1),
    Triple("diffMid", "分层任务·进阶" to "多数学生可挑战的任务", 1),
    Triple("diffTop", "分层任务·挑战" to "学优生拓展任务", 1),
    Triple("method", "教学方法" to "如 欣赏·探究·创作·展评", 1),
    Triple("prep", "教学准备" to "教具 / 素材 / 学具", 1),
    Triple("blackboard", "板书设计" to "提纲 / 图表公式 / 概念网络", 2),
    Triple("homework", "分层作业" to "基础 + 拓展，可操作", 2),
    Triple("reflect", "教学反思" to "目标达成 / 学情 / 改进动作，写具体", 2)
)

/** 完成度环统计的 12 个核心项（与网页端 lsRingPct 对齐） */
fun ringCount(f: LessonFields): Int {
    var n = 0
    if (f.curric.isNotBlank()) n++
    if (f.textbook.isNotBlank()) n++
    if (f.student.isNotBlank()) n++
    if (f.objective.isNotBlank()) n++
    if (f.keyPoints.focus.isNotBlank() && f.keyPoints.difficult.isNotBlank()) n++
    if (f.context.isNotBlank()) n++
    if (f.processText.isNotBlank()) n++
    if (f.questionsText.isNotBlank()) n++
    if ((f.diff.basic + f.diff.mid + f.diff.top).isNotBlank()) n++
    if (f.blackboard.isNotBlank()) n++
    if (f.homework.isNotBlank()) n++
    if (f.reflect.isNotBlank()) n++
    return n
}

/** 专家自检清单自动项（返回 名称→是否达标） */
fun selfCheckAuto(f: LessonFields): List<Pair<String, Boolean>> {
    val qn = f.questionsText.lines().count { it.trim().isNotEmpty() }
    val refOk = f.reflect.isNotBlank() && !Regex("以后多注意|继续努力|加强练习|注意改进").containsMatchIn(f.reflect)
    return listOf(
        "课标依据非空" to f.curric.isNotBlank(),
        "板书三型已选" to f.blackboardType.isNotBlank(),
        "提问 ≥3 且含层级" to (qn >= 3),
        "反思写具体动作" to refOk
    )
}

/**
 * 内置骨架模板（对标网页端 LS_TPLS）：type → (骨, 肉, 皮)。
 * 应用内置模板时，把「肉」骨架写入教学过程字段，帮助新手快速起步。
 */
val BUILTIN_TEMPLATES: List<Triple<String, String, String>> = listOf(
    Triple("新授", "单课时新知目标四步", "导入 → 探究 → 巩固 → 小结 → 作业"),
    Triple("复习", "知识网络重构目标", "梳理 → 辨析易混 → 综合应用 → 检测"),
    Triple("实验探究", "探究素养目标", "猜想 → 设计 → 操作 → 结论 → 迁移"),
    Triple("公开课", "核心素养示范目标", "大情境贯穿 → 高阶任务 → 展示 → 反思")
)
