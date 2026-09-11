package com.tscaffold.component.business.basic.ui.task.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.component.business.basic.R
import com.tscaffold.component.business.basic.databinding.BusinessBasicActivityTaskBinding
import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.viewmodel.TaskViewModel
import com.tscaffold.component.common.ui.activity.BaseActivity
import kotlinx.coroutines.launch

/**
 * 任务列表页 —— 这里是"骨架"里 **BaseActivity** 真正被用起来的例子。
 *
 * 这一页只干两件事：
 * 1. 顶部显示一条统计（已完成几条 / 共几条），以及底部的"重新加载"按钮；
 * 2. 中间那一块塞一个 [TaskFragment]（列表部分在 Fragment 里，见那个文件）。
 *
 * 两个页面元素和 Fragment 用的是**同一个 ViewModel**：
 * Activity 用 `by viewModels()` 建出来，Fragment 用 `by activityViewModels()` 拿它那一份。
 * 所以顶部统计和下面的列表永远是同一份数据，不会各显示各的。
 *
 * 还有一条约定：**一次性事件（弹提示）只在 Activity 这一层处理**。
 * 如果 Activity 和 Fragment 都去收 uiEffect，一条提示会被两边抢着消费，出现"有时候弹有时候不弹"的怪现象。
 * 记住：状态可以多处订阅，事件只在一处处理。
 */
class TaskActivity :
    BaseActivity<BusinessBasicActivityTaskBinding, TaskViewModel>(
        BusinessBasicActivityTaskBinding::inflate
    ) {

    override val viewModel: TaskViewModel by viewModels()

    override fun initialize(savedInstanceState: Bundle?) {
        // 只在第一次创建时塞 Fragment，转屏后系统会自己恢复，别再塞一次
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.task_fragment_container, TaskFragment())
                .commit()
        }

        // 按钮只上报"用户点了重新加载"，怎么加载是 ViewModel 的事
        viewBinding.btnRefresh.setOnClickListener {
            viewModel.setIntent(TaskContract.Intent.Refresh)
        }
    }

    override fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 第一条线：状态变了，重画顶部统计
                launch {
                    viewModel.uiState.collect { state ->
                        viewBinding.tvSummary.text = getString(
                            R.string.business_basic_task_summary,
                            state.doneCount,
                            state.total,
                        )
                    }
                }
                // 第二条线：一次性事件，弹提示（只在这一处收）
                launch {
                    viewModel.uiEffect.collect { effect ->
                        when (effect) {
                            is TaskContract.Effect.ShowToast ->
                                Toast.makeText(this@TaskActivity, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 打开这个页面的统一入口。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, TaskActivity::class.java))
        }
    }
}
