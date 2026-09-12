package com.tscaffold.base.network.websocket.impl

import com.tscaffold.base.network.websocket.WebSocketService
import com.tscaffold.base.network.websocket.WebSocketState
import com.tscaffold.base.network.websocket.WebSocketRetryPolicy
import com.tscaffold.base.network.websocket.WebSocketOptions
import com.tscaffold.base.network.websocket.WebSocketMessage
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全部用**假的 okhttp3.WebSocket** 扮演服务端，不联网、不启模拟器。
 *
 * `okhttp3.WebSocket` 是个接口，所以"服务端接受连接 / 推一条帧 / 异常断开 / 正常关闭"
 * 都能在测试里手工触发 —— 重连和退避这种平时最难验的东西，这里用虚拟时间一秒跑完。
 */
class OkHttpWebSocketConnectionTest {

    @Test
    fun `连上之后状态变 Connected，发消息走同一条 socket`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created)

        conn.connect()
        runCurrent()
        assertEquals(WebSocketState.Connecting, conn.state.value)

        created.single().serverAccepts()
        assertEquals(WebSocketState.Connected, conn.state.value)

        assertTrue(conn.send("hello"))
        assertEquals(listOf("hello"), created.single().sentText)

        conn.close()
    }

    @Test
    fun `文本帧和二进制帧都会交给收集者，一帧都不丢`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created)
        val received = mutableListOf<WebSocketMessage>()
        val collector = backgroundScope.launch { conn.messages.collect { received += it } }

        conn.connect()
        runCurrent()
        created.single().serverAccepts()
        created.single().serverSends("文本")
        created.single().serverSends("二进制".encodeUtf8())
        runCurrent()

        assertEquals(
            listOf(WebSocketMessage.Text("文本"), WebSocketMessage.Binary("二进制".encodeUtf8())),
            received,
        )

        collector.cancel()
        conn.close()
    }

    @Test
    fun `还没开始收集时收到的帧也不会丢`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created)

        conn.connect()
        runCurrent()
        created.single().serverAccepts()
        created.single().serverSends("先到的")

        // 现在才开始收 —— 上面那帧应该还在缓冲里
        val received = mutableListOf<WebSocketMessage>()
        val collector = backgroundScope.launch { conn.messages.collect { received += it } }
        runCurrent()

        assertEquals(listOf(WebSocketMessage.Text("先到的")), received)

        collector.cancel()
        conn.close()
    }

    @Test
    fun `异常断开会自动重连，等待时间按策略递增`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created, maxRetry = 3)

        conn.connect()
        runCurrent()
        created[0].serverAccepts()

        created[0].serverFails(IOException("boom"))
        runCurrent()
        assertEquals(WebSocketState.Reconnecting(1, 1_000), conn.state.value)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, created.size)
        assertEquals(WebSocketState.Connecting, conn.state.value)

        created[1].serverFails(IOException("boom again"))
        runCurrent()
        assertEquals(WebSocketState.Reconnecting(2, 2_000), conn.state.value) // 退避递增

        conn.close()
    }

    @Test
    fun `重连次数用完就放弃，不会一直在后台偷偷重试`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created, maxRetry = 2)

        conn.connect()
        runCurrent()
        created[0].serverFails(IOException("1"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        created[1].serverFails(IOException("2"))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        created[2].serverFails(IOException("3"))
        runCurrent()

        assertTrue(conn.state.value is WebSocketState.Failed)
        assertEquals("用完次数后不该再建连接", 3, created.size)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(3, created.size)
    }

    @Test
    fun `主动关闭之后不再重连`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created)

        conn.connect()
        runCurrent()
        created.single().serverAccepts()

        conn.close()
        runCurrent()
        assertEquals(WebSocketState.Closed(WebSocketService.CLOSE_NORMAL, null), conn.state.value)
        assertEquals("主动关闭要把 socket 关掉", WebSocketService.CLOSE_NORMAL, created.single().closedWith?.first)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, created.size)
    }

    @Test
    fun `对端正常说再见（1000）也不再重连`() = runTest {
        val created = mutableListOf<FakeWebSocket>()
        val conn = newConnection(created)

        conn.connect()
        runCurrent()
        created.single().serverAccepts()
        created.single().serverClosesNormally()
        runCurrent()

        assertEquals(WebSocketState.Closed(WebSocketService.CLOSE_NORMAL, "bye"), conn.state.value)

        advanceTimeBy(600_000)
        runCurrent()
        assertEquals(1, created.size)
    }

    @Test
    fun `还没连上时发消息返回 false，不替业务缓存`() = runTest {
        val conn = newConnection(mutableListOf())

        assertFalse(conn.send("没人接"))
    }

    // ==================== 测试替身 ====================

    private fun TestScope.newConnection(
        created: MutableList<FakeWebSocket>,
        maxRetry: Int = 3,
    ) = OkHttpWebSocketConnection(
        options = WebSocketOptions(url = "wss://example.com/ws", retryPolicy = StepRetryPolicy(maxRetry)),
        scope = backgroundScope,
        newSocket = { _, listener -> FakeWebSocket(listener).also { created += it } },
    )

    /** 每次等 attempt 秒，超过 [max] 次就不重连 —— 便于断言具体数值。 */
    private class StepRetryPolicy(private val max: Int) : WebSocketRetryPolicy {
        override fun delayMillis(attempt: Int): Long? =
            if (attempt > max) null else attempt * 1_000L
    }

    private class FakeWebSocket(private val listener: WebSocketListener) : WebSocket {

        val sentText = mutableListOf<String>()
        var closedWith: Pair<Int, String?>? = null

        override fun request(): Request = Request.Builder().url("wss://example.com/ws").build()

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean {
            sentText += text
            return true
        }

        override fun send(bytes: ByteString): Boolean = true

        override fun close(code: Int, reason: String?): Boolean {
            closedWith = code to reason
            listener.onClosed(this, code, reason.orEmpty())
            return true
        }

        override fun cancel() = Unit

        // ---- 下面几个由测试调用，用来扮演服务端 ----

        fun serverAccepts() {
            listener.onOpen(this, Response.Builder()
                .request(request())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build())
        }

        fun serverSends(text: String) = listener.onMessage(this, text)

        fun serverSends(bytes: ByteString) = listener.onMessage(this, bytes)

        fun serverFails(t: Throwable) = listener.onFailure(this, t, null)

        fun serverClosesNormally() = listener.onClosed(this, WebSocketService.CLOSE_NORMAL, "bye")
    }
}
