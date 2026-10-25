# 第三方组件声明（THIRD_PARTY_NOTICES）

本文件由 `tools/gen_third_party_notices.py` 从 **实际解析出来的依赖树**生成，
请勿手工编辑。重新生成：

```powershell
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath --offline --console=plain *> build\deps-runtime.log
python tools\gen_third_party_notices.py build\deps-runtime.log
```

## 结论先说

* 打进 APK 的运行时依赖**全部是宽松许可**（Apache-2.0 及 MIT/BSD 等），
  **没有任何 GPL / AGPL / LGPL 代码**。本项目自身以 GPLv3 发布，与这些宽松许可的依赖相容。
* 测试专用依赖里存在 **EPL-1.0**（JUnit 4）与 **CDDL + GPLv2 + Classpath Exception**
  （`javax.annotation-api`）等，但它们只在构建/测试期使用，**不会进入 APK**，
  也不构成对本项目源码的传染（详见仓库根目录的许可证扫描报告）。

## 随 APK 分发的组件（99 个模块）

### The Apache Software License, Version 2.0（94 个）

- `androidx.activity:activity:1.9.3`
- `androidx.activity:activity-compose:1.9.3`
- `androidx.activity:activity-ktx:1.9.3`
- `androidx.annotation:annotation:1.8.1`
- `androidx.annotation:annotation-experimental:1.4.1`
- `androidx.annotation:annotation-jvm:1.8.1`
- `androidx.arch.core:core-common:2.2.0`
- `androidx.arch.core:core-runtime:2.2.0`
- `androidx.autofill:autofill:1.0.0`
- `androidx.collection:collection:1.4.4`
- `androidx.collection:collection-jvm:1.4.4`
- `androidx.collection:collection-ktx:1.4.4`
- `androidx.compose:compose-bom:2025.01.00`
- `androidx.compose.animation:animation:1.7.6`
- `androidx.compose.animation:animation-android:1.7.6`
- `androidx.compose.animation:animation-core:1.7.6`
- `androidx.compose.animation:animation-core-android:1.7.6`
- `androidx.compose.foundation:foundation:1.7.6`
- `androidx.compose.foundation:foundation-android:1.7.6`
- `androidx.compose.foundation:foundation-layout:1.7.6`
- `androidx.compose.foundation:foundation-layout-android:1.7.6`
- `androidx.compose.material:material-icons-core:1.7.6`
- `androidx.compose.material:material-icons-core-android:1.7.6`
- `androidx.compose.material:material-icons-extended:1.7.6`
- `androidx.compose.material:material-icons-extended-android:1.7.6`
- `androidx.compose.material:material-ripple:1.7.6`
- `androidx.compose.material:material-ripple-android:1.7.6`
- `androidx.compose.material3:material3:1.3.1`
- `androidx.compose.material3:material3-android:1.3.1`
- `androidx.compose.runtime:runtime:1.7.6`
- `androidx.compose.runtime:runtime-android:1.7.6`
- `androidx.compose.runtime:runtime-saveable:1.7.6`
- `androidx.compose.runtime:runtime-saveable-android:1.7.6`
- `androidx.compose.ui:ui:1.7.6`
- `androidx.compose.ui:ui-android:1.7.6`
- `androidx.compose.ui:ui-geometry:1.7.6`
- `androidx.compose.ui:ui-geometry-android:1.7.6`
- `androidx.compose.ui:ui-graphics:1.7.6`
- `androidx.compose.ui:ui-graphics-android:1.7.6`
- `androidx.compose.ui:ui-text:1.7.6`
- `androidx.compose.ui:ui-text-android:1.7.6`
- `androidx.compose.ui:ui-tooling-preview:1.7.6`
- `androidx.compose.ui:ui-tooling-preview-android:1.7.6`
- `androidx.compose.ui:ui-unit:1.7.6`
- `androidx.compose.ui:ui-unit-android:1.7.6`
- `androidx.compose.ui:ui-util:1.7.6`
- `androidx.compose.ui:ui-util-android:1.7.6`
- `androidx.concurrent:concurrent-futures:1.1.0`
- `androidx.concurrent:concurrent-futures-ktx:1.1.0`
- `androidx.core:core:1.15.0`
- `androidx.core:core-ktx:1.15.0`
- `androidx.customview:customview-poolingcontainer:1.0.0`
- `androidx.emoji2:emoji2:1.3.0`
- `androidx.graphics:graphics-path:1.0.1`
- `androidx.interpolator:interpolator:1.0.0`
- `androidx.lifecycle:lifecycle-common:2.8.7`
- `androidx.lifecycle:lifecycle-common-java8:2.8.7`
- `androidx.lifecycle:lifecycle-common-jvm:2.8.7`
- `androidx.lifecycle:lifecycle-livedata:2.8.7`
- `androidx.lifecycle:lifecycle-livedata-core:2.8.7`
- `androidx.lifecycle:lifecycle-livedata-core-ktx:2.8.7`
- `androidx.lifecycle:lifecycle-process:2.8.7`
- `androidx.lifecycle:lifecycle-runtime:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-android:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-compose-android:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
- `androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.7`
- `androidx.lifecycle:lifecycle-service:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-android:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7`
- `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7`
- `androidx.profileinstaller:profileinstaller:1.3.1`
- `androidx.room:room-common:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- `androidx.room:room-runtime:2.6.1`
- `androidx.savedstate:savedstate:1.2.1`
- `androidx.savedstate:savedstate-ktx:1.2.1`
- `androidx.sqlite:sqlite:2.4.0`
- `androidx.sqlite:sqlite-framework:2.4.0`
- `androidx.startup:startup-runtime:1.1.1`
- `androidx.tracing:tracing:1.2.0`
- `androidx.tracing:tracing-ktx:1.2.0`
- `androidx.versionedparcelable:versionedparcelable:1.1.1`
- `androidx.work:work-runtime:2.10.0`
- `androidx.work:work-runtime-ktx:2.10.0`
- `org.jetbrains:annotations:23.0.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3`

### The Apache License, Version 2.0（4 个）

- `org.jetbrains.kotlin:kotlin-stdlib:2.1.0`
- `org.jetbrains.kotlin:kotlin-stdlib-common:2.1.0`
- `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22`
- `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22`

### （POM 未声明，上游实际为 Apache-2.0）（1 个）

- `com.google.guava:listenablefuture:1.0`


## 附加说明：APK 里内嵌的 C++ 代码

`androidx.graphics:graphics-path` 的 `lib/*/libandroidx.graphics.path.so` 里包含
**Skia** 的 `SkPath` 移植代码（BSD-3-Clause，Copyright 2006 The Android Open Source
Project）。BSD-3-Clause 是「保留版权声明 + 免责声明 + 不得用作者名义背书」三条，
其中第 3 条（不得用版权人/贡献者的名字为衍生产品背书）在二进制分发时同样生效，
因此这里给出**完整的许可证原文**（不是摘要），以履行「保留声明」的义务：

> Copyright 2006 The Android Open Source Project
>
> Redistribution and use in source and binary forms, with or without
> modification, are permitted provided that the following conditions are
> met:
>
> 1. Redistributions of source code must retain the above copyright
>    notice, this list of conditions and the following disclaimer.
> 2. Redistributions in binary form must reproduce the above copyright
>    notice, this list of conditions and the following disclaimer in the
>    documentation and/or other materials provided with the distribution.
> 3. Neither the name of the copyright holder nor the names of its
>    contributors may be used to endorse or promote products derived from
>    this software without specific prior written permission.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
> "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
> LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
> A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
> HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
> SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
> LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
> DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
> THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
> (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
> OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

（这是本项目 APK 里**唯一**一段非 Apache-2.0 的第三方代码；BSD-3-Clause 同样是宽松许可，
没有传染性，只要求署名与不得背书。）


## 使用这些组件时需要遵守的义务

Apache-2.0 的对应义务写在它的第 4 条里，共四项，前两项常见、后两项容易漏：

1. **附许可证副本**（§4(a)）：分发时给出 Apache-2.0 全文，本仓库是 `LICENSE`
   与 `NOTICE`（本项目自身是 GPLv3，这点见下一段）；
2. **标注修改**（§4(b)）：改动过的文件要显著注明「已修改」；
3. **保留声明**（§4(c)）：保留源码形式里的版权、专利、商标与署名声明
   （与衍生作品无关的除外）；
4. **NOTICE 文件的条件性义务**（§4(d)）：**只有**当上游的分发物里含有 `NOTICE`
   文本文件时才有这一条——此时衍生作品必须**可读地**附带该 NOTICE 里的归属声明
   （可以放在本文件或分发包的类似位置，但必须能读到；NOTICE 里若声明了免责/责任限制，
   也不能删）。AndroidX 的部分 AAR 与 Kotlin 的部分 jar 就带 `NOTICE`，
   因此这条并不是「永远用不上」的条款：
   本文件正是用来履行它的载体——把上游归属信息集中在一处，随 APK 一起提供。

注意与本项目**自身**许可证的区别：本项目以 GPLv3 发布（见 `LICENSE`），
上面四项是「使用这些第三方组件」产生的义务，与「使用本项目」产生的 GPLv3 义务
是两件事，不要混为一谈。

因此 **重新打包分发 APK 时，请把本文件与 `LICENSE` 一起提供**。
