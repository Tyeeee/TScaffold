// ============================================================================
// TScaffold —— 只保留 MVI 写法的安卓骨架工程
// 分层：app（应用外壳）→ component_business_basic（你的页面）→ component_common（MVI 核心）→ component_basic（基础能力）
// 依赖与版本统一在 gradle/libs.versions.toml（Version Catalog）管理。
// 说明：本机直连 dl.google.com 与 repo1.maven.org 经常 TLS 握手失败 / 卡住，
//       所以解析仓库只用阿里云镜像（google 与 public 两个），不再回落到官方源。
//       换到网络正常的机器上，把 google() 和 mavenCentral() 加回列表即可。
// ============================================================================
pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google") // Google Maven 镜像
        maven(url = "https://maven.aliyun.com/repository/public") // Maven Central 镜像（Kotlin 插件在这里）
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin") // Gradle 插件门户镜像
    }
}
plugins {
    // 已禁用 foojay-resolver：其自动下载 JDK 需要访问 api.foojay.io（本机构建环境不可达）。
    // 改用 gradle.properties 中 org.gradle.java.installations.paths 登记本地 JDK（含 Android Studio 自带 JBR 25）。
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google") // Google Maven 镜像
        maven(url = "https://maven.aliyun.com/repository/public") // Maven Central 镜像
    }
}

rootProject.name = "TScaffold"
include(":app")
include(":component_basic")
include(":component_common")
include(":component_business_basic")
