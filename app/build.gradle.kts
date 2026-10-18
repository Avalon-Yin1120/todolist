import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/**
 * release 签名配置。
 *
 * 从 `release/keystore.properties` 读取（该目录已在 .gitignore 里排除，
 * keystore 与口令都不进版本库）。文件不存在时**不报错**，只是退回 debug 签名——
 * 这样别人 clone 下来只想编译/跑测试时不会被卡住，代价是产出的 release 包不能上架。
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("release/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.avalonyin1120.todolist"
    compileSdk = 35

    defaultConfig {
        // **预览版与已装的正式版共存**：用户机上还装着 `1.0.0`（versionCode 1），
        // 想同时装预览版就必须换 applicationId —— Android 用包名标识应用，
        // 同包名的包会互相覆盖（签名相同也一样覆盖）。
        // 注意：`namespace` 与代码里的包名**仍是** com.avalonyin1120.todolist，
        // 只有安装标识带 `.preview`；action 字符串、R 类、数据库名都不变。
        // ⚠️ 发布正式版时把这一行改回 `com.avalonyin1120.todolist`。
        applicationId = "com.avalonyin1120.todolist.preview"
        minSdk = 26
        targetSdk = 35
        // **预览版**：应用改为 GitHub 开源 + GitHub Release 分发，不再走应用商店。
        // 版本号带 preview → 「设置 → 版本号」显示「预览构建」（见 VersionInfo.buildTypeLabel），
        // 用来提醒下载的人这是开发中的包。
        //
        // versionCode 升到 4：这一版加了「个性化」「一句话新建」「事件日志」三块新功能，
        // 且 applicationId 加了 .preview 后缀（与已装的 1.0.0 共存）。
        // 纪律：每次发版 versionCode 必须 +1；versionName 只给人看。
        versionCode = 7
        versionName = "1.1.1-preview2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // minSdk 26 ⇒ Android 7.0 起就认 v2 签名，不必再打 v1（jar 签名）；
                // v3 保留，它让将来「换签名密钥」成为可能。
                // 实测 apksigner：v2=true、v3=true、v1=false，符合预期。
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 发布包开启 R8 与资源压缩。Compose / Room / WorkManager 都自带 consumer 规则，
            // 我们这边只需额外保住「由系统按类名实例化的组件」等少数入口，
            // 见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // 没有签名材料时用 debug 签名兜底：仍能构建、能装机，用来验证
                // 「混淆有没有把功能优化坏」；只是这样的包不能上架。
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 需要 BuildConfig.DEBUG 来判断调试构建（用于只在 debug 生效的测试开关）
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        // release 构建默认会顺带跑 lintVital（只拦致命问题）。本机环境取不到
        // com.android.tools.lint:lint-gradle（离线缓存里没有，Google Maven 也拉不动），
        // 跑它会让 assembleRelease 直接失败，所以这里关掉。
        // 它只决定「构建时是否顺手跑一遍检查」，不影响产物本身；
        // 发布前请在有网环境执行 `.\gradlew :app:lint` 看完整报告（见 README §10）。
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            all {
                // Robolectric 默认会挑它能支持的最高 SDK；显式钉在 35，
                // 与 compileSdk / targetSdk 一致，避免上游发布新 API 后测试行为漂移。
                it.systemProperty("robolectric.enabledSdks", "35")
                it.jvmArgs(
                    "-Dfile.encoding=UTF-8",
                    // Robolectric 每个测试类都要建一次 Android 沙箱，堆给小了会频繁 GC，
                    // 表现为整批测试卡在某个类之后长时间没有输出。
                    "-Xmx2048m",
                    "-XX:MaxMetaspaceSize=768m",
                )
                // 让测试结果的 XML 在每个类结束时立刻落盘：
                // 整批卡住时也能看到已经跑完哪些类，不必等任务结束。
                it.reports.junitXml.required.set(true)
                it.testLogging {
                    events("passed", "skipped", "failed")
                    showStandardStreams = false
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX 基础
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager（非精确提醒策略）
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 调试期 Compose 工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 单元测试（JVM + Robolectric）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.work:work-testing:2.10.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")

    // 仪器化测试（模拟器）
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
