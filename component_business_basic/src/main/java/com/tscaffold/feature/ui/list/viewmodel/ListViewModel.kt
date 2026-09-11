package com.tscaffold.feature.ui.list.viewmodel

import androidx.lifecycle.viewModelScope
import com.tscaffold.feature.data.ArticleRepository
import com.tscaffold.feature.data.ArticleSource
import com.tscaffold.feature.ui.list.contract.ListContract
import com.tscaffold.core.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * 列表页的全部逻辑：分页、下拉刷新、加载更多、删除、以及"打开详情页"这件事。
 *
 * 和前面那个简单示例比，这里多了几个真实场景的处理：
 * - 刷新和加载更多要区分开（刷新是整页转圈，加载更多只影响底部那一行）；
 * - 加载更多失败时**不能把已有的数据清掉**，只把底部那一行改成"点我重试"；
 * - 刷新失败但手里还有旧数据时，也保留旧数据，只提示一句；
 * - 正在加载时再点"加载更多"要忽略掉，避免重复请求。
 */
class ListViewModel(
    private val repository: ArticleSource = ArticleRepository,
) : BaseViewModel<ListContract.State, ListContract.Intent>() {

    override fun initializeState(): ListContract.State = ListContract.State()

    init {
        setIntent(ListContract.Intent.Load)
    }

    override fun handleIntent(intent: ListContract.Intent) {
        when (intent) {
            ListContract.Intent.Load -> loadFirstPage()
            ListContract.Intent.Refresh -> refresh()
            ListContract.Intent.LoadMore -> loadMore()

            is ListContract.Intent.Click -> setState { copy(openDetailId = intent.id) }
            is ListContract.Intent.LongClick -> setState { copy(pendingDeleteId = intent.id) }
            ListContract.Intent.ConfirmDelete -> confirmDelete()
            ListContract.Intent.CancelDelete -> setState { copy(pendingDeleteId = null) }

            is ListContract.Intent.DetailClosed -> onDetailClosed(intent.id, intent.deleted)

            ListContract.Intent.MessageShown -> setState { copy(message = null) }
            ListContract.Intent.DetailOpened -> setState { copy(openDetailId = null) }
        }
    }

    /** 第一次加载：转圈盖满整页。 */
    private fun loadFirstPage() {
        setState {
            copy(status = ListContract.Status.Loading, failMessage = null, message = null)
        }
        viewModelScope.launch {
            try {
                val page = repository.loadPage(1)
                setState {
                    copy(
                        articles = page,
                        page = 1,
                        hasMore = page.size >= repository.pageSize,
                        status = ListContract.Status.Success,
                        moreStatus = ListContract.MoreStatus.Idle,
                        failMessage = null,
                    )
                }
            } catch (e: Exception) {
                setState {
                    copy(
                        status = ListContract.Status.Failed,
                        failMessage = e.message ?: "未知错误",
                    )
                }
            }
        }
    }

    /** 下拉刷新：保留旧数据，转圈在顶部；失败了也不把已有数据清掉。 */
    private fun refresh() {
        if (uiState.value.refreshing) return
        setState { copy(status = ListContract.Status.Refreshing, message = null) }
        viewModelScope.launch {
            try {
                val page = repository.loadPage(1)
                setState {
                    copy(
                        articles = page,
                        page = 1,
                        hasMore = page.size >= repository.pageSize,
                        status = ListContract.Status.Success,
                        moreStatus = ListContract.MoreStatus.Idle,
                        failMessage = null,
                        message = "刷新完成，共 ${page.size} 条",
                    )
                }
            } catch (e: Exception) {
                val stillHasData = uiState.value.articles.isNotEmpty()
                setState {
                    copy(
                        // 手里还有数据就照常显示，只提示一句；一条都没有才整页报错
                        status = if (stillHasData) ListContract.Status.Success else ListContract.Status.Failed,
                        failMessage = if (stillHasData) null else e.message,
                        message = if (stillHasData) "刷新失败：${e.message}" else null,
                    )
                }
            }
        }
    }

    /** 加载下一页：只动底部那一行的状态，失败也不清空已有数据。 */
    private fun loadMore() {
        val current = uiState.value
        if (current.moreStatus == ListContract.MoreStatus.Loading) return   // 已经在加载了，别重复请求
        if (!current.hasMore) return                                        // 没有下一页了
        if (current.status == ListContract.Status.Loading || current.status == ListContract.Status.Refreshing) return

        val nextPage = current.page + 1
        setState { copy(moreStatus = ListContract.MoreStatus.Loading) }
        viewModelScope.launch {
            try {
                val more = repository.loadPage(nextPage)
                setState {
                    copy(
                        articles = articles + more,
                        page = nextPage,
                        hasMore = more.size >= repository.pageSize,
                        moreStatus = if (more.size >= repository.pageSize) {
                            ListContract.MoreStatus.Idle
                        } else {
                            ListContract.MoreStatus.NoMore
                        },
                    )
                }
            } catch (e: Exception) {
                setState { copy(moreStatus = ListContract.MoreStatus.Failed) }
            }
        }
    }

    /** 确认删除：删完从列表里去掉，并提示一句。 */
    private fun confirmDelete() {
        val id = uiState.value.pendingDeleteId ?: return
        setState { copy(pendingDeleteId = null) }
        viewModelScope.launch {
            val ok = repository.delete(id)
            if (ok) {
                setState {
                    copy(
                        articles = articles.filterNot { it.id == id },
                        message = "已经删掉这一条",
                    )
                }
            } else {
                setState { copy(message = "删除失败，稍后再试") }
            }
        }
    }

    /** 从详情页回来：如果那边删掉了，这边也把它去掉。 */
    private fun onDetailClosed(id: Int, deleted: Boolean) {
        if (!deleted) return
        setState {
            copy(
                articles = articles.filterNot { it.id == id },
                message = "这一条已在详情页删掉",
            )
        }
    }
}
