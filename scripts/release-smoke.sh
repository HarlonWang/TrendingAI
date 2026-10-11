#!/usr/bin/env bash
# 发布前冒烟：刷新 TinyUI 内置包与 F-Droid 页面源码快照并做发版检查，构建 r2 渠道 release 包（minify 与线上一致），装模拟器验证启动不崩。
# 背景：0.20.0 因 R8 裁掉 WorkDatabase_Impl 构造器启动即崩——debug 包不混淆测不出这类问题，
# 所以打 tag 前必须跑一次本脚本。用法：scripts/release-smoke.sh
# 设备：ANDROID_SERIAL 指定的那台；否则唯一在线的设备（模拟器、真机均可）；都没有时启动 SMOKE_AVD，
# 未设 SMOKE_AVD 且本机只有一个 AVD 就启动它。多台在线或多个 AVD 时不猜，报错列出供选。
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="whl.trending.ai"
APK="androidApp/build/outputs/apk/r2/release/androidApp-r2-release.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

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
    "$SDK/emulator/emulator" -avd "$avd" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 &
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
  echo "  ok: ${stage}（pid=${pid}）"
}

echo "==> 启动并观察 10 秒"
adb -s "$serial" logcat -c
adb -s "$serial" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
# 16 KB 页面设备上，未按 16 KB 对齐的 .so（目前是 quickjs-kmp）会让系统弹「App 兼容性」提示，挡住后面的点击。
# 按系统对话框的按钮 id 点掉（与界面语言无关），并打 WARN 让这个问题保持可见
# 点完要重新确认弹窗已退场：退场动画期间的点击会被吞掉，后面打开聊天页那一下就落空了
if [ "$(adb -s "$serial" shell getconf PAGE_SIZE | tr -d '\r')" = "16384" ]; then
  for _ in 1 2 3; do
    adb -s "$serial" shell uiautomator dump /sdcard/smoke_ui.xml >/dev/null 2>&1 || true
    ok=$(adb -s "$serial" shell cat /sdcard/smoke_ui.xml 2>/dev/null | grep -o 'resource-id="android:id/button1"[^>]*package="android"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o '\[[0-9,]*\]\[[0-9,]*\]' || true)
    [ -n "$ok" ] || break
    read -r x1 y1 x2 y2 <<<"$(echo "$ok" | tr -c '0-9' ' ')"
    adb -s "$serial" shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
    echo "  WARN: 16 KB 页面设备上弹出了兼容性提示（有 .so 未按 16 KB 对齐），已点掉继续"
    sleep 2
  done
fi
check_alive "启动冒烟"

# 首页不含 TextField，只验启动会漏掉「渲染输入框才炸」的那一类。material3 与 CMP 版本
# 脱节时就是这个形状：编译、单测、启动冒烟全绿，一进聊天页/登录面板即 AbstractMethodError。
echo "==> 打开聊天页（验 TextField 渲染）"
size=$(adb -s "$serial" shell wm size 2>/dev/null | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p')
w=$(echo "$size" | cut -d' ' -f1)
h=$(echo "$size" | cut -d' ' -f2)
[ -n "$w" ] && [ -n "$h" ] || { echo "FAIL: 取不到屏幕尺寸，无法定位聊天入口"; exit 1; }
adb -s "$serial" shell input tap $((w * 70 / 100)) $((h * 93 / 100))   # 底栏右侧的聊天 FAB
sleep 6
check_alive "聊天页渲染"

# 没点中入口时上一步等于没验，用 dump 里有没有输入框来兜底（EditText 与界面语言无关）
adb -s "$serial" shell uiautomator dump /sdcard/smoke_ui.xml >/dev/null 2>&1 || true
if ! adb -s "$serial" shell cat /sdcard/smoke_ui.xml 2>/dev/null | grep -q "EditText"; then
  echo "FAIL: 没进到含输入框的界面——聊天入口坐标可能已失效，这一步等于没验"
  exit 1
fi
adb -s "$serial" shell rm -f /sdcard/smoke_ui.xml >/dev/null 2>&1 || true

echo "PASS: 启动与 TextField 渲染均无崩溃。可以打 tag 发布。"
