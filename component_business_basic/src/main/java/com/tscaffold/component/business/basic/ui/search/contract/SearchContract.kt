package com.tscaffold.component.business.basic.ui.search.contract

import com.tscaffold.component.business.basic.data.Article
import com.tscaffold.component.common.ui.viewmodel.UiIntent
import com.tscaffold.component.common.ui.viewmodel.UiState

/**
 * 搜索页的状态和操作。
 *
 * 这一页专门演示**输入防抖**：用户打字很快时，不是每敲一个字都去搜一次，
 * 而是等他停下来 300 毫秒再搜（逻辑在 ViewModel 里）。状态里多存了一个
 * [State.hasSearched]，用来区分"还没搜过"和"搜过了但没结果"——
 * 这两种情况界面上显示的东西不一样。
 */
class SearchContract {

    data class State(
        val keyword: String = "",
        val searching: Boolean = false,
        val results: List<Article> = emptyList(),
        /** 是否已经搜过一次。用来区分"还没搜"和"搜了但没结果"。 */
        val hasSearched: Boolean = false,
        val message: String? = null,
        /** 要打开详情页的那一条，界面打开完回报 [Intent.DetailOpened]。 */
        val openDetailId: Int? = null,
    ) : UiState {

        /** 搜过了、搜完了、但一条都没有。 */
        val noResult: Boolean
            get() = hasSearched && !searching && results.isEmpty() && keyword.isNotBlank()
    }

    sealed interface Intent : UiIntent {
        /** 用户改了搜索框里的字。 */
        data class KeywordChanged(val value: String) : Intent

        /** 点了清空。 */
        data object Clear : Intent

        /** 点了一条结果。 */
        data class Click(val id: Int) : Intent

        /** 界面把详情页打开完了。 */
        data object DetailOpened : Intent

        /** 界面把 [State.message] 显示完了。 */
        data object MessageShown : Intent
    }
}
