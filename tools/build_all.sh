#!/usr/bin/env bash
# 本地全端构建：先跑守卫闸，再遍历 targets.json 里所有 buildable 的目标逐个构建。
# （结构与 e33chat 的 tools/build_all.sh 同款；产物收进 dist/<mod_version>/ 用 collect_jars.sh）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# buildable 目标列表由守卫闸自己输出（它内部用 __file__ 解析路径，跨 bash/python 路径风格）。
# tr -d '\r'：Windows python 的 print 往管道写 \r\n，$() 只截尾部换行，
# 中间行会带着 \r 混进路径 —— 已实锤踩过（forge 目标排第一行，路径悄悄失效）。
BUILDABLE=$(python tools/verify_targets.py --list-buildable | tr -d '\r')

status=0
while IFS= read -r project; do
    [ -z "$project" ] && continue
    echo "=== building $project ==="
    if ! (cd "$ROOT/$project" && ./gradlew.bat build --no-daemon); then
        echo "!!! build FAILED: $project"
        status=1
    fi
done <<< "$BUILDABLE"

exit $status
