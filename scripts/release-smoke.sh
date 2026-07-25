#!/usr/bin/env bash
# 发布前冒烟：构建 r2 渠道 release 包（minify 与线上一致），装模拟器验证启动不崩。
# 背景：0.20.0 因 R8 裁掉 WorkDatabase_Impl 构造器启动即崩——debug 包不混淆测不出这类问题，
# 所以打 tag 前必须跑一次本脚本。用法：scripts/release-smoke.sh
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="whl.trending.ai"
AVD="Pixel_9_2"
APK="androidApp/build/outputs/apk/r2/release/androidApp-r2-release.apk"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

echo "==> 构建 r2 release"
./gradlew :androidApp:assembleR2Release -q
[ -f "$APK" ] || { echo "FAIL: 未找到产物 $APK"; exit 1; }

echo "==> 定位 $AVD 模拟器"
serial=""
for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
  # 真机不认 emu 命令（pipefail 下赋值即非零），且不匹配时 [ ] && 串联同样会被 set -e 判定失败——
  # 两处都必须兜住，否则真机在线就会让脚本在这里静默退出
  name=$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r') || name=""
  if [ "$name" = "$AVD" ]; then serial="$s"; break; fi
done
if [ -z "$serial" ]; then
  echo "==> $AVD 未在线，启动中"
  "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot-save -no-boot-anim >/dev/null 2>&1 &
  for _ in $(seq 1 60); do
    for s in $(adb devices | awk 'NR>1 && $2=="device" {print $1}'); do
      name=$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r') || name=""
      if [ "$name" = "$AVD" ]; then serial="$s"; break 2; fi
    done
    sleep 3
  done
  [ -n "$serial" ] || { echo "FAIL: $AVD 启动超时"; exit 1; }
  adb -s "$serial" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
fi
echo "    使用设备 $serial"

echo "==> 安装（覆盖升级路径；签名不兼容时回退全新安装）"
adb -s "$serial" install -r "$APK" >/dev/null 2>&1 || {
  adb -s "$serial" uninstall "$PKG" >/dev/null 2>&1 || true
  adb -s "$serial" install "$APK" >/dev/null
}

echo "==> 启动并观察 10 秒"
adb -s "$serial" logcat -c
adb -s "$serial" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10

crash=$(adb -s "$serial" logcat -d -b crash -v brief 2>/dev/null | grep -c "$PKG" || true)
pid=$(adb -s "$serial" shell pidof "$PKG" 2>/dev/null | tr -d '\r' || true)
if [ "$crash" -gt 0 ] || [ -z "$pid" ]; then
  echo "FAIL: 启动冒烟未通过（crash 日志行数=${crash}, pid=[${pid}]）"
  adb -s "$serial" logcat -d -b crash -v time | tail -40
  exit 1
fi
echo "PASS: 进程存活（pid=${pid}），无崩溃日志。可以打 tag 发布。"
