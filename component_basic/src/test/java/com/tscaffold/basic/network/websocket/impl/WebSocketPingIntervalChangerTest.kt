package com.tscaffold.basic.network.websocket.impl

import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 这个文件的测试有两个作用：
 *
 * 1. **哨兵**：把"当前 OkHttp 版本上反射能不能改心跳"钉死。OkHttp 一升级、内部结构再变，
 *    这里会失败，提醒我们回来复核 [WebSocketPingIntervalChanger]，而不是让它悄悄退化成空操作
 *    （母工程那段就是这种"看着能用、其实什么都不做"的状态）。
 * 2. **不崩**：不管内部结构长什么样、传进来的是不是真的 RealWebSocket，
 *    这个入口都必须只是返回 false，绝不抛异常把连接搞崩。
 */
class WebSocketPingIntervalChangerTest {

    @Test
    fun `哨兵：当前 OkHttp 版本（5_x）不支持运行时改协议级心跳`() {
        // 依赖里锁的是 OkHttp 5.5.0：没有 RealWebSocket.executor、也没有 PingRunnable，
        // 心跳被按值冻在调度任务里，所以反射改不了 —— 这里记录的是**事实**。
        // 哪天升级 OkHttp 后这条挂了，说明内部结构变了，该回来重新研究这套反射。
        assertFalse(
            "OkHttp 内部结构变了，请复核 WebSocketPingIntervalChanger",
            WebSocketPingIntervalChanger.canChangeLivePing(),
        )
    }

    @Test
    fun `不是真正的 RealWebSocket 时只是返回 false，不抛异常`() {
        assertFalse(WebSocketPingIntervalChanger.apply(FakeSocket(), 5_000))
    }

    @Test
    fun `socket 为空或者间隔非法，都只返回 false、不去建调度器`() {
        assertFalse(WebSocketPingIntervalChanger.apply(null, 5_000))
        assertFalse(WebSocketPingIntervalChanger.apply(FakeSocket(), 0))
        assertFalse(WebSocketPingIntervalChanger.apply(FakeSocket(), -1))
    }

    private class FakeSocket : WebSocket {
        override fun request(): Request = Request.Builder().url("wss://example.com/ws").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }
}
