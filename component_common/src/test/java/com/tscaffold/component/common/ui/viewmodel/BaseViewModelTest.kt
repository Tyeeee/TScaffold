package com.tscaffold.component.common.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 这些测试直接跑在电脑上（./gradlew test），不需要模拟器，一秒不到就跑完。
 * 它们验证的是 [BaseViewModel] 这套写法本身的行为，不是某个具体页面。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // 单元测试里没有手机的主线程，用测试调度器顶上，否则 viewModelScope 用不了
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 一个专门用来测基类的最小页面 ====================

    private data class TestState(val count: Int = 0) : UiState

    private sealed interface TestIntent : UiIntent {
        data object Add : TestIntent
        data object Notify : TestIntent
    }

    private sealed interface TestEffect : UiEffect {
        data class Toast(val text: String) : TestEffect
    }

    /**
     * 注意这个类故意把"初始状态"写成了依赖自己的构造参数 [startCount]。
     * 这是使用者最容易写出来的代码，也是基类的构造顺序最容易踩的坑。
     */
    private class TestViewModel(private val startCount: Int) :
        BaseViewModel<TestState, TestIntent, TestEffect>() {

        override fun initializeState(): TestState = TestState(count = startCount)

        override fun handleIntent(intent: TestIntent) {
            when (intent) {
                TestIntent.Add -> setState { copy(count = count + 1) }
                TestIntent.Notify -> setEffect { TestEffect.Toast("收到") }
            }
        }
    }

    // ==================== 用例 ====================

    @Test
    fun `初始状态可以放心用子类自己的构造参数`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 5)

        assertEquals(5, viewModel.uiState.value.count)
    }

    @Test
    fun `上报操作后会被 handleIntent 收到，状态跟着变`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 0)

        viewModel.setIntent(TestIntent.Add)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `状态是换一份新的，不是就地改老的`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 0)
        val before = viewModel.uiState.value

        viewModel.setIntent(TestIntent.Add)
        advanceUntilIdle()

        assertEquals(0, before.count)                 // 老的那份没被动过
        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `一次性事件取走一次就没了，不会重复`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 0)

        viewModel.setIntent(TestIntent.Notify)
        advanceUntilIdle()

        assertEquals(TestEffect.Toast("收到"), viewModel.uiEffect.first())
        // 再想取一次就取不到了 —— 这正是"弹一次提示只弹一次"的保证
        assertNull(withTimeoutOrNull(100) { viewModel.uiEffect.first() })
    }

    @Test
    fun `状态是热的，后订阅的人也能立刻拿到当前值`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 3)
        viewModel.setIntent(TestIntent.Add)
        advanceUntilIdle()

        // 界面中途才开始订阅（比如转屏后重建），照样能立刻拿到最新状态
        assertEquals(4, viewModel.uiState.first().count)
    }
}
