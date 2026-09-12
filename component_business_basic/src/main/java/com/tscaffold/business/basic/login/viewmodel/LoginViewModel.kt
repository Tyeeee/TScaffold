package com.tscaffold.business.basic.login.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tscaffold.business.basic.login.contract.LoginContract
import com.tscaffold.business.basic.login.data.AccountSource
import com.tscaffold.business.basic.login.data.FakeAccountSource
import com.tscaffold.common.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 登录页的逻辑。界面里一行校验都不写，全是状态算出来的。
 *
 * 处理了表单场景该有的三件事：
 * - **边输边校验**：输入变化就更新状态，错误提示自己会跟着变；
 * - **防重复提交**：提交中 [LoginContract.State.submitting] 为真，"能不能点"由状态算出来，
 *   按钮自动变灰；就算真的连点两下，[submit] 里也会挡住；
 * - **成功之后**：不直接跳页面，而是把 `loggedIn` 写进状态，界面看到才关掉自己。
 *
 * ## 依赖是接口，不是具体实现
 *
 * 它只认 [AccountSource]。默认给 [FakeAccountSource]，所以这一页**不接后端也能单独跑**；
 * 业务侧要换成真实现，用 [factory] 传进去即可，这个文件一行都不用改。
 */
class LoginViewModel(
    private val accountSource: AccountSource = FakeAccountSource,
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
                val ok = accountSource.login(username, password)
                setState {
                    if (ok) {
                        copy(submitting = false, loggedIn = true)
                    } else {
                        copy(submitting = false, message = "账号或密码不对")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setState { copy(submitting = false, message = "登录失败：${e.message}") }
            }
        }
    }

    companion object {

        /**
         * 给业务侧注入真实 [AccountSource] 用的工厂。
         *
         * ```
         * override val viewModel: LoginViewModel by viewModels {
         *     LoginViewModel.factory(MyAccountSource(api))
         * }
         * ```
         *
         * 不调它也行 —— 构造函数有默认值，普通 `by viewModels()` 会用 [FakeAccountSource]。
         */
        fun factory(accountSource: AccountSource): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(accountSource) }
        }
    }
}
