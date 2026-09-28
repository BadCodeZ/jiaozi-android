# -*- coding: utf-8 -*-
"""题库修复：①答案位均衡化 ②题干内嵌选项块清洗
============================================================
背景：仓库 banks/ 中 1442 道「无上游源」的新增题存在两处缺陷——
  A. 答案位严重偏斜（A 902 / B 438 / C 68 / D 34；科二新增题几乎全 A）
  B. 682 道题的题干字段内嵌了整块选项文本且重复两遍（App 中重复显示）

策略（安全优先，宁可少改不可改错）：
  1. 仅处理「新增题」＝ id 不在权威源 bank.json 的题；源题一律不动。
  2. 题干清洗：截断行首 `A. ` 起的整块内容；截断部分须严格等于
     `\\n + opt + \\n + opt`，否则跳过。
  3. 冻结（整题不动）的三种情形：
     a. 题干/选项含位置敏感表述（以上都对 / 全都正确 …）——重排会破坏语义
     b. 选项文本内含独立单字母（如 `A. B为真`）——选项互相指代
     c. 解析中存在「既无法确证为选项指代、又非技术符号」的字母 —— 改写有风险
  4. 解析字母分类（关键）：先标记技术语境（拉丁串 / 数学符号 / 上下标 /
     组合数 C(n,m) / 变量赋值 / 命题命名），这些位置一律跳过不改写；
     其余位置须命中确证引用模式才改写；未命中即判风险 ⇒ 冻结整题。
  5. 答案位确定性打散：按 md5(qid) 排序后循环 A/B/C/D，
     均衡且无「ABCD 循环」的人工痕迹。
  6. 同 id 跨学段条目必须一致 ⇒ 按唯一 id 出计划后回填全部出现位置。
  7. 写回保持「紧凑单行 JSON + 纯 LF + 无 BOM」，仅 4 空格无关。

用法：
  python 工具/balance_answers.py --dry     # 只统计，不写
  python 工具/balance_answers.py --apply   # 备份 + 重建 + 守恒校验
"""
import json, re, os, sys, hashlib, shutil, time, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"D:\WorkBuddyPlace\软件\_removed_assets_20260928\bank.json"
BANKS = os.path.join(ROOT, "banks")
APPLY = "--apply" in sys.argv
# 冻结整题（选项序位敏感）
POS_SENS = re.compile(r'以上都|以上均|全都对|全都不对|都正确|都不正确|均正确|均不正确|'
                      r'选项顺序|按顺序|依次排列|以上答案|以上各项')
# 解析中的技术语境（区间式：整段命中，其内字母一律不改写）
TECH = re.compile(
    r'[A-Za-z]{2,}'                        # 拉丁串 DNA/NaCl/Gauss/happy/Asin
    r'|[A-D][₀₁₂₃₄₅₆₇₈₉ⁿ⁺⁻]|[₀₁₂₃₄₅₆₇₈₉ⁿ⁺⁻][A-D]'   # 带上下标
    r'|[A-D]\s*\((?=[0-9])'                 # 组合数 C(2,1)
    r'|[A-D]\s*[=＝]|[=＝]\s*[A-D]'          # 变量赋值 C = 2πr
    r'|[A-D]\.[A-Z]'                        # 人名缩写 C.F.Gauss
    r'|(?<=非)[A-D]|[A-D](?=则)|[A-D]为(?:真|假)|[A-D]事件'
)
# 邻居式：字母紧邻下列字符 ⇒ 必为公式/缩写，而非选项指代（最保守）
TECH_NB = set(
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    "=+-*/\\^_<>|()[]{}.,;:!?'\"~$%&@#"
    "×·÷∩∪∈∉≤≥≠±∞∑∏∫≈∽⊥∥∠°′″√"
    "₀₁₂₃₄₅₆₇₈₉ⁿ⁺⁻"
    "πΩΣΔθαβγλμσφωΑΒΓ"
)
# 确证为选项指代的模式
STRONG = re.compile(r'(?:故选|应选|答案选|答案为|答案是|答案|选项|选择|选)\s?([A-D])')
STRONG_POST = re.compile(r'^([A-D])\s*(?=项|正确|错误)')
# 仅这两种前导字符可安全认定为「选项枚举」：句读标点 / 行首
PRE_OK = set("。，、；：（）()《‘“” \n\t")

def classify(an, tp):
    """tp = 技术区间内位置集合（跳过不改写，也不视为存疑）"""
    rw, sk = [], []
    for m in re.finditer(r'[A-D]', an):
        i = m.start(); L = m.group(0)
        if i in tp:
            continue                     # 技术语境，跳过
        prev = an[i - 1] if i > 0 else ''
        nxt = an[i + 1] if i + 1 < len(an) else ''
        if (i > 0 and prev in TECH_NB) or (nxt and nxt in TECH_NB):
            continue                     # 紧邻拉丁/数字/符号 ⇒ 公式或缩写
        if STRONG.search(an[max(0, i - 6):i + 1]) or STRONG_POST.match(an[i:i + 4]) \
                or ((i == 0 or prev in PRE_OK) and nxt not in "A-D"):
            rw.append((i, L, prev, nxt, an[max(0, i - 16):i + 20]))
        else:
            sk.append((i, L, prev, nxt, an[max(0, i - 16):i + 20]))
    return rw, sk

def tech_positions(an):
    """TECH 正则命中区间内的全部字符位置"""
    s = set()
    for m in TECH.finditer(an):
        for j in range(m.start(), m.end()):
            s.add(j)
    return s

def opt_self_ref(segs):
    """选项文本自身含独立单字母（选项互相指代）——传入已剥离 `X.` 前缀的段文本"""
    hits = []
    for i, sg in enumerate(segs):
        for m in re.finditer(r'(?<![A-Za-z0-9])[A-D](?![A-Za-z0-9])', sg):
            hits.append((chr(65 + i), m.group(0), sg[max(0, m.start() - 8):m.start() + 10]))
    return hits

# ---------------------------------------------------------------- 载入
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

groups = collections.defaultdict(list)
for fn in order:
    for idx, q in enumerate(files[fn]["exam"]):
        groups[str(q.get("id"))].append((fn, idx))
uniq = {qid: files[l[0][0]]["exam"][l[0][1]] for qid, l in groups.items()}
new_ids = [k for k in uniq if k not in src_ids]
print(f"[新增] 唯一 id {len(new_ids)}（出现位置 {sum(len(groups[k]) for k in new_ids)} 处）")

# ---------------------------------------------------------------- 选项解析
HEAD = re.compile(r'([A-D])\.')
def parse_opt(s):
    ms = list(HEAD.finditer(s))
    if len(ms) != 4 or [m.group(1) for m in ms] != ['A', 'B', 'C', 'D'] or ms[0].start() != 0:
        return None
    segs = []
    for i, m in enumerate(ms):
        end = ms[i + 1].start() if i + 1 < len(ms) else len(s)
        segs.append(s[m.end():end])
    clean, seps = [], []
    for i in range(3):
        mm = re.search(r'(\s+)$', segs[i])
        if mm:
            seps.append(mm.group(1)); clean.append(segs[i][:mm.start()])
        else:
            seps.append(''); clean.append(segs[i])
    clean.append(segs[3])
    return clean, seps

def build_opt(segs, seps):
    out = []
    for i in range(4):
        out.append(chr(ord('A') + i) + '.' + segs[i])
        if i < 3:
            out.append(seps[i])
    return ''.join(out)

def clean_stem(qs, opt):
    m = re.search(r'(?:^|\n)[ \t]*A\.[ \t]*', qs)
    if not m:
        return None
    head = qs[:m.start()].rstrip()
    if len(head) < 8:
        return None
    tail = qs[m.start():].lstrip('\n')
    if tail != opt + "\n" + opt:
        return None
    return head

# ---------------------------------------------------------------- 出计划
stat = collections.Counter()
frz_reason = collections.Counter()
cur, new_dist, frz_dist = collections.Counter(), collections.Counter(), collections.Counter()
stem_fixed, plan = [], {}
cand = []

for qid in new_ids:
    q = uniq[qid]
    opt = q.get("opt", ""); an = q.get("analysis", "") or ""
    stem = q.get("q", "") or ""
    ns = clean_stem(stem, opt)
    if ns is not None:
        stem_fixed.append(qid); stem = ns
    p = parse_opt(opt)
    if p is None:
        stat["跳过:opt非规范"] += 1; continue
    segs, seps = p
    ans = str(q.get("answer", "")).strip()
    if ans not in list("ABCD"):
        stat["跳过:answer异常"] += 1; continue
    cur[ans] += 1

    # a. 位置敏感
    if POS_SENS.search(stem) or POS_SENS.search(" ".join(segs)):
        stat["冻结:位置敏感"] += 1; frz_dist[ans] += 1
        frz_reason["位置敏感"] += 1; continue
    # b. 选项互相指代（选项文本内独立单字母，如 `A. B为真`）
    oh = opt_self_ref(segs)
    if oh:
        stat["冻结:选项互指"] += 1; frz_dist[ans] += 1
        frz_reason["选项互指|%s" % (oh[0][2],)] += 1; continue
    # c. 解析字母
    tp = tech_positions(an)
    rw, sk = classify(an, tp)
    if sk:
        stat["冻结:解析引用存疑"] += 1; frz_dist[ans] += 1
        frz_reason["解析存疑|%s|%s|%s" % (sk[0][2], sk[0][3], sk[0][4])] += 1; continue
    cand.append((qid, segs, seps, ans, rw, an))

cand.sort(key=lambda t: hashlib.md5(t[0].encode()).hexdigest())
for n, (qid, segs, seps, ans, rw, an) in enumerate(cand):
    target = "ABCD"[n % 4]
    mapping = {ans: target}
    for o, nn in zip([x for x in "ABCD" if x != ans], [x for x in "ABCD" if x != target]):
        mapping[o] = nn
    new_segs = [None] * 4
    for o in "ABCD":
        new_segs["ABCD".index(mapping[o])] = segs["ABCD".index(o)]
    na = list(an)
    for (i, L, *_) in rw:
        na[i] = mapping[L]
    plan[qid] = {"segs": new_segs, "seps": seps, "ans": target,
                 "mapping": mapping, "an": "".join(na), "rw": len(rw)}
    new_dist[target] += 1

# ---------------------------------------------------------------- 报告
fix = collections.Counter(frz_dist)
for L in "ABCD":
    fix[L] += new_dist.get(L, 0)
print(f"\n[题干清洗] 命中 {len(stem_fixed)} 题")
print("\n=== 处理统计 ===")
for k, v in stat.most_common():
    print(f"  {v:6d}  {k}")
print(f"\n新增题原答案分布: " + " ".join(f"{L}{cur[L]}" for L in "ABCD"))
print(f"  冻结题(不动): " + " ".join(f"{L}{frz_dist[L]}" for L in "ABCD") + f"  合计 {sum(frz_dist.values())}")
print(f"  重排题目标:   " + " ".join(f"{L}{new_dist.get(L,0)}" for L in "ABCD") + f"  合计 {sum(new_dist.values())}")
print(f"  ▶ 修复后整体: " + " ".join(f"{L}{fix[L]}" for L in "ABCD") + f"  合计 {sum(fix.values())}")
tot = sum(fix.values())
print("  占比: " + " ".join(f"{L} {fix[L]/tot*100:.1f}%" for L in "ABCD"))

print("\n=== 冻结原因 top12 ===")
for k, v in frz_reason.most_common(12):
    print(f"  {v:6d}  {k}")

print("\n=== 重排样本 ===")
c = 0
for qid, pp in plan.items():
    if pp["rw"] == 0:
        continue
    q = uniq[qid]
    print(f"  {qid}  {q.get('answer')} -> {pp['ans']}  改写{pp['rw']}处")
    print(f"    旧: {(q.get('analysis') or '')[:96]}")
    print(f"    新: {pp['an'][:96]}")
    c += 1
    if c >= 5:
        break

print("\n=== 题干清洗样本 ===")
for qid in stem_fixed[:3]:
    print(f"  {qid}")
    print(f"    旧: {uniq[qid]['q'][:120]!r}")
    print(f"    新: {clean_stem(uniq[qid]['q'], uniq[qid]['opt'])!r}")

bad = sum(1 for pp in plan.values()
          if sorted(pp["mapping"]) != list("ABCD") or sorted(pp["mapping"].values()) != list("ABCD"))
print(f"\n[mapping 双射] 非双射 {bad}")

if not APPLY:
    print("\nDRY-RUN 结束，未写任何文件。（加 --apply 执行重建）")
    sys.exit(0)

# ---------------------------------------------------------------- 应用
ts = time.strftime("%Y%m%d_%H%M%S")
bak = os.path.join(ROOT, f"banks_backup_{ts}")
shutil.copytree(BANKS, bak)
print(f"\n[备份] {bak}")

changed = 0
for fn in order:
    for q in files[fn]["exam"]:
        qid = str(q.get("id"))
        # 顺序要点：题干内嵌块是「旧 opt」，必须先按旧 opt 截断，再替换 opt
        if qid in stem_fixed:
            nq = clean_stem(q.get("q", ""), q.get("opt", ""))
            if nq is not None:
                q["q"] = nq
        if qid in plan:
            q["opt"] = build_opt(plan[qid]["segs"], plan[qid]["seps"])
            q["answer"] = plan[qid]["ans"]
            q["analysis"] = plan[qid]["an"]
            changed += 1
    with open(os.path.join(BANKS, fn), "w", encoding="utf-8", newline="") as f:
        f.write(json.dumps(files[fn], ensure_ascii=False, separators=(",", ":")))
print(f"[写入] {len(order)} 文件 / 改动条目 {changed}")

# ---------------------------------------------------------------- 守恒校验
print("\n=== 守恒校验 ===")
tot2, ans2, ids2, filesz = 0, collections.Counter(), set(), 0
for fn in order:
    raw = open(os.path.join(BANKS, fn), "rb").read()
    filesz += len(raw)
    assert b"\r\n" not in raw and not raw.startswith(b"\xef\xbb\xbf"), f"{fn} 格式异常"
    d = json.loads(raw.decode("utf-8"))
    for q in d["exam"]:
        tot2 += 1
        ids2.add(str(q.get("id")))
        if str(q.get("id")) not in src_ids:
            ans2[str(q.get("answer"))] += 1
print(f"  条目总数: {tot2}（应 6669）{'OK' if tot2 == 6669 else 'FAIL'}")
print(f"  唯一 id : {len(ids2)}（应 4784）{'OK' if len(ids2) == 4784 else 'FAIL'}")
print(f"  总字节  : {filesz:,}")
print(f"  新增题答案分布: " + " ".join(f"{L}{ans2[L]}" for L in "ABCD"))
print("完成。")
