package com.tscaffold.business.basic.login.view

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.business.basic.databinding.BusinessBasicActivityLoginBinding
import com.tscaffold.business.basic.login.contract.LoginContract
import com.tscaffold.business.basic.login.viewmodel.LoginViewModel
import com.tscaffold.common.ui.activity.BaseActivity
import kotlinx.coroutines.launch

/**
 * 登录页 —— 业务基础页面，放在 `component_business_basic` 里给各个业务复用。
 *
 * 用 **EditText + 错误提示 + 按钮禁用** 的完整形态：
 * - 输入框每变一次就上报一次"用户改了内容"，界面自己不判断对错；
 * - 错误提示、"能不能点提交"、"要不要转圈"全部从状态里读；
 * - 提交中按钮自动变灰（状态算出来的），连点两下也只会提交一次；
 * - 登录成功不是"直接跳页面"，而是状态里的 `loggedIn` 变成真，界面看到才关掉自己。
 *
 * ## 它不知道下一个页面是谁（这点很重要）
 *
 * 作为**可复用**的基础页面，它绝不能写死"登录成功就跳某某页" ——
 * 那是调用方的决定，而且这个模块是 library，压根不该认识 app 里的页面。
 *
 * 所以约定是：
 * - **成功** → `setResult(RESULT_OK)` 然后 `finish()`；
 * - **用户返回/取消** → 系统默认的 `RESULT_CANCELED`。
 *
 * 调用方拿着结果自己决定去哪儿：
 * ```
 * val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
 *     if (result.resultCode == Activity.RESULT_OK) { …跳首页… }
 * }
 * launcher.launch(LoginActivity.intent(context))
 * ```
 *
 * ## 换掉假的账号实现
 *
 * 默认用 `FakeAccountSource`（`admin` / `123456`）所以能独立跑。
 * 接真后端时改成：
 * ```
 * override val viewModel: LoginViewModel by viewModels {
 *     LoginViewModel.factory(MyAccountSource(api))
 * }
 * ```
 */
class LoginActivity :
    BaseActivity<BusinessBasicActivityLoginBinding, LoginViewModel>(
        BusinessBasicActivityLoginBinding::inflate
    ) {

    override val viewModel: LoginViewModel by viewModels()

    private var shownMessage: String? = null

    /**
     * 是否已经回报过结果了。
     *
     * 为什么需要这个标记：`loggedIn` 会一直为真，而状态可能发射多次
     * （登录结果一次、"清掉提示"又一次）。如果每次都回报，就会 `finish()` 两次、
     * 或者在某些机型上开出两个页面。所以这件事只做一次。
     */
    private var finished = false

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

                    // 登录成功：把结果回给调用方，然后关掉自己。去哪由调用方决定。
                    if (state.loggedIn && !finished) {
                        finished = true
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }

    private fun showOrHide(view: View, text: String?) {
        view.visibility = if (text == null) View.GONE else View.VISIBLE
        if (view is TextView) view.text = text
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

        /**
         * 调用方启动这一页用它，别自己拼 Intent。
         *
         * 要拿登录结果就配合 `ActivityResultLauncher`（见类注释）。
         */
        fun intent(context: Context): Intent = Intent(context, LoginActivity::class.java)
    }
}
