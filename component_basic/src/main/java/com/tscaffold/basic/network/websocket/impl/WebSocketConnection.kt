package com.tscaffold.basic.network.websocket.impl

import com.tscaffold.basic.network.websocket.WebSocketMessage
import com.tscaffold.basic.network.websocket.WebSocketService
import com.tscaffold.basic.network.websocket.WebSocketState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString

/**
 * 一条 WebSocket 连接的内部实现契约 —— **业务不用管，入口是 [WebSocketService]**。
 *
 * 放在 `impl/` 里并标成 `internal`，所以别的模块的补全列表里看不到它。做成接口是为了
 * 测试能塞一个假的连接（不用真连服务器）。
 */
internal interface WebSocketConnection {

    val state: StateFlow<WebSocketState>

    /** 收到的帧。**单消费者**：一条连接给一个收集者用（通常是页面的 ViewModel）。 */
    val messages: Flow<WebSocketMessage>

    /** 开始连接；已经连上或正在连的时候调用是空操作。 */
    fun connect()

    /** 发一条文本帧；还没连上时返回 false（不要替业务缓存，让业务自己看 [state]）。 */
    fun send(text: String): Boolean

    /** 发一条二进制帧；还没连上时返回 false。 */
    fun send(bytes: ByteString): Boolean

    /** 主动关闭：**不再重连**，状态落到 [WebSocketState.Closed]。 */
    fun close(code: Int = WebSocketService.CLOSE_NORMAL, reason: String? = null)

    /**
     * 运行时改心跳间隔 —— **尽力而为**（内部是隔离好的反射，见 `WebSocketPingIntervalChanger`）。
     *
     * @return `true` = 已经改掉；`false` = 这个 OkHttp 版本上反射改不了（**不是出错**）。
     *
     * 两个前提要知道：
     * - OkHttp 的心跳是**客户端级**设置，OkHttp 5 又在建连时把它按值冻进了调度任务，
     *   所以 5.x 上这里会返回 `false`（4.x 上能真改）。启动时的心跳仍然在
     *   `Network.init(webSocketPingIntervalMillis = ...)` 一处配置。
     * - 协议级 ping 只负责"保活 / 探死"。**真正需要动态调整的通常是业务级心跳**
     *   （你自己发 `{"action":"heartCheck"}` 那种），那属于协议配套文件的事，
     *   不该也不需要碰 OkHttp 内部。
     */
    fun changePingInterval(intervalMillis: Long): Boolean

    /** 关闭并释放这条连接占用的协程作用域。 */
    fun release()
}
