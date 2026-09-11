// component_common —— MVI 核心（只有 MVI 相关的东西）
// 内容：BaseViewModel（State / Intent / Effect 三件套）、BaseContract（页面约定模板）、
//      BaseActivity / BaseFragment（XML 页面基类）、compose/MviCompose.kt（Compose 页面小工具）
plugins {
    alias(libs.plugins.android.library)
    // 因为有一个 Compose 小工具文件，所以这个模块也要开 Compose 编译
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tscaffold.component.common"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // BaseActivity / BaseFragment 的泛型要引用 ViewBinding，所以这里要打开
        viewBinding = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":component_basic"))

    // BaseActivity 继承 AppCompatActivity、BaseFragment 继承 Fragment，都用 api 暴露给上层
    api(libs.androidx.appcompat)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose 版小工具：observeState() / observeEffect()
    api(libs.androidx.lifecycle.runtime.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)

    // 单元测试：直接在电脑上跑，不用模拟器
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
