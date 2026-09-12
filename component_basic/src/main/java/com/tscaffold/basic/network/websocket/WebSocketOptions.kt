package com.tscaffold.basic.network.websocket

import okhttp3.Request

/**
 * 一条 WebSocket 连接的参数。业务侧按自己的协议把这个建好交给底座就行。
 *
 * 心跳**不在这里**：OkHttp 的 ping 是客户端级设置，统一在 `Network.init(webSocketPingIntervalMillis = ...)`
 * 里配一处（这样全 App 也只有一个 OkHttpClient）。
 */
data class WebSocketOptions(
    /** ws:// 或 wss:// 地址。token 之类建议走 [headers]，别塞在 URL 里（URL 会被打进日志）。 */
    val url: String,
    /** 握手时带的请求头，例如 token、设备号。 */
    val headers: Map<String, String> = emptyMap(),
    /** 重连策略；不传就用指数退避 + 8 次上限。 */
    val retryPolicy: WebSocketRetryPolicy = ExponentialBackoffRetryPolicy(),
) {
    init {
        require(url.isNotBlank()) { "WebSocket 地址不能为空" }
    }

    internal fun toRequest(): Request = Request.Builder()
        .url(url)
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .build()
}
