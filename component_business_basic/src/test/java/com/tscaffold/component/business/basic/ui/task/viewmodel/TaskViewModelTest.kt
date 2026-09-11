package com.tscaffold.component.business.basic.ui.task.viewmodel

import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
    fun `加载完成后会弹一次提示，而且只弹一次`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        val effect = viewModel.uiEffect.first()

        assertTrue(effect is TaskContract.Effect.ShowToast)
        assertTrue((effect as TaskContract.Effect.ShowToast).message.startsWith("加载完成"))
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
        assertNotNull(state.failMessage)
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
    fun `清掉已完成会移除这些条目，并弹一次提示`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.uiEffect.first()                       // 先把"加载完成"那条提示取走
        val doneCount = viewModel.uiState.value.doneCount
        assertTrue(doneCount > 0)

        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.doneCount)
        val effect = viewModel.uiEffect.first()
        assertTrue((effect as TaskContract.Effect.ShowToast).message.contains("清掉"))
    }

    @Test
    fun `没有已完成的任务时，只弹提示，数据一个字都不改`() = runTest(dispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        viewModel.uiEffect.first()                       // 取走"加载完成"
        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()
        viewModel.uiEffect.first()                       // 取走"清掉了 x 条"
        val tasksNow = viewModel.uiState.value.tasks

        viewModel.setIntent(TaskContract.Intent.ClearDone)
        advanceUntilIdle()

        assertEquals(tasksNow, viewModel.uiState.value.tasks)
        val effect = viewModel.uiEffect.first()
        assertTrue((effect as TaskContract.Effect.ShowToast).message.contains("还没有"))
    }
}
