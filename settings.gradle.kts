// ============================================================================
// Scaffold（框架工程，由母工程 MetaLiveAssistant 脚手架适配而来）
// 分层：app（Compose 壳）→ component_business_basic → component_common → component_basic
// 依赖与版本统一在 gradle/libs.versions.toml（Version Catalog）管理。
// 说明：本机到部分 maven 源（repo.maven.apache.org / dl.google.com / api.foojay.io）的
//       TLS 连接不稳定，故前置了官方镜像（repo1 / 阿里云 google 与 public 镜像）；
//       如网络正常可删除镜像仓库与注释。
// ============================================================================
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven(url = "https://maven.aliyun.com/repository/google") // Google Maven 镜像
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // 已禁用 foojay-resolver：其自动下载 JDK 需要访问 api.foojay.io（本机构建环境不可达）。
    // 改用 gradle.properties 中 org.gradle.java.installations.paths 登记本地 JDK（含 Android Studio 自带 JBR 25）。
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven.aliyun.com/repository/google") // Google Maven 镜像
        maven(url = "https://repo1.maven.org/maven2/")
        maven(url = "https://maven.aliyun.com/repository/public") // Maven Central 镜像
        mavenCentral()
    }
}

rootProject.name = "Scaffold"
include(":app")
include(":component_basic")
include(":component_common")
include(":component_business_basic")
