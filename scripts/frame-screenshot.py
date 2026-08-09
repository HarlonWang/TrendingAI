#!/usr/bin/env python3
"""给模拟器裸截图套 Android Studio 官方设备外壳（等效 AS「带外壳截图」按钮）。

用法:
    python3 scripts/frame-screenshot.py <裸截图.png ...> [-d pixel_9|pixel_tablet] [-o 输出目录]

裸截图分辨率必须与设备一致（pixel_9: 1080x2424, pixel_tablet: 2560x1600），
输出与 Android Studio 手动截图同规格（pixel_9: 1198x2531, pixel_tablet: 2798x1837）。
合成方式：机身 back.webp 打底 → 方形贴入截图 → 叠 mask.webp 前景（圆角边缘 + 挖孔摄像头）。
依赖：Pillow；外壳素材来自本机 Android Studio 安装目录。
"""

import argparse
import sys
from pathlib import Path

from PIL import Image

ART_ROOT = Path(
    "/Applications/Android Studio.app/Contents/plugins/android/resources/device-art-resources"
)

# 屏幕在机身上的偏移，取自各设备 device-art 的 layout 描述文件
DEVICES = {
    "pixel_9": {"screen": (1080, 2424), "offset": (55, 58)},
    "pixel_tablet": {"screen": (2560, 1600), "offset": (119, 117)},
}


def frame(shot_path: Path, device: str, out_dir: Path) -> Path:
    spec = DEVICES[device]
    art = ART_ROOT / device
    shot = Image.open(shot_path).convert("RGBA")
    if shot.size != spec["screen"]:
        raise SystemExit(f"{shot_path.name}: 分辨率 {shot.size} 与 {device} 屏幕 {spec['screen']} 不符")
    back = Image.open(art / "back.webp").convert("RGBA")
    mask = Image.open(art / "mask.webp").convert("RGBA")
    back.paste(shot, spec["offset"])
    back.alpha_composite(mask, spec["offset"])
    out = out_dir / shot_path.name
    back.save(out)
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("shots", nargs="+", type=Path)
    ap.add_argument("-d", "--device", choices=DEVICES, default="pixel_9")
    ap.add_argument("-o", "--out", type=Path, default=Path("framed"))
    args = ap.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    for shot in args.shots:
        out = frame(shot, args.device, args.out)
        print(f"framed: {out}")


if __name__ == "__main__":
    main()
