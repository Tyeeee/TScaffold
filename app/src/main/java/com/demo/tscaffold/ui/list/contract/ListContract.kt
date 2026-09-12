package com.demo.tscaffold.ui.list.contract

import com.demo.tscaffold.model.Article
import com.tscaffold.common.ui.viewmodel.UiIntent
import com.tscaffold.common.ui.viewmodel.UiState

/**
 * 列表页（分页 + 下拉刷新 + 加载更多 + 删除确认）的状态和操作。
 *
 * 这一页的状态比前一个示例复杂，因为它要同时表达好几件事：
 * - 整页处在哪一步（首次加载 / 刷新中 / 成功 / 失败）
 * - 底部那一行处在哪一步（加载中 / 加载失败 / 没有更多了）
 * - 有没有一句话要提示用户
 * - 有没有一条正在等用户确认删除
 * - 有没有一条要打开详情页
 *
 * 注意最后两项：**"要弹的话""要打开的页面"都放在状态里**，界面看到就去做，
 * 做完回报一句把状态清掉。这样转屏、从后台回来都不会丢、也不会重复做。
 */
class ListContract {

    data class State(
        val articles: List<Article> = emptyList(),
        /** 已经加载到第几页。 */
        val page: Int = 0,
        /** 还有没有下一页。 */
        val hasMore: Boolean = true,
        /** 整页的状态。 */
        val status: Status = Status.Idle,
        /** 底部那一行的状态。 */
        val moreStatus: MoreStatus = MoreStatus.Idle,
        /** 整页失败的原因。 */
        val failMessage: String? = null,
        /** 要提示用户的一句话，界面显示完回报 [Intent.MessageShown]。 */
        val message: String? = null,
        /** 正在等用户确认删除的那一条。不为空时界面弹确认框。 */
        val pendingDeleteId: Int? = null,
        /** 要打开详情页的那一条。不为空时界面去打开，打开完回报 [Intent.DetailOpened]。 */
        val openDetailId: Int? = null,
    ) : UiState {

        /** 第一次进来还在转圈（此时列表是空的）。 */
        val firstLoading: Boolean get() = status == Status.Loading && articles.isEmpty()

        /** 下拉刷新的转圈（此时列表里有旧数据，转圈在顶部）。 */
        val refreshing: Boolean get() = status == Status.Refreshing

        /** 加载成功但一条都没有。 */
        val empty: Boolean get() = status == Status.Success && articles.isEmpty()

        /** 整页失败（第一次就失败，或者刷新失败且没有旧数据）。 */
        val failed: Boolean get() = status == Status.Failed

        /** 列表能不能滑到底部继续加载。 */
        val canLoadMore: Boolean get() = hasMore && moreStatus != MoreStatus.Loading
    }

    /** 整页处在哪一步。 */
    enum class Status {
        Idle,        // 还没开始
        Loading,     // 第一次加载（转圈盖满整页）
        Refreshing,  // 下拉刷新（转圈在顶部，列表还在）
        Success,     // 有数据了
        Failed,      // 失败了
    }

    /** 底部那一行处在哪一步。 */
    enum class MoreStatus {
        Idle,     // 什么都没发生
        Loading,  // 正在加载下一页
        Failed,   // 加载下一页失败，点一下可以重试
        NoMore,   // 没有更多了
    }

    /** 用户操作：界面只负责报上来。 */
    sealed interface Intent : UiIntent {
        /** 第一次加载。 */
        data object Load : Intent

        /** 下拉刷新。 */
        data object Refresh : Intent

        /** 滑到底部，加载下一页。 */
        data object LoadMore : Intent

        /** 点了一条，要看详情。 */
        data class Click(val id: Int) : Intent

        /** 长按一条，要删除（先弹确认框）。 */
        data class LongClick(val id: Int) : Intent

        /** 确认框里点了"删除"。 */
        data object ConfirmDelete : Intent

        /** 确认框里点了"取消"。 */
        data object CancelDelete : Intent

        /** 详情页看了回来（从详情页删掉的话，要把这一条从列表里也去掉）。 */
        data class DetailClosed(val id: Int, val deleted: Boolean) : Intent

        /** 界面把 [State.message] 显示完了。 */
        data object MessageShown : Intent

        /** 界面把详情页打开完了。 */
        data object DetailOpened : Intent
    }
}
