package com.tscaffold

import android.util.Log
import com.tscaffold.base.BasicApplication
import com.tscaffold.core.ui.viewmodel.MviLog

/**
 * 应用真正的 Application。
 *
 * 它继承 base 里的 [BasicApplication]（那是留给"基础能力初始化"的位置），
 * 这里只做一件跟本 App 有关的事：**打开这套写法的调试日志**。
 *
 * 打开之后，每个页面上报的操作、每次状态的前后变化都会打到 Logcat：
 * 在 Android Studio 的 Logcat 里过滤 `MVI` 就能看到整条链路，排查问题很快。
 * 不想要就把这两行删掉（默认什么都不打印）。
 */
class App : BasicApplication() {

    override fun onCreate() {
        super.onCreate()

        MviLog.printer = { message -> Log.d("MVI", message) }
    }
}
