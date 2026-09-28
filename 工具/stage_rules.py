# -*- coding: utf-8 -*-
"""题库「学段」判定共享规则（2026-09-28 · 修订版）

学段取值（三态）：
  - "初中"  ：仅适用于初级中学
  - "高中"  ：仅适用于普通高中
  - None    ：通用（两学段均适用）

判定优先级：
  1) 包级「共用」 —— 科目一《综合素质》(301) / 科目二《教育知识与能力》(302)
     官方科目代码表明确标注「初中、高中相同」⇒ 初高中考同一份卷，
     整包不打学段（全部通用），筛学段时恒全部保留。
     依据：ntce.neea.edu.cn 附件《中小学教师资格考试(笔试)科目代码列表》
       「综合素质(中学) 301  初中、高中相同」
       「教育知识与能力 302  初中、高中相同」
  2) 包级「锁死」 —— 官方大纲中「初中独有科」「高中独有科」整包直接锁定：
       初中独有: 历史与社会(316) / 科学(317)
       高中独有: 思想政治(409) / 通用技术(418)
  3) 逐题判定 —— 仅对科三「初高中同名科」(303-315 vs 403-415 分卷) 逐题判定：
       主判据 = 题面 `q`；
       回退   = 解析 `analysis`（仅当题面无信号、且解析只命中单侧时采用）；
       选项 `opt` **不参与**判定。

为什么排除 `opt` 作为判据：
  选项（干扰项）常把「义务教育/高中」并列为易混淆项，是最大噪声源
  （如「《中国教育现代化2035》…」的选项里同时出现"义务教育"与"高中"）。

为什么 `analysis` 只能作回退：
  解析常用「考点：初中…高中…」做对比式说明，两侧词常同现 ⇒ 两侧同中一律归通用。

为什么以「课标引用」为主信号：
  - `level` 是难度档（1/2/3），不是学段；
  - `chapter` 全集仅 17 个章节名，无任何学段字样；
  - `section` / `point` 与学段无统计相关性（同 section 下初高中混杂；point 全为 null）；
  - `fromPaper` 仅 251 题有值，其中只有 28 题带明确学段；
  - 实测只有「《义务教育X课程标准》/《普通高中X课程标准》」与「初中/高中」
    这类显式引用能可靠区分学段。
"""

import re

# --- 学段取值 ---
JUNIOR = "初中"
SENIOR = "高中"

# --- 包级「共用」：官方初高中同卷，整包不分学段 ---
# 依据官方科目代码表备注「初中、高中相同」
SHARED_PACKS = {"ke1", "ke2"}

# --- 包级「锁死」：官方大纲单学段独有科目 ---
JUNIOR_LOCKED_PACKS = {"ke3_kexue", "ke3_lishiyushehui"}
SENIOR_LOCKED_PACKS = {"ke3_sixiangzhengzhi", "ke3_tongyongjishu"}

# 官方科目代码（用于文档与报告展示）
PACK_CODES = {
    "ke1": "301", "ke2": "302",
    "ke3_yuwen": "303/403", "ke3_shuxue": "304/404", "ke3_yingyu": "305/405",
    "ke3_wuli": "306/406", "ke3_huaxue": "307/407", "ke3_shengwu": "308/408",
    "ke3_daodefazhi": "309", "ke3_lishi": "310/410", "ke3_dili": "311/411",
    "ke3_yinyue": "312/412", "ke3_tiyu": "313/413", "ke3_meishu": "314/414",
    "ke3_xinxi": "315/415", "ke3_lishiyushehui": "316", "ke3_kexue": "317",
    "ke3_sixiangzhengzhi": "409", "ke3_tongyongjishu": "418",
}

# --- 逐题判定信号 ---
JUNIOR_PATTERNS = ("义务教育", "初中", "七年级", "八年级", "九年级")
SENIOR_PATTERNS = ("高中",)

# 主判据字段（题面）
PRIMARY_FIELDS = ("q",)
# 回退字段（解析）
FALLBACK_FIELDS = ("analysis",)

# --- 非中学学段痕迹（仅报告预警，不参与判定）---
# 「保教」为「确保教学」的子串，「小学」为《中小学…》法规名的子串 ⇒ 均须排除假阳性
OFF_STAGE_PATTERNS = ("幼儿园",)
_OFF_STAGE_REGEX = re.compile(r"(?<!中)小学")  # 「中小学」不算，独立「小学」才算


def pack_mode(pack_code: str) -> str:
    """返回包级模式：shared / locked / per-question"""
    if pack_code in SHARED_PACKS:
        return "shared"
    if pack_code in JUNIOR_LOCKED_PACKS or pack_code in SENIOR_LOCKED_PACKS:
        return "locked"
    return "per-question"


def pack_locked_stage(pack_code: str):
    """包级锁定学段；共用包与需逐题判定的包返回 None。"""
    if pack_code in JUNIOR_LOCKED_PACKS:
        return JUNIOR
    if pack_code in SENIOR_LOCKED_PACKS:
        return SENIOR
    return None


def _hits(text: str):
    j = [p for p in JUNIOR_PATTERNS if p in text]
    s = [p for p in SENIOR_PATTERNS if p in text]
    return j, s


def question_text(q: dict) -> str:
    """全字段拼接（仅用于非中学痕迹报告）。"""
    return " ".join(str(q.get(k) or "") for k in ("q", "opt", "analysis", "section"))


def judge_stage(q: dict):
    """逐题判定学段（仅用于科三同名分卷包）。

    返回 (stage, diag)：
      stage ∈ {"初中", "高中", None}
    """
    # 主判据：题面
    pq = " ".join(str(q.get(k) or "") for k in PRIMARY_FIELDS)
    pj, ps = _hits(pq)
    if pj and not ps:
        return JUNIOR, {"by": "q", "junior": pj}
    if ps and not pj:
        return SENIOR, {"by": "q", "senior": ps}

    # 回退判据：解析（仅单侧命中时采用）
    fa = " ".join(str(q.get(k) or "") for k in FALLBACK_FIELDS)
    fj, fs = _hits(fa)
    if fj and not fs:
        return JUNIOR, {"by": "analysis", "junior": fj}
    if fs and not fj:
        return SENIOR, {"by": "analysis", "senior": fs}

    if pj and ps:
        return None, {"conflict": True, "where": "q", "junior": pj, "senior": ps}
    return None, {}


def off_stage_hits(text: str):
    """返回命中「非中学学段」痕迹的词列表（仅报告用，已排除假阳性）。"""
    out = [p for p in OFF_STAGE_PATTERNS if p in text]
    if _OFF_STAGE_REGEX.search(text):
        out.append("小学")
    return out


def resolve_question_stage(q: dict, pack_code: str):
    """单题最终 stage：共用包恒通用 → 锁死包直接锁定 → 其余逐题判定。"""
    mode = pack_mode(pack_code)
    if mode == "shared":
        return None, {"shared": True}
    locked = pack_locked_stage(pack_code)
    if locked is not None:
        return locked, {"locked": True}
    return judge_stage(q)
