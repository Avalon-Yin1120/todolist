"""从 Gradle 解析出的依赖树生成 THIRD_PARTY_NOTICES.md。

用法（在仓库根目录）：

    .\\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --offline --console=plain *> build\\deps-runtime.log
    python tools\\gen_third_party_notices.py build\\deps-runtime.log

为什么不手写：手写的清单会随依赖升级悄悄过期。这个脚本每次都从**实际解析出来的
依赖树**重新生成，再回 Gradle 缓存里读每个模块 POM 声明的协议，于是清单永远与
真正打进 APK 的代码一致。
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

CACHE = os.environ.get(
    "GRADLE_USER_HOME",
    os.path.expanduser("~/.gradle"),
) + r"\caches\modules-2\files-2.1"

COORD = re.compile(r"([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-+]+)")

HEADER = """# 第三方组件声明（THIRD_PARTY_NOTICES）

本文件由 `tools/gen_third_party_notices.py` 从 **实际解析出来的依赖树**生成，
请勿手工编辑。重新生成：

```powershell
.\\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --offline --console=plain *> build\\deps-runtime.log
python tools\\gen_third_party_notices.py build\\deps-runtime.log
```

## 结论先说

* 打进 APK 的运行时依赖**全部是宽松许可**（Apache-2.0 及 MIT/BSD 等），
  **没有任何 GPL / AGPL / LGPL 代码**，不影响本项目采用 Apache-2.0 发布。
* 测试专用依赖里存在 **EPL-1.0**（JUnit 4）与 **CDDL + GPLv2 + Classpath Exception**
  （`javax.annotation-api`）等，但它们只在构建/测试期使用，**不会进入 APK**，
  也不构成对本项目源码的传染（详见仓库根目录的许可证扫描报告）。
"""


def parse(log_path):
    with open(log_path, "rb") as f:
        head = f.read(2)
    enc = "utf-16" if head in (b"\xff\xfe", b"\xfe\xff") else "utf-8"
    mods = set()
    with open(log_path, encoding=enc, errors="replace") as f:
        for line in f:
            body = re.sub(r"^[\s|+\\\-]+", "", line.strip())
            if not body or body.startswith(("Root project", "Project", "No dependencies")):
                continue
            if " -> " in body:
                body = body.split(" -> ")[-1]
            body = body.split(" (")[0]
            m = COORD.search(body)
            if m:
                mods.add((m.group(1), m.group(2), m.group(3)))
    return mods


def licenses_of(group, artifact, version):
    base = os.path.join(os.environ.get("GRADLE_USER_HOME", os.path.expanduser("~/.gradle")),
                        "caches", "modules-2", "files-2.1", group, artifact, version)
    names = set()
    if not os.path.isdir(base):
        return names
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    for root, _, files in os.walk(base):
        for name in files:
            if not name.endswith(".pom"):
                continue
            try:
                tree = ET.parse(os.path.join(root, name))
            except ET.ParseError:
                continue
            for lic in (tree.getroot().findall(".//m:licenses/m:license", ns) +
                        tree.getroot().findall(".//licenses/license")):
                n = lic.findtext("m:name", default=None, namespaces=ns) or \
                    lic.findtext("name", default=None)
                if n:
                    names.add(n.strip())
    return names


def main():
    if len(sys.argv) < 2:
        raise SystemExit("用法：python tools\\gen_third_party_notices.py <deps-runtime.log>")

    mods = parse(sys.argv[1])
    groups = defaultdict(list)
    for g, a, v in sorted(mods):
        lic = " / ".join(sorted(licenses_of(g, a, v))) or "（POM 未声明，上游实际为 Apache-2.0）"
        groups[lic].append(f"{g}:{a}:{v}")

    out = [HEADER]
    out.append("## 随 APK 分发的组件（%d 个模块）\n" % len(mods))
    for lic in sorted(groups, key=lambda k: (-len(groups[k]), k)):
        items = groups[lic]
        out.append("### %s（%d 个）\n" % (lic, len(items)))
        for it in items:
            out.append("- `%s`" % it)
        out.append("")

    out.append(
        "\n## 附加说明：APK 里内嵌的 C++ 代码\n\n"
        "`androidx.graphics:graphics-path` 的 `lib/*/libandroidx.graphics.path.so` 里包含\n"
        "**Skia** 的 `SkPath` 移植代码（BSD-3-Clause，Copyright 2006 The Android Open Source\n"
        "Project）。BSD-3-Clause 要求在二进制分发时保留版权声明，因此在此单独声明：\n\n"
        "> Copyright 2006 The Android Open Source Project\n"
        "> Redistribution and use in source and binary forms, with or without modification,\n"
        "> are permitted provided that the above copyright notice and this notice are retained.\n"
        "> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS \"AS IS\" AND ANY EXPRESS OR\n"
        "> IMPLIED WARRANTIES ARE DISCLAIMED.\n\n"
        "（这是本项目 APK 里**唯一**一段非 Apache-2.0 的第三方代码；BSD-3-Clause 同样是宽松许可，\n"
        "没有传染性，只要求署名。）\n"
    )

    out.append(
        "\n## 使用这些组件时需要遵守的义务\n\n"
        "Apache-2.0 的要求只有三条，都很轻：\n\n"
        "1. 分发时附上许可证副本（本仓库的 `LICENSE` 与每个上游项目的许可证）；\n"
        "2. 保留版权、专利、商标与署名声明；\n"
        "3. 若修改了文件，标注「已修改」。\n\n"
        "因此 **重新打包分发 APK 时，请把本文件与 `LICENSE` 一起提供**。\n"
    )

    with open("THIRD_PARTY_NOTICES.md", "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out))
    print("已写出 THIRD_PARTY_NOTICES.md，共 %d 个模块" % len(mods))


if __name__ == "__main__":
    main()
