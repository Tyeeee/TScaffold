package com.scaffold.component.basic

import android.app.Application

/**
 * 应用的 Application —— 整个 App 启动时最先跑的地方。
 *
 * 现在是空的，专门留给你放"App 一启动就要做的事"，例如：
 * 初始化日志、初始化本地存储、初始化网络库、注册崩溃收集等等。
 *
 * 它已经在 app 模块的 AndroidManifest.xml 里注册好了（android:name），
 * 你只要在这个 onCreate 里加代码就会生效，不用再改清单文件。
 */
open class BasicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO 在这里初始化你自己要用的东西
    }
}
