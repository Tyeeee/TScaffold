package com.tscaffold.component.business.basic.ui.counter.contract

import com.tscaffold.component.common.ui.viewmodel.UiEffect
import com.tscaffold.component.common.ui.viewmodel.UiIntent
import com.tscaffold.component.common.ui.viewmodel.UiState

/**
 * "计数器"示例页面用到的三样东西。
 * 两份界面（XML 版 CounterActivity、Compose 版 CounterComposeActivity）共用这一个文件，
 * 说明一件事：**界面怎么写不影响逻辑，逻辑只写一遍。**
 */
class CounterContract {

    /**
     * 页面状态：界面上会变的东西都在这里。
     * 现在只有两个字段——数字是几、下面那行提示文字。
     */
    data class State(
        val count: Int = 0,
        val message: String = "点一下按钮试试",
    ) : UiState

    /** 用户操作：界面能发出来的三种动作。 */
    sealed interface Intent : UiIntent {
        /** 加一。 */
        data object Increase : Intent

        /** 减一。 */
        data object Decrease : Intent

        /** 归零。 */
        data object Reset : Intent
    }

    /** 一次性事件：做完就没了的动作。这里只有"弹一条提示"。 */
    sealed interface Effect : UiEffect {
        data class ShowToast(val text: String) : Effect
    }
}
