package com.tscaffold.basic.network.websocket

import com.tscaffold.basic.network.websocket.impl.OkHttpWebSocketConnection
import com.tscaffold.basic.network.websocket.impl.WebSocketConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门面（业务唯一入口）的管理行为：按 key 复用句柄、close 与 release 的区别、不同 key 互不干扰。
 *
 * 用假连接替身，不联网 —— 真服务器上的连接/重连行为由
 * `WebSocketScenarioTest` / `WebSocketFailureScenarioTest` / `WebSocketFlappingScenarioTest`（都是真 socket）覆盖。
 */
class WebSocketServiceTest {

    @After
    fun tearDown() {
        WebSocketService.releaseAll()
        WebSocketService.connectionFactory = { options -> OkHttpWebSocketConnection(options) }
    }

    @Test
    fun `同一个 key 拿到同一个句柄，release 之后才重建`() {
        val created = installFakeFactory()
        val options = options()

        val first = WebSocketService.connect("room-1", options)
        val again = WebSocketService.connect("room-1", options)
        assertSame("同一个 key 应该返回同一个句柄", first, again)
        assertEquals("同一个 key 不该建第二条连接", 1, created.size)
        assertEquals(1, created[0].connectCount)

        // close 只是主动关：句柄还能用，可以再连
        first.close()
        assertEquals(WebSocketService.CLOSE_NORMAL, created[0].closedWith?.first)
        WebSocketService.connect("room-1", options)
        assertSame("close 之后仍然是同一条连接", first, WebSocketService.existing("room-1"))
        assertEquals(1, created.size)
        assertEquals(2, created[0].connectCount)

        // release 才是真的放掉，之后再 connect 会建新的
        first.release()
        assertTrue(created[0].released)
        assertNull(WebSocketService.existing("room-1"))
        val rebuilt = WebSocketService.connect("room-1", options)
        assertNotSame(first, rebuilt)
        assertEquals(2, created.size)
    }

    @Test
    fun `句柄转发收发与状态`() {
        val created = installFakeFactory()

        val socket = WebSocketService.connect("room-1", options())

        // 还没连上（假替身停在 Connecting）：发不出去
        assertFalse(socket.send("发不出去"))
        created[0].markConnected()
        assertTrue(socket.send("发得出去"))
        assertTrue(socket.send("二进制".encodeUtf8()))
        assertEquals(WebSocketState.Connected, socket.state.value)

        socket.close(1001, "自己要走")
        assertEquals(1001 to "自己要走", created[0].closedWith)
    }

    // ==================== 测试替身 ====================

    private fun options() = WebSocketOptions(url = "wss://example.com/ws")

    /** 换掉建连接的方式，并把每次建出来的假连接记下来。 */
    private fun installFakeFactory(): MutableList<FakeConnection> {
        val created = mutableListOf<FakeConnection>()
        WebSocketService.connectionFactory = { FakeConnection().also { created += it } }
        return created
    }

    private class FakeConnection : WebSocketConnection {

        private val stateFlow = MutableStateFlow<WebSocketState>(WebSocketState.Idle)
        override val state: StateFlow<WebSocketState> = stateFlow.asStateFlow()
        override val messages: Flow<WebSocketMessage> = emptyFlow()

        var connectCount = 0
        var closedWith: Pair<Int, String?>? = null
        var released = false

        override fun connect() {
            // 和真实现一样是幂等的：已经在连 / 已连上时再调是空操作
            if (stateFlow.value is WebSocketState.Connecting || stateFlow.value == WebSocketState.Connected) return
            connectCount++
            stateFlow.value = WebSocketState.Connecting
        }

        fun markConnected() {
            stateFlow.value = WebSocketState.Connected
        }

        override fun send(text: String): Boolean {
            if (stateFlow.value != WebSocketState.Connected) return false
            return true
        }

        override fun send(bytes: ByteString): Boolean = stateFlow.value == WebSocketState.Connected

        override fun close(code: Int, reason: String?) {
            closedWith = code to reason
            stateFlow.value = WebSocketState.Closed(code, reason)
        }

        override fun changePingInterval(intervalMillis: Long): Boolean = false

        override fun release() {
            released = true
            stateFlow.value = WebSocketState.Closed(WebSocketService.CLOSE_NORMAL, "release")
        }
    }
}
