#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""题库「学段」打标工具（2026-09-28）

对 banks/ 下 19 个题库文件打 `stage` 字段（初中 / 高中 / 不写=通用），
实现「按报考学段分类存储」。判定规则见 工具/stage_rules.py。

三种包级模式：
  shared       科一/科二：官方初高中同卷 ⇒ 整包不打 stage（全部通用）
  locked       科三单学段独有科 ⇒ 整包锁死（科学/历史与社会=初中；思想政治/通用技术=高中）
  per-question 科三初高中同名分卷科 ⇒ 逐题按题面/解析判定

stage 键插入在 `subject` 之后；值为 None（通用）时不落盘该键。

用法：
  python 工具/tag_stage.py --dry-run    # 只出报告，不写文件
  python 工具/tag_stage.py              # 写回 banks/*.json
  python 工具/tag_stage.py --report out.json
"""
import argparse
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import stage_rules as SR  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BANKS = os.path.join(ROOT, "banks")

MODE_LABEL = {
    "shared": "共用·不分段",
    "locked": "包级锁死",
    "per-question": "逐题判定",
}


def load(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def dump(path, data):
    # newline="\n"：防止 Windows 默认转换把题库写成 CRLF（仓库要求纯 LF）
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))


def reorder(q):
    """把 stage 插到 subject 之后，其余字段顺序保持原样；通用则删键。"""
    out = {}
    for k, v in q.items():
        if k == "stage":
            continue
        out[k] = v
        if k == "subject":
            if q.get("stage") is not None:
                out["stage"] = q["stage"]
    if "stage" not in out and q.get("stage") is not None:
        out["stage"] = q["stage"]
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只出报告，不写文件")
    ap.add_argument("--report", default=None, help="报告 JSON 输出路径")
    args = ap.parse_args()

    files = sorted(f for f in os.listdir(BANKS) if f.endswith(".json"))
    files.sort(key=lambda n: (n.startswith("ke3"), n))

    report = {"generated": "2026-09-28", "packs": []}
    grand = collections.Counter()
    by_reason = collections.Counter()
    off_stage_all = collections.Counter()
    conflict_samples = []

    print("=" * 100)
    print(f"{'题库文件':<24}{'代码':<10}{'总数':>6}{'初中':>7}{'高中':>7}{'通用':>7}  {'模式':<12}{'判据来源'}")
    print("-" * 100)

    for fn in files:
        pack = fn[:-5]
        path = os.path.join(BANKS, fn)
        data = load(path)
        exam = data.get("exam", [])
        mode = SR.pack_mode(pack)
        code = SR.PACK_CODES.get(pack, "-")

        stat = collections.Counter()
        reason = collections.Counter()
        off = collections.Counter()
        detail = []

        for q in exam:
            stage, diag = SR.resolve_question_stage(q, pack)
            stat[stage if stage else "通用"] += 1
            for w in SR.off_stage_hits(SR.question_text(q)):
                off[w] += 1
                off_stage_all[w] += 1
            if diag.get("shared"):
                reason["官方同卷(301/302)"] += 1
            elif diag.get("locked"):
                reason["官方独有科锁死"] += 1
            elif diag.get("by") == "q":
                reason["题面"] += 1
            elif diag.get("by") == "analysis":
                reason["解析回退"] += 1
            elif diag.get("conflict"):
                reason["题面冲突·归通用"] += 1
                if len(conflict_samples) < 20:
                    conflict_samples.append({
                        "pack": pack, "id": q.get("id"),
                        "junior": diag.get("junior"), "senior": diag.get("senior"),
                        "q": str(q.get("q"))[:120],
                    })
            else:
                reason["无信号·归通用"] += 1

            q2 = dict(q)
            q2["stage"] = stage            # None ⇒ reorder 会删键
            detail.append(reorder(q2))

        print(f"{fn:<24}{code:<10}{len(exam):>6}{stat['初中']:>7}{stat['高中']:>7}{stat['通用']:>7}  "
              f"{MODE_LABEL[mode]:<12}{dict(reason)}")

        report["packs"].append({
            "file": fn, "pack": pack, "code": code, "n": len(exam), "mode": mode,
            "junior": stat["初中"], "senior": stat["高中"], "generic": stat["通用"],
            "reason": dict(reason), "off_stage": dict(off),
        })
        grand["n"] += len(exam)
        grand["初中"] += stat["初中"]
        grand["高中"] += stat["高中"]
        grand["通用"] += stat["通用"]
        for k, v in reason.items():
            by_reason[k] += v

        if not args.dry_run:
            data["exam"] = detail
            dump(path, data)

    print("-" * 100)
    print(f"{'合计':<24}{'':<10}{grand['n']:>6}{grand['初中']:>7}{grand['高中']:>7}{grand['通用']:>7}")
    print("=" * 100)
    tagged = grand["初中"] + grand["高中"]
    pct = tagged / grand["n"] * 100 if grand["n"] else 0
    print(f"已分学段 {tagged} 题（初中 {grand['初中']} + 高中 {grand['高中']}，占 {pct:.1f}%）；"
          f"通用 {grand['通用']} 题（{100 - pct:.1f}%）")
    print(f"判定来源分布: {dict(by_reason)}")
    if off_stage_all:
        print(f"[WARN] 非中学学段痕迹（不参与判定）：{dict(off_stage_all)}")
    print(f"[{'DRY-RUN 未写文件' if args.dry_run else '已写回 banks/*.json'}]")

    report["total"] = dict(grand)
    report["by_reason"] = dict(by_reason)
    report["conflict_samples"] = conflict_samples
    report["off_stage"] = dict(off_stage_all)
    rp = args.report or os.path.join(ROOT, "_stage_report.json")
    dump(rp, report)
    print(f"报告已写入: {rp}")

    assert grand["n"] == grand["初中"] + grand["高中"] + grand["通用"], "分档合计不守恒"
    print("守恒校验通过：初中 + 高中 + 通用 == 总数")


if __name__ == "__main__":
    main()
