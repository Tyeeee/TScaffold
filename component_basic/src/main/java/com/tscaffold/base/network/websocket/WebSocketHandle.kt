package com.tscaffold.base.network.websocket

import com.tscaffold.base.network.websocket.impl.WebSocketConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString

/**
 * 一条 WebSocket 连接的**句柄** —— 从 [WebSocketService.connect] 拿到它，之后的收发都用它。
 *
 * ```
 * val socket = WebSocketService.connect("live", WebSocketOptions(url = ..., headers = ...))
 *
 * socket.state          // 连接状态（状态，不是事件）
 * socket.messages       // 收到的帧（Flow，页面销毁自动取消）
 * socket.send(json)     // 发送
 * socket.close()        // 主动关：不再重连
 * socket.release()      // 放掉：从 WebSocketService 里摘掉，之后可以再 connect 一条新的
 * ```
 *
 * 为什么不做成 `WebSocketService.messages("live")` 那种按 key 取的静态方法：那样在
 * "先收集、后连接"（或者 key 写错）的时候会返回一个永远不更新的空流，**静默收不到任何消息**，
 * 很难查。拿到句柄就意味着连接一定已经建立过了。
 */
class WebSocketHandle internal constructor(
    /** 这条连接在 [WebSocketService] 里的名字。 */
    val key: String,
    internal val connection: WebSocketConnection,
) {

    /** 连接状态：Idle / Connecting / Connected / Reconnecting / Closed / Failed。 */
    val state: StateFlow<WebSocketState> = connection.state

    /** 收到的帧。**单消费者**：一条连接给一个收集者用（通常是页面的 ViewModel）。 */
    val messages: Flow<WebSocketMessage> = connection.messages

    /** 发一条文本帧；没连上时返回 false（不替业务缓存，业务看 [state] 自己决定）。 */
    fun send(text: String): Boolean = connection.send(text)

    /** 发一条二进制帧；没连上时返回 false。 */
    fun send(bytes: ByteString): Boolean = connection.send(bytes)

    /**
     * 运行时改心跳间隔 —— **尽力而为**，返回 false 表示这个 OkHttp 版本上改不了。
     * 详见 [WebSocketConnection.changePingInterval]。
     */
    fun changePingInterval(intervalMillis: Long): Boolean =
        connection.changePingInterval(intervalMillis)

    /** 主动关闭：**不再重连**，状态落到 [WebSocketState.Closed]；句柄还能用，可以再 [WebSocketService.connect]。 */
    fun close(code: Int = WebSocketService.CLOSE_NORMAL, reason: String? = null) {
        connection.close(code, reason)
    }

    /** 关闭并放掉占用的资源（它也会把自己从 [WebSocketService] 里摘掉）。 */
    fun release() {
        WebSocketService.release(key)
    }
}
