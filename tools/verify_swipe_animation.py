"""验证日/周/月切换是否存在过渡动画。

思路：切换瞬间用 adb 连续截图（每张约 200-300ms），
如果存在过渡动画，就必然能抓到「两次视图同时可见」的中间帧——
表现为该帧与切换前的稳定帧、切换后的稳定帧都明显不同，
且差异主要来自水平位移。

对照组：把系统动画缩放设为 0 后再跑一次，此时不应出现明显的中间帧。

依赖：Pillow（仅用于读像素，不生成新图）。
"""

import subprocess
import sys
import time
from pathlib import Path

from PIL import Image, ImageChops

ADB = r"D:\Android\sdk\platform-tools\adb.exe"
SERIAL = "emulator-5554"
OUT = Path(r"D:\Android\anim")
DEVICE_PNG = "/sdcard/anim.png"


def adb(*args, capture=False):
    cmd = [ADB, "-s", SERIAL, *args]
    if capture:
        return subprocess.run(cmd, capture_output=True, check=False)
    return subprocess.run(cmd, capture_output=True, check=False)


def grab(path: Path):
    """抓一张当前屏幕。"""
    adb("shell", "screencap", "-p", DEVICE_PNG)
    adb("pull", DEVICE_PNG, str(path))
    return path


def diff_ratio(a: Path, b: Path):
    """返回两图的平均像素差（0-255）与不同像素占比。"""
    ia = Image.open(a).convert("RGB")
    ib = Image.open(b).convert("RGB")
    if ia.size != ib.size:
        return 255.0, 1.0
    d = ImageChops.difference(ia, ib)
    hist = d.convert("L").histogram()
    total = sum(hist)
    changed = sum(hist[16:])  # 差异大于 16 视为「明显不同」
    stat = sum(i * n for i, n in enumerate(hist)) / total
    return stat, changed / total


def run_case(label: str, tag_out: str, tap_xy, shots: int = 6):
    d = OUT / tag_out
    d.mkdir(parents=True, exist_ok=True)
    for f in d.glob("*.png"):
        f.unlink()

    # 回到主界面并切到目标之前的稳定状态
    before = grab(d / "00_before.png")

    # 触发切换，同时尽快连拍
    subprocess.run(
        [ADB, "-s", SERIAL, "shell", "input", "tap", str(tap_xy[0]), str(tap_xy[1])],
        capture_output=True,
        check=False,
    )
    t0 = time.time()
    mid = []
    for i in range(shots):
        mid.append(grab(d / f"{i + 1:02d}_mid.png"))
    dt = time.time() - t0
    after = grab(d / "99_after.png")

    print(f"\n=== {label} ===")
    print(f"连拍 {shots} 张耗时 {dt:.2f}s（单张 {dt / shots * 1000:.0f}ms）")
    base, base_ratio = diff_ratio(before, after)
    print(f"切换前 vs 切换后稳定帧：平均差 {base:.1f}，明显不同像素 {base_ratio * 100:.1f}%")
    print("各中间帧：")
    for p in mid:
        s1, r1 = diff_ratio(p, before)
        s2, r2 = diff_ratio(p, after)
        # 与前后都不同 → 是过渡帧
        transitional = r1 > 0.02 and r2 > 0.02
        mark = "  <== 过渡帧（前后视图同时可见）" if transitional else ""
        print(
            f"  {p.name}: vs前 平均{s1:5.1f}/{r1 * 100:5.1f}%  "
            f"vs后 平均{s2:5.1f}/{r2 * 100:5.1f}%{mark}"
        )
    return mid, before, after


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    # 先把系统动画打开，确保测的是应用自身动画
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", k, "1")
    adb("shell", "am", "force-stop", "com.avalonyin1120.todolist")
    adb("shell", "am", "start", "-n", "com.avalonyin1120.todolist/.MainActivity")
    time.sleep(4)

    # 底部导航坐标：720x1600 下 日/周/月 约在 y=1490
    # 坐标取自 uiautomator dump（720x1600 设备）
    TAB_DAY = (268, 1504)
    TAB_WEEK = (452, 1504)
    TAB_MONTH = (636, 1504)

    # 起始状态：日视图
    adb("shell", "input", "tap", str(TAB_DAY[0]), str(TAB_DAY[1]))
    time.sleep(1.5)

    print("系统动画缩放 = 1（默认）")
    run_case("日视图 -> 周视图", "anim_on_day_to_week", TAB_WEEK)
    run_case("日视图 -> 月视图", "anim_on_day_to_month", TAB_MONTH)

    # 对照组：关掉动画
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", k, "0")
    time.sleep(1)
    adb("shell", "input", "tap", str(TAB_DAY[0]), str(TAB_DAY[1]))
    time.sleep(1.5)
    print("\n系统动画缩放 = 0（对照组）")
    run_case("日视图 -> 周视图（对照）", "anim_off_day_to_week", TAB_WEEK)

    # 恢复系统动画
    for k in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        adb("shell", "settings", "put", "global", k, "0")

    print(f"\n截图保存在 {OUT}")


if __name__ == "__main__":
    sys.exit(main())
