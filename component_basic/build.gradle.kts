// component_basic —— 基础能力：网络底座、MMKV 封装、Application、通用扩展
// 现在有：BasicApplication、网络底座（OkHttp / Retrofit / WebSocket）、MMKV 封装（MMKVUtils，
// 用 AndroidX Startup 自己挂初始化）、"取 App 级 ViewModel"的小工具。
//
// 依赖方向：common（最底） ← **basic** ← business ← app
// 它站在 common 之上。这里 api 引用 common，是为了"上层只要依赖 basic 就同时拿到 common"，
// 和以前 common 转发 basic 的写法对称 —— 只是这回方向是对的。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tscaffold.basic"

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
    // 站在 common 之上：业务层只要依赖 basic，就同时拿到 common（MVI 核心）
    api(project(":component_common"))

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

    // MMKV 封装。用 implementation：业务模块 import 不到 MMKV，只能走 MMKVUtils
    implementation(libs.mmkv)
    // 启动初始化。也从这走 implementation：业务不需要知道底层是 Startup，
    // provider 会随清单合并到 App 里，运行时类在就行
    implementation(libs.androidx.startup.runtime)

    // 单元测试：直接在电脑上跑，不用模拟器
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 真起一个本地服务器跑"真实场景"测试（真 socket、真握手）
    testImplementation(libs.okhttp.mockwebserver)

    // 设备上的测试：MMKV 是 native 库，只能在真机/模拟器上跑
    androidTestImplementation(libs.androidx.junit)
}
