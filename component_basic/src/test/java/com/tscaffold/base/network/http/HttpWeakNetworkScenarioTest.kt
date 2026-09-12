package com.tscaffold.base.network.http

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

/**
 * 真实场景（**弱网与网络抖动**）：限速、慢响应、以及**把断连注入到各个阶段**。
 *
 * 弱网的特点是"什么时候出问题都有可能"：请求刚发出去就断、响应头还没来就断、
 * body 传到一半断、慢得读不出来、时快时慢。这里逐条都真跑一遍，
 * 而且每一条都要求：失败之后**下一次请求必须能正常**（网络恢复要能自愈）。
 */
class HttpWeakNetworkScenarioTest {

    private lateinit var server: MockWebServer

    private interface GreetingApi {
        @GET("greeting")
        suspend fun greeting(): Greeting

        @GET("big")
        suspend fun big(): List<Greeting>
    }

    private data class Greeting(val id: Int = 0, val text: String? = null)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        RetrofitService.baseUrl = server.url("/api/").toString()
    }

    @After
    fun tearDown() {
        HttpClientWeak.restore()
        runCatching { server.close() }
    }

    // ==================== 慢：时快时慢的抖动 ====================

    @Test
    fun `响应时快时慢：快的成功、超时的报超时，而且失败不影响下一次请求`() = runBlocking {
        HttpClientWeak.useShortReadTimeout() // 1.5 秒就判超时

        server.enqueue(json("""{"id":1,"text":"很快"}"""))                                  // 立刻回
        server.enqueue(json("""{"id":2,"text":"很慢"}""").newBuilder()
            .headersDelay(5, TimeUnit.SECONDS).build())                                     // 慢到超时
        server.enqueue(json("""{"id":3,"text":"又快了"}"""))                                // 恢复

        assertEquals("很快", call { api().greeting() }.text)          // ① 正常
        assertTrue("慢响应应该是 Timeout", catchApiError { call { api().greeting() } } is ApiException.Timeout)
        assertEquals("超时之后必须能自愈", "又快了", call { api().greeting() }.text) // ③ 恢复
    }

    // ==================== 限速：带宽很小的弱网 ====================

    @Test
    fun `带宽很小但能传完：完整拿到大响应`() = runBlocking {
        HttpClientWeak.useLongReadTimeout() // 慢传输需要放宽超时
        server.enqueue(
            json(bigList(count = 500))
                .newBuilder()
                .throttleBody(64 * 1024, 1, TimeUnit.SECONDS) // 每秒只给 64KB
                .build()
        )

        val list = call { api().big() }

        assertEquals(500, list.size)
        assertEquals("第 500 条", list.last().text)
    }

    @Test
    fun `弱网慢速传输不该被误判成超时：只要还有数据在来就一直等下去`() = runBlocking {
        // 这条钉住一个很容易搞错的语义：OkHttp 的 readTimeout 是**两次读到数据之间的间隔**，
        // 不是整次请求的总时长。所以"每秒只给 8KB、总共要 8 秒"这种弱网，
        // 只要数据一直在来，就不该被判超时（哪怕读超时只有 1.5 秒）。
        HttpClientWeak.useShortReadTimeout() // 1.5 秒
        server.enqueue(
            json(bigList(count = 2_000))
                .newBuilder()
                .throttleBody(8 * 1024, 1, TimeUnit.SECONDS) // 每秒只给 8KB，远慢于 1.5 秒
                .build()
        )

        val list = call { api().big() }

        assertEquals("慢但一直在传，就该耐心等完", 2_000, list.size)
    }

    @Test
    fun `弱网要限制的是总时长：用 withTimeout 包一层，慢连接按时被掐断`() = runBlocking {
        HttpClientWeak.useLongReadTimeout()
        server.enqueue(
            json(bigList(count = 2_000))
                .newBuilder()
                .throttleBody(8 * 1024, 1, TimeUnit.SECONDS)
                .build()
        )

        val started = System.currentTimeMillis()
        val result = runCatching { withTimeout(1_500) { call { api().big() } } }
        val elapsed = System.currentTimeMillis() - started

        assertTrue(
            "应该按总时长被掐断，实际是 ${result.exceptionOrNull()?.javaClass?.name}",
            result.exceptionOrNull() is TimeoutCancellationException,
        )
        assertTrue("1.5 秒左右就该放弃，实际 ${elapsed}ms", elapsed < 6_000)
    }

    // ==================== 断在任何一个阶段 ====================

    @Test
    fun `请求刚发出去服务端就断（onRequestStart）：NoNetwork，且下一次能自愈`() = runBlocking {
        assertBrokenAt(SocketEffect.CloseSocket(true, true, true), stage = "onRequestStart")
    }

    @Test
    fun `响应头还没来就断（onResponseStart）：NoNetwork，且下一次能自愈`() = runBlocking {
        server.enqueue(
            json("""{"id":1,"text":"还没开始发就断了"}""").newBuilder()
                .onResponseStart(SocketEffect.CloseSocket(true, true, true))
                .build()
        )
        server.enqueue(json("""{"id":2,"text":"恢复了"}"""))

        val error = catchApiError { call { api().greeting() } }
        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)
        assertEquals("恢复了", call { api().greeting() }.text)
    }

    @Test
    fun `body 传到一半断（onResponseBody）：NoNetwork，且下一次能自愈`() = runBlocking {
        server.enqueue(
            json("""{"id":1,"text":"这是一段会被掐断的响应体"}""").newBuilder()
                .onResponseBody(SocketEffect.CloseSocket(true, true, true))
                .build()
        )
        server.enqueue(json("""{"id":2,"text":"恢复了"}"""))

        val error = catchApiError { call { api().greeting() } }
        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)
        assertEquals("恢复了", call { api().greeting() }.text)
    }

    @Test
    fun `服务端先下线、再在同端口回来：同一个接口代理能自愈`() = runBlocking {
        val port = server.port
        server.enqueue(json("""{"id":1,"text":"下线之前"}"""))
        assertEquals("下线之前", call { api().greeting() }.text)

        runCatching { server.close() } // 服务器下线

        val error = catchApiError { call { api().greeting() } }
        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)

        // 同端口起一台新的 —— 客户端不需要重建任何东西，下一次请求就该好
        val reborn = MockWebServer()
        reborn.enqueue(json("""{"id":2,"text":"又回来了"}"""))
        reborn.start(port)
        server = reborn

        assertEquals("又回来了", call { api().greeting() }.text)
    }

    // ==================== 辅助 ====================

    /** 在 onRequestStart 阶段就掐断，并验证失败之后能自愈。 */
    private suspend fun assertBrokenAt(effect: SocketEffect, stage: String) {
        server.enqueue(json("""{"id":1,"text":"x"}""").newBuilder().onRequestStart(effect).build())
        server.enqueue(json("""{"id":2,"text":"恢复了"}"""))

        val error = catchApiError { call { api().greeting() } }

        assertTrue("$stage 阶段断连应该是 NoNetwork，实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)
        assertEquals("$stage 之后要能自愈", "恢复了", call { api().greeting() }.text)
    }

    private fun api(): GreetingApi = RetrofitService.create()

    private suspend fun <T> call(block: suspend () -> T): T = RetrofitService.call(block)

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type: application/json")
        .body(body)
        .build()

    private fun bigList(count: Int) = """[${
        (1..count).joinToString(",") { """{"id":$it,"text":"第 $it 条"}""" }
    }]"""

    private suspend fun catchApiError(block: suspend () -> Any?): ApiException =
        try {
            block()
            throw AssertionError("这里应该抛 ApiException，但正常返回了")
        } catch (e: ApiException) {
            e
        }

    /** 弱网用例需要临时改共享 client 的超时。 */
    private object HttpClientWeak {
        fun useShortReadTimeout() {
            com.tscaffold.base.network.HttpClient.readTimeoutMillis = 1_500
        }

        fun useLongReadTimeout() {
            com.tscaffold.base.network.HttpClient.readTimeoutMillis = 20_000
        }

        fun restore() {
            com.tscaffold.base.network.HttpClient.readTimeoutMillis = 30_000
        }
    }
}
