plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.demo.tscaffold"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.demo.tscaffold"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 列表页 / 登录页是 XML 写的，要用 ViewBinding 取控件：binding.tvListTitle
        viewBinding = true
    }
}

dependencies {
    // 模块分层（2026-09-12 修正了方向）：app（最上层）
    //     → component_business_basic（业务基础页面）
    //     → component_basic（基础能力：网络底座）
    //     → component_common（最底层：MVI 核心）
    // app 三层都直接用到了，所以都显式声明（不靠转发，看这一处就知道 app 依赖谁）。
    implementation(project(":component_business_basic"))
    implementation(project(":component_basic"))
    implementation(project(":component_common"))

    // 本 App 的启动初始化（AppInitializer）要写 Initializer，所以这边也要显式声明
    implementation(libs.androidx.startup.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 示例页面用到的两个控件（原来声明在 component_business_basic，随页面一起搬过来）
    implementation(libs.androidx.recyclerview)        // 列表页
    implementation(libs.androidx.swiperefreshlayout)   // 列表页的下拉刷新
    testImplementation(libs.junit)
    // pokemontcg 的接口测试要真起一个本地服务器，并用 runBlocking 起协程
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}