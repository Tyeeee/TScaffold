// component_business_basic —— 你的页面写在这里
// 现在是四个形态各异的示例页面（列表分页 / 输入表单 / Compose 搜索 / 带参数详情），
// 共用同一套写法（Contract + ViewModel + 界面）和同一份假数据源；写自己的页面时挑最像的照抄。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tscaffold.feature"
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
        // XML 页面要用 ViewBinding 直接取控件：viewBinding.tvListTitle
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
    implementation(libs.androidx.recyclerview)       // 列表页用的列表控件
    implementation(libs.androidx.swiperefreshlayout) // 列表页用的下拉刷新

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // 单元测试：直接在电脑上跑，不用模拟器
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 真起一个本地服务器跑"真实场景"测试（真 socket、真握手）
    testImplementation(libs.okhttp.mockwebserver)
}
