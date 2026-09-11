package com.tscaffold.component.business.basic.ui.task.view

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.component.business.basic.R
import com.tscaffold.component.business.basic.databinding.BusinessBasicFragmentTaskBinding
import com.tscaffold.component.business.basic.databinding.BusinessBasicItemTaskBinding
import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.data.Task
import com.tscaffold.component.business.basic.ui.task.viewmodel.TaskViewModel
import com.tscaffold.component.common.ui.fragment.BaseFragment
import kotlinx.coroutines.launch

/**
 * 任务列表页的"列表那一块" —— 这里是 **BaseFragment** 真正被用起来的例子。
 *
 * 它和 TaskActivity 用同一个 ViewModel（`by activityViewModels()`），
 * 所以顶部统计和这里显示的列表天然一致。
 *
 * 分工和 Activity 商量好了：
 * - 状态（列表、加载中、错误）→ 这里订阅并画出来；
 * - 状态里的那句提示（`state.message`）→ 交给 Activity 弹，这里不碰，免得两边抢着弹。
 */
class TaskFragment :
    BaseFragment<BusinessBasicFragmentTaskBinding, TaskViewModel>(
        BusinessBasicFragmentTaskBinding::inflate
    ) {

    /** 注意这里用的是 activityViewModels()，拿的是 Activity 里那一份，不是自己新建一份。 */
    override val viewModel: TaskViewModel by activityViewModels()

    override fun initialize(savedInstanceState: Bundle?) {
        viewBinding.btnClearDone.setOnClickListener {
            viewModel.setIntent(TaskContract.Intent.ClearDone)
        }
        viewBinding.btnRetry.setOnClickListener {
            viewModel.setIntent(TaskContract.Intent.Load)
        }
    }

    override fun observe() {
        // 用 viewLifecycleOwner：Fragment 的视图没了就自动停下来，不会往已经销毁的界面上画东西
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /** 把状态画成界面。这里只有"怎么显示"，没有"该不该显示"，判断全在 ViewModel 里。 */
    private fun render(state: TaskContract.State) {
        // 加载中 → 转圈
        viewBinding.pbLoading.visibility = if (state.loading) View.VISIBLE else View.GONE

        // 有数据 → 画列表
        renderList(state.tasks)

        // 空 / 出错 → 显示一行说明；出错时额外给一个"重试"按钮
        val tip = when (state.loadStatus) {
            TaskContract.LoadStatus.Empty -> getString(R.string.business_basic_task_empty)
            TaskContract.LoadStatus.Failed -> state.failMessage ?: getString(R.string.business_basic_task_failed)
            else -> ""
        }
        viewBinding.tvTip.text = tip
        viewBinding.tvTip.visibility = if (tip.isEmpty()) View.GONE else View.VISIBLE
        viewBinding.btnRetry.visibility =
            if (state.loadStatus == TaskContract.LoadStatus.Failed) View.VISIBLE else View.GONE
    }

    /**
     * 画列表。
     *
     * 这里用的是最直白的做法：每次把旧的清掉、按数据重新加一遍。
     * 数据条数多的时候要换成 RecyclerView（那是"你自己要做的组件"之一），
     * 但对跑通数据流来说，这样写最容易看懂。
     */
    private fun renderList(tasks: List<Task>) {
        viewBinding.llTaskList.removeAllViews()
        tasks.forEach { task ->
            val item = BusinessBasicItemTaskBinding.inflate(layoutInflater, viewBinding.llTaskList, false)
            item.cbTask.text = task.title
            item.cbTask.isChecked = task.done
            // 用点击而不是"选中状态变化"来上报，避免代码改状态时又把事件弹回去
            item.cbTask.setOnClickListener { viewModel.setIntent(TaskContract.Intent.Toggle(task.id)) }
            viewBinding.llTaskList.addView(item.root)
        }
    }
}
