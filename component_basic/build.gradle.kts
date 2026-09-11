// component_basic —— 最底层，留给你自己放基础能力（网络、本地存储、日志、工具类……）
// 现在只有一个 Application 和一个"取 App 级 ViewModel"的小工具，没有任何第三方库。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.scaffold.component.basic"

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
}
