# -*- coding: utf-8 -*-
"""GitHub Git Data API 推送器 v3（指定文件列表，以远端 head 的 tree 为基线）

适用场景：远端 head 是上一次 API 推送产生的提交（本地对象库中不存在），
         因此无法用本地提交链直接推送；此时改为「远端 tree 为基线 + 覆盖指定文件」。

用法：
  python push_files_v3.py <repo_slug> <branch> <local_repo_dir> <msg_file> <path1> [path2 ...]

其中 <path> 为相对仓库根的文件路径，内容取自 `git show HEAD:<path>`。
"""
import base64, json, os, ssl, subprocess, sys, urllib.error, urllib.request

REPO = sys.argv[1]
BRANCH = sys.argv[2]
LOCALDIR = sys.argv[3]
MSGFILE = sys.argv[4]
PATHS = sys.argv[5:]

assert REPO and BRANCH and LOCALDIR and MSGFILE and PATHS, "参数不足"

OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({}),
    urllib.request.HTTPSHandler(context=ssl.create_default_context()),
)


def run(*args):
    p = subprocess.run(list(args), cwd=LOCALDIR, capture_output=True, text=True)
    if p.returncode != 0:
        raise SystemExit("git %s failed: %s" % (" ".join(args), p.stderr[:400]))
    return p.stdout


def get_token():
    out = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True,
    ).stdout
    user = pwd = None
    for line in out.splitlines():
        if line.startswith("username="):
            user = line.split("=", 1)[1]
        elif line.startswith("password="):
            pwd = line.split("=", 1)[1]
    if not pwd:
        raise SystemExit("无法从 git credential 取得 token")
    return user, pwd


TOKEN_USER, TOKEN = get_token()
API = "https://api.github.com"
HDRS = {
    "Authorization": "Bearer " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "push-files-v3",
    "X-GitHub-Api-Version": "2022-11-28",
    "Content-Type": "application/json",
}


def api(method, path, payload=None):
    url = path if path.startswith("http") else API + path
    body = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=body, headers=HDRS, method=method)
    try:
        with OPENER.open(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raise SystemExit("HTTP %s on %s %s\n%s" % (e.code, method, path, e.read().decode()[:600]))


st, ref = api("GET", "/repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
remote_head = ref["object"]["sha"]
st, hc = api("GET", "/repos/%s/git/commits/%s" % (REPO, remote_head))
base_tree = hc["tree"]["sha"]
print("[1] remote_head=%s base_tree=%s" % (remote_head[:12], base_tree[:12]))

# 取文件权限模式
mode_map = {}
out = run("git", "ls-tree", "-r", "HEAD")
for line in out.splitlines():
    meta, path = line.split("\t", 1)
    mode, _typ, _sha = meta.split()
    mode_map[path] = mode

entries = []
for rel in PATHS:
    rel = rel.replace("\\", "/")
    blob = subprocess.run(["git", "show", "HEAD:" + rel], cwd=LOCALDIR, capture_output=True)
    if blob.returncode != 0:
        raise SystemExit("git show HEAD:%s failed" % rel)
    content = blob.stdout
    st, b = api("POST", "/repos/%s/git/blobs" % REPO,
                {"content": base64.b64encode(content).decode(), "encoding": "base64"})
    entries.append({"path": rel, "mode": mode_map.get(rel, "100644"),
                    "type": "blob", "sha": b["sha"]})
    print("    blob %s %s (%d B)" % (b["sha"][:12], rel, len(content)))

st, tree = api("POST", "/repos/%s/git/trees" % REPO, {"base_tree": base_tree, "tree": entries})
print("[2] new_tree=%s (%d entries)" % (tree["sha"][:12], len(tree["tree"])))

with open(MSGFILE, "r", encoding="utf-8") as f:
    message = f.read().strip()

st, commit = api("POST", "/repos/%s/git/commits" % REPO, {
    "message": message,
    "tree": tree["sha"],
    "parents": [remote_head],
})
new_sha = commit["sha"]
print("[3] new_commit=%s" % new_sha)

st, _ = api("PATCH", "/repos/%s/git/refs/heads/%s" % (REPO, BRANCH),
            {"sha": new_sha, "force": False})
print("[4] ref %s -> %s  OK" % (BRANCH, new_sha))
print("DONE new_head=%s" % new_sha)
