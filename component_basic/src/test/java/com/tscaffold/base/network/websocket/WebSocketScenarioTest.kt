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
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（WebSocket）：MockWebServer 起一个**真的本地服务器并真的升级成 WebSocket**，
 * 走真 socket、真握手、真帧、真断开重连。**没有假替身**。
 */
class WebSocketScenarioTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    /** 每条测试建出来的"服务端一侧"，tearDown 时要显式关掉。 */
    private val serverSides = mutableListOf<ServerSide>()

    @After
    fun tearDown() {
        // 顺序很重要：MockWebServer 还有连接开着的时候 close() 会直接抛
        // "Gave up waiting for queue to shut down"，所以先断开客户端、再关服务端 socket。
        WebSocketService.releaseAll()
        serverSides.forEach { side ->
            if (side.socket.isCompleted) side.socket.getCompleted().close(WebSocketService.CLOSE_NORMAL, null)
        }
        Thread.sleep(300)
        server.close()
    }

    @Test
    fun `连上真服务器，收到服务端推来的文本帧和二进制帧`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect("live", options())
        val received = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { received += it } }

        awaitState(socket, WebSocketState.Connected)

        val serverSocket = withTimeout(10_000) { side.socket.await() }
        serverSocket.send("服务端说：你好")
        serverSocket.send("二进制帧".encodeUtf8())

        awaitItems(received, 2)
        assertEquals(
            listOf(WebSocketMessage.Text("服务端说：你好"), WebSocketMessage.Binary("二进制帧".encodeUtf8())),
            received,
        )

        collector.cancel()
        socket.close()
    }

    @Test
    fun `客户端发出去的帧真的到了服务端`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect("live", options())

        awaitState(socket, WebSocketState.Connected)
        assertTrue(socket.send("""{"cmd":1,"data":{"action":"heartCheck"}}"""))

        awaitItems(side.received, 1)
        assertEquals(listOf("""{"cmd":1,"data":{"action":"heartCheck"}}"""), side.received)

        socket.close()
    }

    @Test
    fun `服务端异常断开（1011）后客户端自动重连，第二次握手真的连上了`() = runBlocking {
        val first = ServerSide()
        val second = ServerSide()
        accept(first)
        accept(second)

        val socket = WebSocketService.connect("live", options())
        awaitState(socket, WebSocketState.Connected)
        val firstSocket = withTimeout(10_000) { first.socket.await() }

        // 服务端"重启"：1011 不是正常关闭码
        firstSocket.close(1011, "服务端要重启")

        // 客户端应该走 Reconnecting → 再握手一次
        awaitStateIs(socket, WebSocketState.Reconnecting::class.java)
        withTimeout(10_000) { second.socket.await() }
        awaitState(socket, WebSocketState.Connected)

        // 重连后的新连接要真的能用
        second.socket.getCompleted().send("重连之后的消息")
        val received = mutableListOf<WebSocketMessage>()
        val collector = launch { socket.messages.collect { received += it } }
        awaitItems(received, 1)
        assertEquals(WebSocketMessage.Text("重连之后的消息"), received.first())

        collector.cancel()
        socket.close()
    }

    @Test
    fun `服务端正常说再见（1000）后不再重连`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect("live", options())
        awaitState(socket, WebSocketState.Connected)

        withTimeout(10_000) { side.socket.await() }.close(WebSocketService.CLOSE_NORMAL, "下班了")

        awaitStateIs(socket, WebSocketState.Closed::class.java)
        // 留足重连的时间：正常关闭就不该再握手
        delay(500)
        assertEquals("正常关闭后不该再握手", 1, server.requestCount)
    }

    @Test
    fun `两个 key 是两条真连接，消息不会串`() = runBlocking {
        val sideA = ServerSide()
        val sideB = ServerSide()
        accept(sideA)
        accept(sideB)

        val a = WebSocketService.connect("room-a", options())
        awaitState(a, WebSocketState.Connected)
        val b = WebSocketService.connect("room-b", options())
        awaitState(b, WebSocketState.Connected)
        assertEquals("两条连接就该有两次握手", 2, server.requestCount)

        val gotA = mutableListOf<WebSocketMessage>()
        val gotB = mutableListOf<WebSocketMessage>()
        val jobA = launch { a.messages.collect { gotA += it } }
        val jobB = launch { b.messages.collect { gotB += it } }

        withTimeout(10_000) { sideA.socket.await() }.send("给 A 的")
        withTimeout(10_000) { sideB.socket.await() }.send("给 B 的")

        awaitItems(gotA, 1)
        awaitItems(gotB, 1)
        assertEquals(listOf<WebSocketMessage>(WebSocketMessage.Text("给 A 的")), gotA)
        assertEquals(listOf<WebSocketMessage>(WebSocketMessage.Text("给 B 的")), gotB)

        jobA.cancel()
        jobB.cancel()
        a.close()
        b.close()
    }

    @Test
    fun `release 之后收集者能正常收尾，不会永远挂在那`() = runBlocking {
        val side = ServerSide()
        accept(side)
        val socket = WebSocketService.connect("live", options())
        awaitState(socket, WebSocketState.Connected)

        val finished = CompletableDeferred<Unit>()
        val collector = launch {
            try {
                socket.messages.collect { }
            } finally {
                finished.complete(Unit)
            }
        }
        delay(100) // 让收集者真的挂上
        socket.release()

        withTimeout(10_000) { finished.await() }
        collector.cancel()
    }

    // ==================== 辅助 ====================

    /** 服务端那一侧：暴露"服务端那条 socket"，并记下收到的帧。 */
    private class ServerSide : WebSocketListener() {
        val socket = CompletableDeferred<WebSocket>()
        val received = mutableListOf<String>()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            socket.complete(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
        }
    }

    /** 让服务器的下一次握手升级成 WebSocket。 */
    private fun accept(side: ServerSide) {
        serverSides += side
        server.enqueue(MockResponse.Builder().webSocketUpgrade(side).build())
    }

    private fun options() = WebSocketOptions(
        url = server.url("/ws").toString().replaceFirst("http://", "ws://"),
        retryPolicy = ExponentialBackoffRetryPolicy(
            maxAttempts = 5,
            baseDelayMillis = 100,
            maxDelayMillis = 100,
            jitterRatio = 0.0,
        ),
    )

    private suspend fun awaitState(socket: WebSocketHandle, expected: WebSocketState) {
        withTimeout(10_000) { socket.state.first { it == expected } }
    }

    private suspend fun awaitStateIs(socket: WebSocketHandle, expected: Class<out WebSocketState>) {
        withTimeout(10_000) { socket.state.first { expected.isInstance(it) } }
    }

    private suspend fun awaitItems(list: List<*>, size: Int) {
        withTimeout(10_000) {
            while (list.size < size) delay(20)
        }
    }
}
