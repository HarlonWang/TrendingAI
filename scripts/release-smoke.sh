#!/usr/bin/env bash
# 发版冒烟的脚本部分。规格、用例表与执行记录在 docs/smoke-test-spec.md，本脚本只负责其中能确定性判定的条目：
#   第一层产物断言 A1–A6（TinyUI 内置包与 F-Droid 快照、发版单测、非 debuggable、targetSdk、.so 16 KB 对齐、arm64 ABI）
#   第二层 S01（启动不崩、无兼容弹窗、无 R8 特征异常）与 S08 的兜底（聊天页输入框渲染）
# 脚本 PASS 之后还要按规格文档走 S02–S10，全过才打 tag。用法：scripts/release-smoke.sh
# 设备：ANDROID_SERIAL 指定的那台；否则唯一在线的设备（模拟器、真机均可）；都没有时启动 SMOKE_AVD，
# 未设 SMOKE_AVD 且本机只有一个 AVD 就启动它。多台在线或多个 AVD 时不猜，报错列出供选。
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="whl.trending.ai"
APK="androidApp/build/outputs/apk/r2/release/androidApp-r2-release.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
# R8 裁错类 / 构造器 / 序列化反射时的典型形态：被 catch 住写进 logcat，页面显示空态，进程不死——只看 crash 缓冲区发现不了
R8_PATTERN='UnsatisfiedLinkError|AbstractMethodError|NoSuchMethodError|NoClassDefFoundError|NoSuchFieldError|MissingFieldException|SerializationException'

# TinyUI 内置包（tinyui docs/updates.md §1.4）：production 比内置新且已全量才换，有变化就提交，冒烟测的就是要发出去的字节
EMBEDDED="shared/src/commonMain/composeResources/files/tinyui/trendingai"
# F-Droid 构建时从这份页面源码快照重编 .jsb（docs/fdroid-tinyui-build.md），快照必须跟着内置包走、同一个 commit 提交
FDROID_TINYUI="androidApp/src/fdroid/tinyui"
host=$(sed -n 's/^const val HOST_VERSION = "\([0-9]*\)"$/\1/p' shared/src/commonMain/kotlin/whl/trending/ai/tinyui/TrendingTinyUI.kt)
tinyui=$(sed -n 's/^tinyui = "\(.*\)"$/\1/p' gradle/libs.versions.toml)
echo "==> 刷新 TinyUI 内置包（宿主版本 ${host}）"
if ! npx --yes -p "tinyui-cli@$tinyui" tinyui pull --app trendingai --channel production --host-version "$host" --out "$EMBEDDED"; then
  echo "    WARN: 刷新失败，沿用现有内置包（原因见上）"
fi
echo "==> 同步 F-Droid 页面源码快照"
"$FDROID_TINYUI/sync.sh"
if [ -n "$(git status --porcelain -- "$EMBEDDED" "$FDROID_TINYUI/source")" ]; then
  v=$(node -p "require('./$EMBEDDED/manifest.json').version")
  git add -A -- "$EMBEDDED" "$FDROID_TINYUI/source"
  git commit -q -m "chore(tinyui): 内置包 trendingai@$v" -- "$EMBEDDED" "$FDROID_TINYUI/source"
  echo "    已提交内置包与快照 $v"
fi

echo "==> F-Droid 构建路径检查（从快照重编字节码，哈希须与 manifest 一致）"
fdroid_out=$(mktemp -d)
if ! "$FDROID_TINYUI/build.sh" --out "$fdroid_out" > "$fdroid_out.log" 2>&1; then
  tail -30 "$fdroid_out.log"
  echo "FAIL: F-Droid 构建路径不通，F-Droid 版会打不出订阅页——禁止发版"
  exit 1
fi
grep -E '^    ok |^PASS' "$fdroid_out.log"
rm -rf "$fdroid_out" "$fdroid_out.log"

echo "==> TinyUI 发版检查（宿主快照、内置包）"
./gradlew :shared:testAndroidHostTest --tests 'whl.trending.ai.tinyui.HostSnapshotTest' --tests 'whl.trending.ai.tinyui.EmbeddedPackageTest' -Ptinyui.releaseCheck -q

echo "==> 构建 r2 release"
./gradlew :androidApp:assembleR2Release -q
[ -f "$APK" ] || { echo "FAIL: 未找到产物 $APK"; exit 1; }

echo "==> 产物断言（A3 非 debuggable、A4 targetSdk、A5 .so 16 KB 对齐、A6 arm64）"
build_tools="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
badging=$("$build_tools/aapt" dump badging "$APK")
if echo "$badging" | grep -q "^application-debuggable"; then
  echo "FAIL: A3 产物为 debuggable，可调试包不得分发"; exit 1
fi
expected_target=$(sed -n 's/^android-targetSdk = "\([0-9]*\)"$/\1/p' gradle/libs.versions.toml)
actual_target=$(echo "$badging" | sed -n "s/^targetSdkVersion:'\([0-9]*\)'$/\1/p")
if [ "$actual_target" != "$expected_target" ]; then
  echo "FAIL: A4 targetSdkVersion=${actual_target}，libs.versions.toml 要求 ${expected_target}"; exit 1
fi
if ! echo "$badging" | grep "^native-code" | grep -q "arm64-v8a"; then
  echo "FAIL: A6 包内没有 arm64-v8a 原生库：$(echo "$badging" | grep '^native-code')"; exit 1
fi
# Play 自 2025-11 起拒绝不支持 16 KB 页面的更新（1.9.3 因 quickjs-kmp 0.1.2 的 4 KB 对齐被拒）。
# 判据是 ELF 每个 LOAD 段的对齐 ≥ 2**14；zip 内对齐由 AGP 负责，这里不查。
so_dir=$(mktemp -d)
unzip -q -o "$APK" 'lib/*' -d "$so_dir"
misaligned=""
while IFS= read -r so; do
  for align in $(objdump -p "$so" | awk '/LOAD/{print $NF}' | sort -u); do
    case "$align" in
      2\*\*1[4-9]|2\*\*[2-9][0-9]) ;;
      *) misaligned="${misaligned}    ${so#"$so_dir"/}  LOAD align=${align}"$'\n' ;;
    esac
  done
done < <(find "$so_dir/lib" -name '*.so' | sort)
rm -rf "$so_dir"
if [ -n "$misaligned" ]; then
  echo "FAIL: A5 以下 .so 未按 16 KB 页面对齐，Play 会拒绝这次更新——升级对应的库再发："
  printf '%s' "$misaligned"
  exit 1
fi
echo "  ok: 非 debuggable、targetSdk=${actual_target}、含 arm64-v8a、全部 .so LOAD 段对齐 ≥ 16 KB"

online() { adb devices | awk 'NR>1 && $2=="device" {print $1}'; }

echo "==> 选择设备"
serial="${ANDROID_SERIAL:-}"
if [ -z "$serial" ]; then
  devices=$(online)
  count=$(printf '%s' "$devices" | grep -c . || true)
  if [ "$count" -gt 1 ]; then
    echo "FAIL: 有 $count 台设备在线，用 ANDROID_SERIAL 指定一台："
    printf '%s\n' "$devices" | sed 's/^/    /'
    exit 1
  elif [ "$count" -eq 1 ]; then
    serial="$devices"
  else
    avd="${SMOKE_AVD:-}"
    if [ -z "$avd" ]; then
      avds=$("$SDK/emulator/emulator" -list-avds 2>/dev/null | grep -v '^INFO' || true)
      if [ "$(printf '%s' "$avds" | grep -c . || true)" -ne 1 ]; then
        echo "FAIL: 没有设备在线，且无法确定启动哪个 AVD；先开一台，或用 SMOKE_AVD 指定："
        printf '%s\n' "${avds:-（本机没有 AVD）}" | sed 's/^/    /'
        exit 1
      fi
      avd="$avds"
    fi
    echo "==> 没有设备在线，启动 $avd"
    # 主机的代理不会传给模拟器：不带 -http-proxy 时 S10 的「检查更新」直连 GitHub API 多半失败。手动启动的模拟器要自己加
    proxy_args=()
    [ -n "${http_proxy:-}" ] && proxy_args=(-http-proxy "$http_proxy")
    "$SDK/emulator/emulator" -avd "$avd" -no-snapshot-save -no-boot-anim "${proxy_args[@]}" >/dev/null 2>&1 &
    for _ in $(seq 1 60); do
      serial=$(online | head -1)
      [ -n "$serial" ] && break
      sleep 3
    done
    [ -n "$serial" ] || { echo "FAIL: $avd 启动超时"; exit 1; }
  fi
fi
adb -s "$serial" wait-for-device
adb -s "$serial" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
echo "    使用设备 ${serial}（$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')）"

echo "==> 安装（覆盖升级路径；签名不兼容时回退全新安装）"
adb -s "$serial" install -r "$APK" >/dev/null 2>&1 || {
  adb -s "$serial" uninstall "$PKG" >/dev/null 2>&1 || true
  adb -s "$serial" install "$APK" >/dev/null
}

check_alive() {
  stage="$1"
  crash=$(adb -s "$serial" logcat -d -b crash -v brief 2>/dev/null | grep -c "$PKG" || true)
  pid=$(adb -s "$serial" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
  if [ "$crash" -gt 0 ] || [ -z "$pid" ]; then
    echo "FAIL: ${stage}未通过（crash 日志行数=${crash}, pid=[${pid}]）"
    adb -s "$serial" logcat -d -b crash -v time | tail -40
    exit 1
  fi
  r8_hits=$(adb -s "$serial" logcat -d -v brief --pid="$pid" 2>/dev/null | grep -E "$R8_PATTERN" || true)
  if [ -n "$r8_hits" ]; then
    echo "FAIL: ${stage}的 logcat 里出现 R8 特征异常（进程没死，但有类 / 方法被裁掉）："
    printf '%s\n' "$r8_hits" | head -20 | sed 's/^/    /'
    exit 1
  fi
  echo "  ok: ${stage}（pid=${pid}，无 crash、无 R8 特征异常）"
}

echo "==> 启动并观察 10 秒"
adb -s "$serial" logcat -c
adb -s "$serial" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
# 16 KB 页面设备上，未按 16 KB 对齐的 .so 会让系统弹「App 兼容性」提示。A5 已经静态拦过，
# 这里再出现说明两边判据不一致，按 FAIL 处理而不是点掉继续（按系统对话框的按钮 id 识别，与界面语言无关）
if [ "$(adb -s "$serial" shell getconf PAGE_SIZE | tr -d '\r')" = "16384" ]; then
  adb -s "$serial" shell uiautomator dump /sdcard/smoke_ui.xml >/dev/null 2>&1 || true
  if adb -s "$serial" shell cat /sdcard/smoke_ui.xml 2>/dev/null | grep -q 'resource-id="android:id/button1"[^>]*package="android"'; then
    echo "FAIL: 16 KB 页面设备上弹出了系统「App 兼容性」提示——有 .so 未按 16 KB 对齐，但 A5 静态检查没拦住，先查两边判据"
    exit 1
  fi
fi
check_alive "S01 启动冒烟"

# 首页不含 TextField，只验启动会漏掉「渲染输入框才炸」的那一类。material3 与 CMP 版本
# 脱节时就是这个形状：编译、单测、启动冒烟全绿，一进聊天页/登录面板即 AbstractMethodError。
echo "==> 打开聊天页（验 TextField 渲染）"
# 入口优先从 uiautomator dump 里按 content-desc 找（chat_title 的中英文），找不到才退回底栏右侧 FAB 的百分比坐标。
# 刚启动那几秒的点击偶尔会被吞掉（列表首屏加载、悬浮底栏入场），所以点完没见到输入框就重试，最多三次
size=$(adb -s "$serial" shell wm size 2>/dev/null | sed -n 's/^Physical size: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | head -1)
w=$(echo "$size" | cut -d' ' -f1)
h=$(echo "$size" | cut -d' ' -f2)
[ -n "$w" ] && [ -n "$h" ] || { echo "FAIL: 取不到屏幕尺寸，无法定位聊天入口"; exit 1; }
chat_open=""
for attempt in 1 2 3; do
  adb -s "$serial" shell uiautomator dump /sdcard/smoke_ui.xml >/dev/null 2>&1 || true
  ui=$(adb -s "$serial" shell cat /sdcard/smoke_ui.xml 2>/dev/null || true)
  if echo "$ui" | grep -q "EditText"; then chat_open=1; break; fi
  bounds=$(echo "$ui" | grep -o '<node[^>]*content-desc="AI \(Chat\|对话\)"[^>]*>' | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | grep -o '\[[0-9,]*\]\[[0-9,]*\]' || true)
  if [ -n "$bounds" ]; then
    read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | tr -c '0-9' ' ')"
    adb -s "$serial" shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
  else
    adb -s "$serial" shell input tap $((w * 70 / 100)) $((h * 93 / 100))   # 底栏右侧的聊天 FAB
  fi
  sleep 5
done
adb -s "$serial" shell uiautomator dump /sdcard/smoke_ui.xml >/dev/null 2>&1 || true
if [ -z "$chat_open" ] && ! adb -s "$serial" shell cat /sdcard/smoke_ui.xml 2>/dev/null | grep -q "EditText"; then
  echo "FAIL: 三次都没进到含输入框的界面——聊天入口的 content-desc 或底栏布局可能已变，这一步等于没验"
  exit 1
fi
check_alive "S08 兜底：聊天页渲染"
adb -s "$serial" shell rm -f /sdcard/smoke_ui.xml >/dev/null 2>&1 || true

echo "PASS: 产物断言 A1–A6 与 S01 通过，聊天页输入框已渲染。"
echo "      接着按 docs/smoke-test-spec.md 走 S02–S10（订阅页在登录前验，登录用演示账号），全过再打 tag；结果记进文档的执行记录。"
