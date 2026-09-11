// component_business_basic —— 示例业务层
// 里面是两个"计数器"示例页面（XML 版 + Compose 版），共用同一个 CounterViewModel。
// 你写自己的页面时，照抄这两个的结构即可。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tscaffold.component.business.basic"
    // 资源名统一加前缀，避免以后多个模块重名打架
    resourcePrefix = "business_basic_"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // XML 页面要用 ViewBinding 直接取控件：viewBinding.tvCount
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":component_common"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)       // by viewModels()
    implementation(libs.androidx.activity.compose)   // setContent { }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
