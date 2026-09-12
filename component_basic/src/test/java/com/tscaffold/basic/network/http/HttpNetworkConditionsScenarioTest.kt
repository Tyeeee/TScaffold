package com.tscaffold.basic.network.http

import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

/**
 * 真实场景（HTTP 的各种网络状况）：真的起服务器、真的走 socket。
 *
 * 覆盖：各种状态码、响应体畸形、服务器中途断连、服务器卡住（超时）、重定向、分块大响应、
 * DNS 解析不了、连接被拒、以及**请求进行中取消协程**。
 */
class HttpNetworkConditionsScenarioTest {

    private lateinit var server: MockWebServer

    private interface GreetingApi {
        @GET("greeting")
        suspend fun greeting(): Greeting

        @GET("greetings")
        suspend fun greetings(): List<Greeting>
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
        // 超时用例会把读超时调短，这里还原，免得影响别的用例
        HttpClientDefaults.restore()
        server.close()
    }

    // ==================== 响应体畸形 ====================

    @Test
    fun `200 但响应体是空的：抛 Unknown，不是崩，也不是"成功但没数据"`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("").build())

        val error = catchApiError { call { api().greeting() } }

        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.Unknown)
        assertEquals("出了点问题，稍后再试", error.userMessage)
    }

    @Test
    fun `200 但 JSON 语法错：抛 Unknown`() = runBlocking {
        server.enqueue(json("""{"id":1,"text":""")) // 少了右括号

        assertTrue(catchApiError { call { api().greeting() } } is ApiException.Unknown)
    }

    @Test
    fun `200 但 JSON 类型不符（要对象给数组）：抛 Unknown`() = runBlocking {
        server.enqueue(json("""[1,2,3]"""))

        assertTrue(catchApiError { call { api().greeting() } } is ApiException.Unknown)
    }

    // ==================== 各种状态码 ====================

    @Test
    fun `4xx 都归到 Http(code)，文案按码给`() = runBlocking {
        val expected = mapOf(
            400 to "请求有误",
            401 to "登录已失效，请重新登录",
            403 to "登录已失效，请重新登录",
            404 to "内容不存在",
            422 to "请求失败（422）",
        )
        for ((code, message) in expected) {
            server.enqueue(MockResponse.Builder().code(code).build())

            val error = catchApiError { call { api().greeting() } }

            assertTrue("$code 应该是 Http", error is ApiException.Http)
            assertEquals(code, (error as ApiException.Http).code)
            assertEquals("HTTP $code 的文案", message, error.userMessage)
        }
    }

    @Test
    fun `5xx 都归到 Http(code)，文案说服务器的问题`() = runBlocking {
        for (code in listOf(500, 502, 503, 504)) {
            server.enqueue(MockResponse.Builder().code(code).build())

            val error = catchApiError { call { api().greeting() } }

            assertTrue(error is ApiException.Http)
            assertEquals(code, (error as ApiException.Http).code)
            assertEquals("服务器开小差了，稍后再试", error.userMessage)
        }
    }

    // ==================== 连接层面的问题 ====================

    @Test
    fun `服务器没人监听（连接被拒）：抛 NoNetwork`() = runBlocking {
        val port = server.port
        server.close()
        RetrofitService.baseUrl = "http://127.0.0.1:$port/api/"

        val error = catchApiError { call { api().greeting() } }

        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)
    }

    @Test
    fun `服务器回一半就把连接断了：抛 NoNetwork`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .onResponseStart(SocketEffect.CloseSocket(true, true, true))
                .build()
        )

        val error = catchApiError { call { api().greeting() } }

        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.NoNetwork)
    }

    @Test
    fun `服务器卡住不响应：读超时后抛 Timeout`() = runBlocking {
        // 把读超时调短（共享 client 会重建），否则这条要等 30 秒
        HttpClientDefaults.useShortReadTimeout()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"id":1}""")
                .headersDelay(3, TimeUnit.SECONDS)
                .build()
        )

        val error = catchApiError { call { api().greeting() } }

        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.Timeout)
        assertEquals("请求超时，稍后再试", error.userMessage)
    }

    @Test
    fun `请求进行中取消协程：取消原样抛出，不会被当成请求失败`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"id":1}""")
                .headersDelay(10, TimeUnit.SECONDS)
                .build()
        )
        var caught: Throwable? = null
        val job = launch {
            try {
                call { api().greeting() }
            } catch (e: Throwable) {
                caught = e
            }
        }

        delay(300)
        job.cancel()
        job.join()

        assertTrue("实际是 ${caught?.javaClass?.name}", caught is CancellationException)
        assertFalse("取消不能被翻译成 ApiException", caught is ApiException)
    }

    // ==================== 正常但特殊的情况 ====================

    @Test
    fun `302 重定向：默认跟随，最终拿到那个 200`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location: /api/greeting-2").build()
        )
        server.enqueue(json("""{"id":9,"text":"重定向之后"}"""))

        val greeting = call { api().greeting() }

        assertEquals(9, greeting.id)
        assertEquals("重定向之后", greeting.text)
        assertEquals("真的跟了两次请求", 2, server.requestCount)
    }

    @Test
    fun `分块传输的大响应：正常解析`() = runBlocking {
        val items = (1..2_000).joinToString(",") { """{"id":$it,"text":"第 $it 条"}""" }
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type: application/json")
                .chunkedBody("[$items]", 1024)
                .build()
        )

        val list = call { api().greetings() }

        assertEquals(2_000, list.size)
        assertEquals("第 2000 条", list.last().text)
    }

    // ==================== 辅助 ====================

    private fun api(): GreetingApi = RetrofitService.create()

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type: application/json")
        .body(body)
        .build()

    /** 走真实入口调一次接口：异常会被统一翻译成 ApiException。 */
    private suspend fun <T> call(block: suspend () -> T): T = RetrofitService.call(block)

    private suspend fun catchApiError(block: suspend () -> Any?): ApiException =
        try {
            block()
            throw AssertionError("这里应该抛 ApiException，但正常返回了")
        } catch (e: ApiException) {
            e
        }

    /** 超时用例需要把共享 client 的读超时临时调短。 */
    private object HttpClientDefaults {
        fun useShortReadTimeout() {
            com.tscaffold.basic.network.HttpClient.readTimeoutMillis = 1_500
        }

        fun restore() {
            com.tscaffold.basic.network.HttpClient.readTimeoutMillis = 30_000
        }
    }
}
