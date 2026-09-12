// component_basic —— 最底层，放基础能力（网络、本地存储、日志、工具类……）
// 现在有：Application、一个"取 App 级 ViewModel"的小工具、以及网络底座（OkHttp / Retrofit）。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tscaffold.base"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // extensions/ViewModel.kt 用到了 Activity / Fragment / ViewModel，所以这几个用 api 暴露给上层
    api(libs.androidx.core.ktx)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)

    // 网络：接口声明（@GET/@POST 那些）写在上层模块里，所以用 api 暴露出去
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.okhttp)
    api(libs.gson)
    // 日志拦截器只在网络底座内部用
    implementation(libs.okhttp.logging)

    // 单元测试：直接在电脑上跑，不用模拟器
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 真起一个本地服务器跑"真实场景"测试（真 socket、真握手）
    testImplementation(libs.okhttp.mockwebserver)
}
