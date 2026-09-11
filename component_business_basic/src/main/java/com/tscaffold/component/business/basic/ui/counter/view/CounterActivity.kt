package com.tscaffold.component.business.basic.ui.counter.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.component.business.basic.databinding.BusinessBasicActivityCounterBinding
import com.tscaffold.component.business.basic.ui.counter.contract.CounterContract
import com.tscaffold.component.business.basic.ui.counter.viewmodel.CounterViewModel
import com.tscaffold.component.common.ui.activity.BaseActivity
import kotlinx.coroutines.launch

/**
 * 计数器示例 —— 用 XML 布局写的版本。
 *
 * 请对照着看这个文件的三段：
 * 1. [initialize]：界面上的按钮被点到时，只上报"用户做了什么"（setIntent），不做任何计算；
 * 2. [observe]：把 ViewModel 里的状态画到界面上，并处理弹提示这类一次性事件；
 * 3. [CounterViewModel]：全部逻辑都在那儿，这里没有一行 if/else 的业务判断。
 *
 * Compose 版在同一个包外面的 ...counter.compose.CounterComposeActivity，
 * 两边的逻辑完全相同，只是界面写法不一样。
 */
class CounterActivity :
    BaseActivity<BusinessBasicActivityCounterBinding, CounterViewModel>(
        BusinessBasicActivityCounterBinding::inflate
    ) {

    /** 这一页的 ViewModel。系统会自动帮你创建并保存，页面转屏也不会丢状态。 */
    override val viewModel: CounterViewModel by viewModels()

    override fun initialize(savedInstanceState: Bundle?) {
        // 三个按钮只负责"上报操作"，不负责计算。
        viewBinding.btnIncrease.setOnClickListener {
            viewModel.setIntent(CounterContract.Intent.Increase)
        }
        viewBinding.btnDecrease.setOnClickListener {
            viewModel.setIntent(CounterContract.Intent.Decrease)
        }
        viewBinding.btnReset.setOnClickListener {
            viewModel.setIntent(CounterContract.Intent.Reset)
        }
    }

    override fun observe() {
        // repeatOnLifecycle：页面可见时才接收，页面退到后台就自动停下来，不会白干活。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 第一条线：状态变了就重画界面。
                launch {
                    viewModel.uiState.collect { state ->
                        viewBinding.tvCount.text = state.count.toString()
                        viewBinding.tvMessage.text = state.message
                    }
                }
                // 第二条线：一次性事件，弹提示、跳页面。
                launch {
                    viewModel.uiEffect.collect { effect ->
                        when (effect) {
                            is CounterContract.Effect.ShowToast ->
                                Toast.makeText(
                                    this@CounterActivity,
                                    effect.text,
                                    Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 打开这个页面的统一入口，别的地方只要 `CounterActivity.start(context)` 就行。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, CounterActivity::class.java))
        }
    }
}
