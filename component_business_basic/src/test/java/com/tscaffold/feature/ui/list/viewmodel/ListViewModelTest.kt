package com.tscaffold.feature.ui.list.viewmodel

import com.tscaffold.feature.data.Article
import com.tscaffold.feature.data.ArticleSource
import com.tscaffold.feature.ui.list.contract.ListContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 列表页的分页逻辑，是这套骨架里最绕的一块，用测试钉住：
 * 加载更多失败不能把已有数据清掉、刷新失败也一样、重试能接着往下走。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** 假的数据源：每页固定 3 条，可以指定"哪一页会失败"。 */
    private class FakeSource(
        override val pageSize: Int = 3,
        private val total: Int = 7,
    ) : ArticleSource {
        val failPages = mutableSetOf<Int>()
        var loadPageTimes = 0

        override suspend fun loadPage(page: Int): List<Article> {
            loadPageTimes++
            if (page in failPages) error("故意失败")
            val from = (page - 1) * pageSize
            return (from until minOf(from + pageSize, total)).map {
                Article(it + 1, "第 ${it + 1} 条", "摘要", "作者")
            }
        }

        override suspend fun search(keyword: String) = emptyList<Article>()
        override suspend fun loadDetail(id: Int) = null
        override suspend fun delete(id: Int) = true
    }

    @Test
    fun `进页面自动加载第一页`() = runTest(dispatcher) {
        val viewModel = ListViewModel(FakeSource())

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ListContract.Status.Success, state.status)
        assertEquals(3, state.articles.size)
        assertEquals(1, state.page)
        assertTrue(state.hasMore)
    }

    @Test
    fun `第一页就失败时整页显示失败`() = runTest(dispatcher) {
        val fake = FakeSource().apply { failPages += 1 }
        val viewModel = ListViewModel(fake)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ListContract.Status.Failed, state.status)
        assertTrue(state.articles.isEmpty())
    }

    @Test
    fun `加载更多失败时保留已有数据，底部变成可以重试`() = runTest(dispatcher) {
        val fake = FakeSource()
        val viewModel = ListViewModel(fake)
        advanceUntilIdle()
        val loaded = viewModel.uiState.value.articles.size

        fake.failPages += 2
        viewModel.setIntent(ListContract.Intent.LoadMore)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("已有的数据不能被清掉", loaded, state.articles.size)
        assertEquals(ListContract.MoreStatus.Failed, state.moreStatus)
        assertTrue(state.hasMore)
    }

    @Test
    fun `加载更多失败之后点重试能接着加载`() = runTest(dispatcher) {
        val fake = FakeSource()
        val viewModel = ListViewModel(fake)
        advanceUntilIdle()
        fake.failPages += 2
        viewModel.setIntent(ListContract.Intent.LoadMore)
        advanceUntilIdle()

        fake.failPages.clear()                      // 网络好了
        viewModel.setIntent(ListContract.Intent.LoadMore)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.page)
        assertTrue(state.articles.size > 3)
    }

    @Test
    fun `刷新失败但手里还有数据时，保留旧数据只提示一句`() = runTest(dispatcher) {
        val fake = FakeSource()
        val viewModel = ListViewModel(fake)
        advanceUntilIdle()
        val loaded = viewModel.uiState.value.articles.size

        fake.failPages += 1
        viewModel.setIntent(ListContract.Intent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ListContract.Status.Success, state.status)
        assertEquals(loaded, state.articles.size)
        assertNull(state.failMessage)
        assertTrue("应该提示一句刷新失败", state.message?.contains("刷新失败") == true)
    }

    @Test
    fun `在加载中再点加载更多不会重复请求`() = runTest(dispatcher) {
        val fake = FakeSource()
        val viewModel = ListViewModel(fake)
        advanceUntilIdle()
        val timesBefore = fake.loadPageTimes

        viewModel.setIntent(ListContract.Intent.LoadMore)
        viewModel.setIntent(ListContract.Intent.LoadMore)   // 还没加载完又点了一下
        advanceUntilIdle()

        assertEquals("第二次点击应该被忽略", timesBefore + 1, fake.loadPageTimes)
    }

    @Test
    fun `点一条会记下要打开哪个详情页，回报之后清掉`() = runTest(dispatcher) {
        val viewModel = ListViewModel(FakeSource())
        advanceUntilIdle()

        viewModel.setIntent(ListContract.Intent.Click(2))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.openDetailId)

        viewModel.setIntent(ListContract.Intent.DetailOpened)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.openDetailId)
    }

    @Test
    fun `从详情页删掉回来的那一条，列表里也要去掉`() = runTest(dispatcher) {
        val viewModel = ListViewModel(FakeSource())
        advanceUntilIdle()
        val target = viewModel.uiState.value.articles.first()

        viewModel.setIntent(ListContract.Intent.DetailClosed(target.id, deleted = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.articles.none { it.id == target.id })
    }
}
