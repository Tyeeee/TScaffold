package com.tscaffold.basic.network.websocket

import com.tscaffold.basic.network.HttpClient
import com.tscaffold.basic.network.websocket.impl.OkHttpWebSocketConnection
import com.tscaffold.basic.network.websocket.impl.WebSocketConnection

/**
 * ============================ WebSocket 这一类，业务只用这一个 ============================
 *
 * 按 key 管理长连接（每个业务一个 key，同一个 key 永远是同一条连接）。
 * 一次 [connect] 拿到句柄，之后收发都用句柄 —— 这样就不会出现"key 写错 / 先收后连"导致静默收不到消息。
 *
 * ```
 * // 1) 进页面/进直播间：拿到句柄（同时已经开始连接了）
 * val socket = WebSocketService.connect("live", WebSocketOptions(url = ..., headers = mapOf("token" to token)))
 *
 * // 2) 在 ViewModel 里收：状态是状态、帧是流，都两行接完
 * viewModelScope.launch { socket.state.collect { setIntent(Intent.ConnectionChanged(it)) } }
 * viewModelScope.launch { socket.messages.collect { setIntent(Intent.Received(it)) } }
 *
 * // 3) 发
 * socket.send(LiveSocket.encode(cmd, data))
 *
 * // 4) 走的时候
 * socket.close()      // 主动关，不再重连
 * socket.release()    // 连句柄一起放掉（或 App 退出时 WebSocketService.releaseAll()）
 * ```
 *
 * 协议相关的东西（命令码、`{cmd,data,id}` 信封的编解码）写在业务自己的配套文件里，
 * 通过 [WebSocketOptions] 传进来 —— 底座只给原始帧，不猜你的信封。
 *
 * 这个包里 `impl/` 下的东西都是内部机制（OkHttpWebSocketConnection、反射改心跳等），
 * 它们被标成了 `internal`，别的模块的补全列表里根本不会出现。
 * ============================================================================
 */
object WebSocketService {

    /** 正常关闭码（RFC 6455）。 */
    const val CLOSE_NORMAL = 1000

    /** 异常断开（没有握手 / 关闭码可用）。 */
    const val CLOSE_ABNORMAL = 1006

    private class Entry(val url: String, val handle: WebSocketHandle)

    private val connections = mutableMapOf<String, Entry>()

    /** 只给测试用：换掉"怎么建连接"，测试里塞假的，不去真连服务器。 */
    internal var connectionFactory: (WebSocketOptions) -> WebSocketConnection =
        { options -> OkHttpWebSocketConnection(options) }

    /**
     * 建（或复用）一条命名连接并开始连接，返回它的 [WebSocketHandle]。
     *
     * **同一个 key 复用同一条连接**：第二次传的 options 会被忽略；如果连的是不同 url，
     * 会记一条日志（想换地址请先 `handle.release()`）。这样避免"页面换了 token 又默默连出第二条长连接"。
     */
    fun connect(key: String, options: WebSocketOptions): WebSocketHandle =
        connection(key, options).also { it.connection.connect() }

    /** 已经在用的那条连接的句柄；没有就返回 null。 */
    @Synchronized
    fun existing(key: String): WebSocketHandle? = connections[key]?.handle

    /** 放掉这条连接（[WebSocketHandle.release] 也是走这里）。 */
    @Synchronized
    fun release(key: String) {
        connections.remove(key)?.handle?.connection?.release()
    }

    /** 全部放掉（App 退出时调）。 */
    @Synchronized
    fun releaseAll() {
        connections.values.forEach { it.handle.connection.release() }
        connections.clear()
    }

    @Synchronized
    private fun connection(key: String, options: WebSocketOptions): WebSocketHandle {
        val running = connections[key]
        if (running != null) {
            if (running.url != options.url) {
                HttpClient.logger(
                    "[$key] 已经在连 ${running.url}，这次给的 ${options.url} 被忽略" +
                        "（同一个 key 复用同一条连接，要换地址请先 release）"
                )
            }
            return running.handle
        }
        val handle = WebSocketHandle(key = key, connection = connectionFactory(options))
        connections[key] = Entry(options.url, handle)
        return handle
    }
}
