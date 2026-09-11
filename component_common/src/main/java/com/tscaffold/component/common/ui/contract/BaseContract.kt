package com.tscaffold.component.common.ui.contract

import com.tscaffold.component.common.ui.viewmodel.UiEffect
import com.tscaffold.component.common.ui.viewmodel.UiIntent
import com.tscaffold.component.common.ui.viewmodel.UiState

/**
 * 页面约定的模板文件（照着抄一份，改成你自己页面的内容就行）。
 *
 * 把一个页面的 State / Intent / Effect 写在一起，好处是打开一个文件就能看懂这一页：
 * - 这一页有哪些东西会变？（看 State 的字段）
 * - 用户能对这一页做哪些操作？（看 Intent 的分支）
 * - 这一页会做哪些"只做一次"的事？（看 Effect 的分支）
 *
 * 注意：这个类不参与运行，它只是一份模板。你完全可以删掉它，换成自己的页面约定。
 */
class BaseContract {

    /**
     * 页面状态：界面上每一处变化都来自这里。
     * 用 data class 是因为要能 `copy(...)` —— 改一个字段，其余字段照旧。
     */
    data class State(
        val title: String = "",
        val loading: Boolean = false,
        val list: List<String> = emptyList(),
        val loadStatus: LoadStatus = LoadStatus.Idle,
    ) : UiState

    /** 列表当前处在哪一步（界面通常靠它决定显示转圈、空页面还是错误提示）。 */
    enum class LoadStatus {
        Idle,     // 还没开始加载
        Loading,  // 正在加载
        Success,  // 加载成功，有数据
        Empty,    // 加载成功，但没数据
        Failed,   // 加载失败
    }

    /** 用户操作：界面只负责"报上来"，怎么处理由 ViewModel 决定。 */
    sealed interface Intent : UiIntent {
        /** 第一次加载。 */
        data object Load : Intent

        /** 重新加载（下拉刷新）。 */
        data object Refresh : Intent

        /** 点了列表里的某一项。 */
        data class OnItemClick(val position: Int) : Intent
    }

    /** 一次性事件：弹提示、跳页面这类只做一次的事。 */
    sealed interface Effect : UiEffect {
        data class ShowToast(val message: String) : Effect

        data object Back : Effect
    }
}
