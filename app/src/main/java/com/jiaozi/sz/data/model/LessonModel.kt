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
    // 🔴 2026-09-21 按稿：课型文案「实验探究」→「实验」（稿内课型行实测为 新授/复习/实验/公开课）。
    //    ⚠️ 「教学设计题」稿内未出现，但它是科三真实题型、删掉会丢功能，故**保留待杰哥拍板**。
    val TYPE = listOf("新授", "复习", "实验", "公开课", "教学设计题")
    val BLACKBOARD = listOf("提纲", "图表公式", "概念网络")
}

/**
 * 完成度环统计的 **12 个逻辑项**（与网页端 lsRingPct 对齐）。
 *
 * 🔴 口径澄清（2026-09-21 核对，12 号规范 E4 / pending_normalization 的「分母 12 vs 三区字段 18」疑点已闭环）：
 * 编辑器三区共 **18 个 LessonField**，但本函数统计的是 **12 个逻辑项**，差额来自三处**刻意设计**：
 * - 骨区 6 字段 → **5 项**：`重难点·重点` 与 `重难点·难点` 合并为 1 项（两者须同时填写才计数）；
 * - 肉区 8 字段 → **4 项**：`分层任务·基础 / 进阶 / 挑战` 合并为 1 项（任一非空即计数），
 *   且 `教学方法` / `教学准备` **不计入完成度**（属可选项，非教案核心结构）；
 * - 皮区 4 字段 → **3 项**：`板书三型` 是枚举（走 SegmentedRow）不属字段，不计入；
 *   另 `板书设计` / `分层作业` / `教学反思` 各 1 项。
 * 5 + 4 + 3 = **12** ✅ 分母正确，非 bug。
 *
 * ⚠️ 因高保真稿的完成度卡只显示「9/12」与进度条、**无任何附加说明文字**，
 * 按「图未覆盖不得凭推断改码」红线 ⇒ **UI 保持原样不补说明**，口径只在源码注释与设计规范里澄清。
 */
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
    // 🔴 2026-09-21 按稿：行标题由「类型 · 骨标题」改为「类型 · 覆盖分区」（稿实测「公开课 · 骨+肉+皮」）。
    //    第三个元素是**功能性**的（应用骨架时写入「教学过程」字段），稿内副标题带省略号＝占位，
    //    故保留原有真实流程文案不改。
    Triple("新授", "骨+肉+皮", "导入 → 探究 → 巩固 → 小结 → 作业"),
    Triple("复习", "骨+肉", "梳理 → 辨析易混 → 综合应用 → 检测"),
    Triple("实验", "骨+肉+皮", "猜想 → 设计 → 操作 → 结论 → 迁移"),
    Triple("公开课", "骨+肉+皮", "大情境贯穿 → 高阶任务 → 展示 → 反思")
)
