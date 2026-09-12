// component_business_basic —— 目前是空模块（保留，未删除）
//
// 原来的四个示例页面（列表分页 / 输入表单 / Compose 搜索 / 带参数详情）连同它们的
// 假数据源，已经整体搬到 app 模块：
//     app/src/main/java/com/tscaffold/ui/      四个页面
//     app/src/main/java/com/tscaffold/data/    假数据源 + remote 数据层
//     app/src/main/res/layout/                 布局（已去掉 business_basic_ 前缀）
//     app/src/main/AndroidManifest.xml         四个 Activity 的声明
//
// 这个模块的定位是「业务相关的基础页面」：登录页、错误页 / 空态页、权限引导页这类
// **需要被多个业务复用**的公共页面放这里，各业务模块依赖它，不用各写一份。
//
// 现在业务还没那么多，这类页面只有一份、也没到被复用的程度，所以集中放在 app 里
// （见上面那几行）。等出现第二个业务、登录/错误页开始被重复实现时，再把它们搬进来。
//
// 注意：这里放的是**横向复用**的公共页面，不是"按业务纵向拆模块"。
//
// 下面的 namespace / resourcePrefix / 依赖都还在，而且已经配齐了这类页面需要的东西
// （viewBinding、compose、appcompat、recyclerview、swiperefreshlayout），搬进来即可用。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tscaffold.business.basic"
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
    // 站在 basic 之上；basic 又 api 了 common，所以这一条就把三层都串起来了
    api(project(":component_basic"))

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
