package com.tscaffold.feature.ui.detail.viewmodel

import androidx.lifecycle.viewModelScope
import com.tscaffold.feature.data.ArticleRepository
import com.tscaffold.feature.data.ArticleSource
import com.tscaffold.feature.ui.detail.contract.DetailContract
import com.tscaffold.core.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * 详情页的逻辑。
 *
 * 这一页专门演示一件事：**参数怎么进来**。
 * 界面从 Intent 里拿到 id（用户是从列表点进来的），然后作为一个"操作"报给 ViewModel：
 * `setIntent(Load(id))`。ViewModel 不直接去读 Intent，
 * 这样它就不依赖安卓的启动参数，测试、复用都方便。
 */
class DetailViewModel(
    private val repository: ArticleSource = ArticleRepository,
) : BaseViewModel<DetailContract.State, DetailContract.Intent>() {

    override fun initializeState(): DetailContract.State = DetailContract.State()

    override fun handleIntent(intent: DetailContract.Intent) {
        when (intent) {
            is DetailContract.Intent.Load -> load(intent.id)
            DetailContract.Intent.Retry -> load(uiState.value.id)
            DetailContract.Intent.Delete -> delete()
            DetailContract.Intent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun load(id: Int) {
        setState {
            copy(id = id, loading = true, failMessage = null, message = null)
        }
        viewModelScope.launch {
            try {
                val article = repository.loadDetail(id)
                if (article == null) {
                    setState { copy(loading = false, failMessage = "这条内容不见了") }
                } else {
                    setState { copy(loading = false, article = article, failMessage = null) }
                }
            } catch (e: Exception) {
                setState { copy(loading = false, failMessage = e.message ?: "未知错误") }
            }
        }
    }

    private fun delete() {
        val article = uiState.value.article ?: return
        setState { copy(loading = true, message = null) }
        viewModelScope.launch {
            val ok = repository.delete(article.id)
            setState {
                if (ok) {
                    copy(loading = false, deleted = true, message = "已经删掉了")
                } else {
                    copy(loading = false, message = "删除失败，稍后再试")
                }
            }
        }
    }
}
