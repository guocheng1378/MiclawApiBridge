#!/bin/sh
# ============================================================
# MiclawApiBridge 一键发布脚本 (源码仓库 + LSP 市场)
#
# 用法:  ./release.sh <版本号> [发布说明]
# 示例:  ./release.sh 2.1 "修复端口冲突"
#
# 流程:
#   1. 更新 versionCode / versionName
#   2. commit + push main
#   3. 源码仓库打 tag v<版本> -> GitHub Actions 自动构建 Release
#   4. 等待构建完成, 下载 APK
#   5. LSP 市场仓库创建 Release <序号>-<版本> + 上传 APK
#   6. 可选: --docs 参数同时更新市场 README/SUMMARY (需在 MARKET_README.md / MARKET_SUMMARY 文件)
#
# 依赖: git / curl / python3
# Token: 从 git remote URL 自动提取, 或设置环境变量 GH_TOKEN
# ============================================================

set -e

VERSION=""
MSG=""
DO_DOCS="no"

# ---------- 解析参数 ----------
while [ $# -gt 0 ]; do
    case "$1" in
        --docs) DO_DOCS="yes" ;;
        -h|--help)
            echo "用法: ./release.sh <版本号> [发布说明] [--docs]"
            echo "示例: ./release.sh 2.1 \"修复端口冲突\""
            echo "      ./release.sh 2.1 \"新功能\" --docs   # 同时更新市场 README"
            exit 0
            ;;
        *)
            if [ -z "$VERSION" ]; then VERSION="$1"
            elif [ -z "$MSG" ]; then MSG="$1"
            else echo "❌ 多余参数: $1"; exit 1
            fi
            ;;
    esac
    shift
done

if [ -z "$VERSION" ]; then
    echo "❌ 用法: ./release.sh <版本号> [发布说明] [--docs]"
    exit 1
fi
[ -z "$MSG" ] && MSG="release"

# ---------- 0. 环境检查 ----------
command -v curl >/dev/null 2>&1 || { echo "❌ 需要 curl"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "❌ 需要 python3"; exit 1; }
[ -f app/build.gradle.kts ] || { echo "❌ 请在仓库根目录运行 (缺少 app/build.gradle.kts)"; exit 1; }

# ---------- 1. 提取 GitHub token ----------
TOKEN=$(python3 -c "
import re
s=open('.git/config').read()
m=re.search(r'url = https://([^@/]+)@github\.com/', s)
print(m.group(1) if m else '')
")
[ -z "$TOKEN" ] && TOKEN="$GH_TOKEN"
if [ -z "$TOKEN" ]; then
    echo "❌ 无法获取 GitHub token: 请在 git remote 中带 token 或设置环境变量 GH_TOKEN"
    exit 1
fi

SRC_REPO="guocheng1378/MiclawApiBridge"
MARKET_REPO="Xposed-Modules-Repo/io.github.guocheng1378.miclawbridge"

echo "=============================================="
echo "🚀 MiclawApiBridge 发布 v$VERSION"
echo "=============================================="

# ---------- 2. 计算 versionCode ----------
CUR_CODE=$(python3 -c "
import re
s=open('app/build.gradle.kts').read()
m=re.search(r'versionCode\s*=\s*(\d+)', s)
print(m.group(1) if m else '1')
")
NEW_CODE=$((CUR_CODE + 1))
echo "📝 versionCode: $CUR_CODE -> $NEW_CODE | versionName: $VERSION"

# ---------- 3. 计算市场序号 (市场最新 tag 序号 + 1) ----------
MAX_N=$(curl -s --max-time 20 -H "Authorization: token $TOKEN" \
    "https://api.github.com/repos/$MARKET_REPO/releases?per_page=100" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    nums=[]
    if isinstance(d,list):
        for r in d:
            t=r.get('tag_name','')
            if '-' in t:
                try: nums.append(int(t.split('-')[0]))
                except: pass
    print(max(nums)+1 if nums else 1)
except:
    print(1)
")
MARKET_TAG="$MAX_N-$VERSION"
SRC_TAG="v$VERSION"
echo "🏷  源码 tag: $SRC_TAG | 市场 tag: $MARKET_TAG"

# ---------- 4. 更新 build.gradle.kts ----------
python3 - "$NEW_CODE" "$VERSION" <<'PYEOF'
import sys, re
code, ver = sys.argv[1], sys.argv[2]
p = 'app/build.gradle.kts'
s = open(p).read()
s = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {code}', s)
s = re.sub(r'versionName\s*=\s*"[^"]*"', f'versionName = "{ver}"', s)
open(p, 'w').write(s)
print("✅ build.gradle.kts 已更新")
PYEOF

# ---------- 5. commit + push main ----------
git add -A
if git diff --cached --quiet; then
    echo "(无变更可提交)"
else
    git commit -m "v$VERSION: $MSG" >/dev/null
    echo "✅ 已提交"
fi
git push origin main >/dev/null 2>&1 && echo "✅ main 已推送" || { echo "❌ push main 失败"; exit 1; }

# ---------- 6. 源码仓库 tag -> 触发 Actions ----------
git tag -f "$SRC_TAG" >/dev/null 2>&1
git push origin "$SRC_TAG" >/dev/null 2>&1 && echo "✅ 源码 tag $SRC_TAG 已推送, Actions 开始构建..." \
    || { echo "❌ push tag 失败"; exit 1; }

# ---------- 7. 等待 Actions 构建 (最多 15 分钟) ----------
echo "⏳ 等待 GitHub Actions 构建..."
RUN_ID=""
i=0
while [ $i -lt 8 ] && [ -z "$RUN_ID" ]; do
    i=$((i+1))
    sleep 5
    RUN_ID=$(curl -s --max-time 15 -H "Authorization: token $TOKEN" \
        "https://api.github.com/repos/$SRC_REPO/actions/runs?per_page=1&event=push&branch=$SRC_TAG" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    ws=d.get('workflow_runs',[])
    print(ws[0]['id'] if ws else '')
except: print('')
")
done
if [ -z "$RUN_ID" ]; then
    echo "⚠️  未找到构建任务, 请到 https://github.com/$SRC_REPO/actions 查看"
    exit 1
fi
echo "   build run: $RUN_ID"

RESULT=""
i=0
while [ $i -lt 60 ]; do
    i=$((i+1))
    sleep 15
    STATUS=$(curl -s --max-time 15 -H "Authorization: token $TOKEN" \
        "https://api.github.com/repos/$SRC_REPO/actions/runs/$RUN_ID" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    c=d.get('conclusion','')
    s=d.get('status','')
    print(s if s=='completed' else 'running', c)
except: print('running unknown')
")
    echo "   [$i] status=$STATUS"
    case "$STATUS" in
        completed\ success) RESULT=ok; break ;;
        completed\ *) RESULT=fail; break ;;
    esac
done
if [ "$RESULT" != "ok" ]; then
    echo "❌ 构建未成功 ($STATUS)。请检查 https://github.com/$SRC_REPO/actions"
    exit 1
fi
echo "✅ 构建成功!"

# ---------- 8. 下载 APK ----------
APK="/tmp/MiclawApiBridge-$VERSION.apk"
echo "⬇️  下载 APK: $SRC_TAG"
curl -s -L --max-time 120 "https://github.com/$SRC_REPO/releases/download/$SRC_TAG/app-release.apk" -o "$APK"
if [ ! -s "$APK" ]; then
    echo "❌ APK 下载失败 (检查源码 release asset 名称是否仍为 app-release.apk)"
    exit 1
fi
echo "✅ APK 已下载: $(wc -c < "$APK") bytes"

# ---------- 9. 市场仓库 Release + APK ----------
RELEASE_JSON=$(curl -s --max-time 20 -X POST \
    -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json" \
    -d "{\"tag_name\":\"$MARKET_TAG\",\"name\":\"$VERSION\",\"body\":\"v$VERSION: $MSG\"}" \
    "https://api.github.com/repos/$MARKET_REPO/releases")
RELEASE_ID=$(echo "$RELEASE_JSON" | python3 -c "
import json,sys
try: print(json.load(sys.stdin).get('id',''))
except: print('')
")
if [ -z "$RELEASE_ID" ]; then
    echo "❌ 市场 Release 创建失败:"
    echo "$RELEASE_JSON" | head -c 300
    exit 1
fi
echo "✅ 市场 Release $MARKET_TAG 已创建"

curl -s --max-time 120 -X POST \
    -H "Authorization: token $TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@$APK" \
    "https://uploads.github.com/repos/$MARKET_REPO/releases/$RELEASE_ID/assets?name=MiclawApiBridge-$VERSION.apk" | python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    if 'id' in d: print('✅ APK 已上传到市场:', d['name'], d['size'], 'bytes')
    else: print('⚠️  APK 上传响应:', d)
except Exception as e: print('⚠️  上传响应解析失败:', e)
"

# ---------- 10. 可选: 更新市场 README/SUMMARY ----------
if [ "$DO_DOCS" = "yes" ]; then
    if [ -f MARKET_README.md ]; then
        SHA=$(curl -s --max-time 15 -H "Authorization: token $TOKEN" \
            "https://api.github.com/repos/$MARKET_REPO/contents/README.md" | python3 -c "
import json,sys
try: print(json.load(sys.stdin).get('sha',''))
except: print('')
")
        python3 - "$MARKET_REPO" "$SHA" <<'PYEOF'
import json, base64, sys, subprocess
repo, sha = sys.argv[1], sys.argv[2]
token = subprocess.run(['git','config','--get','remote.origin.url'],capture_output=True,text=True).stdout
import re
m = re.search(r'https://([^@/]+)@github\.com/', token)
token = m.group(1) if m else ''
content = open('MARKET_README.md').read()
payload = {"message":"docs: update","content":base64.b64encode(content.encode()).decode(),"sha":sha,"branch":"main"}
out = subprocess.run(['curl','-s','--max-time','20','-X','PUT',
    '-H',f'Authorization: token {token}',
    '-H','Accept: application/vnd.github+json',
    '-d',json.dumps(payload),
    f'https://api.github.com/repos/{repo}/contents/README.md'],capture_output=True,text=True).stdout
try:
    d=json.loads(out)
    print('✅ 市场 README 已更新' if 'content' in d else f'⚠️  README 更新: {d}')
except: print('⚠️  README 更新响应解析失败')
PYEOF
    else
        echo "ℹ️  跳过 README 更新 (无 MARKET_README.md 文件)"
    fi
fi

echo ""
echo "=============================================="
echo "🎉 v$VERSION 发布完成!"
echo "  源码 Release: https://github.com/$SRC_REPO/releases/tag/$SRC_TAG"
echo "  市场 Release: https://github.com/$MARKET_REPO/releases/tag/$MARKET_TAG"
echo "  市场索引同步: modules.lsposed.org 约 1-24h 内刷新"
echo "=============================================="
