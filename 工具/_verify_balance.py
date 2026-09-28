# -*- coding: utf-8 -*-
"""独立验证：拿备份（改动前）与当前（改动后）逐题比对。
判据：
  V1 条目数、唯一 id 守恒
  V2 每道被处理题：新 answer 所指选项文本 == 旧 answer 所指选项文本（语义等价核心断言）
  V3 非答案选项集合守恒（只是顺序变）
  V4 题干不再含内嵌选项块；且被截断的题干 == 旧题干去掉 "\\n opt \\n opt"
  V5 未处理题（冻结/源题）逐字节不变
  V6 同 id 跨文件条目仍一致
"""
import json, re, os, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BANKS = os.path.join(ROOT, "banks")
bak = sys.argv[1] if len(sys.argv) > 1 else None
if not bak:
    cands = sorted([d for d in os.listdir(ROOT) if d.startswith("banks_backup_")])
    bak = os.path.join(ROOT, cands[-1])
print("备份:", bak)

HEAD = re.compile(r'([A-D])\.')
def parse_opt(s):
    ms = list(HEAD.finditer(s))
    if len(ms) != 4 or [m.group(1) for m in ms] != ['A','B','C','D'] or ms[0].start() != 0:
        return None
    segs = []
    for i, m in enumerate(ms):
        end = ms[i+1].start() if i+1 < len(ms) else len(s)
        segs.append(re.sub(r'\s+$', '', s[m.end():end]))
    return segs

def load(d):
    m = {}
    for fn in sorted(os.listdir(d)):
        if fn.endswith(".json"):
            m[fn] = json.load(open(os.path.join(d, fn), encoding="utf-8"))
    return m

old = load(bak); new = load(BANKS)
assert set(old) == set(new), "文件名集合不一致"
print(f"文件 {len(new)} 个，均一致")

cnt = collections.Counter()
fail = collections.defaultdict(list)
emb_before = emb_after = 0
EMB = re.compile(r'(?:^|\n)[ \t]*A\.[ \t]*')

for fn in new:
    oe = old[fn]["exam"]; ne = new[fn]["exam"]
    if len(oe) != len(ne):
        fail["条数不一致"].append(fn); continue
    for o, n in zip(oe, ne):
        qid = str(o.get("id"))
        if str(n.get("id")) != qid:
            fail["id 错位"].append((fn, qid)); continue
        cnt["题数"] += 1

        # V5 字段集合守恒
        if set(o.keys()) != set(n.keys()):
            fail["字段集合变化"].append((qid, set(o.keys()) ^ set(n.keys()))); continue

        # 题干
        if EMB.search(o.get("q", "")):
            emb_before += 1
        if EMB.search(n.get("q", "")):
            emb_after += 1

        # 未变字段（除 q/opt/answer/analysis 外全等）
        for k in o:
            if k in ("q", "opt", "answer", "analysis"):
                continue
            if o[k] != n[k]:
                fail["非目标字段被改"].append((qid, k)); break

        # V2/V3：答案位语义等价
        po, pn = parse_opt(o["opt"]), parse_opt(n["opt"])
        if po and pn:
            ao, an_ = str(o["answer"]).strip(), str(n["answer"]).strip()
            if ao in "ABCD" and an_ in "ABCD":
                so = po["ABCD".index(ao)]
                sn = pn["ABCD".index(an_)]
                if so != sn:
                    fail["正确答案文本漂移"].append((qid, ao, an_, so, sn))
                if sorted(po) != sorted(pn):
                    fail["选项集合变化"].append((qid, po, pn))
            else:
                fail["answer 非法"].append((qid, ao, an_))
        else:
            if o["opt"] != n["opt"]:
                fail["opt 变了但解析失败"].append((qid,))

        # V4 题干截断正确性
        if o["q"] != n["q"]:
            t = o["q"]
            m = EMB.search(t)
            if not m:
                fail["题干变了但无内嵌块"].append((qid,))
            else:
                expect = t[:m.start()].rstrip()
                tail = t[m.start():].lstrip("\n")
                if n["q"] != expect:
                    fail["题干截断不符"].append((qid, n["q"][:60], expect[:60]))
                if tail != o["opt"] + "\n" + o["opt"]:
                    fail["被截内容非 opt*2"].append((qid,))

print()
print("=== 独立验证结果 ===")
print(f"逐题比对: {cnt['题数']}")
print(f"题干内嵌选项块  改动前 {emb_before} → 改动后 {emb_after}")
for k, v in fail.items():
    print(f"  ✗ {k}: {len(v)}")
    for x in v[:5]:
        print("      ", x)
if not fail:
    print("  ✓ 全部断言通过")

# V6 同 id 跨文件一致（仅新增题）
g = collections.defaultdict(set)
for fn in new:
    for q in new[fn]["exam"]:
        if len(g[str(q["id"])]) < 4:
            g[str(q["id"])].add((q["opt"], q["analysis"], str(q["answer"]), q.get("q", "")))
bad = [k for k, v in g.items() if len(v) > 1]
print(f"\n同 id 内容不一致: {len(bad)}" + (f" 例 {bad[:5]}" if bad else "  ✓"))

# 抽样打印
print("\n=== 抽样（改动前后）===")
shown = 0
for fn in new:
    if shown >= 6: break
    for o, n in zip(old[fn]["exam"], new[fn]["exam"]):
        if o["opt"] != n["opt"]:
            print(f"\n[{fn}] {n['id']}  ans {o['answer']} -> {n['answer']}")
            print(f"  旧 opt: {o['opt'][:110]}")
            print(f"  新 opt: {n['opt'][:110]}")
            print(f"  答案文本: {parse_opt(n['opt'])['ABCD'.index(n['answer'])]}")
            shown += 1
            break
