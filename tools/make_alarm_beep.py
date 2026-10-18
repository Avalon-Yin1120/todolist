"""重新生成「闹钟式提醒」使用的提示音 `app/src/main/res/raw/alarm_beep.wav`。

## 为什么仓库里要有这个脚本

`alarm_beep.wav` 是**本项目用程序合成的**，不是从网上下载的音效：
22050 Hz / 单声道 / 16 bit，两声 880 Hz 与 1170 Hz 的纯正弦（频谱分析实测没有谐波），
中间留出静音，整段 2.8 秒、循环播放。把生成脚本放进仓库，是为了让「这条音频哪来的」
这个问题有一个可验证的答案，而不是一句口头声明。

> 注意：Android 端真正负责出声的是 `AlarmRinger`，它优先播放这个内置音频；
> **不要**把它换成任何来路不明的音效素材，那会立刻引入一项许可风险。

用法：

    python tools/make_alarm_beep.py            # 覆盖写入资源文件
    python tools/make_alarm_beep.py --out x.wav
"""

import argparse
import math
import struct
import wave

RATE = 22050          # 采样率：够用且体积小（旧机型的闹钟音频也常用这个档位）
DURATION = 2.8        # 总时长（秒）
AMPLITUDE = 0.55      # 峰值占满量程的比例，留出余量避免削波
TONE_A = 880.0        # Hz
TONE_B = 1170.0       # Hz
TONE_LEN = 0.19       # 每声时长
GAP_AFTER_A = 0.05    # 两声之间的静音
GAP_AFTER_B = 0.07    # 一组结束后到下一组的静音
RAMP = 0.012          # 起落斜坡，避免「咔」的爆音


def tone(freq, seconds, rate=RATE):
    """带线性起落斜坡的正弦片段。"""
    n = int(round(seconds * rate))
    ramp = max(1, int(round(RAMP * rate)))
    out = []
    for i in range(n):
        v = math.sin(2 * math.pi * freq * i / rate)
        if i < ramp:
            v *= i / ramp
        elif i > n - ramp:
            v *= (n - i) / ramp
        out.append(v)
    return out


def silence(seconds, rate=RATE):
    return [0.0] * int(round(seconds * rate))


def build():
    one_group = (
        tone(TONE_A, TONE_LEN)
        + silence(GAP_AFTER_A)
        + tone(TONE_B, TONE_LEN)
        + silence(GAP_AFTER_B)
    )
    total = int(round(DURATION * RATE))
    samples = []
    while len(samples) < total:
        samples.extend(one_group)
    return samples[:total]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        default="app/src/main/res/raw/alarm_beep.wav",
        help="输出的 wav 路径（默认写到 Android 资源目录）",
    )
    args = parser.parse_args()

    samples = build()
    peak = AMPLITUDE * 32767
    frames = b"".join(
        struct.pack("<h", max(-32767, min(32767, int(round(s * peak)))))
        for s in samples
    )

    with wave.open(args.out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)

    print("已写出 %s：%.2f 秒，%d Hz，单声道 16 bit" % (args.out, len(samples) / RATE, RATE))


if __name__ == "__main__":
    main()
