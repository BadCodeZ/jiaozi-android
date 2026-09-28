#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""题库「学科 × 学段」分层生成器（2026-09-28）

在 工具/split_bank.py（学科拆分 + 逐题 stage 打标）基础上，进一步把
「学段」从**运行时过滤**下沉为**文件级分类**，让用户只下载本学段数据。

输出形态：
  科一《综合素质》      → ke1.json                 （官方「初中、高中相同」⇒ 恒通用，单文件）
  科二《教育知识与能力》 → ke2.json                 （同上，单文件）
  科三 初中独有科(316/317) → ke3_lishiyushehui.json / ke3_kexue.json        （整包锁初中，不拆）
  科三 高中独有科(409/418) → ke3_sixiangzhengzhi.json / ke3_tongyongjishu.json（整包锁高中，不拆）
  科三 同名分卷科（13 个） → ke3_<code>_junior.json / ke3_<code>_senior.json
      各含「通用层 + 本学段专属层」。通用题在两份中**派生冗余一份**（脚本一次生成，
      无手工同步风险）；id 全局唯一性已验证（3342/3342）⇒ 合并按 id 去重安全。

空集不生成文件。

学段判定复用 工具/stage_rules.py；单题打标复用 split_bank.tag_stage（stage 键插在 subject 之后）。

用法：
  python 工具/split_bank_layer.py                # 读 _removed_assets_20260928/bank.json
  python 工具/split_bank_layer.py <源bank.json>
  python 工具/split_bank_layer.py --clean-old    # 顺带把旧的「全量版」同名分卷文件移入 _trash_20260927/
"""
import collections
import json
import os
import re
import shutil
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

import stage_rules as SR                                    # noqa: E402
from split_bank import (                                    # noqa: E402
    DEFAULT_SRC, KE3_FALLBACK, KE3_MAP, OUT_DIR, ROOT, tag_stage,
)

JUNIOR, SENIOR = SR.JUNIOR, SR.SENIOR

# 13 个「同名分卷」科三学科（需按学段拆两版）
PER_QUESTION_CODES = [
    f"ke3_{c}" for _, (c, _) in KE3_MAP.items()
    if f"ke3_{c}" not in SR.JUNIOR_LOCKED_PACKS
    and f"ke3_{c}" not in SR.SENIOR_LOCKED_PACKS
]
# 旧「全量版」文件名（本次起不再生成；--clean-old 时移走）
LEGACY_FULL_FILES = [f"{c}.json" for c in PER_QUESTION_CODES]

TRASH_DIR = os.path.join(ROOT, "_trash_20260927")


def route(q: dict):
    """按 subject/disc 把单题分流到 pack_code，并把 disc 规范为官方名。"""
    subj = q.get("subject")
    if subj in ("科一", "科二"):
        return ("ke1" if subj == "科一" else "ke2"), q
    if subj == "科三":
        disc = q.get("disc")
        key = disc if disc in KE3_MAP else KE3_FALLBACK
        code, official = KE3_MAP[key]
        q = dict(q)
        q["disc"] = official          # 思想品德 -> 道德与法治，保证 文件/字段/展示 三方一致
        return f"ke3_{code}", q
    return None, q


def dump(name: str, questions: list) -> int:
    path = os.path.join(OUT_DIR, f"{name}.json")
    # newline="\n"：Windows 下 open(...,"w") 默认把 \n 转成 \r\n；
    # 仓库既有风格为「紧凑单行 + 纯 LF」，漏写会把题库写成 CRLF 污染远端。
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"exam": questions, "papers": []}, f,
                  ensure_ascii=False, separators=(",", ":"))
    return os.path.getsize(path)


def brief(qs: list) -> str:
    c = collections.Counter(
        (q.get("stage") or "通用") for q in qs
    )
    return f"[初{c.get('初中', 0)} 高{c.get('高中', 0)} 通{c.get('通用', 0)}]"


def main():
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    clean_old = "--clean-old" in sys.argv
    src = argv[0] if argv else DEFAULT_SRC
    if not os.path.exists(src):
        print(f"[ERROR] 源题库不存在: {src}")
        sys.exit(1)

    with open(src, "r", encoding="utf-8") as f:
        exam = json.load(f).get("exam", [])

    os.makedirs(OUT_DIR, exist_ok=True)

    # ---- 1) 分流 + 打标 ----
    by_code = {"ke1": [], "ke2": []}
    for _, (c, _) in KE3_MAP.items():
        by_code[f"ke3_{c}"] = []
    unknown = []
    for q in exam:
        code, q2 = route(q)
        if code is None:
            unknown.append(q.get("subject"))
            continue
        by_code[code].append(tag_stage(q2, code, True))

    # ---- 2) 分层输出 ----
    files = []          # (filename, count, bytes, stage_brief)
    checks = []         # 守恒校验行

    def emit(name: str, qs: list):
        if not qs:
            return
        size = dump(name, qs)
        files.append((f"{name}.json", len(qs), size, brief(qs)))

    emit("ke1", by_code["ke1"])
    emit("ke2", by_code["ke2"])

    for _, (c, official) in KE3_MAP.items():
        code = f"ke3_{c}"
        qs = by_code[code]
        mode = SR.pack_mode(code)
        if mode == "locked":
            emit(code, qs)
            continue
        # per-question：拆 junior / senior
        jr = [q for q in qs if q.get("stage") in (None, JUNIOR)]
        sr = [q for q in qs if q.get("stage") in (None, SENIOR)]
        emit(f"{code}_junior", jr)
        emit(f"{code}_senior", sr)
        # 守恒：两版并集（按 id 去重）应等于原包
        ids = {q["id"] for q in jr} | {q["id"] for q in sr}
        checks.append((official, len(qs), len(jr), len(sr), len(ids)))

    # ---- 3) 报告 ----
    total_q = 0
    total_b = 0
    print("=" * 78)
    print("按 学科 × 学段 分层生成结果")
    print("-" * 78)
    for name, n, size, br in files:
        total_q += n
        total_b += size
        print(f"  {name:<32} {n:>5} 题  {size/1024:>8.1f} KB  {br}")
    print("-" * 78)
    print(f"  文件数 {len(files)}   条目合计 {total_q} 题   体积合计 {total_b/1024/1024:.2f} MB")

    print("-" * 78)
    print("守恒校验（同名分卷包：junior ∪ senior 去重 后应 == 原包题量）")
    ok = True
    for official, n0, nj, ns, nu in checks:
        flag = "OK " if nu == n0 else "FAIL"
        if nu != n0:
            ok = False
        print(f"  [{flag}] {official:<8} 原 {n0:>3}  junior {nj:>3}  senior {ns:>3}  并集 {nu:>3}")
    dup = total_q - len(exam)
    print(f"  源 exam 总数 {len(exam)}   分层条目合计 {total_q}"
          f"（通用题在两版各存一份 ⇒ 冗余 {dup} 条，属预期）")
    print(f"  去重并集 == 原包：{'全部 OK' if ok else '存在 FAIL'}")

    # 学段专属题守恒：junior 中各包『初中』题之和 + 锁初中包题量 == 源库初中专属总量
    src_j = src_s = src_u = 0
    for q in exam:
        code, _ = route(q)
        if code is None:
            continue
        st, _ = SR.resolve_question_stage(q, code)
        src_j += st == JUNIOR
        src_s += st == SENIOR
        src_u += st is None
    out_j = out_s = 0
    for name, _, _, br in files:
        # br 形如 [初13 高0 通231]
        nums = [int(x) for x in re.findall(r"\d+", br)]
        out_j += nums[0]
        out_s += nums[1]
    print(f"  学段专属守恒：初中 {src_j} → 分层后 {out_j}   "
          f"高中 {src_s} → 分层后 {out_s}   （应为相等）")
    if unknown:
        print(f"  [WARN] 未识别 subject: {set(unknown)}")

    # ---- 4) 学段下载量模拟 ----
    def files_for_stage(stage):
        out = ["ke1.json", "ke2.json"]
        for _, (c, _) in KE3_MAP.items():
            code = f"ke3_{c}"
            if SR.pack_mode(code) == "locked":
                if SR.pack_locked_stage(code) == stage:
                    out.append(f"{code}.json")
            else:
                suf = "junior" if stage == JUNIOR else "senior"
                out.append(f"{code}_{suf}.json")
        return out

    print("-" * 78)
    size_map = {n: s for n, _, s, _ in files}
    for stage in (JUNIOR, SENIOR):
        fs = files_for_stage(stage)
        b = sum(size_map.get(n, 0) for n in fs)
        miss = [n for n in fs if n not in size_map]
        print(f"  {stage}用户下载: {len(fs)} 文件  {b/1024/1024:.2f} MB"
              + (f"   [缺文件 {miss}]" if miss else ""))
    print("=" * 78)
    print(f"输出目录: {OUT_DIR}")

    # ---- 5) 清理旧的「全量版」同名分卷文件 ----
    if clean_old:
        os.makedirs(TRASH_DIR, exist_ok=True)
        moved = 0
        for name in LEGACY_FULL_FILES:
            p = os.path.join(OUT_DIR, name)
            if os.path.exists(p):
                shutil.move(p, os.path.join(TRASH_DIR, name))
                moved += 1
        print(f"[--clean-old] 旧版全量文件移入 {TRASH_DIR} : {moved} 个")


if __name__ == "__main__":
    main()
