package com.tscaffold.feature.ui.list

import com.tscaffold.base.network.http.RetrofitService
import com.tscaffold.feature.data.remote.ArticleRemoteSource
import com.tscaffold.feature.ui.list.contract.ListContract
import com.tscaffold.feature.ui.list.viewmodel.ListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（整页全链路）：真服务器 + 真 [ArticleRemoteSource] + 真 [ListViewModel]。
 *
 * 这一个文件模拟的就是"用户进列表页"会发生的事：进页面加载、滑到底加载第二页、
 * 加载更多失败（**已有数据不能被清空**）、点重试接着加载、下拉刷新失败保留旧数据。
 * 全程没有假替身，走的都是真实 socket 和真 JSON。
 */
class ListPageScenarioTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        // ViewModel 用 Dispatchers.Main，这里换成 Unconfined：协程照样按真实时序跑，
        // 只是不需要 Android 主线程。网络是真实的，所以也不用虚拟时间。
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer()
        server.start()
        RetrofitService.baseUrl = server.url("/api/").toString()
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `进页面自动加载第一页：真的从服务器拿到 10 条`() = runBlocking {
        server.enqueue(page(from = 1))
        val viewModel = newViewModel()

        val state = await(viewModel) { it.articles.size == 10 }

        assertEquals(ListContract.Status.Success, state.status)
        assertEquals(1, state.page)
        assertTrue("还有下一页", state.hasMore)
        assertEquals("第 1 篇", state.articles.first().title)
    }

    @Test
    fun `滑到底加载第二页：追加到 20 条，页码走到 2`() = runBlocking {
        server.enqueue(page(from = 1))
        val viewModel = newViewModel()
        await(viewModel) { it.articles.size == 10 }

        server.enqueue(page(from = 11))
        viewModel.setIntent(ListContract.Intent.LoadMore)

        val state = await(viewModel) { it.articles.size == 20 }
        assertEquals(2, state.page)
        assertEquals("第二页的第一条接在第一页后面", "第 11 篇", state.articles[10].title)
        assertEquals("第 20 篇", state.articles.last().title)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `加载更多失败：底部变成可重试，而已有的 10 条不能被清空`() = runBlocking {
        server.enqueue(page(from = 1))
        val viewModel = newViewModel()
        await(viewModel) { it.articles.size == 10 }

        server.enqueue(MockResponse.Builder().code(500).build())
        viewModel.setIntent(ListContract.Intent.LoadMore)

        val failed = await(viewModel) { it.moreStatus == ListContract.MoreStatus.Failed }
        assertEquals("失败不能把用户已经看到的列表清掉", 10, failed.articles.size)
        assertEquals(1, failed.page)
        assertEquals(ListContract.Status.Success, failed.status)
    }

    @Test
    fun `加载更多失败之后点重试：接着往下加载到 30 条`() = runBlocking {
        server.enqueue(page(from = 1))
        val viewModel = newViewModel()
        await(viewModel) { it.articles.size == 10 }

        server.enqueue(MockResponse.Builder().code(500).build())
        viewModel.setIntent(ListContract.Intent.LoadMore)
        await(viewModel) { it.moreStatus == ListContract.MoreStatus.Failed }

        // 用户点底部"加载更多失败，点我重试"
        server.enqueue(page(from = 11))
        viewModel.setIntent(ListContract.Intent.LoadMore)

        val recovered = await(viewModel) { it.articles.size == 20 }
        assertEquals(ListContract.MoreStatus.Idle, recovered.moreStatus)

        server.enqueue(page(from = 21))
        viewModel.setIntent(ListContract.Intent.LoadMore)
        val third = await(viewModel) { it.articles.size == 30 }
        assertEquals(3, third.page)
    }

    @Test
    fun `下拉刷新失败：保留旧数据，只提示一句话，整页状态不变成失败`() = runBlocking {
        server.enqueue(page(from = 1))
        val viewModel = newViewModel()
        await(viewModel) { it.articles.size == 10 }

        server.enqueue(MockResponse.Builder().code(503).build())
        viewModel.setIntent(ListContract.Intent.Refresh)

        val state = await(viewModel) { it.message != null }
        assertEquals("刷新失败也要留着旧数据", 10, state.articles.size)
        assertEquals(ListContract.Status.Success, state.status)
        assertTrue("提示要说清是刷新失败：${state.message}", state.message!!.startsWith("刷新失败"))
    }

    @Test
    fun `第一页就失败：整页显示失败态，列表是空的`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(500).build())
        val viewModel = newViewModel()

        val state = await(viewModel) { it.status == ListContract.Status.Failed }

        assertTrue(state.failed)
        assertEquals(0, state.articles.size)
        assertEquals("服务器开小差了，稍后再试", state.failMessage)
    }

    // ==================== 辅助 ====================

    private fun newViewModel() = ListViewModel(repository = ArticleRemoteSource(RetrofitService.create()))

    /** 一页真实 JSON：10 条（正好等于 pageSize，所以还有下一页）。 */
    private fun page(from: Int) = json(
        """{"code":0,"data":{"list":[${
            (from until from + 10).joinToString(",") { i ->
                """{"id":$i,"title":"第 $i 篇","summary":"摘要 $i","author":"作者 A"}"""
            }
        }],"total":42}}"""
    )

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type: application/json")
        .body(body)
        .build()

    private suspend fun await(
        viewModel: ListViewModel,
        predicate: (ListContract.State) -> Boolean,
    ): ListContract.State = withTimeout(10_000) { viewModel.uiState.first(predicate) }
}
