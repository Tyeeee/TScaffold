package com.tscaffold.basic.network

import com.tscaffold.basic.network.http.RetrofitService

/**
 * 网络底座的**唯一初始化入口** —— 在 Startup 的 Initializer 里调一次，别的地方都不用管。
 *
 * ```
 * class NetworkInitializer : Initializer<Unit> {
 *     override fun create(context: Context) {
 *         Network.init(
 *             baseUrl = "https://your.host/api/",   // HTTP 接口地址（Retrofit）
 *             webSocketPingIntervalMillis = 20_000, // WebSocket 心跳
 *             logEnabled = true,                    // 想看请求日志就打开
 *         )
 *     }
 *     override fun dependencies() = emptyList<Class<out Initializer<*>>>()
 * }
 * ```
 * 然后把它的全类名加进清单里 `androidx.startup.InitializationProvider` 的 meta-data
 * （app 模块的 `com.demo.tscaffold.provider.AppInitializer` 就是这么写的，照着抄一份即可）。
 *
 * 里面的三样东西必须在第一次发请求 / 第一次握手**之前**设好：
 * - OkHttpClient 的地址与心跳都是构建时读的（[HttpClient.instance] 是懒加载，只构建一次）；
 * - Retrofit 的 baseUrl 同理。
 *
 * 业务层不需要（也不应该）碰 OkHttpClient 或 Retrofit —— 它只写协议配套文件、调底座。
 */
object Network {

    fun init(
        /** HTTP 接口地址，必须以 `/` 结尾；传 null 表示这次不设置（比如还没接后端）。 */
        baseUrl: String? = null,
        /** WebSocket 心跳间隔，0 表示不自动 ping。 */
        webSocketPingIntervalMillis: Long = 20_000,
        /** 建连超时（毫秒）。 */
        connectTimeoutMillis: Long = 15_000,
        /** 读超时（毫秒）：接上了但服务器迟迟不回。 */
        readTimeoutMillis: Long = 30_000,
        /** 写超时（毫秒）。 */
        writeTimeoutMillis: Long = 30_000,
        /** 是否打印 HTTP / WebSocket 握手的日志。 */
        logEnabled: Boolean = false,
        /** 日志往哪打；默认什么都不打。 */
        logger: ((String) -> Unit)? = null,
    ) {
        baseUrl?.let { RetrofitService.baseUrl = it }
        HttpClient.pingIntervalMillis = webSocketPingIntervalMillis
        HttpClient.connectTimeoutMillis = connectTimeoutMillis
        HttpClient.readTimeoutMillis = readTimeoutMillis
        HttpClient.writeTimeoutMillis = writeTimeoutMillis
        HttpClient.logEnabled = logEnabled
        logger?.let { HttpClient.logger = it }
    }
}
