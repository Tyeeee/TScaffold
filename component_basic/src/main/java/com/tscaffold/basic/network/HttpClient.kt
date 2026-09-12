package com.tscaffold.basic.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 全 App **唯一**创建 OkHttpClient 的地方。HTTP（Retrofit）和 WebSocket 共用它一个实例。
 *
 * 为什么必须共用：OkHttp 的 `newWebSocket()` 本来就是跑在同一个 client 上的，
 * 共用才能共享连接池、线程池、拦截器（token/日志）、超时和 DNS；
 * 各建各的等于两套配置各自漂移（母工程就是每次请求 / 每次连接都 `builder.build()` 一次）。
 *
 * 想加自己的拦截器（token、公共参数、加解密）在下面 [buildClient] 里排队加进去，别在业务里建 client。
 *
 * 上面的参数**改了就重建 client**（顺带换掉连接池，所以只在真需要时改：切环境、调超时）。
 * 重建之后要重新 `RetrofitService.create()` 拿接口代理，才会用上新配置 ——
 * 旧的代理绑定的是旧 Retrofit + 旧 client。
 */
object HttpClient {

    /** 打开后每个请求/每次握手都会交给 [logger]。 */
    var logEnabled: Boolean = false
        set(value) {
            field = value
            loggingInterceptor.level =
                if (value) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

    /** 换掉它就能接住日志，例如在 Application 里写 `Log.d("HTTP", it)`。默认什么都不打。 */
    var logger: (String) -> Unit = {}

    /**
     * WebSocket 心跳间隔（毫秒）。
     *
     * OkHttp 的 ping 是**客户端级**设置（不是每条连接），所以只能统一在这里配一处。
     * 设为 0 表示不自动发 ping；改动只对**之后新建的连接**生效。
     */
    var pingIntervalMillis: Long = 20_000

    /** 建连超时（毫秒）。 */
    var connectTimeoutMillis: Long = 15_000

    /** 读超时（毫秒）：接上了但服务器迟迟不回，判定为超时的时间。 */
    var readTimeoutMillis: Long = 30_000

    /** 写超时（毫秒）。 */
    var writeTimeoutMillis: Long = 30_000

    private val loggingInterceptor = HttpLoggingInterceptor { logger(it) }
        .apply { level = HttpLoggingInterceptor.Level.NONE }

    private var cachedKey: String? = null
    private var cachedClient: OkHttpClient? = null

    /** 全 App 唯一的那个 client；上面几个参数一变就重建。 */
    @get:Synchronized
    val instance: OkHttpClient
        get() {
            val key = "$pingIntervalMillis/$connectTimeoutMillis/$readTimeoutMillis/$writeTimeoutMillis"
            cachedClient?.let { if (cachedKey == key) return it }
            return buildClient().also {
                cachedClient = it
                cachedKey = key
            }
        }

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
        // 连接失败时换条线路重试，注意这是 OkHttp 层的重试，不是业务重试
        .retryOnConnectionFailure(true)
        // WebSocket 心跳：OkHttp 会自己发 ping、自己回 pong
        .pingInterval(pingIntervalMillis, TimeUnit.MILLISECONDS)
        // .addInterceptor(AuthInterceptor())  // 需要 token 时在这里加
        .addInterceptor(loggingInterceptor)
        .build()
}
