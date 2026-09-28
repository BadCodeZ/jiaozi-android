# -*- coding: utf-8 -*-
"""GitHub Git Data API 推送器 v2（相比 v1 增加「删除文件」能力）

用途：github.com:443 (git push) 被网络阻断、但 api.github.com 可达时，
      用纯 REST 完成 commit + 更新 ref，等价于 git push。

与 v1 的差异：
  - 不再需要手工列 PATHS；自动用 `git diff --name-status <remote_head> <local_head>`
    算出 A/M/D 三类变化。
  - D 类以 {"path": ..., "mode": ..., "type": "blob", "sha": None} 表达，
    即从 base_tree 中摘除该条目。

用法：python push_via_api2.py <repo_slug> <branch> <local_repo_dir> <msg_file>
"""
import base64, json, os, ssl, subprocess, sys, time, urllib.error, urllib.request

REPO, BRANCH, LOCALDIR, MSGFILE = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]


def run(*args):
    p = subprocess.run(list(args), cwd=LOCALDIR, capture_output=True, text=True)
    if p.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed: {p.stderr[:400]}")
    return p.stdout


def get_token():
    out = subprocess.run(["git", "credential", "fill"],
                         input="protocol=https\nhost=github.com\n\n",
                         capture_output=True, text=True).stdout
    for l in out.splitlines():
        if l.startswith("password="):
            return l.split("=", 1)[1].strip()
    raise SystemExit("no token")


TOKEN = get_token()
OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({}),
    urllib.request.HTTPSHandler(context=ssl.create_default_context()),
)


def api(method, path, data=None, tries=5):
    url = "https://api.github.com" + path
    body = json.dumps(data).encode() if data is not None else None
    last = None
    for i in range(1, tries + 1):
        try:
            req = urllib.request.Request(url, data=body, method=method)
            req.add_header("Authorization", "Bearer " + TOKEN)
            req.add_header("Accept", "application/vnd.github+json")
            req.add_header("User-Agent", "JiaoziPush/2.0")
            if body:
                req.add_header("Content-Type", "application/json")
            with OPENER.open(req, timeout=180) as r:
                raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:400]
            if e.code not in (403, 429, 500, 502, 503, 504) or i == tries:
                raise SystemExit(f"HTTP {e.code} on {method} {path}\n{detail}")
            last = f"HTTP {e.code} {detail}"
        except Exception as e:
            last = repr(e)
        print(f"   retry {i}/{tries} ({last})", flush=True)
        time.sleep(2 * i)
    raise SystemExit(f"failed after {tries}: {last}")


# 1) 远端 head
s, d = api("GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}")
remote_head = d["object"]["sha"]
s, d = api("GET", f"/repos/{REPO}/git/commits/{remote_head}")
base_tree = d["tree"]["sha"]
print(f"[1] remote_head={remote_head[:12]} base_tree={base_tree[:12]}", flush=True)

# 本地 head 必须存在且以 remote_head 为祖先
local_head = run("git", "rev-parse", "HEAD").strip()
print(f"[2] local_head={local_head[:12]}", flush=True)
if local_head == remote_head:
    raise SystemExit("nothing to push (local == remote)")
try:
    run("git", "merge-base", "--is-ancestor", remote_head, local_head)
except SystemExit:
    raise SystemExit("remote_head 不是 local_head 的祖先，先 git fetch/merge 再推送")

# 2) 差异
changed = run("git", "diff", "--name-status", "--no-renames", remote_head, local_head)
mods, dels = [], []
for line in changed.splitlines():
    parts = line.split("\t")
    if len(parts) < 2:
        continue
    st, p = parts[0].strip(), parts[1].strip()
    if st == "D":
        dels.append(p)
    elif st in ("A", "M", "T"):
        mods.append(p)
print(f"[3] 变化：A/M={len(mods)}  D={len(dels)}", flush=True)


def blob_sha(rel):
    ap = os.path.join(LOCALDIR, rel.replace("/", os.sep))
    raw = open(ap, "rb").read()
    s2, d2 = api("POST", f"/repos/{REPO}/git/blobs",
                 {"content": base64.b64encode(raw).decode(), "encoding": "base64"})
    print(f"    blob {rel}  {len(raw):>9,} B -> {d2['sha'][:10]}", flush=True)
    return d2["sha"]


def mode_of(rel):
    out = run("git", "ls-files", "-s", "--", rel).strip()
    if out:
        return out.split()[0]
    return "100644"


entries = []
print("[4] uploading blobs ...", flush=True)
for rel in mods:
    entries.append({"path": rel, "mode": mode_of(rel), "type": "blob", "sha": blob_sha(rel)})
for rel in dels:
    entries.append({"path": rel, "mode": mode_of(rel), "type": "blob", "sha": None})
    print(f"    delete {rel}", flush=True)

# 3) tree
print("[5] creating tree ...", flush=True)
s, d = api("POST", f"/repos/{REPO}/git/trees",
           {"base_tree": base_tree, "tree": entries})
tree = d["sha"]
print(f"    tree={tree[:12]}  entries={len(entries)}", flush=True)

# 4) commit
msg = open(MSGFILE, "r", encoding="utf-8").read()
print("[6] creating commit ...", flush=True)
s, d = api("POST", f"/repos/{REPO}/git/commits",
           {"message": msg, "tree": tree, "parents": [remote_head]})
commit = d["sha"]
print(f"    commit={commit[:12]}", flush=True)

# 5) update ref
print("[7] updating ref ...", flush=True)
s, d = api("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}",
           {"sha": commit, "force": False})
print(f"    {BRANCH} -> {d['object']['sha'][:12]}", flush=True)
print("PUSH_OK " + commit)
