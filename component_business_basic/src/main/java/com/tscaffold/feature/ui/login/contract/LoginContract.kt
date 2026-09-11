package com.tscaffold.feature.ui.login.contract

import com.tscaffold.core.ui.viewmodel.UiIntent
import com.tscaffold.core.ui.viewmodel.UiState

/**
 * 表单页（登录）的状态和操作。
 *
 * 这一页专门演示**表单**这类场景，重点在两处：
 * 1. 错误提示是**算出来的**（见下面的 [usernameError]），不是另存一份。
 *    少存一份，就不会出现"改了输入框，错误提示没跟着变"的经典 bug。
 * 2. "能不能点提交"也是算出来的（[canSubmit]）：正在提交时自动变灰，
 *    用户连点两下也不会提交两次。
 */
class LoginContract {

    data class State(
        val username: String = "",
        val password: String = "",
        /** 正在提交中（按钮变灰、转圈）。 */
        val submitting: Boolean = false,
        /** 登录成功了。界面看到它就跳到下一个页面。 */
        val loggedIn: Boolean = false,
        /** 要提示用户的一句话。 */
        val message: String? = null,
    ) : UiState {

        /** 账号哪里不对。还没开始输就不报错，免得一进来就满屏红字。 */
        val usernameError: String?
            get() = when {
                username.isEmpty() -> null
                username.length < 3 -> "账号至少 3 个字符"
                else -> null
            }

        /** 密码哪里不对。 */
        val passwordError: String?
            get() = when {
                password.isEmpty() -> null
                password.length < 6 -> "密码至少 6 位"
                else -> null
            }

        /** 能不能点提交。三个条件都从上面的字段算出来，不另存。 */
        val canSubmit: Boolean
            get() = username.length >= 3 && password.length >= 6 && !submitting
    }

    sealed interface Intent : UiIntent {
        data class UsernameChanged(val value: String) : Intent
        data class PasswordChanged(val value: String) : Intent

        /** 点了提交。 */
        data object Submit : Intent

        /** 界面把 [State.message] 显示完了。 */
        data object MessageShown : Intent
    }
}
