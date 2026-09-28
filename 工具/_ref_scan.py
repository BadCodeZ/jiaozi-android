# -*- coding: utf-8 -*-
"""解析字母引用全量分类：判定哪处可安全改写、哪处必须跳过。
只读，不写题库。输出聚类 + 待人工裁定样本。
"""
import json, re, os, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"D:\WorkBuddyPlace\软件\_removed_assets_20260928\bank.json"
BANKS = os.path.join(ROOT, "banks")

src = json.load(open(SRC, encoding="utf-8"))
src_ids = set(str(q.get("id")) for q in src["exam"])

alls = []
for fn in sorted(os.listdir(BANKS)):
    if not fn.endswith(".json"):
        continue
    d = json.load(open(os.path.join(BANKS, fn), encoding="utf-8"))
    for q in d.get("exam", []):
        alls.append((fn, q))

seen = set()
newuniq = []
for fn, q in alls:
    k = str(q.get("id"))
    if k in src_ids or k in seen:
        continue
    seen.add(k)
    newuniq.append((fn, q))
print("新增唯一题:", len(newuniq))

HEAD = re.compile(r'([A-D])\.')
def parse_opt(s):
    ms = list(HEAD.finditer(s))
    if len(ms) != 4 or [m.group(1) for m in ms] != ['A','B','C','D'] or ms[0].start() != 0:
        return None
    segs = []
    for i, m in enumerate(ms):
        end = ms[i+1].start() if i+1 < len(ms) else len(s)
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

# 邻域「拉丁/数学」字符集
LATIN_NUM = set("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
MATH_SYM  = set("=+-×·÷∩∪≤≥<>|/\\^{}[]()%$")
SUBSCRIPT = set("₀₁₂₃₄₅₆₇₈₉⁺⁻ⁿ")
ALLTECH = LATIN_NUM | MATH_SYM | SUBSCRIPT

STRONG_PRE = re.compile(r'(故选|应选|答案选|答案为|正确选项为|选项|选择)\s*([A-D])')
STRONG_POST = re.compile(r'([A-D])\s*(项正确|项为正确|正确|项错误)')

cat = collections.Counter()
samples = collections.defaultdict(list)
judge_pool = []

# 统计各字段字母出现
field_cnt = collections.Counter()

for fn, q in newuniq:
    qid = str(q.get("id"))
    p = parse_opt(q.get("opt", ""))
    if not p:
        continue
    segs, seps = p
    an = q.get("analysis", "") or ""
    seg_head3 = [re.sub(r'\s+', '', s)[:3] for s in segs]

    for m in re.finditer(r'[A-D]', an):
        i = m.start(); L = m.group(0)
        prev = an[i-1] if i > 0 else ''
        nxt = an[i+1] if i+1 < len(an) else ''
        ctxL = an[max(0,i-10):i]
        ctxR = an[i+1:i+16]

        # 1) 科技符号邻域 → 跳过
        if prev in ALLTECH or nxt in ALLTECH:
            c = 'SKIP_技术邻域'
        # 2) 强引用模式
        elif STRONG_PRE.search(an[max(0,i-6):i+1]) or STRONG_POST.search(an[i:i+4]):
            c = 'REF_强模式'
        else:
            # 3) 内容锚定：字母后 12 字内是否含对应段前3字
            nxtn = re.sub(r'\s+', '', an[i+1:i+14])
            hit = seg_head3[ord(L) - 65]
            if hit and len(hit) >= 2 and hit in nxtn:
                c = 'REF_内容锚定(后)'
            elif hit and len(hit) >= 2 and re.sub(r'\s+','',ctxL)[-6:].find(hit) >= 0:
                c = 'REF_内容锚定(前)'
            else:
                c = 'UNSAFE_待判'
                judge_pool.append((qid, L, prev, nxt, an[max(0,i-14):i+18]))
        cat[c] += 1
        if len(samples[c]) < 6:
            samples[c].append((qid, L, prev, nxt, an[max(0,i-12):i+20]))

print("\n=== 解析字母引用分类 ===")
for k, v in cat.most_common():
    print(f"{v:6d}  {k}")

print("\n=== 各类样本 ===")
for k in cat:
    print(f"\n--- {k} ---")
    for s in samples[k]:
        print("   ", s[0], "|字母", s[1], "|前", repr(s[2]), "|后", repr(s[3]), "|", repr(s[4]))

print(f"\n=== UNSAFE 待判池 {len(judge_pool)} 处（全量）===")
for j in judge_pool[:120]:
    print("   ", j[0], "|", j[1], "|前", repr(j[2]), "|后", repr(j[3]), "|", repr(j[4]))

# 其他字段是否含字母
print("\n=== 其他字段字母出现 ===")
for fld in ['q', 'point', 'topic', 'chapter', 'section', 'level']:
    n = 0
    for fn, q in newuniq:
        v = q.get(fld)
        if isinstance(v, str):
            n += len(re.findall(r'(?<![A-Za-z0-9])[A-D](?![A-Za-z0-9])', v))
    print("  ", fld, n)
