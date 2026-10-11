import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.todolist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.todolist"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
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
