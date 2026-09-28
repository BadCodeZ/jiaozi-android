# -*- coding: utf-8 -*-
"""源题题干清洗：截掉 `q` 字段尾部内嵌的整块选项文本
============================================================
背景：权威源 bank.json（3342 条）中 1390 条题目的 `q` 字段把整块选项
      （与 `opt` 完全同文）重复写进了题干尾部，形如
        q   = '教育的最基本职能是（ ）。\\nA. 培养人才\\nB. …\\nC. …\\nD. …'
        opt = 'A. 培养人才\\nB. 传承文化\\nC. 服务社会\\nD. 发展科学'
      App `PracticeSession.kt:261` 直接 `Text(qq.q)` 渲染题干、又用
      `parseOptions(opt)` 渲染选项 ⇒ 用户看到「题干 + 两遍选项」。

策略（安全优先）：
  1. 仅截断，**不改 answer / analysis / opt 任何字段**（源题答案分布本就正常）。
  2. 判据必须「严格尾匹配」：q 必须以 opt（可选前置一个 \\n）结尾，否则跳过。
  3. 截断后题干非空且长度 ≥ 6，且含问句/提示特征（（ ？ 。 ： 等），否则跳过。
  4. 同 id 跨学段条目同步清洗（全局按 id 对齐）。
  5. 写回保持「紧凑单行 JSON + 纯 LF + 无 BOM」。

用法：
  python 工具/clean_src_stems.py --dry
  python 工具/clean_src_stems.py --apply
"""
import json, re, os, sys, shutil, time, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"D:\WorkBuddyPlace\软件\_removed_assets_20260928\bank.json"
BANKS = os.path.join(ROOT, "banks")
APPLY = "--apply" in sys.argv

src = json.load(open(SRC, encoding="utf-8"))
src_ids = set(str(q.get("id")) for q in src["exam"])
print(f"[源] {len(src['exam'])} 条 / 唯一 id {len(src_ids)}")

files, order = {}, []
for fn in sorted(os.listdir(BANKS)):
    if not fn.endswith(".json"):
        continue
    files[fn] = json.loads(open(os.path.join(BANKS, fn), "rb").read().decode("utf-8"))
    order.append(fn)
print(f"[仓] {len(order)} 文件 / {sum(len(files[f]['exam']) for f in order)} 条目")


def _ok_head(head):
    head = head.rstrip()
    if len(head) < 6:
        return None
    if not re.search(r"[（(？?。：:]", head):
        return None
    return head


def strip_opt_tail(qs, opt):
    """严格尾匹配：q = head (+'\\n') + opt ⇒ 返回 head；否则 None"""
    if not opt.strip():
        return None
    if qs.endswith("\n" + opt):
        head = qs[: -(len(opt) + 1)]
    elif qs.endswith(opt):
        head = qs[: -len(opt)]
    else:
        return None
    return _ok_head(head)


# 宽松尾匹配：尾部是一整块 A./B./C./D. 四段，且与 opt 高度相似（≥0.90）
# 用于修同一缺陷的「变体」——题干内嵌块与 opt 存在个别字符差异，严格尾匹配会漏。
SAME_OK = 0.90


def strip_opt_tail_loose(qs, opt):
    if not opt.strip():
        return None
    m = re.search(r"(?:^|\n)[ \t]*A\.[ \t]*", qs)
    if not m:
        return None
    head = qs[: m.start()]
    tail = qs[m.start():].lstrip("\n")
    # 结构校验：四段齐全且严格 ABCD 顺序
    ms = list(re.finditer(r"(?:^|\n)[ \t]*([A-D])\.[ \t]*", tail))
    if [x.group(1) for x in ms] != ["A", "B", "C", "D"]:
        return None
    import difflib
    r = difflib.SequenceMatcher(None, tail, opt).ratio()
    if r < SAME_OK:
        return None
    return _ok_head(head)


# ---------------------------------------------------------------- 出计划
stat = collections.Counter()
plan = []          # [(fn, idx, head)]
per_id = {}        # id -> head（首次遇到即可，同 id 应一致）
conflict = []
loose_samples = []
for fn in order:
    for idx, q in enumerate(files[fn]["exam"]):
        qid = str(q.get("id"))
        qs = q.get("q", "") or ""
        opt = q.get("opt", "") or ""
        head = strip_opt_tail(qs, opt)
        mode = "严格"
        if head is None:
            head = strip_opt_tail_loose(qs, opt)
            mode = "宽松(变体)"
        if head is None:
            stat["跳过:无尾内嵌块"] += 1
            continue
        is_src = qid in src_ids
        stat[f"命中:{mode}:{'源题' if is_src else '新增题'}"] += 1
        if mode.startswith("宽松") and len(loose_samples) < 5:
            import difflib
            tail = qs[re.search(r"(?:^|\n)[ \t]*A\.[ \t]*", qs).start():].lstrip("\n")
            loose_samples.append((fn, qid, head, tail, opt, difflib.SequenceMatcher(None, tail, opt).ratio()))
        if qid in per_id and per_id[qid] != head:
            conflict.append(qid)
        per_id[qid] = head
        plan.append((fn, idx, head))
        stat["命中:合计"] += 1

print("\n=== 处理统计 ===")
for k, v in sorted(stat.items()):
    print(f"  {v:6d}  {k}")
print(f"\n[计划] 待截断条目 {len(plan)} / 唯一 id {len(per_id)}")
print(f"[同 id 冲突] {len(conflict)}")

print("\n=== 样本（前 5） ===")
for fn, idx, head in plan[:5]:
    q = files[fn]["exam"][idx]
    print(f"  {fn} {q.get('id')}")
    print(f"    旧: {q.get('q')[:110]!r}")
    print(f"    新: {head[:110]!r}")

if loose_samples:
    print("\n=== 宽松(变体)命中明细 ===")
    for fn, qid, head, tail, opt, r in loose_samples:
        print(f"  {fn} {qid}  相似度 {r:.3f}")
        print(f"    题干块: {tail[:120]!r}")
        print(f"    opt   : {opt[:120]!r}")
        print(f"    截断后: {head[:120]!r}")

if not APPLY:
    print("\nDRY-RUN 结束，未写任何文件。（加 --apply 执行重建）")
    sys.exit(0)

# ---------------------------------------------------------------- 应用
ts = time.strftime("%Y%m%d_%H%M%S")
bak = os.path.join(ROOT, f"banks_backup_srcstem_{ts}")
shutil.copytree(BANKS, bak)
print(f"\n[备份] {bak}")

changed = 0
for fn, idx, head in plan:
    files[fn]["exam"][idx]["q"] = head
    changed += 1

for fn in order:
    with open(os.path.join(BANKS, fn), "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(files[fn], ensure_ascii=False, separators=(",", ":")))
print(f"[写入] {len(order)} 文件 / 截断条目 {changed}")

# ---------------------------------------------------------------- 守恒校验
print("\n=== 守恒校验 ===")
tot, ids, residual, answer_drift = 0, set(), 0, 0
for fn in order:
    raw = open(os.path.join(BANKS, fn), "rb").read()
    assert b"\r\n" not in raw and not raw.startswith(b"\xef\xbb\xbf"), f"{fn} 格式异常"
    d = json.loads(raw.decode("utf-8"))
    for q in d["exam"]:
        tot += 1
        ids.add(str(q.get("id")))
        qs = q.get("q", "") or ""
        opt = q.get("opt", "") or ""
        if opt.strip() and (qs.endswith(opt) or qs.endswith("\n" + opt)):
            residual += 1
print(f"  条目总数: {tot}（应 6669）{'OK' if tot == 6669 else 'FAIL'}")
print(f"  唯一 id : {len(ids)}（应 4784）{'OK' if len(ids) == 4784 else 'FAIL'}")
print(f"  残留尾内嵌块: {residual}（应 0）{'OK' if residual == 0 else 'FAIL'}")

# 与备份比对：仅 q 字段变化
diff_fields = collections.Counter()
for fn in order:
    old = json.loads(open(os.path.join(bak, fn), "rb").read().decode("utf-8"))
    new = files[fn]
    assert len(old["exam"]) == len(new["exam"])
    for a, b in zip(old["exam"], new["exam"]):
        for k in set(a) | set(b):
            if a.get(k) != b.get(k):
                diff_fields[k] += 1
print(f"  逐题字段变化: {dict(diff_fields)}（应仅 {{'q': 1390}}）")
print("完成。")
