package com.tscaffold.feature.data.remote

import com.tscaffold.base.network.http.ApiException
import com.tscaffold.base.network.http.RetrofitService
import com.tscaffold.feature.data.Article
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（数据层）：真起一个本地服务器，走真的 HTTP、真的 Retrofit、真的 Gson，
 * 让 [ArticleRemoteSource] 完成"拆信封 → 转模型 → 错误翻译"的全过程。**没有假替身。**
 */
class ArticleRemoteSourceScenarioTest {

    private lateinit var server: MockWebServer
    private lateinit var source: ArticleRemoteSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        RetrofitService.baseUrl = server.url("/api/").toString()
        source = ArticleRemoteSource(RetrofitService.create())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `真服务器返回真实 JSON：拆掉信封、DTO 转成页面模型`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":{"list":[
            {"id":1,"title":"第一篇","summary":"摘要 1","author":"作者 A"},
            {"id":2,"title":"第二篇","summary":"摘要 2","author":"作者 B"}
        ],"total":42}}"""))

        val page = source.loadPage(1)

        assertEquals(
            listOf(
                Article(1, "第一篇", "摘要 1", "作者 A"),
                Article(2, "第二篇", "摘要 2", "作者 B"),
            ),
            page,
        )
        // 确认真的按协议的路径和参数发了请求
        val request = server.takeRequest()
        assertEquals("/api/article/page", request.url.encodedPath)
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("10", request.url.queryParameter("size"))
    }

    @Test
    fun `后端业务码不是成功：抛 Business，提示就是后端给的那句话`() = runBlocking {
        server.enqueue(json("""{"code":1001,"msg":"这个账号没有权限"}"""))

        val error = catchApiError { source.loadPage(1) }

        assertTrue("实际是 ${error.javaClass.name}", error is ApiException.Business)
        assertEquals(1001, (error as ApiException.Business).code)
        assertEquals("这个账号没有权限", error.message)
    }

    @Test
    fun `真实 HTTP 500：抛 Http，带状态码和给用户看的那句话`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).body("server boom").build())

        val error = catchApiError { source.loadPage(1) }

        assertTrue(error is ApiException.Http)
        assertEquals(500, (error as ApiException.Http).code)
        assertEquals("服务器开小差了，稍后再试", error.userMessage)
    }

    @Test
    fun `详情接口说成功但没数据：返回 null，交给界面显示"这条不见了"`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":null}"""))

        assertNull(source.loadDetail(99))
    }

    @Test
    fun `搜索空关键字：一个请求都不发`() = runBlocking {
        assertEquals(emptyList<Article>(), source.search("   "))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `搜索有结果：真的把关键字发给后端并解析回来`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":[{"id":3,"title":"第三篇","author":"作者 C"}]}"""))

        val found = source.search("三")

        assertEquals(1, found.size)
        assertEquals("第三篇", found[0].title)
        assertEquals("", found[0].summary) // 后端没给 summary，兜底成空串
        assertEquals("三", server.takeRequest().url.queryParameter("keyword"))
    }

    @Test
    fun `删除失败：抛异常，而不是悄悄返回 false`() = runBlocking {
        server.enqueue(json("""{"code":403,"msg":"没权限删"}"""))

        val error = catchApiError { source.delete(1) }

        assertEquals("没权限删", error.message)
    }

    @Test
    fun `删除成功：返回 true`() = runBlocking {
        server.enqueue(json("""{"code":0,"data":true}"""))

        assertTrue(source.delete(1))
        assertEquals("DELETE", server.takeRequest().method)
    }

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type: application/json")
        .body(body)
        .build()

    private suspend fun catchApiError(block: suspend () -> Any?): ApiException =
        try {
            block()
            throw AssertionError("这里应该抛 ApiException，但正常返回了")
        } catch (e: ApiException) {
            e
        }
}
