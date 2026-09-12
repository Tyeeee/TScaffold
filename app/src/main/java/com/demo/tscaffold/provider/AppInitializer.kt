package com.demo.tscaffold.provider

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.tscaffold.basic.network.Network
import com.tscaffold.common.ui.viewmodel.MviLog

/**
 * 本 App 自己的启动初始化 —— 挂到 AndroidX Startup 上，**Application 里不写初始化代码**。
 *
 * 这个工程里所有初始化都是同一个套路：写一个 Initializer 放进 `provider` 包，
 * 再去清单里 `androidx.startup.InitializationProvider` 的 meta-data 加一条指向它。
 * component_basic 里那条挂的是 MMKV（`com.tscaffold.basic.provider.MMKVInitializer`），
 * 这里挂本 App 的配置。
 *
 * @see com.tscaffold.basic.provider.MMKVInitializer
 * @see com.tscaffold.basic.BasicApplication
 */
class AppInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // 打开这套写法的调试日志：每个页面上报的操作、每次状态前后变化都会打到 Logcat，
        // 过滤 `MVI` 就能看到整条链路。不想要就删掉这行（默认什么都不打印）。
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

    /** 没有前置依赖：本 App 的配置自己就是最先要做的。 */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
