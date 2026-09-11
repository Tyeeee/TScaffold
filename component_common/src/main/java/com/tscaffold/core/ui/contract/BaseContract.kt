package com.tscaffold.core.ui.contract

import com.tscaffold.core.ui.viewmodel.UiIntent
import com.tscaffold.core.ui.viewmodel.UiState

/**
 * 页面约定的模板文件（照着抄一份，改成你自己页面的内容就行）。
 *
 * 把一个页面的状态和操作写在一起，好处是打开一个文件就能看懂这一页：
 * - 这一页有哪些东西会变？（看 State 的字段）
 * - 用户能对这一页做哪些操作？（看 Intent 的分支）
 *
 * 注意：这个类不参与运行，它只是一份模板。你完全可以删掉它，换成自己的页面约定。
 */
class BaseContract {

    /**
     * 页面状态：界面上每一处变化都来自这里。
     *
     * 两条规矩：
     * 1. 用 data class，这样能 `copy(...)` —— 改一个字段，其余字段照旧；
     * 2. 能从别的字段算出来的，就别再存一份（见下面的 [loading]），
     *    免得两个字段说的是一件事、改了一处忘了另一处。
     */
    data class State(
        val list: List<String> = emptyList(),
        val loadStatus: LoadStatus = LoadStatus.Idle,
        /** 要弹给用户看的一句话。界面显示完之后要回报 [Intent.MessageShown] 把它清掉。 */
        val message: String? = null,
    ) : UiState {

        /** 正在加载。直接由 [loadStatus] 推出来，不另外存一份。 */
        val loading: Boolean get() = loadStatus == LoadStatus.Loading
    }

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

        /**
         * 界面上把 [State.message] 那句话显示完了，回报一句。
         * ViewModel 收到后把 message 清成空，这样它不会重复弹。
         */
        data object MessageShown : Intent
    }
}
