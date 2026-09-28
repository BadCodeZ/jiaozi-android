# -*- coding: utf-8 -*-
"""解析字母引用 —— 召回优先的判定 + 全量人工可核列表。
只读，不写题库。
"""
import json, re, os, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"D:\WorkBuddyPlace\软件\_removed_assets_20260928\bank.json"
BANKS = os.path.join(ROOT, "banks")

src = json.load(open(SRC, encoding="utf-8"))
src_ids = set(str(q.get("id")) for q in src["exam"])
uniq = {}
for fn in sorted(os.listdir(BANKS)):
    if not fn.endswith(".json"):
        continue
    d = json.load(open(os.path.join(BANKS, fn), encoding="utf-8"))
    for q in d.get("exam", []):
        k = str(q.get("id"))
        if k not in uniq:
            uniq[k] = q
new_ids = [k for k in uniq if k not in src_ids]
print("新增唯一题:", len(new_ids))

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

# ---- 技术语境（整题冻结）----
FREEZE = re.compile(
    r'[A-Za-z]{2,}'                       # 拉丁串
    r'|[₀₁₂₃₄₅₆₇₈₉ⁿ⁺⁻]'                    # 上下标
    r'|[≤≥≠±×÷∩∪∈∉√∫∑∞πΩ≈⊥∥∠°]'             # 数学/单位符号
    r'|[A-D]\s*[=＝]|[=＝]\s*[A-D]'         # 变量赋值 C = 2πr / A=5
    r'|(?<=非)[A-D]|[A-D](?=则)'           # 命题 非B则非A
    r'|事件[A-D]|[A-D]事件'                 # 事件命名
    r'|以上都|以上均|全都对|全都不对|都正确|都不正确'  # 位置敏感
    r'|选项顺序|按顺序|依次排列'
)

STRONG = re.compile(r'(?:故选|应选|答案选|答案为|答案是|答案|选项|选择|选)\s?([A-D])')
STRONG_POST = re.compile(r'^([A-D])\s*(?=项|正确|错误)')
DISC_WORDS = set("项为应是缺句逗两全同义最符属短引巴曹钱均不也一和与或由经成判代表附选")
PRE_OK = set("。，、；：（）《‘“” \n\t")
POST_OK = set("。，、；：》‘“” \n\t")

def classify(an):
    rw, sk = [], []
    for m in re.finditer(r'[A-D]', an):
        i = m.start(); L = m.group(0)
        prev = an[i-1] if i > 0 else ''
        nxt = an[i+1] if i+1 < len(an) else ''
        ok = False
        if STRONG.search(an[max(0, i-6):i+1]):
            ok = True
        elif STRONG_POST.match(an[i:i+4]):
            ok = True
        elif nxt in DISC_WORDS:
            ok = True
        elif prev == '（' or prev == '(':
            ok = True            # 括号内单字母 → 必为选项引用
        elif (prev in PRE_OK or i == 0) and nxt not in "A-D" and (nxt in POST_OK or nxt in DISC_WORDS or nxt in "《‘“（）"):
            ok = True
        elif prev in "、，；和与" and nxt in "、，；。》":
            ok = True
        elif prev in "《‘“" and nxt in "》、《‘“":
            ok = True
        (rw if ok else sk).append((i, L, prev, nxt, an[max(0, i-14):i+18]))
    return rw, sk

stat = collections.Counter()
sk_all = []
rw_cnt = 0
for qid in new_ids:
    q = uniq[qid]
    p = parse_opt(q.get("opt", ""))
    if p is None:
        stat["opt异常"] += 1; continue
    segs, seps = p
    an = q.get("analysis", "") or ""
    blob = an + " " + " ".join(segs)
    fm = FREEZE.search(blob)
    if fm:
        stat["硬冻结"] += 1
        continue
    rw, sk = classify(an)
    rw_cnt += len(rw)
    if sk:
        stat["因引用歧义冻结"] += 1
        sk_all.extend((qid, s) for s in sk)
    else:
        stat["可重排"] += 1

print("\n=== 统计 ===")
for k, v in stat.most_common():
    print(f"  {v:6d}  {k}")
print("可重排题中改写点合计:", rw_cnt)

print(f"\n=== 全量待判（{len(sk_all)} 处）===")
for qid, s in sk_all:
    print(f"  {qid} |{s[1]}| 前{repr(s[2])} 后{repr(s[3])} | …{s[4]}…")
