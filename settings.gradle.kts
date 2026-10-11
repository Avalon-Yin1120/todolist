// 仓库配置说明：
//
// Gradle 的 `mavenCentral()` 会解析到 repo.maven.apache.org，该域名在本机网络下
// TCP 直接超时（实测），会让依赖解析长时间卡住。因此这里显式使用可用的端点：
//   * Maven Central -> repo1.maven.org（实测可用），并保留腾讯云镜像作为兜底
//   * Google Maven  -> dl.google.com/dl/android/maven2（实测可用）

pluginManagement {
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") {
            name = "MavenCentralByHost"
        }
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") {
            name = "TencentMavenPublic"
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://repo1.maven.org/maven2") {
            name = "MavenCentralByHost"
        }
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public") {
            name = "TencentMavenPublic"
        }
    }
}

rootProject.name = "Todolist"
include(":app")
