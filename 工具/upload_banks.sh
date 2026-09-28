#!/usr/bin/env bash
# upload_banks.sh — 将题库外置包 JSON 推送到 GitHub 仓库（供 App 首启下载）
#
# 🔴🔴 【2026-09-28 重要变更】题库已**并入源码主仓** `BadCodeZ/jiaozi-android`（不再有独立题库仓）。
#     因此本脚本「把 banks 目录当独立仓库 git init + push」的旧逻辑**在并入主仓后不再适用**：
#     主仓 main 已有完整历史，直接 push 一个独立的 banks 历史会被拒（non-fast-forward）。
#
#     ✅ 现在推题库请改用：`工具/github_push_disk.py`
#        （以远端 main 的 tree 为基线叠加，不强推、不破坏源码；
#          配合 `工具/_map_banks.txt` 映射「本地 banks/x.json → banks/x.json」）
#        python github_push_disk.py BadCodeZ/jiaozi-android main banks "<commit msg>" 工具/_map_banks.txt
#
#     本脚本保留作**独立题库仓场景**的历史参考（如需再拆分题库仓时可用）。
#
# 题库按【学科 × 学段】双层拆分，共 32 个文件：
#   科目一《综合素质》          → ke1.json                    （1 个，初高中同卷）
#   科目二《教育知识与能力》    → ke2.json                    （1 个，初高中同卷）
#   科目三《学科知识与教学能力》 → ke3_<pinyin>[_junior|_senior].json
#     · 单学段独有科（整包不拆，无后缀）：
#         初中独有 → ke3_kexue.json / ke3_lishiyushehui.json
#         高中独有 → ke3_sixiangzhengzhi.json / ke3_tongyongjishu.json
#     · 初高中同名分卷科（各拆两版，后缀 _junior / _senior）：
#         ke3_meishu / ke3_yuwen / ke3_shuxue / ke3_yingyu / ke3_yinyue / ke3_tiyu /
#         ke3_xinxi / ke3_wuli / ke3_huaxue / ke3_shengwu / ke3_lishi / ke3_dili /
#         ke3_daodefazhi
#         ⇒ 13 × 2 = 26 个
#   合计：2 + 4 + 26 = 32 个文件。
#
#   分层口径：每版含「本学段专属题 + 通用题」，通用题在两版各存一份（派生冗余）；
#             用户备考时仅下载本学段 17 个文件（初中 ≈1.83MB / 高中 ≈1.85MB）。
#
# 前置条件：
#   1) 仓库 BadCodeZ/jiaozi-android（题库与源码同仓）已存在且为【公开(Public)】仓库，默认分支为 main。
#      （App 用 raw.githubusercontent.com 直连下载且无鉴权，仓库必须是 public；
#        若仓库尚不存在，请先在 github.com 新建一个空的 public 仓库再运行本脚本。）
#   2) 本机已安装 Git 且已登录（Git Credential Manager 已保存 GitHub 凭据）。
#
# 用法（在 Git Bash 中执行其一）：
#   A. 先 cd 到 banks 目录，再运行：   bash /path/to/upload_banks.sh
#   B. 直接在任意目录运行，脚本会自动定位同级的 banks/ 目录。
#
# 行为：
#   - 若当前目录或 ../banks 含 ke1.json，则定位到该目录。
#   - 若目录已有 .git：add + commit + 普通 push（不强制覆盖，保护已有内容）。
#   - 若目录没有 .git：git init + 关联远端 + 首次 push（适合空仓库）。

set -e

REMOTE="https://github.com/BadCodeZ/jiaozi-android.git"
BRANCH="main"

# 定位 banks 目录
if [ -f "ke1.json" ]; then
  DIR="."
elif [ -f "$(dirname "$0")/../banks/ke1.json" ]; then
  DIR="$(cd "$(dirname "$0")/../banks" && pwd)"
else
  echo "[错误] 找不到 ke1.json，请把本脚本放在 banks/ 同级目录，或先 cd 到 banks/ 目录再运行。" >&2
  exit 1
fi

cd "$DIR"
echo "[info] 工作目录: $DIR"

if [ ! -d ".git" ]; then
  echo "[info] 初始化本地仓库..."
  git init -q
  git remote remove origin 2>/dev/null || true
  git remote add origin "$REMOTE"
  git checkout -q -B "$BRANCH"
else
  echo "[info] 检测到已有仓库，切换到分支 $BRANCH"
  git checkout -q "$BRANCH" 2>/dev/null || git checkout -q -B "$BRANCH"
fi

echo "[info] 暂存题库文件（32 个：ke1 / ke2 / ke3_* + ke3_*_junior|_senior）..."
# 注：旧的整包全量文件（无后缀的同名分卷科，如 ke3_meishu.json）已不再生成，
#     若仓库中残留旧版请在本目录执行 git rm 清理，否则 App 仍有可能读到旧全量包。
git add ke1.json ke2.json ke3_*.json
echo "[info] 本次将提交的文件："
git diff --cached --name-only | sed 's/^/        /'

if git diff --cached --quiet; then
  echo "[info] 文件内容无变化，无需提交。"
else
  git commit -q -m "feat: 题库外置包按「学科 x 学段」双层拆分 32 文件（ke1/ke2 + ke3_*_junior|_senior）(v2.8.0)"
  echo "[info] 已提交到本地。"
fi

echo "[info] 推送到 origin/$BRANCH ..."
if git push -u origin "$BRANCH"; then
  echo "[完成] 推送成功。App 首启将从以下地址下载本学段文件："
  echo "        https://raw.githubusercontent.com/BadCodeZ/jiaozi-android/main/banks/<code>.json"
  echo "        初中 17 个：ke1 / ke2 / ke3_*_junior(13) / ke3_kexue / ke3_lishiyushehui"
  echo "        高中 17 个：ke1 / ke2 / ke3_*_senior(13) / ke3_sixiangzhengzhi / ke3_tongyongjishu"
else
  echo "[失败] 推送被拒（可能是非空仓库的非快进冲突）。请先执行 'git pull --rebase' 合并后再运行，"
  echo "        并确认：(1) 仓库为 public；(2) 默认分支名为 main；(3) 已登录 GitHub。" >&2
  exit 1
fi
