package com.tscaffold.component.business.basic.ui.task.viewmodel

import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "任务列表"这个示例的数据流测试。跑在电脑上，不用模拟器。
 *
 * 这些用例做的事情，和你在界面上点来点去是一模一样的：
 * 上报操作 → 等异步任务跑完 → 检查状态变成什么样了。
 * 区别只是这里能精确断言，而且仓库那 800 毫秒的等待是"虚拟时间"，不用真等。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TaskViewModel(TaskRepository())

    /** 模拟界面：看到状态里有提示就"弹"掉，然后回报一句 —— 和真界面的做法一致。 */
    private fun TaskViewModel.consumeMessage() {
        setIntent(TaskContract.Intent.MessageShown)
    }

    @Test
    fun `一进页面就自动加载，加载完是有数据的成功状态`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TaskContract.LoadStatus.Success, state.loadStatus)
        assertTrue(state.tasks.isNotEmpty())
        assertFalse(state.loading)
        assertNull(state.failMessage)
    }

    @Test
    fun `等待数据的那段时间里，状态显示成加载中`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        // 只让"不用等"的活儿跑完，仓库那 800 毫秒还没过去
        runCurrent()

        assertEquals(TaskContract.LoadStatus.Loading, viewModel.uiState.value.loadStatus)
        assertTrue(viewModel.uiState.value.loading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `加载完成后状态里带着一句提示，界面回报之后就被清掉`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        // ① 状态里有话要提示
        val message = viewModel.uiState.value.message
        assertNotNull(message)
        assertTrue(message!!.startsWith("加载完成"))

        // ② 界面弹完，回报一句，状态清干净
        viewModel.consumeMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `连着加载三次，第三次会失败并带上原因`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()                                                   // 第 1 次：成功

        viewModel.setIntent(TaskContract.Intent.Refresh)
        advanceUntilIdle()                                                   // 第 2 次：成功

        viewModel.setIntent(TaskContract.Intent.Refresh)
        advanceUntilIdle()                                                   // 第 3 次：假仓库故意报错

        val state = viewModel.uiState.value
        assertEquals(TaskContract.LoadStatus.Failed, state.loadStatus)
        assertNotNull(state.failMessage)        // 错误原因会一直显示在页面上
        assertNotNull(state.message)            // 同时还有一句要提示的话
        assertFalse(state.loading)
        // 失败时不要把已经拿到的数据清空，界面还能接着显示旧列表
        assertTrue(state.tasks.isNotEmpty())
    }

    @Test
    fun `失败之后点重试能恢复正常`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.setIntent(TaskContract.Intent.Refresh)
        advanceUntilIdle()
        viewModel.setIntent(TaskContract.Intent.Refresh)
        advanceUntilIdle()
        assertEquals(TaskContract.LoadStatus.Failed, viewModel.uiState.value.loadStatus)

        viewModel.setIntent(TaskContract.Intent.Load)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TaskContract.LoadStatus.Success, state.loadStatus)
        assertNull(state.failMessage)          // 错误信息要被清掉，不能一直挂在界面上
    }

    @Test
    fun `勾选某一条只影响那一条，其余条目和条数都不变`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val before = viewModel.uiState.value.tasks
        val target = before.first { !it.done }

        viewModel.setIntent(TaskContract.Intent.Toggle(target.id))
        advanceUntilIdle()

        val after = viewModel.uiState.value.tasks
        assertEquals(before.size, after.size)
        assertTrue(after.first { it.id == target.id }.done)
        assertEquals(
            before.filterNot { it.id == target.id },
            after.filterNot { it.id == target.id },
        )
    }

    @Test
    fun `清掉已完成会移除这些条目，状态里带着一句提示`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        val doneCount = viewModel.uiState.value.doneCount
        assertTrue(doneCount > 0)

        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.doneCount)
        assertTrue(state.message!!.contains("清掉"))
    }

    @Test
    fun `没有已完成的任务时，只多出一句提示，数据一个字都不改`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()
        viewModel.consumeMessage()                       // 界面把"清掉了 x 条"弹掉
        advanceUntilIdle()
        val tasksNow = viewModel.uiState.value.tasks

        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tasksNow, state.tasks)              // 数据没变
        assertTrue(state.message!!.contains("还没有"))    // 只是多了一句提示
    }
}
