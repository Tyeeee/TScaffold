package com.tscaffold.component.business.basic.ui.detail.contract

import com.tscaffold.component.business.basic.data.Article
import com.tscaffold.component.common.ui.viewmodel.UiIntent
import com.tscaffold.component.common.ui.viewmodel.UiState

/** 详情页的状态和操作。 */
class DetailContract {

    data class State(
        val id: Int = 0,
        val loading: Boolean = false,
        val article: Article? = null,
        val failMessage: String? = null,
        /** 删除成功了。界面看到它就带着结果关掉自己。 */
        val deleted: Boolean = false,
        /** 要提示用户的一句话。 */
        val message: String? = null,
    ) : UiState

    sealed interface Intent : UiIntent {
        /** 进来时加载这一条。 */
        data class Load(val id: Int) : Intent

        /** 加载失败后点重试。 */
        data object Retry : Intent

        /** 点删除。 */
        data object Delete : Intent

        /** 界面把 [State.message] 显示完了。 */
        data object MessageShown : Intent
    }
}
