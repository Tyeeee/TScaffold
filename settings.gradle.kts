// ============================================================================
// TScaffold —— 只保留 MVI 写法的安卓骨架工程
// 分层：app（应用外壳）→ component_business_basic（你的页面）→ component_common（MVI 核心）→ component_basic（基础能力）
// 依赖与版本统一在 gradle/libs.versions.toml（Version Catalog）管理。
// 说明：本机直连 dl.google.com 与 repo1.maven.org 经常 TLS 握手失败 / 卡住，
//       所以国内镜像放最前面，而且**全工程统一用腾讯云这一个源**：
//       它的 maven-public 一个仓库就覆盖了 androidx / Google Maven / Maven Central / 插件标记，
//       Gradle 发行包也走同一个源（见 gradle/wrapper/gradle-wrapper.properties），
//       免得一会儿阿里一会儿腾讯。官方源留在后面兜底，网络正常的机器会命中官方源。
// ============================================================================
pluginManagement {
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") // 国内镜像（统一腾讯云）
        google()             // 官方兜底
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // 这里不放 foojay-resolver：本工程没有声明 toolchain，不需要 Gradle 自动下载 JDK，
    // 也就不需要访问 api.foojay.io。Gradle 直接用启动它的那个 JVM 即可。
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") // 国内镜像（统一腾讯云）
        google()             // 官方兜底
        mavenCentral()
    }
}

rootProject.name = "TScaffold"
include(":app")
include(":component_basic")
include(":component_common")
include(":component_business_basic")
