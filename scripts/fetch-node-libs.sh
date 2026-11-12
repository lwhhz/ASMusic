#!/usr/bin/env bash
# 下载 nodejs-mobile v18.20.4 (Android, arm64-v8a) 并把 .so 放到 jniLibs。
# 仓库不包含这些二进制（约 70 MB），克隆后运行一次即可编译。
set -euo pipefail

VERSION="v18.20.4"
ZIP="nodejs-mobile-${VERSION}-android.zip"
URL="https://github.com/nodejs-mobile/nodejs-mobile/releases/download/${VERSION}/${ZIP}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/androidApp/src/main/jniLibs/arm64-v8a"
TMP="$(mktemp -d)"

echo "==> 下载 ${ZIP}"
curl -fL --retry 3 -o "${TMP}/${ZIP}" "${URL}"

echo "==> 解压到 ${DEST}"
mkdir -p "${DEST}"
unzip -o -q "${TMP}/${ZIP}" -d "${TMP}/out"
# 压缩包结构：nodejs-mobile/bin/arm64-v8a/*.so（不同版本略有差异，做一次兜底查找）
find "${TMP}/out" -name 'libnode.so' -exec cp {} "${DEST}/" \;
find "${TMP}/out" -name 'libnode_exec.so' -exec cp {} "${DEST}/" \;
find "${TMP}/out" -name 'libc++_shared.so' -exec cp {} "${DEST}/" \;

ls -lh "${DEST}"
rm -rf "${TMP}"
echo "==> 完成"
