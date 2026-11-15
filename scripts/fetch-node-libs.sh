#!/usr/bin/env bash
# 恢复 androidApp/src/main/jniLibs/arm64-v8a 下的 Node 运行时（约 50 MB）。
#
# 三个文件必须成套，不能从官方 nodejs-mobile release 里凑：
#   libnode.so        nodejs-mobile v18.20.4 的共享库，node 本体都在这里
#   libnode_exec.so   ~4.5 KB 的瘦壳，main() 只调 node::Start()；由 cpp/node-main.cpp
#                     链接 libnode.so 构建。官方 zip 里没有这个文件，它也不是
#                     "可执行的 node"，只是一个能把 libnode 当前台进程跑起来的入口。
#   libc++_shared.so  libnode 的 C++ 运行时依赖
#
# 官方 zip 只给 libnode.so，而且同一包里含 armeabi-v7a / x86_64，按文件名直接
# `find -name 'libnode.so'` 拷贝会互相覆盖，最后留下哪个 ABI 看解压顺序。所以这里
# 直接从本项目的发布 APK 里取整套：三者出自同一次构建，ABI 与版本必然一致。
#
# 用法：./scripts/fetch-node-libs.sh [tag]，默认 v1.3.64
set -euo pipefail

VERSION="${1:-v1.3.64}"
APK="ASMusic-${VERSION#v}-arm64.apk"
URL="https://github.com/lwhhz/ASMusic/releases/download/${VERSION}/${APK}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/androidApp/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"

cleanup() { rm -rf -- "$TMP" 2>/dev/null || true; }
trap cleanup EXIT

echo "==> 下载 ${APK}"
curl -fL --retry 3 -o "${TMP:?}/${APK}" "${URL}"

echo "==> 解出 lib/arm64-v8a/*.so 到 ${DEST}"
mkdir -p "${DEST}"
unzip -o -q -j "${TMP}/${APK}" \
    'lib/arm64-v8a/libnode.so' \
    'lib/arm64-v8a/libnode_exec.so' \
    'lib/arm64-v8a/libc++_shared.so' \
    -d "${DEST}"

ls -lh "${DEST}"
echo "==> 完成"
