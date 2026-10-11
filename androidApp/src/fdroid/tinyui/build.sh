#!/usr/bin/env bash
# 从 source/<pkg>/ 的页面源码快照重新生成 TinyUI 内置包的 .jsb，逐页对照 manifest.json 的 sha256 后写回。
# F-Droid 配方在 rm 掉 pages/ 之后调用（无参数）；发版冒烟带 --out <dir> 调用，只验证、不碰仓库。
# manifest 与 i18n 始终是线上原件，不重新生成（原因见 docs/fdroid-tinyui-build.md §3）。
# 编译器默认用页面仓库 lockfile 里的 npm 版 qjsc-kmp；设了 TINYUI_QJSC 时 tinyui-cli 改用它指定的那份。
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
EMBEDDED="$ROOT/shared/src/commonMain/composeResources/files/tinyui"

OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?--out needs a directory}"; shift 2 ;;
    *) echo "usage: $0 [--out <dir>]" >&2; exit 2 ;;
  esac
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

count=0
for manifest in "$EMBEDDED"/*/manifest.json; do
  [ -f "$manifest" ] || continue
  pkg_dir="$(dirname "$manifest")"
  pkg="$(basename "$pkg_dir")"
  src="$HERE/source/$pkg"
  echo "==> $pkg"

  # 1. 来源校验：快照必须正是 manifest 记录的页面 commit
  version="$(node -p "require(process.argv[1]).version" "$manifest")"
  short="${version##*-}"
  [ -f "$src/SOURCE" ] || { echo "FAIL: $src/SOURCE is missing; run sync.sh" >&2; exit 1; }
  commit="$(sed -n 's/^commit=//p' "$src/SOURCE")"
  case "$commit" in
    "$short"*) ;;
    *) echo "FAIL: snapshot commit [$commit] does not match manifest version [$version]; run sync.sh" >&2; exit 1 ;;
  esac

  # 2. F-Droid 模式下提交进仓库的字节码必须已被配方删掉，否则说明配方漏了这个包的 rm
  if [ -z "$OUT" ] && [ -e "$pkg_dir/pages" ]; then
    echo "FAIL: $pkg_dir/pages still exists; the fdroiddata recipe is missing rm: .../tinyui/$pkg/pages" >&2
    exit 1
  fi

  # 3. 构建：pnpm 版本取快照 package.json 的 packageManager，与页面仓库 CI 一致
  build="$WORK/$pkg"
  cp -R "$src" "$build"
  pm="$(node -p "require(process.argv[1]).packageManager" "$build/package.json")"
  (cd "$build" && npx --yes "$pm" install --frozen-lockfile && npx --yes "$pm" exec tinyui build)

  # 4. 逐页校验：manifest 的每一页都要生成且哈希一致，生成结果里也不能多出 manifest 没有的页
  node - "$manifest" "$build/dist" <<'EOF'
const fs = require("fs"), path = require("path"), crypto = require("crypto");
const [manifestPath, dist] = process.argv.slice(2);
const m = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const expected = new Set(m.pages.map((p) => `${m.files[p]}.jsb`));
let ok = true;
for (const p of m.pages) {
  const file = path.join(dist, `${m.files[p]}.jsb`);
  if (!fs.existsSync(file)) { console.error(`FAIL: ${p} was not generated`); ok = false; continue; }
  const sha = crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
  if (sha !== m.hashes[p]) { console.error(`FAIL: ${p} sha256 ${sha}, manifest records ${m.hashes[p]}`); ok = false; }
  else console.log(`    ok ${p} ${sha}`);
}
const walk = (d) => fs.readdirSync(d, { withFileTypes: true })
  .flatMap((e) => e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]);
for (const f of walk(dist).filter((f) => f.endsWith(".jsb"))) {
  const rel = path.relative(dist, f).split(path.sep).join("/");
  if (!expected.has(rel)) { console.error(`FAIL: ${rel} is not listed in the manifest`); ok = false; }
}
process.exit(ok ? 0 : 1);
EOF

  # 5. 写回：只放 .jsb，manifest 与 i18n 不动
  dest="${OUT:+$OUT/$pkg}"
  dest="${dest:-$pkg_dir}"
  node -e '
    const m = require(process.argv[1]);
    for (const p of m.pages) console.log(m.files[p] + ".jsb");
  ' "$manifest" | while read -r rel; do
    mkdir -p "$(dirname "$dest/$rel")"
    cp "$build/dist/$rel" "$dest/$rel"
  done
  count=$((count + 1))
done

[ "$count" -gt 0 ] || { echo "FAIL: no embedded package under $EMBEDDED" >&2; exit 1; }
echo "PASS: rebuilt $count package(s) from source; every page matches its manifest sha256"
