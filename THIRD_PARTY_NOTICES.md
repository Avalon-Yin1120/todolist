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
Project）。BSD-3-Clause 要求在二进制分发时保留版权声明，因此在此单独声明：

> Copyright 2006 The Android Open Source Project
> Redistribution and use in source and binary forms, with or without modification,
> are permitted provided that the above copyright notice and this notice are retained.
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS "AS IS" AND ANY EXPRESS OR
> IMPLIED WARRANTIES ARE DISCLAIMED.

（这是本项目 APK 里**唯一**一段非 Apache-2.0 的第三方代码；BSD-3-Clause 同样是宽松许可，
没有传染性，只要求署名。）


## 使用这些组件时需要遵守的义务

Apache-2.0 的要求只有三条，都很轻：

1. 分发时附上许可证副本（本仓库的 `LICENSE` 与每个上游项目的许可证）；
2. 保留版权、专利、商标与署名声明；
3. 若修改了文件，标注「已修改」。

因此 **重新打包分发 APK 时，请把本文件与 `LICENSE` 一起提供**。
