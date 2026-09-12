package com.tscaffold.feature.data.remote

import com.tscaffold.base.network.http.ApiException
import com.tscaffold.feature.data.Article
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 数据层的测试：**不联网**，塞一个假的 [ArticleApi] 进去就行。
 *
 * 这正是"把取数据抽成接口"的意义 —— 网络模型怎么映射、错误怎么翻译，
 * 全都能在电脑上验证，不用模拟器、不用后端。
 */
class ArticleRemoteSourceTest {

    private val api = FakeArticleApi()
    private val source = ArticleRemoteSource(api)

    @Test
    fun `取一页数据：拆掉外壳，把网络模型转成页面模型`() = runTest {
        api.pageResult = {
            Envelope(
                code = Envelope.CODE_OK,
                data = ArticlePageDto(
                    list = listOf(ArticleDto(1, "标题", "摘要", "作者 A")),
                    total = 1,
                ),
            )
        }

        val page = source.loadPage(1)

        assertEquals(listOf(Article(1, "标题", "摘要", "作者 A")), page)
        // 每页几条是数据源自己的规矩，页面不该猜 —— 这里确认真的把它传给了接口
        assertEquals(source.pageSize, api.lastPageSize)
    }

    @Test
    fun `后端少给字段时用空串兜底，不会崩`() = runTest {
        api.pageResult = {
            Envelope(Envelope.CODE_OK, data = ArticlePageDto(list = listOf(ArticleDto(id = 9))))
        }

        val article = source.loadPage(1).single()

        assertEquals(9, article.id)
        assertEquals("", article.title)
        assertEquals("", article.author)
    }

    @Test
    fun `业务码不是成功：抛 Business，提示用后端给的那句`() = runTest {
        api.pageResult = { Envelope(code = 1001, message = "没有权限") }

        val error = catchApiException { source.loadPage(1) }

        assertTrue(error is ApiException.Business)
        assertEquals(1001, (error as ApiException.Business).code)
        assertEquals("没有权限", error.message)
    }

    @Test
    fun `业务失败但后端没给提示：提示里带上错误码，不糊弄用户`() = runTest {
        api.searchResult = { Envelope(code = 500) }

        val error = catchApiException { source.search("标题") }

        assertTrue(error.message!!.contains("500"))
    }

    @Test
    fun `说成功却没有数据：算失败，不能当空列表糊过去`() = runTest {
        api.pageResult = { Envelope(code = Envelope.CODE_OK, data = null) }

        val error = catchApiException { source.loadPage(1) }

        assertEquals("服务端没有返回数据", error.message)
    }

    @Test
    fun `HTTP 失败：抛 Http，带上状态码`() = runTest {
        api.detailResult = { throw httpException(503) }

        val error = catchApiException { source.loadDetail(1) }

        assertTrue(error is ApiException.Http)
        assertEquals(503, (error as ApiException.Http).code)
    }

    @Test
    fun `断网和超时分别翻译成 NoNetwork 与 Timeout`() = runTest {
        api.detailResult = { throw UnknownHostException("no net") }
        assertTrue(catchApiException { source.loadDetail(1) } is ApiException.NoNetwork)

        api.detailResult = { throw SocketTimeoutException("timeout") }
        assertTrue(catchApiException { source.loadDetail(1) } is ApiException.Timeout)
    }

    @Test
    fun `详情没有数据：返回 null，交给界面显示这条内容不见了`() = runTest {
        api.detailResult = { Envelope(code = Envelope.CODE_OK, data = null) }

        assertNull(source.loadDetail(1))
    }

    @Test
    fun `删除成功返回 true`() = runTest {
        api.deleteResult = { Envelope(code = Envelope.CODE_OK) }

        assertTrue(source.delete(1))
    }

    @Test
    fun `删除失败是抛异常，而不是悄悄返回 false`() = runTest {
        api.deleteResult = { Envelope(code = 403, message = "没权限删") }

        val error = catchApiException { source.delete(1) }

        assertEquals("没权限删", error.message)
    }

    @Test
    fun `空关键字不发请求`() = runTest {
        api.searchResult = { Envelope(code = 1001, message = "不该被调用到这里") }

        assertEquals(emptyList<Article>(), source.search("   "))
        assertEquals(0, api.searchCallCount)
    }

    // ==================== 测试替身：假的接口实现，不联网 ====================

    private class FakeArticleApi : ArticleApi {

        var pageResult: () -> Envelope<ArticlePageDto> = { Envelope() }
        var searchResult: () -> Envelope<List<ArticleDto>> = { Envelope() }
        var detailResult: () -> Envelope<ArticleDto> = { Envelope() }
        var deleteResult: () -> Envelope<Any> = { Envelope() }

        var lastPageSize: Int = -1
        var searchCallCount: Int = 0

        override suspend fun page(page: Int, size: Int): Envelope<ArticlePageDto> {
            lastPageSize = size
            return pageResult()
        }

        override suspend fun search(keyword: String): Envelope<List<ArticleDto>> {
            searchCallCount++
            return searchResult()
        }

        override suspend fun detail(id: Int): Envelope<ArticleDto> = detailResult()

        override suspend fun delete(id: Int): Envelope<Any> = deleteResult()
    }

    private suspend fun catchApiException(block: suspend () -> Any?): ApiException =
        try {
            block()
            throw AssertionError("这里应该抛 ApiException，但正常返回了")
        } catch (e: ApiException) {
            e
        }

    private fun httpException(code: Int): HttpException = HttpException(
        Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType()))
    )
}
