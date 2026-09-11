package com.tscaffold.component.business.basic.ui.task.viewmodel

import androidx.lifecycle.viewModelScope
import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.data.TaskRepository
import com.tscaffold.component.common.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

/**
 * 任务列表的全部逻辑。三份界面共用它，界面里没有一行业务判断。
 *
 * 完整的数据流就是下面这样跑一圈的：
 *
 *   ① 页面打开 → setIntent(Load)              界面只要"上报"，不关心后面怎么走
 *   ② handleIntent 收到 → 改状态成"加载中"      界面立刻显示转圈
 *   ③ repository 去取数据（现在是假的，以后是 Retrofit）
 *   ④ 拿到了 → setState 把数据写进状态          界面自动重画成列表
 *      出错了 → setState 写进错误信息           界面自动显示错误和"重试"
 *   ⑤ 顺带 setEffect 发一条提示                 界面弹一次 Toast
 */
class TaskViewModel(
    // 现在是假的数据源；接上 Retrofit 后，这里换成真正的仓库实现即可
    private val repository: TaskRepository = TaskRepository(),
) : BaseViewModel<TaskContract.State, TaskContract.Intent, TaskContract.Effect>() {

    /** 页面刚打开时的样子：什么都还没有，也没开始加载。 */
    override fun initializeState(): TaskContract.State = TaskContract.State()

    init {
        // 一进页面就发起加载。注意这里也是走"上报操作"这条路，没有特殊通道。
        setIntent(TaskContract.Intent.Load)
    }

    /** 用户做了什么 → 对应做什么。整个页面只有这一个分叉口。 */
    override fun handleIntent(intent: TaskContract.Intent) {
        when (intent) {
            TaskContract.Intent.Load,
            TaskContract.Intent.Refresh -> load()

            is TaskContract.Intent.Toggle -> toggle(intent.id)

            TaskContract.Intent.ClearDone -> clearDone()
        }
    }

    /** ② ③ ④ ⑤ 全在这个方法里：改状态 → 取数据 → 再改状态 → 发提示。 */
    private fun load() {
        // 先告诉界面"我在加载"，并清掉上一次的错误信息
        setState {
            copy(
                loadStatus = TaskContract.LoadStatus.Loading,
                failMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val tasks = repository.loadTasks()

                setState {
                    copy(
                        tasks = tasks,
                        loadStatus = if (tasks.isEmpty()) {
                            TaskContract.LoadStatus.Empty
                        } else {
                            TaskContract.LoadStatus.Success
                        },
                    )
                }
                setEffect { TaskContract.Effect.ShowToast("加载完成，共 ${tasks.size} 条") }
            } catch (e: Exception) {
                setState {
                    copy(
                        loadStatus = TaskContract.LoadStatus.Failed,
                        failMessage = e.message ?: "未知错误",
                    )
                }
                setEffect { TaskContract.Effect.ShowToast("加载失败，点重试再来一次") }
            }
        }
    }

    /** 勾选 / 取消勾选：只在本地改状态，列表里那一条会自己变。 */
    private fun toggle(id: Int) {
        setState {
            copy(tasks = tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
        }
    }

    /** 清掉已完成的。一条都没有时不改状态，只弹个提示。 */
    private fun clearDone() {
        val doneCount = uiState.value.doneCount
        if (doneCount == 0) {
            setEffect { TaskContract.Effect.ShowToast("还没有完成的任务") }
            return
        }
        setState { copy(tasks = tasks.filterNot { it.done }) }
        setEffect { TaskContract.Effect.ShowToast("清掉了 $doneCount 条已完成的") }
    }
}
