package com.tscaffold.common.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private data class TestState(
        val count: Int = 0,
        /** 要弹给用户看的一句话。按官方做法，这类内容也放在状态里。 */
        val message: String? = null,
    ) : UiState

    private sealed interface TestIntent : UiIntent {
        data object Add : TestIntent
        data object Notify : TestIntent
        data object MessageShown : TestIntent
    }

    /**
     * 注意这个类故意把"初始状态"写成了依赖自己的构造参数 [startCount]。
     * 这是使用者最容易写出来的代码，也是基类的构造顺序最容易踩的坑。
     */
    private class TestViewModel(private val startCount: Int) :
        BaseViewModel<TestState, TestIntent>() {

        override fun initializeState(): TestState = TestState(count = startCount)

        override fun handleIntent(intent: TestIntent) {
            when (intent) {
                TestIntent.Add -> setState { copy(count = count + 1) }
                TestIntent.Notify -> setState { copy(message = "收到") }
                TestIntent.MessageShown -> setState { copy(message = null) }
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
    fun `要弹一次的话放进状态里，界面回报一句就清掉了`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 0)

        // ① ViewModel 把"要提示的话"写进状态，界面会看到它
        viewModel.setIntent(TestIntent.Notify)
        advanceUntilIdle()
        assertEquals("收到", viewModel.uiState.value.message)

        // ② 界面弹完提示，回报一句，状态回到"屏幕上没有这句话"
        viewModel.setIntent(TestIntent.MessageShown)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `状态是热的，后订阅的人也能立刻拿到当前值`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 3)
        viewModel.setIntent(TestIntent.Add)
        advanceUntilIdle()

        // 界面中途才开始订阅（比如转屏后重建），照样能立刻拿到最新状态
        assertEquals(4, viewModel.uiState.first().count)
    }

    @Test
    fun `连着上报同一个操作两次，两次都会被处理`() = runTest(dispatcher) {
        val viewModel = TestViewModel(startCount = 0)

        // 界面上连点两下同一个按钮，上报的是同一个操作对象。
        // 这条必须成立：如果哪天把"操作"改成用 StateFlow 存，它会自带"相同值不重复发射"，
        // 第二次点击就会被悄悄吞掉，界面看起来像卡了一下。
        viewModel.setIntent(TestIntent.Add)
        viewModel.setIntent(TestIntent.Add)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.count)
    }

    @Test
    fun `打开调试日志后，能打出收到的操作和状态变化`() = runTest(dispatcher) {
        val lines = mutableListOf<String>()
        MviLog.printer = { lines += it }
        try {
            val viewModel = TestViewModel(startCount = 0)
            viewModel.setIntent(TestIntent.Add)
            advanceUntilIdle()
        } finally {
            MviLog.printer = {}      // 用完关掉，免得影响别的测试
        }

        assertTrue("应该打出收到的操作：$lines", lines.any { it.contains("收到操作") && it.contains("Add") })
        assertTrue("应该打出状态变化：$lines", lines.any { it.contains("状态") && it.contains("count=1") })
        // 名字必须是这个页面自己的类名，不能是协程之类的名字（踩过一次）
        assertTrue("日志里应该是页面的名字：$lines", lines.all { it.contains("TestViewModel") })
    }
}
