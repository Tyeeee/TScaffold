package com.tscaffold.base.network.websocket.impl

import com.tscaffold.base.network.websocket.WebSocketState
import com.tscaffold.base.network.websocket.WebSocketService
import com.tscaffold.base.network.websocket.WebSocketOptions
import com.tscaffold.base.network.websocket.WebSocketMessage
import com.tscaffold.base.network.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * 建在 OkHttp 上的 WebSocket 实现。
 *
 * 三处刻意与母工程不同：
 * 1. **不建 OkHttpClient** —— 直接用 `HttpClient.instance`，和 HTTP 共用连接池/拦截器/日志；
 * 2. **不反射改 OkHttp 内部** —— 心跳用 client 级的 `pingInterval`（[HttpClient] 里配一处），
 *    要动态改就重连；母工程是反射改 `RealWebSocket.executor`，OkHttp 一升级就崩；
 * 3. **重连在协程里做**（指数退避 + 上限），不再用"事件队列 → 命令队列 → 延时投递"绕两个
 *    dispatcher，也不需要两个常年空转的生产者线程。
 *
 * @param scope 连接生命周期挂在哪个作用域上；不传就自己建一个（[release] 时释放）。
 *              测试里传 `TestScope` 就能用虚拟时间验证退避。
 * @param newSocket 建 socket 的方式。抽成函数只为测试能塞假实现 —— 生产代码走默认值。
 */
internal class OkHttpWebSocketConnection(
    private val options: WebSocketOptions,
    scope: CoroutineScope? = null,
    private val newSocket: (Request, WebSocketListener) -> WebSocket = { request, listener ->
        HttpClient.instance.newWebSocket(request, listener)
    },
) : WebSocketConnection {

    private val ownedScope: CoroutineScope? =
        if (scope == null) CoroutineScope(SupervisorJob() + Dispatchers.IO) else null
    private val scope: CoroutineScope = scope ?: requireNotNull(ownedScope)

    private val _state = MutableStateFlow<WebSocketState>(WebSocketState.Idle)
    override val state: StateFlow<WebSocketState> = _state.asStateFlow()

    // 用 Channel 而不是 SharedFlow：连上之后、界面还没开始收集的那几毫秒里，
    // 服务端先推过来的帧不会被丢掉（SharedFlow 无订阅者时是直接丢弃的）。
    private val incoming = Channel<WebSocketMessage>(Channel.UNLIMITED)
    override val messages: Flow<WebSocketMessage> = incoming.receiveAsFlow()

    private var connectJob: Job? = null

    @Volatile
    private var socket: WebSocket? = null

    /** 是不是我们自己主动关的（主动关就不再重连）。 */
    @Volatile
    private var closedByUs = false

    override fun connect() {
        if (connectJob?.isActive == true) return
        closedByUs = false
        connectJob = scope.launch { connectUntilClosed() }
    }

    /** 连上 → 等到结束 → 按策略退避 → 再连，直到主动关闭、正常关闭或次数用完。 */
    private suspend fun connectUntilClosed() {
        var retries = 0
        while (currentCoroutineContext().isActive && !closedByUs) {
            _state.value = WebSocketState.Connecting
            val ended = openOnce()
            socket = null

            if (closedByUs) {
                _state.value = WebSocketState.Closed(ended.code, ended.reason)
                return
            }
            // 对端正常说再见（1000）就别再敲人家门了
            if (ended.code == WebSocketService.CLOSE_NORMAL) {
                _state.value = WebSocketState.Closed(ended.code, ended.reason)
                return
            }

            // 注意这里分清"连接次数"和"重连次数"：策略里的 maxAttempts 是**重连**次数上限，
            // 首次连接不算，否则用户看到的重试次数会比自己配的多一次。
            val nextRetry = retries + 1
            val wait = options.retryPolicy.delayMillis(nextRetry)
            if (wait == null) {
                _state.value = WebSocketState.Failed("连接已断开，重连 $retries 次都没成功", ended.cause)
                return
            }
            retries = nextRetry
            _state.value = WebSocketState.Reconnecting(retries, wait)
            delay(wait)
        }
    }

    /** 开一条连接并挂起，直到它关闭或失败。 */
    private suspend fun openOnce(): End {
        val ended = CompletableDeferred<End>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                _state.value = WebSocketState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                incoming.trySend(WebSocketMessage.Text(text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                incoming.trySend(WebSocketMessage.Binary(bytes))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // 对端发起关闭：按协议回一个 close，并且**立刻**按"这次连接结束"处理。
                //
                // 为什么不能只等 onClosed：onClosed 要等双方把关闭握手走完才回调，
                // OkHttp 的 webSocketCloseTimeout 默认 60 秒 —— 期间我们的状态会一直停在
                // Connected，界面上显示"已连接"但早就收不到任何东西了（实测就是这样）。
                webSocket.close(WebSocketService.CLOSE_NORMAL, null)
                ended.complete(End(code, reason, null))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ended.complete(End(code, reason, null))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ended.complete(
                    End(
                        code = response?.code ?: WebSocketService.CLOSE_ABNORMAL,
                        reason = response?.message ?: t.message,
                        cause = t,
                    )
                )
            }
        }

        socket = try {
            newSocket(options.toRequest(), listener)
        } catch (t: Exception) {
            // 连"发起"都失败（例如 URL 写法不对）也算一次失败，交给上面的退避循环处理；
            // 不接住的话异常会掀掉整个协程，状态就永远停在 Connecting 了。
            return End(WebSocketService.CLOSE_ABNORMAL, t.message, t)
        }
        return ended.await()
    }

    override fun send(text: String): Boolean = socket?.send(text) == true

    override fun send(bytes: ByteString): Boolean = socket?.send(bytes) == true

    override fun close(code: Int, reason: String?) {
        closedByUs = true
        connectJob?.cancel()
        socket?.close(code, reason)
        socket = null
        _state.value = WebSocketState.Closed(code, reason)
    }

    override fun changePingInterval(intervalMillis: Long): Boolean =
        WebSocketPingIntervalChanger.apply(socket, intervalMillis)

    override fun release() {
        close(WebSocketService.CLOSE_NORMAL, "release")
        // 收尾：让还在 collect 的界面拿到流结束，而不是永远挂着
        incoming.close()
        ownedScope?.cancel()
    }

    private data class End(val code: Int, val reason: String?, val cause: Throwable?)
}
