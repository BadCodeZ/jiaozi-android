# -*- coding: utf-8 -*-
"""侦察：选项串无损解析验证 + 解析字母引用上下文抽样。
只读，不写任何题库文件。
判据：恒等置换下 rebuild(parse(opt)) == opt 必须逐字节相等（round-trip 断言）。
"""
import json, re, os, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"D:\WorkBuddyPlace\软件\_removed_assets_20260928\bank.json"
BANKS = os.path.join(ROOT, "banks")

src = json.load(open(SRC, encoding="utf-8"))
src_exam = src["exam"] if isinstance(src, dict) and "exam" in src else src
src_ids = set(str(q.get("id")) for q in src_exam)
print("源题数:", len(src_exam), "源唯一id:", len(src_ids))

# 收集仓库全部题目
alls = []
for fn in sorted(os.listdir(BANKS)):
    if not fn.endswith(".json"):
        continue
    d = json.load(open(os.path.join(BANKS, fn), encoding="utf-8"))
    for q in d.get("exam", []):
        alls.append((fn, q))
print("仓库条目:", len(alls))

newq = [(fn, q) for fn, q in alls if str(q.get("id")) not in src_ids]
# 去重（同 id 可能出现在 junior/senior 两版）
seen = set()
newuniq = []
for fn, q in newq:
    k = str(q.get("id"))
    if k in seen:
        continue
    seen.add(k)
    newuniq.append((fn, q))
print("新增题条目:", len(newq), "新增唯一id:", len(newuniq))

# ---- 解析器 ----
# 选项串形态：以 "A." / "B." / "C." / "D." 四段，段头为 "X."（无空格紧跟文本）
# 段间分隔：双空格 或 \n（或前后混）
HEAD = re.compile(r'([A-D])\.')

def parse_opt(s):
    """返回 (heads, segs, seps) ；heads 恒 ['A','B','C','D']；segs 4 段文本；seps 3 个分隔符。"""
    ms = list(HEAD.finditer(s))
    if len(ms) != 4:
        return None
    letters = [m.group(1) for m in ms]
    if letters != ['A', 'B', 'C', 'D']:
        return None
    if ms[0].start() != 0:
        return None
    segs = []
    seps = []
    for i, m in enumerate(ms):
        start = m.end()
        end = ms[i + 1].start() if i + 1 < len(ms) else len(s)
        segs.append(s[start:end])
    # 段间分隔：把 segs[i] 的尾部空白切出来
    clean = []
    for i in range(4):
        if i < 3:
            m = re.search(r'(\s+)$', segs[i])
            if m:
                seps.append(m.group(1))
                clean.append(segs[i][:m.start()])
            else:
                seps.append('')
                clean.append(segs[i])
        else:
            clean.append(segs[i])
    return letters, clean, seps

def rebuild(segs, seps):
    out = []
    for i in range(4):
        out.append(chr(ord('A') + i) + '.' + segs[i])
        if i < 3:
            out.append(seps[i])
    return ''.join(out)

# round-trip 校验
rt_ok = 0
rt_bad = []
parse_fail = []
sepstat = collections.Counter()
for fn, q in newuniq:
    opt = q.get("opt", "")
    p = parse_opt(opt)
    if p is None:
        parse_fail.append((fn, str(q.get("id")), opt[:80]))
        continue
    letters, segs, seps = p
    if rebuild(segs, seps) == opt:
        rt_ok += 1
    else:
        rt_bad.append((fn, str(q.get("id")), opt[:120], rebuild(segs, seps)[:120]))
    sepstat[tuple(repr(x) for x in seps)] += 1

print("\n=== round-trip ===")
print("解析成功且恒等重建逐字节相等:", rt_ok, "/", len(newuniq))
print("round-trip 不一致:", len(rt_bad))
for r in rt_bad[:5]:
    print("  BAD", r)
print("解析失败(非严格ABCD/X.开头):", len(parse_fail))
for r in parse_fail[:8]:
    print("  FAIL", r)

print("\n=== 分隔符风格 top ===")
for k, v in sepstat.most_common(12):
    print("  ", v, k)

# 空段检查
empty_seg = sum(1 for fn, q in newuniq if parse_opt(q.get("opt", "")) and any(s.strip() == "" for s in parse_opt(q["opt"])[1]))
print("\n存在空选项段的新增题:", empty_seg)

# 解析里字母引用的上下文抽样
print("\n=== 解析字母引用上下文（前 40 条）===")
cnt = 0
for fn, q in newuniq:
    an = q.get("analysis", "") or ""
    for m in re.finditer(r'[A-D]', an):
        s = max(0, m.start() - 8)
        e = min(len(an), m.end() + 8)
        ctx = an[s:e].replace("\n", "\\n")
        print("  ", str(q.get("id")), "|", ctx)
        cnt += 1
        break
    if cnt >= 40:
        break
