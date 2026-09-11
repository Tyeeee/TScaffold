package com.tscaffold.component.business.basic.ui.search.viewmodel

import androidx.lifecycle.viewModelScope
import com.tscaffold.component.business.basic.data.ArticleRepository
import com.tscaffold.component.business.basic.data.ArticleSource
import com.tscaffold.component.business.basic.ui.search.contract.SearchContract
import com.tscaffold.component.common.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 搜索页的逻辑，重点是**防抖**：
 *
 * 用户每敲一个字都会报一次 [SearchContract.Intent.KeywordChanged]，
 * 但我们不立刻去搜，而是先记下"要搜什么"，然后等 300 毫秒；
 * 这期间如果又来了一次输入，就把上一次的等待取消掉，重新计时。
 * 用户停下来了，才真正去搜 —— 打字过程中一个请求都不会发。
 *
 * 这就是"异步的节奏控制在业务逻辑里"的实际样子：
 * 界面只管把输入报上来，什么时候查、查几次，全在这里说了算。
 */
class SearchViewModel(
    private val repository: ArticleSource = ArticleRepository,
) : BaseViewModel<SearchContract.State, SearchContract.Intent>() {

    /** 正在等待的那次搜索。新输入来了就把它取消掉，这就是"防抖"。 */
    private var searchJob: Job? = null

    override fun initializeState(): SearchContract.State = SearchContract.State()

    override fun handleIntent(intent: SearchContract.Intent) {
        when (intent) {
            is SearchContract.Intent.KeywordChanged -> onKeywordChanged(intent.value)
            SearchContract.Intent.Clear -> clear()
            is SearchContract.Intent.Click -> setState { copy(openDetailId = intent.id) }
            SearchContract.Intent.DetailOpened -> setState { copy(openDetailId = null) }
            SearchContract.Intent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun onKeywordChanged(keyword: String) {
        searchJob?.cancel()                 // 上一次还没搜就作废
        setState { copy(keyword = keyword, message = null) }

        if (keyword.isBlank()) {
            // 清空了就回到"还没搜过"的样子
            setState { copy(results = emptyList(), searching = false, hasSearched = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)                      // 停 300 毫秒还没新输入，才真的去搜
            setState { copy(searching = true) }
            try {
                val found = repository.search(keyword)
                setState { copy(searching = false, results = found, hasSearched = true) }
            } catch (e: CancellationException) {
                throw e                     // 被取消不算失败，直接往上抛，别当错误处理
            } catch (e: Exception) {
                setState {
                    copy(searching = false, hasSearched = true, message = "搜索失败：${e.message}")
                }
            }
        }
    }

    private fun clear() {
        searchJob?.cancel()
        setState {
            copy(keyword = "", results = emptyList(), searching = false, hasSearched = false)
        }
    }
}
