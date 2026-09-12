package com.tscaffold.base.network.websocket

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（WebSocket 的各种**失败与异常状况**）：真服务器、真握手、真断线。
 *
 * 覆盖：握手被拒、服务器根本不可达、服务端粗暴掐断（不发关闭帧）、服务端重启（同端口）、
 * 1001 going away、重试耗尽后彻底放弃、连接中 release、断开期间发送、消息突发、二进制双向、
 * 关掉之后还能再连。
 */
class WebSocketFailureScenarioTest {

    private lateinit var server: MockWebServer
    private val serverSides = mutableListOf<ServerSide>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        WebSocketService.releaseAll()
        serverSides.forEach { side ->
            if (side.socket.isCompleted) side.socket.getCompleted().close(WebSocketService.CLOSE_NORMAL, null)
        }
        Thread.sleep(200)
        runCatching { server.close() }
    }

    // ==================== 连不上 ====================

    @Test
    fun `握手被服务端拒掉（401）：会重试，重试次数用完后落到 Failed`() = runBlocking {
        repeat(6) { server.enqueue(MockResponse.Builder().code(401).body("unauthorized").build()) }

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 3))

        val failed = awaitStateIs(socket, WebSocketState.Failed::class.java) as WebSocketState.Failed
        assertEquals("连接已断开，重连 3 次都没成功", failed.userMessage)
        assertNotNull("要能看出是握手被拒，而不是干巴巴一句失败", failed.cause)
        assertEquals("首次 + 3 次重试 = 4 次握手", 4, server.requestCount)
    }

    @Test
    fun `服务器根本没人监听：重试到上限后放弃，不再偷偷重试`() = runBlocking {
        val deadPort = server.port
        server.close() // 端口上没人了

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 2, port = deadPort))

        val failed = awaitStateIs(socket, WebSocketState.Failed::class.java) as WebSocketState.Failed
        assertEquals("连接已断开，重连 2 次都没成功", failed.userMessage)

        // 状态就停在 Failed，不会再自己偷偷试
        delay(600)
        assertTrue(socket.state.value is WebSocketState.Failed)
    }

    // ==================== 连上了又断 ====================

    @Test
    fun `服务端粗暴掐断（不发关闭帧）：客户端能察觉并进入重连`() = runBlocking {
        val first = ServerSide()
        accept(first)

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 40))
        awaitState(socket, WebSocketState.Connected)
        withTimeout(10_000) { first.socket.await() }

        // 粗暴：整个服务器直接下线，TCP 被切断，一个关闭帧都不发
        runCatching { server.close() }

        // 客户端要能察觉（而不是一直以为"还连着"）。
        // 「断了之后又能连回来」由下面的"服务端重启"用例覆盖。
        val state = awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        assertTrue("应该进入重连，实际是 $state", state is WebSocketState.Reconnecting)
    }

    @Test
    fun `服务端说 1001 going away：也算异常关闭，会重连`() = runBlocking {
        val first = ServerSide()
        val second = ServerSide()
        accept(first)
        accept(second)

        val socket = WebSocketService.connect(KEY, options())
        awaitState(socket, WebSocketState.Connected)

        withTimeout(10_000) { first.socket.await() }.close(1001, "going away")

        awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        withTimeout(10_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)
    }

    @Test
    fun `服务端重启（换到同端口的新服务器）：客户端会重新连上`() = runBlocking {
        val port = server.port
        val first = ServerSide()
        accept(first)

        // 重试预算给足，好跨越服务器下线的那段空窗
        val socket = WebSocketService.connect(KEY, options(maxAttempts = 40))

        awaitState(socket, WebSocketState.Connected)
        withTimeout(10_000) { first.socket.await() }

        // 老服务器整个下线（TCP 直接断，没有关闭帧）
        runCatching { server.close() }
        awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        Thread.sleep(200)

        val reborn = MockWebServer()
        val second = ServerSide()
        reborn.enqueue(MockResponse.Builder().webSocketUpgrade(second).build())
        reborn.start(port) // 同一个端口，起一台新的
        server = reborn    // 交给 tearDown 收尾

        withTimeout(15_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)
    }

    // ==================== 断开期间的边界行为 ====================

    @Test
    fun `断开重连期间发送：返回 false，不假装成功也不缓存`() = runBlocking {
        val first = ServerSide()
        val second = ServerSide()
        accept(first)
        accept(second)

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 40))
        awaitState(socket, WebSocketState.Connected)
        withTimeout(10_000) { first.socket.await() }
        runCatching { server.close() }
        awaitStateIs(socket, WebSocketState.Reconnecting::class.java)

        assertFalse("断线期间发不出去", socket.send("这条发不出去"))
    }

    @Test
    fun `还在连接中就 release：干净收尾`() = runBlocking {
        accept(ServerSide())

        val socket = WebSocketService.connect(KEY, options())
        socket.release()

        assertTrue(socket.state.value is WebSocketState.Closed)
        assertNull("release 之后服务里不该还留着它", WebSocketService.existing(KEY))
    }

    @Test
    fun `主动关闭之后还能再连上（同一个句柄）`() = runBlocking {
        accept(ServerSide())
        val socket = WebSocketService.connect(KEY, options())
        awaitState(socket, WebSocketState.Connected)

        socket.close()
        awaitStateIs(socket, WebSocketState.Closed::class.java)

        val second = ServerSide()
        accept(second)
        WebSocketService.connect(KEY, options()) // 同一个 key → 还是这个句柄

        withTimeout(10_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)
        assertEquals(2, server.requestCount)
    }

    // ==================== 大流量与二进制 ====================

    @Test
    fun `服务端连推 100 条：一条不丢、顺序不乱`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect(KEY, options())
        awaitState(socket, WebSocketState.Connected)

        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }

        val serverSocket = withTimeout(10_000) { side.socket.await() }
        repeat(100) { serverSocket.send("第 $it 条") }

        awaitItems(got, 100)
        assertEquals((0 until 100).map { WebSocketMessage.Text("第 $it 条") }, got)

        collector.cancel()
        socket.close()
    }

    @Test
    fun `二进制帧双向都走得通`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect(KEY, options())
        awaitState(socket, WebSocketState.Connected)

        val serverSocket = withTimeout(10_000) { side.socket.await() }

        // 客户端 → 服务端
        assertTrue(socket.send("客户端发的二进制".encodeUtf8()))
        awaitItems(side.receivedBytes, 1)
        assertEquals("客户端发的二进制".encodeUtf8(), side.receivedBytes.first())

        // 服务端 → 客户端
        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }
        serverSocket.send("服务端发的二进制".encodeUtf8())
        awaitItems(got, 1)
        assertEquals(WebSocketMessage.Binary("服务端发的二进制".encodeUtf8()), got.first())

        collector.cancel()
        socket.close()
    }

    // ==================== 辅助 ====================

    private companion object {
        const val KEY = "scenario"
    }

    /** 服务端那一侧：文本和二进制都记下来，并暴露"服务端那条 socket"。 */
    private class ServerSide : WebSocketListener() {
        val socket = kotlinx.coroutines.CompletableDeferred<WebSocket>()
        val received = mutableListOf<String>()
        val receivedBytes = mutableListOf<ByteString>()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket.complete(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            receivedBytes += bytes
        }
    }

    private fun accept(side: ServerSide) {
        serverSides += side
        server.enqueue(MockResponse.Builder().webSocketUpgrade(side).build())
    }

    private fun options(maxAttempts: Int = 5, port: Int = server.port) = WebSocketOptions(
        url = "ws://127.0.0.1:$port/ws",
        retryPolicy = ExponentialBackoffRetryPolicy(
            maxAttempts = maxAttempts,
            baseDelayMillis = 100,
            maxDelayMillis = 100,
            jitterRatio = 0.0,
        ),
    )

    private suspend fun awaitState(socket: WebSocketHandle, expected: WebSocketState) {
        withTimeout(15_000) { socket.state.first { it == expected } }
    }

    private suspend fun awaitStateIs(socket: WebSocketHandle, expected: Class<out WebSocketState>): WebSocketState =
        withTimeout(15_000) { socket.state.first { expected.isInstance(it) } }

    private suspend fun awaitItems(list: List<*>, size: Int) {
        withTimeout(15_000) { while (list.size < size) delay(20) }
    }
}
