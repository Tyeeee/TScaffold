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
 * 关于"弹提示"：按官方文档的做法，ViewModel 不往界面发事件，而是把要说的那句话放进状态
 * （`state.message`）。界面看到状态里有这句话就弹出来，弹完回报一个 [TaskContract.Intent.MessageShown]，
 * ViewModel 收到把它清掉。所以**提示只在这一处处理**（Fragment 不碰），避免两边抢着弹。
 */
class TaskActivity :
    BaseActivity<BusinessBasicActivityTaskBinding, TaskViewModel>(
        BusinessBasicActivityTaskBinding::inflate
    ) {

    override val viewModel: TaskViewModel by viewModels()

    /** 上一次已经弹过的那句话，用来避免同一条提示弹两次。 */
    private var shownMessage: String? = null

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
        // repeatOnLifecycle：页面可见时才接收，退到后台就自动停下来，不会白干活；
        // 后台期间产生的提示也留在状态里，回到前台照样会弹出来，不会丢。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    viewBinding.tvSummary.text = getString(
                        R.string.business_basic_task_summary,
                        state.doneCount,
                        state.total,
                    )
                    showMessageOnce(state.message)
                }
            }
        }
    }

    /** 状态里出现了"要弹一次"的话，就弹掉，并回报给 ViewModel 把状态清干净。 */
    private fun showMessageOnce(message: String?) {
        if (message == null) {
            // 已经被清掉了，记一笔重置，下次出现同样的一句话还能再弹
            shownMessage = null
            return
        }
        if (message == shownMessage) return
        shownMessage = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        viewModel.setIntent(TaskContract.Intent.MessageShown)
    }

    companion object {
        /** 打开这个页面的统一入口。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, TaskActivity::class.java))
        }
    }
}
