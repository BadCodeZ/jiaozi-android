# -*- coding: utf-8 -*-
"""GitHub Git Data API 磁盘推送器（不走 git push，绕过 github.com:443 不通）。

用法：
  python github_push_disk.py <repo_slug> <branch> <local_root> <msg> <mapping_file>

mapping_file: 每行  local_rel_path<TAB>repo_path
  local_rel_path 相对 local_root；repo_path 为仓库内目标路径（含目录）。
内容直接从磁盘读取（不依赖 git），base64 后创建 blob。
以远端 head 的 tree 为基线叠加，不破坏已有文件。
若远端仓库不存在则自动创建（public）。
"""
import base64, json, os, ssl, subprocess, sys, urllib.error, urllib.request

REPO = sys.argv[1]
BRANCH = sys.argv[2]
LOCALROOT = sys.argv[3]
MSG = sys.argv[4]
MAPFILE = sys.argv[5]

class _Redir(urllib.request.HTTPRedirectHandler):
    """跟随 GitHub 的 307/308 规范化重定向（/repos/{owner}/{repo} -> /repositories/{id}），
    并保持原请求方法与 body（urllib 默认不对 POST 307 重定向）。"""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        m = req.get_method()
        if code in (301, 302, 303):
            m = "GET"
            data = None
        elif code in (307, 308):
            data = req.data
        else:
            return None
        nh = dict(req.headers)
        nh.pop("Host", None)
        return urllib.request.Request(newurl, data=data, headers=nh, method=m)


OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({}),
    _Redir(),
    urllib.request.HTTPSHandler(context=ssl.create_default_context()),
)


def get_token():
    import os
    env = os.environ.get("GH_PUSH_TOKEN")
    if env:
        return "BadCodeZ", env
    # 回退：子进程方式（沙箱内可能 hang，优先用环境变量注入）
    out = subprocess.run(["git", "credential", "fill"],
                         input="protocol=https\nhost=github.com\n\n",
                         capture_output=True, text=True).stdout
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
HDRS = {"Authorization": "Bearer " + TOKEN, "Accept": "application/vnd.github+json",
        "User-Agent": "push-disk", "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json"}


def api(method, path, payload=None):
    url = path if path.startswith("http") else API + path
    body = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=body, headers=HDRS, method=method)
    try:
        with OPENER.open(req, timeout=90) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raise SystemExit("HTTP %s %s %s\n%s" % (e.code, method, path, e.read().decode()[:600]))


# 确保远端仓库存在
try:
    st, repo = api("GET", "/repos/%s" % REPO)
except SystemExit as e:
    if "404" in str(e):
        st = 404
    else:
        raise
if st == 404:
    print("[*] 仓库不存在，创建 public 仓库 %s ..." % REPO)
    st, repo = api("POST", "/user/repos",
                   {"name": REPO.split("/")[-1], "private": False, "auto_init": True})
    print("    创建完成，等待初始化...")
    import time; time.sleep(5)
elif st != 200:
    raise SystemExit("仓库查询失败 HTTP %s" % st)

# 取远端 head + base_tree
st, ref = api("GET", "/repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
if st == 404:
    # 新建仓 auto_init 后应有 main；再试一次
    st, ref = api("GET", "/repos/%s/git/ref/heads/%s" % (REPO, BRANCH))
remote_head = ref["object"]["sha"]
st, hc = api("GET", "/repos/%s/git/commits/%s" % (REPO, remote_head))
base_tree = hc["tree"]["sha"]
print("[1] remote_head=%s base_tree=%s" % (remote_head[:12], base_tree[:12]))

# 读映射
entries = []
with open(MAPFILE, "r", encoding="utf-8") as f:
    for line in f:
        # 兼容 CRLF 映射文件：必须同时剥离 \r，否则 \r 会被带进仓库目标路径
        line = line.rstrip("\r\n")
        if not line.strip():
            continue
        lp, rp = line.split("\t")
        lp = lp.replace("\\", "/").strip(); rp = rp.replace("\\", "/").strip()
        full = os.path.join(LOCALROOT, lp)
        if not os.path.isfile(full):
            print("    [跳过] 本地缺失: %s" % lp)
            continue
        with open(full, "rb") as fh:
            data = fh.read()
        st, b = api("POST", "/repos/%s/git/blobs" % REPO,
                    {"content": base64.b64encode(data).decode(), "encoding": "base64"})
        entries.append({"path": rp, "mode": "100644", "type": "blob", "sha": b["sha"]})
        print("    blob %s %s (%d B)" % (b["sha"][:12], rp, len(data)))

print("[2] 创建 tree，%d 个条目..." % len(entries))
st, tree = api("POST", "/repos/%s/git/trees" % REPO,
               {"base_tree": base_tree, "tree": entries})
print("    new_tree=%s" % tree["sha"][:12])

st, commit = api("POST", "/repos/%s/git/commits" % REPO,
                 {"message": MSG, "tree": tree["sha"], "parents": [remote_head]})
new_sha = commit["sha"]
print("[3] new_commit=%s" % new_sha[:12])

st, _ = api("PATCH", "/repos/%s/git/refs/heads/%s" % (REPO, BRANCH),
            {"sha": new_sha, "force": False})
print("[4] ref %s -> %s OK" % (BRANCH, new_sha[:12]))
print("DONE new_head=%s" % new_sha)
