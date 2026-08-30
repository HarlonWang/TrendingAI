#!/usr/bin/env bash
# 编 chat 模块 iOS cinterop 所需的 cmark-gfm 静态库（真机 arm64 + 模拟器 arm64）。
# 源码取 apple/swift-cmark（Apple 维护的 gfm 分支，swift-markdown/DocC 同源），tag 固定；
# 产物落 chat/native/out/（不入库），CI 与本地各自现编，Gradle 的 cinterop 依赖此产物。
# 用法：chat/native/build-cmark.sh [--force]
set -euo pipefail
cd "$(dirname "$0")"

TAG="swift-6.3.3-RELEASE"
SRC_DIR="out/src-$TAG"
TARBALL_URL="https://codeload.github.com/apple/swift-cmark/tar.gz/refs/tags/$TAG"

if [ "${1:-}" != "--force" ] && [ -f "out/ios_arm64/libcmark-gfm.a" ] && [ -f "out/ios_simulator_arm64/libcmark-gfm.a" ]; then
  echo "cmark-gfm 静态库已就绪${TAG}，跳过。--force 重编"
  exit 0
fi

mkdir -p out
if [ ! -d "$SRC_DIR" ]; then
  echo "==> 下载 swift-cmark $TAG"
  curl -fsSL "$TARBALL_URL" -o "out/src.tar.gz"
  mkdir -p "$SRC_DIR"
  tar -xzf "out/src.tar.gz" -C "$SRC_DIR" --strip-components=1
  rm "out/src.tar.gz"
fi

build_one() {
  local name="$1" sdk="$2"
  local build_dir="out/build-$name"
  echo "==> cmake $name ($sdk)"
  cmake -S "$SRC_DIR" -B "$build_dir" \
    -DCMAKE_SYSTEM_NAME=iOS \
    -DCMAKE_OSX_SYSROOT="$sdk" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=18.2 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_MACOSX_BUNDLE=NO \
    -DCMARK_THREADING=ON \
    -DBUILD_TESTING=OFF \
    >/dev/null
  cmake --build "$build_dir" --target libcmark-gfm libcmark-gfm-extensions -j >/dev/null
  local out_dir="out/$name"
  mkdir -p "$out_dir/include"
  find "$build_dir" -name "libcmark-gfm*.a" -exec cp {} "$out_dir/" \;
  # cinterop 头文件集合：此 fork 的公开头都预生成在 src/include 与 extensions/include
  cp "$SRC_DIR"/src/include/*.h "$out_dir/include/"
  cp "$SRC_DIR"/extensions/include/*.h "$out_dir/include/"
  ls "$out_dir"/libcmark-gfm.a "$out_dir"/libcmark-gfm-extensions.a >/dev/null
  echo "    产物: $out_dir"
}

build_one ios_arm64 iphoneos
build_one ios_simulator_arm64 iphonesimulator
echo "PASS: cmark-gfm 静态库构建完成 ${TAG}"
