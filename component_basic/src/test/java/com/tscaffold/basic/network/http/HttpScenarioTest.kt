package com.tscaffold.basic.network.http

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.http.GET

/**
 * 真实场景（HTTP）：真的起一个本地服务器，真的走 socket、真的用 Retrofit + Gson 解析。
 * **没有假替身**，验的是"接上真后端会长什么样"。
 */
class HttpScenarioTest {

    private lateinit var server: MockWebServer

    /** 测试用的协议接口（业务里就写在自己的协议配套文件里）。 */
    private interface GreetingApi {
        @GET("greeting")
        suspend fun greeting(): Greeting
    }

    private data class Greeting(val id: Int = 0, val text: String? = null)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        RetrofitService.baseUrl = server.url("/api/").toString()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `200 加真实 JSON：真的被解析成对象`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type: application/json")
                .body("""{"id":7,"text":"来自真实服务器"}""")
                .build()
        )
        val api: GreetingApi = RetrofitService.create()

        val greeting = RetrofitService.call { api.greeting() }

        assertEquals(7, greeting.id)
        assertEquals("来自真实服务器", greeting.text)
        // 确认请求真的打到了服务器，而且是按 baseUrl + @GET 的路径拼的
        val recorded = server.takeRequest()
        assertEquals("/api/greeting", recorded.url.encodedPath)
    }

    @Test
    fun `后端少给字段：可空字段兜底，不崩`() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"id":1}""").build()
        )
        val api: GreetingApi = RetrofitService.create()

        val greeting = RetrofitService.call { api.greeting() }

        assertEquals(1, greeting.id)
        assertEquals(null, greeting.text)
    }

    @Test
    fun `换 baseUrl 后重新 create：真的打到新服务器，而不是还用着旧的那台`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":1,"text":"第一台"}""").build())
        val api: GreetingApi = RetrofitService.create()
        assertEquals("第一台", RetrofitService.call { api.greeting() }.text)

        // 再起一台服务器，把地址换过去 —— 注意要重新 create()：
        // Retrofit 的接口代理绑定在创建时的实例上，旧代理还会打旧地址（所以这条用例也钉住了这个契约）
        val second = MockWebServer().apply { start() }
        try {
            second.enqueue(MockResponse.Builder().code(200).body("""{"id":2,"text":"第二台"}""").build())
            RetrofitService.baseUrl = second.url("/api/").toString()

            val newApi: GreetingApi = RetrofitService.create()
            assertEquals("第二台", RetrofitService.call { newApi.greeting() }.text)
            assertEquals("请求应该打到第二台服务器", 1, second.requestCount)
        } finally {
            second.close()
        }
    }

    private suspend fun catchApiError(block: suspend () -> Any?): ApiException =
        try {
            block()
            throw AssertionError("这里应该抛 ApiException，但正常返回了")
        } catch (e: ApiException) {
            e
        }
}
