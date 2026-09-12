// component_common —— **最底层**：MVI 核心（只有 MVI 相关的东西）
// 内容：BaseViewModel（State / Intent 两件套）、BaseContract（页面约定模板）、
//      BaseActivity / BaseFragment（XML 页面基类）、compose/MviCompose.kt（Compose 页面小工具）
//
// 依赖方向（2026-09-12 修正，之前是反的）：
//     common（最底） ← basic ← business ← app
//
// 这个模块**不依赖任何其它业务模块** —— MVI 核心不该知道 App 里有没有网络层、有没有登录页。
// 它需要的 AndroidX 库全部自己声明（见下面的 dependencies），不靠别人转发。
plugins {
    alias(libs.plugins.android.library)
    // 因为有一个 Compose 小工具文件，所以这个模块也要开 Compose 编译
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tscaffold.common"

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
    // 注意：这里**没有** api(project(":component_basic"))。
    // 以前有，方向是反的 —— 让 MVI 核心去依赖网络层，纯粹是为了"顺带转发依赖"，
    // 代码上一处都没用到（实测互相 0 处 import）。现在各层自己声明自己要的东西。

    // BaseActivity 继承 AppCompatActivity、BaseFragment 继承 Fragment，都用 api 暴露给上层
    api(libs.androidx.appcompat)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose 版小工具：observeState()
    api(libs.androidx.lifecycle.runtime.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)

    // 单元测试：直接在电脑上跑，不用模拟器
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
