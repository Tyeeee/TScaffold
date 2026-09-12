package com.tscaffold.base.network.websocket

import kotlinx.coroutines.CompletableDeferred
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（**网络抖动 / 弱网**）：连接反复断、又反复自己连回来。
 *
 * 弱网下的真实样子是"抖动到让人怀疑人生"：刚连上就断、消息发一半断、断断续续好几轮。
 * 这里逐条真跑，要求是——**每一次抖动之后都必须能自己恢复，并且恢复后收发照常**；
 * 重试预算真的用光时才落到 [WebSocketState.Failed]，而且用户重新 connect 必须能救回来。
 */
class WebSocketFlappingScenarioTest {

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

    @Test
    fun `网络抖动：反复掉线重连 5 轮，每一轮之后都能继续收发`() = runBlocking {
        val rounds = 5
        val sides = (0..rounds).map { ServerSide().also { accept(it) } }

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 60))
        awaitState(socket, WebSocketState.Connected)
        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }

        repeat(rounds) { round ->
            val current = withTimeout(15_000) { sides[round].socket.await() }

            // 抖动：服务端以异常码断开（弱网里"掉线"最常见的样子）
            current.close(1011, "第 $round 轮：网络抖了一下")

            awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
            awaitState(socket, WebSocketState.Connected)

            // 新一轮的握手真的发生了，而且恢复之后收发正常
            val next = withTimeout(15_000) { sides[round + 1].socket.await() }
            next.send("第 $round 轮恢复后的消息")
        }

        awaitItems(got, rounds)
        assertEquals(
            "每一轮恢复后发的消息都要收到，且顺序不乱",
            (0 until rounds).map { WebSocketMessage.Text("第 $it 轮恢复后的消息") },
            got,
        )
        assertEquals("抖了 5 轮之后仍然是连着的", WebSocketState.Connected, socket.state.value)
        assertEquals("每次都重新握手：首次 + 5 轮", rounds + 1, server.requestCount)

        collector.cancel()
        socket.close()
    }

    @Test
    fun `抖动期间发出的帧丢掉：恢复后照常收新消息，也不会因为丢帧卡住`() = runBlocking {
        val first = ServerSide()
        val second = ServerSide()
        accept(first)
        accept(second)

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 60))
        awaitState(socket, WebSocketState.Connected)
        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }

        val current = withTimeout(15_000) { first.socket.await() }
        // 抖动前一刻还在推消息：这些可能真的收不到（现实如此），不该因此出问题
        repeat(3) { current.send("抖动前的第 $it 条") }
        current.close(1011, "断了")

        awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        val next = withTimeout(15_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)

        repeat(5) { next.send("恢复后的第 $it 条") }
        awaitItems(got, 5)

        // 恢复后的 5 条都在、且没有重复
        val afterRecovery = got.filter { it is WebSocketMessage.Text && it.text.startsWith("恢复后的") }
        assertEquals(5, afterRecovery.size)
        assertEquals(afterRecovery.distinct().size, afterRecovery.size)

        collector.cancel()
        socket.close()
    }

    @Test
    fun `刚连上就被掐断（抖动很凶）：不会放弃，重连之后照常收发`() = runBlocking {
        val sides = (0..3).map { ServerSide().also { accept(it) } }

        val socket = WebSocketService.connect(KEY, options(maxAttempts = 60))

        // 前两次都是"刚握手成功就断"，第三次才稳下来
        repeat(2) { round ->
            awaitState(socket, WebSocketState.Connected)
            val current = withTimeout(15_000) { sides[round].socket.await() }
            current.close(1011, "刚连上就断")
            awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        }

        awaitState(socket, WebSocketState.Connected)
        val stable = withTimeout(15_000) { sides[2].socket.await() }

        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }
        stable.send("稳定之后的消息")
        awaitItems(got, 1)
        assertEquals(WebSocketMessage.Text("稳定之后的消息"), got.first())

        collector.cancel()
        socket.close()
    }

    @Test
    fun `抖了几轮之后来一波突发：50 条一条不丢、顺序不乱`() = runBlocking {
        val sides = (0..2).map { ServerSide().also { accept(it) } }
        val socket = WebSocketService.connect(KEY, options(maxAttempts = 60))
        awaitState(socket, WebSocketState.Connected)

        repeat(2) { round ->
            withTimeout(15_000) { sides[round].socket.await() }.close(1011, "抖")
            awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
            awaitState(socket, WebSocketState.Connected)
        }

        val stable = withTimeout(15_000) { sides[2].socket.await() }
        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }
        repeat(50) { stable.send("突发第 $it 条") }

        awaitItems(got, 50)
        assertEquals((0 until 50).map { WebSocketMessage.Text("突发第 $it 条") }, got)

        collector.cancel()
        socket.close()
    }

    @Test
    fun `弱网太久把重试预算用光：落 Failed；之后用户重新 connect 能救回来`() = runBlocking {
        val port = server.port
        val first = ServerSide()
        accept(first)

        // 只允许重连 1 次，模拟"短暂下线但重试预算很小"
        val socket = WebSocketService.connect(KEY, options(maxAttempts = 1, port = port))
        awaitState(socket, WebSocketState.Connected)
        withTimeout(15_000) { first.socket.await() }

        runCatching { server.close() } // 服务器下线，且这次只重试 1 次

        val failed = awaitStateIs(socket, WebSocketState.Failed::class.java) as WebSocketState.Failed
        assertEquals("连接已断开，重连 1 次都没成功", failed.userMessage)

        // 网络恢复：服务器在同端口回来，用户（或页面重新进入）再 connect 一次
        val reborn = MockWebServer()
        val second = ServerSide()
        reborn.enqueue(MockResponse.Builder().webSocketUpgrade(second).build())
        reborn.start(port)
        server = reborn
        serverSides += second

        WebSocketService.connect(KEY, options(maxAttempts = 5, port = port))

        withTimeout(15_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)

        val got = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { got += it } }
        second.socket.getCompleted().send("救回来了")
        awaitItems(got, 1)
        assertEquals(WebSocketMessage.Text("救回来了"), got.first())

        collector.cancel()
        socket.close()
    }

    // ==================== 辅助 ====================

    private companion object {
        const val KEY = "flapping"
    }

    private class ServerSide : WebSocketListener() {
        val socket = CompletableDeferred<WebSocket>()
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

    private fun options(maxAttempts: Int = 20, port: Int = server.port) = WebSocketOptions(
        url = "ws://127.0.0.1:$port/ws",
        retryPolicy = ExponentialBackoffRetryPolicy(
            maxAttempts = maxAttempts,
            baseDelayMillis = 100,
            maxDelayMillis = 100,
            jitterRatio = 0.0,
        ),
    )

    private suspend fun awaitState(socket: WebSocketHandle, expected: WebSocketState) {
        withTimeout(20_000) { socket.state.first { it == expected } }
    }

    private suspend fun awaitStateIs(socket: WebSocketHandle, expected: Class<out WebSocketState>): WebSocketState =
        withTimeout(20_000) { socket.state.first { expected.isInstance(it) } }

    private suspend fun awaitItems(list: List<*>, size: Int) {
        withTimeout(20_000) { while (list.size < size) delay(10) }
    }
}
