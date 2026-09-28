#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
题库外置拆分工具（2026-09-28 · 子科目版）：
将源题库 bank.json 按「官方大纲科目」拆分为独立题库文件：
  - 科目一《综合素质》            -> banks/ke1.json
  - 科目二《教育知识与能力》      -> banks/ke2.json
  - 科目三《学科知识与教学能力》  -> 按子科目拆 17 个 banks/ke3_<pinyin>.json
    （科三各子科目题目**严禁混在一起**，故每子科目单独成文件）

数据键：科三以 question.disc 判别子科目（科一/科二无 disc）。
disc 为空的 30 题（b3a001~b3a030，全为「二、教学设计」美术内容）并入「美术」。
「思想品德」为初中「道德与法治」旧称，输出统一规范为官方名「道德与法治」。

每个输出文件均为完整 Bank 结构 {"exam": [...], "papers": []}，
与 app 内 BankStore / BankRemote.fetchSubject 的解析口径一致。

学段标记（2026-09-28 新增）：
  拆分时同步为每题写入 `stage` 字段（"初中" / "高中"；不写 = 通用），
  规则见 工具/stage_rules.py（科一科二官方同卷不分段 / 4 个独有科包级锁死 /
  科三同名分卷科按题面+解析逐题判定）。`stage` 键插在 `subject` 之后。

用法：
  python 工具/split_bank.py                 # 默认读 _removed_assets_20260928/bank.json
  python 工具/split_bank.py <源bank.json>   # 指定源文件
  python 工具/split_bank.py --no-stage      # 不写学段（兼容旧行为）
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stage_rules as SR  # noqa: E402

# 工程根 = 本文件所在 工具/ 的上一级
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SRC = os.path.join(
    os.path.dirname(ROOT), "_removed_assets_20260928", "bank.json"
)
OUT_DIR = os.path.join(ROOT, "banks")

# 科三 disc(数据键) -> (拼音文件代号, 官方显示名)
# 官方名以《中小学教师资格考试大纲》为准（初中「道德与法治」= 旧称「思想品德」）
KE3_MAP = {
    "美术":       ("meishu",         "美术"),
    "语文":       ("yuwen",          "语文"),
    "数学":       ("shuxue",         "数学"),
    "英语":       ("yingyu",         "英语"),
    "音乐":       ("yinyue",         "音乐"),
    "体育与健康": ("tiyu",           "体育与健康"),
    "信息技术":   ("xinxi",          "信息技术"),
    "物理":       ("wuli",           "物理"),
    "化学":       ("huaxue",         "化学"),
    "生物":       ("shengwu",        "生物"),
    "历史":       ("lishi",          "历史"),
    "地理":       ("dili",           "地理"),
    "思想品德":   ("daodefazhi",     "道德与法治"),
    "思想政治":   ("sixiangzhengzhi", "思想政治"),
    "通用技术":   ("tongyongjishu",  "通用技术"),
    "历史与社会": ("lishiyushehui",  "历史与社会"),
    "科学":       ("kexue",          "科学"),
}
# disc 为空时的兜底子科目 disc 键（经内容核验为美术）
KE3_FALLBACK = "美术"


def tag_stage(q: dict, pack_code: str, enabled: bool = True) -> dict:
    """为单题写入学段标记（见 stage_rules）。stage 键插在 subject 之后。"""
    q = dict(q)
    q.pop("stage", None)
    if not enabled:
        return q
    stage, _ = SR.resolve_question_stage(q, pack_code)
    if stage is None:
        return q
    out = {}
    for k, v in q.items():
        out[k] = v
        if k == "subject":
            out["stage"] = stage
    if "stage" not in out:
        out["stage"] = stage
    return out


def dump(name: str, questions: list) -> int:
    path = os.path.join(OUT_DIR, f"{name}.json")
    # newline="\n"：防止 Windows 默认转换把题库写成 CRLF（仓库要求纯 LF）
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"exam": questions, "papers": []}, f,
                  ensure_ascii=False, separators=(",", ":"))
    return os.path.getsize(path)


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    write_stage = "--no-stage" not in sys.argv
    src = argv[0] if argv else DEFAULT_SRC
    if not os.path.exists(src):
        print(f"[ERROR] 源题库不存在: {src}")
        sys.exit(1)

    with open(src, "r", encoding="utf-8") as f:
        bank = json.load(f)
    exam = bank.get("exam", [])

    os.makedirs(OUT_DIR, exist_ok=True)
    # 清理旧的单文件 ke3.json（本次起改为 17 个子文件）
    legacy = os.path.join(OUT_DIR, "ke3.json")
    if os.path.exists(legacy):
        os.remove(legacy)
        print("已移除旧文件 ke3.json（改为 17 个子科目文件）")

    by_subj = {"科一": [], "科二": []}
    by_ke3 = {code: [] for code, _ in KE3_MAP.values()}
    unknown_subj, unknown_disc = [], []
    stage_stat = {}          # pack_code -> Counter

    for q in exam:
        subj = q.get("subject")
        if subj in ("科一", "科二"):
            code = "ke1" if subj == "科一" else "ke2"
            by_subj[subj].append(tag_stage(q, code, write_stage))
            _tally(stage_stat, code, q, write_stage)
        elif subj == "科三":
            disc = q.get("disc")
            key = disc if disc in KE3_MAP else KE3_FALLBACK
            if disc not in KE3_MAP and disc is not None:
                unknown_disc.append(disc)
            code, official = KE3_MAP[key]
            # 数据键统一为官方名（如 思想品德 -> 道德与法治），保证 文件/字段/展示 三方一致
            q = dict(q)
            q["disc"] = official
            by_ke3[code].append(tag_stage(q, f"ke3_{code}", write_stage))
            _tally(stage_stat, f"ke3_{code}", q, write_stage)
        else:
            unknown_subj.append(subj)

    total = 0
    print("=" * 66)
    print("科目一 / 科目二（官方：初中、高中同卷 ⇒ 不分学段）")
    for subj, code in (("科一", "ke1"), ("科二", "ke2")):
        qs = by_subj[subj]
        total += len(qs)
        kb = dump(code, qs) / 1024
        print(f"  {code}.json  ({subj})  {len(qs)} 题  {kb:.1f} KB  {_stage_brief(stage_stat.get(code))}")

    print("-" * 66)
    print("科目三《学科知识与教学能力》子科目（%d 个）" % len(KE3_MAP))
    for disc, (code, official) in KE3_MAP.items():
        qs = by_ke3[code]
        total += len(qs)
        kb = dump(f"ke3_{code}", qs) / 1024
        tag = "" if disc == official else f"  [{disc} -> {official}]"
        print(f"  ke3_{code}.json  ({official})  {len(qs)} 题  {kb:.1f} KB"
              f"  {_stage_brief(stage_stat.get('ke3_' + code))}{tag}")

    print("=" * 66)
    print(f"源 exam 总数: {len(exam)}  拆出总数: {total}")
    if unknown_subj:
        print(f"[WARN] 未识别 subject: {set(unknown_subj)}")
    if unknown_disc:
        print(f"[WARN] 未识别 disc（已并入美术）: {set(unknown_disc)}")
    if write_stage:
        agg = {"初中": 0, "高中": 0, "通用": 0}
        for c in stage_stat.values():
            for k in agg:
                agg[k] += c.get(k, 0)
        print(f"学段标记: 初中 {agg['初中']} / 高中 {agg['高中']} / 通用 {agg['通用']}"
              f"  （合计 {sum(agg.values())}，守恒 {sum(agg.values()) == total}）")
    else:
        print("[--no-stage] 未写学段标记")
    print(f"输出目录: {OUT_DIR}")


def _tally(stat: dict, code: str, q: dict, enabled: bool):
    if not enabled:
        return
    import collections
    c = stat.setdefault(code, collections.Counter())
    stage, _ = SR.resolve_question_stage(q, code)
    c[stage if stage else "通用"] += 1


def _stage_brief(c):
    if not c:
        return ""
    return f"[初{c.get('初中', 0)} 高{c.get('高中', 0)} 通{c.get('通用', 0)}]"


if __name__ == "__main__":
    main()