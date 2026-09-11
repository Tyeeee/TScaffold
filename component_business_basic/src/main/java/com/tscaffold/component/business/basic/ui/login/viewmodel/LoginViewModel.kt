package com.tscaffold.component.business.basic.ui.login.viewmodel

import androidx.lifecycle.viewModelScope
import com.tscaffold.component.business.basic.data.AccountRepository
import com.tscaffold.component.business.basic.ui.login.contract.LoginContract
import com.tscaffold.component.common.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 登录表单的逻辑。界面里一行校验都不写，全是状态算出来的。
 *
 * 演示三个表单场景该有的处理：
 * - **边输边校验**：输入变化就更新状态，错误提示自己会跟着变；
 * - **防重复提交**：提交中 [LoginContract.State.submitting] 为真，
 *   "能不能点"由状态算出来，按钮自动变灰；就算真的连点两下，handleIntent 里也会挡住；
 * - **成功之后**：不直接跳页面，而是把 `loggedIn` 写进状态，界面看到才跳。
 */
class LoginViewModel(
    private val repository: AccountRepository = AccountRepository,
) : BaseViewModel<LoginContract.State, LoginContract.Intent>() {

    override fun initializeState(): LoginContract.State = LoginContract.State()

    override fun handleIntent(intent: LoginContract.Intent) {
        when (intent) {
            is LoginContract.Intent.UsernameChanged ->
                setState { copy(username = intent.value, message = null) }

            is LoginContract.Intent.PasswordChanged ->
                setState { copy(password = intent.value, message = null) }

            LoginContract.Intent.Submit -> submit()

            LoginContract.Intent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun submit() {
        // 两道防线：状态说不能提交就别动；已经在提交了也别重复发请求
        if (!uiState.value.canSubmit || uiState.value.submitting) return

        val (username, password) = uiState.value.let { it.username to it.password }
        setState { copy(submitting = true, message = null) }

        viewModelScope.launch {
            try {
                val ok = repository.login(username, password)
                setState {
                    if (ok) {
                        copy(submitting = false, loggedIn = true, message = "登录成功")
                    } else {
                        copy(submitting = false, message = "账号或密码不对（试试 admin / 123456）")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(submitting = false, message = "登录失败：${e.message}") }
            }
        }
    }
}
