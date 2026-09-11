package com.tscaffold.component.business.basic.ui.task.data

import kotlinx.coroutines.delay

/** 一条任务。这就是列表里每一行显示的东西。 */
data class Task(
    val id: Int,
    val title: String,
    val done: Boolean = false,
)

/**
 * 数据从哪来 —— 现在的假数据源。
 *
 * 它做三件事，模拟真实网络请求的样子：
 * 1. 等 800 毫秒再返回（真实网络也要等）；
 * 2. 每次多给一条数据（这样反复点"重新加载"能看到列表变化）；
 * 3. **第 3 次调用故意报错**，用来演示"加载失败"这条分支，不然错误处理没法验证。
 *
 * 等接上 Retrofit 之后，把这里换成真正的接口调用即可，
 * 上面的 Contract 和 ViewModel 基本不用改 —— 这就是把"取数据"单独放一层的意义。
 */
class TaskRepository {

    private var loadTimes = 0

    suspend fun loadTasks(): List<Task> {
        delay(800)          // 假装在等网络
        loadTimes++

        if (loadTimes % 3 == 0) {
            error("网络开小差了（这是故意做的失败，用来演示出错时的样子）")
        }

        val all = listOf(
            Task(1, "看一遍 BaseViewModel 里那三样东西", done = true),
            Task(2, "照着这个示例写一个自己的页面", done = false),
            Task(3, "把界面里的业务判断挪进 handleIntent", done = false),
            Task(4, "给 ViewModel 写一个单元测试", done = false),
            Task(5, "加上 Retrofit 换成真接口", done = false),
        )
        // 第几次调用就给前几条，模拟"数据一点点变多"
        return all.take((loadTimes + 3).coerceAtMost(all.size))
    }
}
