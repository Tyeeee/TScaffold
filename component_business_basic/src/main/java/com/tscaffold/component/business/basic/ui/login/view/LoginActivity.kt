package com.tscaffold.component.business.basic.ui.login.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.component.business.basic.databinding.BusinessBasicActivityLoginBinding
import com.tscaffold.component.business.basic.ui.list.view.ListActivity
import com.tscaffold.component.business.basic.ui.login.contract.LoginContract
import com.tscaffold.component.business.basic.ui.login.viewmodel.LoginViewModel
import com.tscaffold.component.common.ui.activity.BaseActivity
import kotlinx.coroutines.launch

/**
 * 登录表单页 —— 用 **EditText + 错误提示 + 按钮禁用** 的完整形态。
 *
 * 这一页专门演示表单场景：
 * - 输入框每变一次，就上报一次"用户改了内容"，界面自己不判断对错；
 * - 错误提示、"能不能点提交"、"要不要转圈"全部从状态里读；
 * - 提交中按钮自动变灰（状态算出来的），连点两下也只会提交一次；
 * - 登录成功也不是"直接跳页面"，而是状态里的 `loggedIn` 变成真，界面看到才跳。
 */
class LoginActivity :
    BaseActivity<BusinessBasicActivityLoginBinding, LoginViewModel>(
        BusinessBasicActivityLoginBinding::inflate
    ) {

    override val viewModel: LoginViewModel by viewModels()

    private var shownMessage: String? = null

    /**
     * 是否已经跳过页面了。
     *
     * 为什么需要这个标记：`loggedIn` 会一直为真，而状态可能发射多次
     * （登录结果一次、"清掉提示"又一次）。如果每次都跳，就会开出两个一模一样的页面。
     * 所以跳转这件事只做一次。列表页那边用的是"状态里的 openDetailId + 回报 DetailOpened 清掉"，
     * 效果一样，都是为了保证"只做一次"。
     */
    private var navigated = false

    override fun initialize(savedInstanceState: Bundle?) {
        // 输入框只负责"把用户敲的内容报上去"，不判断对错
        viewBinding.etUsername.doAfterTextChanged { text ->
            viewModel.setIntent(LoginContract.Intent.UsernameChanged(text?.toString().orEmpty()))
        }
        viewBinding.etPassword.doAfterTextChanged { text ->
            viewModel.setIntent(LoginContract.Intent.PasswordChanged(text?.toString().orEmpty()))
        }
        viewBinding.btnSubmit.setOnClickListener {
            viewModel.setIntent(LoginContract.Intent.Submit)
        }
    }

    override fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 错误提示：状态里给什么就显示什么，没给就藏起来
                    showOrHide(viewBinding.tvUsernameError, state.usernameError)
                    showOrHide(viewBinding.tvPasswordError, state.passwordError)

                    // 能不能点、要不要转圈，都是状态说了算
                    viewBinding.btnSubmit.isEnabled = state.canSubmit
                    viewBinding.pbSubmitting.visibility =
                        if (state.submitting) View.VISIBLE else View.GONE

                    showMessageOnce(state.message)

                    // 登录成功：状态说成功，界面才跳；而且只跳一次
                    if (state.loggedIn && !navigated) {
                        navigated = true
                        ListActivity.start(this@LoginActivity)
                        finish()
                    }
                }
            }
        }
    }

    private fun showOrHide(view: View, text: String?) {
        view.visibility = if (text == null) View.GONE else View.VISIBLE
        if (view is android.widget.TextView) view.text = text
    }

    private fun showMessageOnce(message: String?) {
        if (message == null) {
            shownMessage = null
            return
        }
        if (message == shownMessage) return
        shownMessage = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        viewModel.setIntent(LoginContract.Intent.MessageShown)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}
