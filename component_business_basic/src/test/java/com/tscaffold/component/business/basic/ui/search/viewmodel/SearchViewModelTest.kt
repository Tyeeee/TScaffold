package com.tscaffold.component.business.basic.ui.search.viewmodel

import com.tscaffold.component.business.basic.data.Article
import com.tscaffold.component.business.basic.data.ArticleSource
import com.tscaffold.component.business.basic.ui.search.contract.SearchContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * 搜索页的重点是**防抖**：打字过程中一次请求都不能发，停下来 300 毫秒才搜一次。
 * 这件事在真机上不容易看清（键盘输入快起来肉眼分不出几次），所以在这儿钉住。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** 假的取数实现：不连网络，还能数出"到底搜了几次"。 */
    private class FakeSource(
        private val results: (String) -> List<Article> = { emptyList() },
    ) : ArticleSource {

        override val pageSize: Int = 10
        var searchTimes = 0
        val searchedKeywords = mutableListOf<String>()

        override suspend fun loadPage(page: Int) = emptyList<Article>()
        override suspend fun search(keyword: String): List<Article> {
            searchTimes++
            searchedKeywords += keyword
            return results(keyword)
        }

        override suspend fun loadDetail(id: Int) = null
        override suspend fun delete(id: Int) = false
    }

    @Test
    fun `连着打字只搜一次，搜的是最后停下来的那个词`() = runTest(dispatcher) {
        val fake = FakeSource { keyword -> listOf(Article(1, keyword, "摘要", "作者")) }
        val viewModel = SearchViewModel(fake)

        // 模拟用户逐字敲 "abc"，每字间隔 100 毫秒（小于 300 毫秒的防抖窗口）
        viewModel.setIntent(SearchContract.Intent.KeywordChanged("a"))
        advanceTimeBy(100.milliseconds)
        viewModel.setIntent(SearchContract.Intent.KeywordChanged("ab"))
        advanceTimeBy(100.milliseconds)
        viewModel.setIntent(SearchContract.Intent.KeywordChanged("abc"))
        advanceUntilIdle()

        assertEquals("打字过程中不该发请求，只该搜最后那一次", 1, fake.searchTimes)
        assertEquals(listOf("abc"), fake.searchedKeywords)
        assertEquals(1, viewModel.uiState.value.results.size)
        assertTrue(viewModel.uiState.value.hasSearched)
    }

    @Test
    fun `停下来超过 300 毫秒会真的去搜`() = runTest(dispatcher) {
        val fake = FakeSource { keyword -> listOf(Article(1, keyword, "摘要", "作者")) }
        val viewModel = SearchViewModel(fake)

        viewModel.setIntent(SearchContract.Intent.KeywordChanged("abc"))
        advanceTimeBy(200.milliseconds)
        assertTrue("200 毫秒时还在等，不该搜", fake.searchTimes == 0)

        advanceUntilIdle()
        assertEquals(1, fake.searchTimes)
    }

    @Test
    fun `清空之后不再搜，结果也清光`() = runTest(dispatcher) {
        val fake = FakeSource { keyword -> listOf(Article(1, keyword, "摘要", "作者")) }
        val viewModel = SearchViewModel(fake)

        viewModel.setIntent(SearchContract.Intent.KeywordChanged("abc"))
        advanceUntilIdle()
        assertEquals(1, fake.searchTimes)

        viewModel.setIntent(SearchContract.Intent.Clear)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.keyword)
        assertTrue(state.results.isEmpty())
        assertTrue(!state.hasSearched)
        assertEquals("清空不该触发新的搜索", 1, fake.searchTimes)
    }

    @Test
    fun `空关键字不搜`() = runTest(dispatcher) {
        val fake = FakeSource()
        val viewModel = SearchViewModel(fake)

        viewModel.setIntent(SearchContract.Intent.KeywordChanged("   "))
        advanceUntilIdle()

        assertEquals(0, fake.searchTimes)
        assertTrue(!viewModel.uiState.value.hasSearched)
    }
}
