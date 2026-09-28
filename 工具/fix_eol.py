# -*- coding: utf-8 -*-
"""修正远端仓库中被写成 CRLF 的文本文件：自动比对「远端 tree」与「本地 git 对象」，
对仅行尾不同的文件，用本地 LF blob 重新上传。

用法：python fix_eol.py <slug> <branch> <local_repo_dir> <rev_lf> <msg_file>
"""
import base64, json, ssl, subprocess, sys, urllib.error, urllib.request

REPO, BRANCH, LOCALDIR, REV, MSGFILE = sys.argv[1:6]
BIN_EXT = (".png", ".jpg", ".jpeg", ".webp", ".apk", ".jks", ".keystore", ".ico", ".gif")

OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({}),
    urllib.request.HTTPSHandler(context=ssl.create_default_context()),
)
_out = subprocess.run(["git", "credential", "fill"],
                      input="protocol=https\nhost=github.com\n\n",
                      capture_output=True, text=True).stdout
TOK = [l.split("=", 1)[1] for l in _out.splitlines() if l.startswith("password=")][0]
HDRS = {"Authorization": "Bearer " + TOK, "Accept": "application/vnd.github+json",
        "User-Agent": "fix-eol", "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json"}


def api(method, path, payload=None):
    req = urllib.request.Request("https://api.github.com" + path,
                                 data=json.dumps(payload).encode() if payload is not None else None,
                                 headers=HDRS, method=method)
    try:
        with OPENER.open(req, timeout=180) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raise SystemExit("HTTP %s %s %s\n%s" % (e.code, method, path, e.read().decode()[:600]))


def tree(rev):
    out = subprocess.run(["git", "ls-tree", "-r", rev], cwd=LOCALDIR,
                         capture_output=True, text=True).stdout
    m = {}
    for line in out.splitlines():
        meta, p = line.split("\t", 1)
        mode, typ, sha = meta.split()
        if typ == "blob":
            m[p] = (mode, sha)
    return m


st, ref = api("GET", "/repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
head = ref["object"]["sha"]
st, hc = api("GET", "/repos/%s/git/commits/%s" % (REPO, head))
base_tree = hc["tree"]["sha"]
print("[1] remote head=%s base_tree=%s" % (head[:12], base_tree[:12]))

# 远端完整 tree（recursive）
st, rt = api("GET", "/repos/%s/git/trees/%s?recursive=1" % (REPO, head))
remote_blobs = {x["path"]: (x["mode"], x["sha"]) for x in rt["tree"] if x["type"] == "blob"}
local_blobs = tree(REV)
print("    remote blobs=%d  local blobs=%d" % (len(remote_blobs), len(local_blobs)))


def cat(sha):
    return subprocess.run(["git", "cat-file", "blob", sha], cwd=LOCALDIR,
                          capture_output=True).stdout


targets = []
for p, (mode, rsha) in sorted(remote_blobs.items()):
    if p not in local_blobs:
        print("    !! 远端多出文件（跳过）:", p)
        continue
    if p.endswith(BIN_EXT):
        continue
    lsha = local_blobs[p][1]
    if lsha == rsha:
        continue
    try:
        rb = cat(rsha)
        lb = cat(lsha)
    except Exception:
        continue
    if rb.replace(b"\r\n", b"\n") == lb.replace(b"\r\n", b"\n"):
        targets.append((p, mode, lsha, rb, lb))

print("[2] 仅行尾不同的文件 = %d" % len(targets))
if not targets:
    print("无需修复")
    raise SystemExit(0)

entries = []
for p, mode, lsha, rb, lb in targets:
    assert b"\r\n" not in lb, "%s 本地仍为 CRLF" % p
    crlf_cnt = rb.count(b"\r\n")
    st, b = api("POST", "/repos/%s/git/blobs" % REPO,
                {"content": base64.b64encode(lb).decode(), "encoding": "base64"})
    entries.append({"path": p, "mode": mode, "type": "blob", "sha": b["sha"]})
    print("    %-92s CRLFx%-5d -> LF" % (p, crlf_cnt))

st, newtree = api("POST", "/repos/%s/git/trees" % REPO, {"base_tree": base_tree, "tree": entries})
print("[3] new_tree=%s (%d entries)" % (newtree["sha"][:12], len(entries)))

with open(MSGFILE, encoding="utf-8") as f:
    message = f.read().strip()

st, commit = api("POST", "/repos/%s/git/commits" % REPO,
                 {"message": message, "tree": newtree["sha"], "parents": [head]})
print("[4] new_commit=%s" % commit["sha"])

st, _ = api("PATCH", "/repos/%s/git/refs/heads/%s" % (REPO, BRANCH),
            {"sha": commit["sha"], "force": False})
print("[5] %s -> %s  OK" % (BRANCH, commit["sha"]))
print("DONE new_head=%s" % commit["sha"])
