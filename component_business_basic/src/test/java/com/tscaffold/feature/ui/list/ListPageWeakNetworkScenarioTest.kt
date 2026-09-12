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
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 真实场景（整页 + **弱网/抖动**）：抖动的时机不确定，这里挑最要命的几个时刻各打一次 ——
 * 刚进页面那一刻、正在翻下一页那一刻、连着抖好几下。
 *
 * 真服务器 + 真 [ArticleRemoteSource] + 真 [ListViewModel]，抖动是**连接层**的：
 * 服务端直接把连接掐断，而不是回 500。
 *
 * 关于 [FlakyBackend]：这里用自定义 dispatcher 而不是 `enqueue`，因为
 * **OkHttp 自己会重试连接层失败**（实测一次调用最多发 3 次请求），用队列会被它把
 * "给用户重试准备的下一个响应"吃掉。用一个可以随时坏掉/恢复的后端，重试自然被吸收。
 */
class ListPageWeakNetworkScenarioTest {

    private lateinit var server: MockWebServer
    private lateinit var backend: FlakyBackend

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        backend = FlakyBackend()
        server = MockWebServer()
        server.dispatcher = backend
        server.start()
        RetrofitService.baseUrl = server.url("/api/").toString()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
        Dispatchers.resetMain()
    }

    @Test
    fun `进页面那一刻网络抖动：整页失败态（不是空列表），网络好了点重试就恢复`() = runBlocking {
        backend.dropConnections = true
        val viewModel = newViewModel()

        val failed = await(viewModel) { it.status == ListContract.Status.Failed }
        assertTrue("整页失败要有重试入口", failed.failed)
        assertEquals("不能显示成空列表", 0, failed.articles.size)
        assertEquals("网络不可用，检查一下连接", failed.failMessage)

        // 网络恢复，用户点重试
        backend.dropConnections = false
        viewModel.setIntent(ListContract.Intent.Load)

        val recovered = await(viewModel) { it.articles.size == 10 }
        assertEquals(ListContract.Status.Success, recovered.status)
        assertEquals("第 1 篇", recovered.articles.first().title)
    }

    @Test
    fun `翻第 2 页那一刻网络抖动：已有 10 条保留，恢复后重试补齐到 20 条`() = runBlocking {
        val viewModel = newViewModel()
        await(viewModel) { it.articles.size == 10 }

        // 就在这时候网络抖了
        backend.dropConnections = true
        viewModel.setIntent(ListContract.Intent.LoadMore)

        val failed = await(viewModel) { it.moreStatus == ListContract.MoreStatus.Failed }
        assertEquals("抖动不能把已经看到的 10 条清掉", 10, failed.articles.size)
        assertEquals(1, failed.page)
        assertEquals(ListContract.Status.Success, failed.status)

        // 网络好了，用户点底部"点我重试"
        backend.dropConnections = false
        viewModel.setIntent(ListContract.Intent.LoadMore)

        val recovered = await(viewModel) { it.articles.size == 20 }
        assertEquals(2, recovered.page)
        assertEquals("第 11 篇", recovered.articles[10].title)
    }

    @Test
    fun `连着抖好几次：每次都只是失败，网络一好立刻就能正常加载`() = runBlocking {
        backend.dropConnections = true
        val viewModel = newViewModel()

        await(viewModel) { it.status == ListContract.Status.Failed }
        viewModel.setIntent(ListContract.Intent.Load)
        await(viewModel) { it.status == ListContract.Status.Failed }
        viewModel.setIntent(ListContract.Intent.Load)
        await(viewModel) { it.status == ListContract.Status.Failed }

        backend.dropConnections = false
        viewModel.setIntent(ListContract.Intent.Load)

        val ok = await(viewModel) { it.articles.size == 10 }
        assertEquals(ListContract.Status.Success, ok.status)
        assertEquals("第 1 篇", ok.articles.first().title)
    }

    // ==================== 可随时坏掉/恢复的假后端 ====================

    /**
     * 一个真的会分页的后端：`?page=N` 返回第 N 页（每页 10 条）。
     * [dropConnections] 打开时，它收到请求就把连接掐断（模拟弱网/抖动）。
     */
    private class FlakyBackend : Dispatcher() {

        @Volatile
        var dropConnections = false

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (dropConnections) {
                return MockResponse.Builder()
                    .code(200)
                    .onResponseStart(SocketEffect.CloseSocket(true, true, true))
                    .build()
            }
            val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
            val from = (page - 1) * PAGE_SIZE + 1
            val items = (from until from + PAGE_SIZE).joinToString(",") { i ->
                """{"id":$i,"title":"第 $i 篇","summary":"摘要 $i","author":"作者 A"}"""
            }
            return MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type: application/json")
                .body("""{"code":0,"data":{"list":[$items],"total":42}}""")
                .build()
        }

        private companion object {
            const val PAGE_SIZE = 10
        }
    }

    // ==================== 辅助 ====================

    private fun newViewModel() = ListViewModel(repository = ArticleRemoteSource(RetrofitService.create()))

    private suspend fun await(
        viewModel: ListViewModel,
        predicate: (ListContract.State) -> Boolean,
    ): ListContract.State = withTimeout(15_000) { viewModel.uiState.first(predicate) }
}
