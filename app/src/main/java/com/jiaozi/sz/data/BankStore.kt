package com.jiaozi.sz.data

import android.content.Context
import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Question
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 单个题库包定义（2026-09-28 题库外置 · 子科目版）。
 *
 * @param code     文件名代号（= 远端 `banks/<code>.json`），如 `ke1` / `ke3_meishu`
 * @param subject  数据键：`科一` / `科二` / `科三`（与 [Question.subject] 一致）
 * @param disc     科三子科目官方名（与 [Question.disc] 一致）；科一/科二为 null
 * @param group    所属官方科目分组名（见 [GROUP_ORDER]）
 * @param name     展示名（一律用《考试大纲》规定的官方名称）
 */
data class BankPack(
    val code: String,
    val subject: String,
    val disc: String?,
    val group: String,
    val name: String
)

/**
 * 本地题库存储（2026-09-28 题库外置 · **学科 × 学段 双层拆分**）：
 * - 内置题库不再打包进 assets，改为首次启动从 GitHub 下载到 `filesDir/banks/<code>.json`；
 * - 按**官方大纲**分文件：科目一《综合素质》→ `ke1`、科目二《教育知识与能力》→ `ke2`、
 *   科目三《学科知识与教学能力》→ **按子科目拆 17 个** `ke3_<pinyin>`
 *   （科三各子科目题目**严禁混装在同一文件**）；
 * - 进一步按**报考学段**拆远端文件：13 个初高中同名分卷科各出 `_junior` / `_senior` 两版
 *   （见 [remoteCode]），用户只下载本学段数据；4 个单学段独有科与科一科二恒单文件。
 * - 🔴 **本地文件名恒为逻辑 [BankPack.code]**（`ke3_meishu.json` 等 19 个固定名），
 *   与远端文件名解耦 ⇒ 学段切换时同名覆盖，[loadLocal] 读取口径恒定不变。
 * - [loadLocal] 按 [PACKS] 顺序合并所有已下载题库包，作为进程内题库真源；无任何本地包则返回空 Bank。
 *
 * 🔴 展示口径：向用户展示科目名称时一律走 [officialName] / [BankPack.name]，
 *   使用《中小学教师资格考试大纲》标准名称（中学学段）：
 *   科目一《综合素质》(301) / 科目二《教育知识与能力》(302) / 科目三《学科知识与教学能力》。
 *   初中「道德与法治」为官方名（数据键旧称「思想品德」已在拆分时规范）。
 */
object BankStore {
    // ── 官方科目分组名（大队纲口径）──
    const val GROUP_1 = "综合素质"
    const val GROUP_2 = "教育知识与能力"
    const val GROUP_3 = "学科知识与教学能力"
    val GROUP_ORDER: List<String> = listOf(GROUP_1, GROUP_2, GROUP_3)

    /**
     * 全部题库包（19 个）：科目一 / 科目二 各 1，科目三 17 个子科目各 1。
     *
     * 科三子科目**顺序沿用源题库 disc 首现顺序**（美术首位 ⇒ 与旧版 `discList.first()` 兜底一致，
     * 保证默认学科仍是美术）；`code` 为拼音代号，与 `banks/` 下文件名一一对应。
     */
    val PACKS: List<BankPack> = listOf(
        // 科目一 / 科目二
        BankPack("ke1", "科一", null, GROUP_1, "综合素质"),
        BankPack("ke2", "科二", null, GROUP_2, "教育知识与能力"),
        // 科目三 · 17 个子科目（官方名，顺序＝源 disc 首现顺序）
        BankPack("ke3_meishu", "科三", "美术", GROUP_3, "美术"),
        BankPack("ke3_yuwen", "科三", "语文", GROUP_3, "语文"),
        BankPack("ke3_shuxue", "科三", "数学", GROUP_3, "数学"),
        BankPack("ke3_yingyu", "科三", "英语", GROUP_3, "英语"),
        BankPack("ke3_yinyue", "科三", "音乐", GROUP_3, "音乐"),
        BankPack("ke3_tiyu", "科三", "体育与健康", GROUP_3, "体育与健康"),
        BankPack("ke3_xinxi", "科三", "信息技术", GROUP_3, "信息技术"),
        BankPack("ke3_wuli", "科三", "物理", GROUP_3, "物理"),
        BankPack("ke3_huaxue", "科三", "化学", GROUP_3, "化学"),
        BankPack("ke3_shengwu", "科三", "生物", GROUP_3, "生物"),
        BankPack("ke3_lishi", "科三", "历史", GROUP_3, "历史"),
        BankPack("ke3_dili", "科三", "地理", GROUP_3, "地理"),
        // 「思想品德」为初中「道德与法治」旧称 ⇒ 输出统一用官方名
        BankPack("ke3_daodefazhi", "科三", "道德与法治", GROUP_3, "道德与法治"),
        BankPack("ke3_sixiangzhengzhi", "科三", "思想政治", GROUP_3, "思想政治"),
        BankPack("ke3_tongyongjishu", "科三", "通用技术", GROUP_3, "通用技术"),
        BankPack("ke3_lishiyushehui", "科三", "历史与社会", GROUP_3, "历史与社会"),
        BankPack("ke3_kexue", "科三", "科学", GROUP_3, "科学")
    )

    /** 科三子科目包（17 个），按 [PACKS] 顺序 */
    val DISC_PACKS: List<BankPack> = PACKS.filter { it.subject == "科三" }

    /** 旧版单文件科三包文件名（2026-09-28 子科目拆分后废弃，见 [pruneLegacy]） */
    private const val LEGACY_KE3 = "ke3"

    // ── 报考学段（2026-09-28 学段筛题；规则与 `工具/stage_rules.py` 严格对齐）──

    /** 学段取值：初中 */
    const val STAGE_JUNIOR = "初中"

    /** 学段取值：高中 */
    const val STAGE_SENIOR = "高中"

    /** 可选学段列表（UI 展示顺序） */
    val STAGE_OPTIONS: List<String> = listOf(STAGE_JUNIOR, STAGE_SENIOR)

    /**
     * 共用包（科一/科二）：官方科目代码表 301/302 备注「**初中、高中相同**」⇒ 初高中同卷，
     * 整包题目恒为**通用**（[Question.stage] == null），不参与学段过滤。
     */
    val SHARED_PACKS: Set<String> = setOf("ke1", "ke2")

    /** 包级锁「初中」的科三子科目（初中学段独有学科） */
    val JUNIOR_LOCKED_PACKS: Set<String> = setOf("ke3_kexue", "ke3_lishiyushehui")

    /** 包级锁「高中」的科三子科目（高中学段独有学科） */
    val SENIOR_LOCKED_PACKS: Set<String> = setOf("ke3_sixiangzhengzhi", "ke3_tongyongjishu")

    /**
     * 单包判定模式（与 `工具/stage_rules.py:pack_mode` 一致）：
     * - `"shared"`：科一/科二 —— 初高中同卷，整包通用；
     * - `"locked"`：科三独有学科 —— 整包锁死单一学段；
     * - `"per-question"`：其余 13 个同名分卷包 —— 逐题按题面判定。
     */
    fun packMode(code: String): String = when (code) {
        in SHARED_PACKS -> "shared"
        in JUNIOR_LOCKED_PACKS, in SENIOR_LOCKED_PACKS -> "locked"
        else -> "per-question"
    }

    /** 包级锁死学段；非 locked 包返回 null */
    fun packLockedStage(code: String): String? = when (code) {
        in JUNIOR_LOCKED_PACKS -> STAGE_JUNIOR
        in SENIOR_LOCKED_PACKS -> STAGE_SENIOR
        else -> null
    }

    // ── 学科 × 学段：远端文件名映射（2026-09-28 学段真拆包）──
    //
    // 远端文件集（共 32 个，见 工具/split_bank_layer.py）：
    //   ke1 / ke2                                    恒单文件（官方初高中同卷）
    //   ke3_kexue / ke3_lishiyushehui                 整包锁初中，单文件
    //   ke3_sixiangzhengzhi / ke3_tongyongjishu       整包锁高中，单文件
    //   其余 13 个同名分卷科                           ke3_<x>_junior / ke3_<x>_senior 两版
    // 🔴 本地文件名恒为逻辑 code（19 个固定名）；远端文件名为 [remoteCode]。

    /**
     * 该包在某学段下对应的**远端文件名代号**（不含 `.json`）。
     *
     * - 科一/科二：恒逻辑 code 本身（`shared` 模式，不拆）；
     * - 单学段独有科（`locked`）：恒逻辑 code 本身，且**仅在本学段可用**（见 [packAvailableIn]）；
     * - 同名分卷科（`per-question`）：`ke3_<x>_junior` / `ke3_<x>_senior`。
     *
     * @param stage 目标学段；为空 ⇒ 回退逻辑 code（兼容未设置学段的场景）
     */
    fun remoteCode(code: String, stage: String?): String {
        val pack = byCode(code) ?: return code
        if (pack.subject != "科三") return code          // 科一/科二：恒单文件
        if (packMode(code) == "locked") return code      // 单学段独有科：不拆
        if (stage == null) return code                   // 无学段：回退全量名（理论上不会走到）
        return if (stage == STAGE_JUNIOR) "${code}_junior" else "${code}_senior"
    }

    /**
     * 该包在某学段下**是否可用**：
     * 单学段独有科只在其所属学段可用（如 `ke3_kexue` 仅初中）；
     * 其余包（科一/科二/同名分卷科）两学段均可用。
     */
    fun packAvailableIn(code: String, stage: String?): Boolean {
        val locked = packLockedStage(code) ?: return true
        return stage == null || stage == locked
    }

    /** 当前学段下**全部可用包**（用于下载页/管理页按学段过滤展示） */
    fun availablePacks(stage: String?): List<BankPack> =
        PACKS.filter { packAvailableIn(it.code, stage) }

    /** 当前学段下按分组组织的可用包（分组视图，组内空则省略该组） */
    fun availablePacksByGroup(stage: String?): List<Pair<String, List<BankPack>>> =
        GROUP_ORDER.map { g -> g to availablePacks(stage).filter { it.group == g } }
            .filter { it.second.isNotEmpty() }

    /**
     * 单题是否匹配报考学段（**本学段 + 通用题** 口径）：
     * `question.stage == null`（通用）恒命中；否则要求与 [stage] 相等。
     *
     * @param stage 用户报考学段；为 null ⇒ 不启用学段过滤（恒 true，兼容未设置场景）
     */
    fun stageMatches(question: Question, stage: String?): Boolean {
        if (stage == null) return true
        val qs = question.stage
        return qs == null || qs == stage
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── 官方名称映射（展示用）──

    /** 科目官方名（《考试大纲》口径） */
    fun officialName(subject: String): String = when (subject) {
        "科一" -> GROUP_1
        "科二" -> GROUP_2
        "科三" -> GROUP_3
        else -> subject
    }

    /** 科目紧凑展示名（筛选 chip / 窄标签用）：科三 17 子科目自带学科名，故用其官方简写 */
    fun shortName(subject: String): String = when (subject) {
        "科一" -> GROUP_1
        "科二" -> "教育知识"
        "科三" -> "学科知识"
        else -> subject
    }

    /** 按 [code] 取包定义；未知 code → null */
    fun byCode(code: String): BankPack? = PACKS.firstOrNull { it.code == code }

    /** 按 (subject, disc) 取包定义（科一/科二 disc 传 null） */
    fun bySubjectDisc(subject: String, disc: String?): BankPack? =
        PACKS.firstOrNull { it.subject == subject && it.disc == disc }

    /** 展示名：科三带子科目时用「学科知识与教学能力 · 美术」形式 */
    fun displayName(pack: BankPack): String =
        if (pack.subject == "科三") "${GROUP_3} · ${pack.name}" else pack.name

    /** 分组视图：[GROUP_ORDER] 顺序 → 该组下的包列表 */
    fun packsByGroup(): List<Pair<String, List<BankPack>>> =
        GROUP_ORDER.map { g -> g to PACKS.filter { it.group == g } }

    // ── 文件存取 ──

    fun dir(ctx: Context): File =
        File(ctx.filesDir, "banks").also { if (!it.exists()) it.mkdirs() }

    fun localFile(ctx: Context, code: String): File = File(dir(ctx), "$code.json")

    /** 某题库包是否已下载到本地 */
    fun isDownloaded(ctx: Context, code: String): Boolean = localFile(ctx, code).exists()

    /**
     * 清理旧版单文件科三包（`banks/ke3.json`）。
     * 子科目拆分后该文件已废弃；若用户此前下载过，残留会导致科三题目在 `disc` 归并上出现重复风险。
     */
    fun pruneLegacy(ctx: Context) {
        runCatching { File(dir(ctx), "$LEGACY_KE3.json").takeIf { it.exists() }?.delete() }
    }

    /** 合并本地已下载的所有题库包，返回 [Bank]；无本地包则返回空题库 */
    fun loadLocal(ctx: Context): Bank {
        val exam = mutableListOf<Question>()
        for (pack in PACKS) {
            val f = localFile(ctx, pack.code)
            if (f.exists()) runCatching {
                val b = json.decodeFromString<Bank>(f.readText())
                exam += b.exam
            }
        }
        return Bank(exam, emptyList())
    }

    /** 持久化单个题库包原始 JSON（覆盖写） */
    fun persist(ctx: Context, code: String, text: String) {
        localFile(ctx, code).writeText(text)
    }

    /** 持久化单个题库包的题目列表（序列化为完整 [Bank] 结构，papers 留空） */
    fun persistQuestions(ctx: Context, code: String, questions: List<Question>) {
        localFile(ctx, code).writeText(json.encodeToString(Bank.serializer(), Bank(questions, emptyList())))
    }

    /** 删除单个题库包（移除下载） */
    fun deleteLocal(ctx: Context, code: String) {
        runCatching { localFile(ctx, code).delete() }
    }

    /**
     * 删除**当前学段不可用**的本地包。
     * 典型场景：用户从「初中」切到「高中」后，初中独有的 `ke3_kexue` / `ke3_lishiyushehui`
     * 若残留在 `filesDir/banks/`，会被 [loadLocal] 合并进题库 ⇒ 高中用户看到初中独有学科。
     *
     * @param stage 目标学段；为 null（未设置）⇒ 不清理，保持全量。
     */
    fun pruneUnavailable(ctx: Context, stage: String?) {
        if (stage == null) return
        for (pack in PACKS) {
            if (!packAvailableIn(pack.code, stage)) deleteLocal(ctx, pack.code)
        }
    }
}
