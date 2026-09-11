package com.tscaffold.component.business.basic.ui.task.contract

import com.tscaffold.component.business.basic.ui.task.data.Task
import com.tscaffold.component.common.ui.viewmodel.UiEffect
import com.tscaffold.component.common.ui.viewmodel.UiIntent
import com.tscaffold.component.common.ui.viewmodel.UiState

/**
 * "任务列表"这个示例页面的三样东西。
 *
 * 三份界面（XML 的 TaskActivity + TaskFragment、Compose 的 TaskComposeActivity）
 * 全都共用这一个文件，所以界面怎么写都不会影响逻辑。
 */
class TaskContract {

    /**
     * 页面状态：界面上会变的东西全在这里。
     *
     * 这里只存"最原始的那几样"，其余能从它们推出来的，一律用计算属性现算
     * （比如 [loading]、[total]、[doneCount]）。这样就不会出现"两个字段说的是一件事、
     * 结果某次改了其中一个忘了另一个"的情况 —— 状态永远只有一个说法。
     */
    data class State(
        val tasks: List<Task> = emptyList(),
        val loadStatus: LoadStatus = LoadStatus.Idle,
        val failMessage: String? = null,
    ) : UiState {

        /** 正在加载。直接由 [loadStatus] 推出来，不另外存一份。 */
        val loading: Boolean get() = loadStatus == LoadStatus.Loading

        /** 一共几条（顶部统计用）。 */
        val total: Int get() = tasks.size

        /** 完成了几条（顶部统计用）。 */
        val doneCount: Int get() = tasks.count { it.done }
    }

    /** 列表走到哪一步了。界面靠它决定显示转圈、空页面还是错误提示。 */
    enum class LoadStatus {
        Idle,     // 还没开始
        Loading,  // 正在加载
        Success,  // 加载成功，有数据
        Empty,    // 加载成功，但一条都没有
        Failed,   // 加载失败
    }

    /** 用户操作：界面只负责报上来。 */
    sealed interface Intent : UiIntent {
        /** 加载（第一次进页面、或失败后点重试）。 */
        data object Load : Intent

        /** 重新加载。 */
        data object Refresh : Intent

        /** 勾选 / 取消勾选某一条。 */
        data class Toggle(val id: Int) : Intent

        /** 把已完成的一次清掉。 */
        data object ClearDone : Intent
    }

    /** 一次性事件：弹一次提示，做完就没了。 */
    sealed interface Effect : UiEffect {
        data class ShowToast(val message: String) : Effect
    }
}
