package com.tscaffold

import android.util.Log
import com.tscaffold.base.BasicApplication
import com.tscaffold.base.network.Network
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

        // 网络底座**唯一**的初始化入口：HTTP 接口地址、WebSocket 心跳、日志开关都在这一处。
        // 业务层不碰 OkHttpClient / Retrofit，只写协议配套文件、调底座的 API。
        Network.init(
            // baseUrl = "https://your.host/api/",   // 接真后端时填这里（必须以 / 结尾）
            webSocketPingIntervalMillis = 20_000,
            logEnabled = true,
            logger = { message -> Log.d("HTTP", message) },
        )
    }
}
