#!/usr/bin/env bash
# 按各内置包 manifest.json 记录的页面 commit，把 source/<pkg>/ 刷新成页面仓库在该 commit 的完整导出（除 .github/）。
# 发版冒烟在 tinyui pull 之后调用，快照与 manifest、.jsb 同一个 commit 提交。
# 仓库地址取自 source/<pkg>/SOURCE 的 repo= 行；新增包时先手写一行 repo=https://github.com/<owner>/<repo>。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
EMBEDDED="$ROOT/shared/src/commonMain/composeResources/files/tinyui"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

for manifest in "$EMBEDDED"/*/manifest.json; do
  [ -f "$manifest" ] || continue
  pkg="$(basename "$(dirname "$manifest")")"
  src="$HERE/source/$pkg"
  version="$(node -p "require(process.argv[1]).version" "$manifest")"
  short="${version##*-}"

  repo="$(sed -n 's/^repo=//p' "$src/SOURCE" 2>/dev/null || true)"
  [ -n "$repo" ] || { echo "FAIL: $src/SOURCE 缺 repo= 行（新增包时手写一行仓库地址）" >&2; exit 1; }
  current="$(sed -n 's/^commit=//p' "$src/SOURCE")"
  case "$current" in
    "$short"*) echo "==> $pkg 快照已是 ${current}"; continue ;;
  esac

  slug="${repo#https://github.com/}"
  slug="${slug%.git}"
  commit="$(curl -fsSL "https://api.github.com/repos/$slug/commits/$short" | node -p "JSON.parse(require('fs').readFileSync(0)).sha")"
  echo "==> $pkg 快照 ${current:-（无）} → $commit"

  mkdir -p "$WORK/$pkg"
  curl -fsSL "https://codeload.github.com/$slug/tar.gz/$commit" | tar -xz -C "$WORK/$pkg" --strip-components 1
  rm -rf "$WORK/$pkg/.github"

  rm -rf "$src"
  mv "$WORK/$pkg" "$src"
  printf 'repo=%s\ncommit=%s\n' "$repo" "$commit" > "$src/SOURCE"
done
