# -*- coding: utf-8 -*-
"""GitHub Git Data API 推送器
用途：当 github.com:443 (git push) 被网络阻断、但 api.github.com 可达时，
      用纯 REST 完成 commit + 更新 ref，等价于 git push。

用法：python push_via_api.py <repo_slug> <branch> <local_repo_dir> <msg_file> <path1> [path2 ...]
"""
import base64, json, os, ssl, subprocess, sys, time, urllib.error, urllib.request

REPO, BRANCH, LOCALDIR, MSGFILE = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
PATHS = sys.argv[5:]

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
            req.add_header("User-Agent", "JiaoziPush/1.0")
            if body:
                req.add_header("Content-Type", "application/json")
            with OPENER.open(req, timeout=180) as r:
                raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            detail = e.read().decode()[:400]
            # 4xx（除限流）不重试
            if e.code not in (403, 429, 500, 502, 503, 504) or i == tries:
                raise SystemExit(f"HTTP {e.code} on {method} {path}\n{detail}")
            last = f"HTTP {e.code} {detail}"
        except Exception as e:
            last = repr(e)
        print(f"   retry {i}/{tries} ({last})", flush=True)
        time.sleep(2 * i)
    raise SystemExit(f"failed after {tries}: {last}")

def blob(abspath, rel):
    raw = open(abspath, "rb").read()
    s, d = api("POST", f"/repos/{REPO}/git/blobs",
               {"content": base64.b64encode(raw).decode(), "encoding": "base64"})
    print(f"   blob {rel}  {len(raw):>9,} B -> {d['sha'][:10]}", flush=True)
    return d["sha"]

# 1) 当前 head
s, d = api("GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}")
head = d["object"]["sha"]
s, d = api("GET", f"/repos/{REPO}/git/commits/{head}")
base_tree = d["tree"]["sha"]
print(f"[1] head={head[:12]} base_tree={base_tree[:12]}")

# 2) blobs + tree entries
print("[2] uploading blobs ...")
entries = []
for rel in PATHS:
    ap = os.path.join(LOCALDIR, rel.replace("/", os.sep))
    if not os.path.isfile(ap):
        raise SystemExit("missing local file: " + ap)
    entries.append({"path": rel, "mode": "100755" if rel == "gradlew" else "100644",
                    "type": "blob", "sha": blob(ap, rel)})

# 3) tree
print("[3] creating tree ...")
s, d = api("POST", f"/repos/{REPO}/git/trees",
           {"base_tree": base_tree, "tree": entries})
tree = d["sha"]
print(f"   tree={tree[:12]}  entries={len(entries)}")

# 4) commit
msg = open(MSGFILE, "r", encoding="utf-8").read()
print("[4] creating commit ...")
s, d = api("POST", f"/repos/{REPO}/git/commits",
           {"message": msg, "tree": tree, "parents": [head]})
commit = d["sha"]
print(f"   commit={commit[:12]}")

# 5) update ref
print("[5] updating ref ...")
s, d = api("PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}",
           {"sha": commit, "force": False})
print(f"   {BRANCH} -> {d['object']['sha'][:12]}")
print("PUSH_OK " + commit)
