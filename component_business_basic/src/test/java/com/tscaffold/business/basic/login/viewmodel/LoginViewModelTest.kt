package com.tscaffold.business.basic.login.viewmodel

import com.tscaffold.business.basic.login.contract.LoginContract
import com.tscaffold.business.basic.login.data.AccountSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 登录页逻辑的测试 —— 全部在电脑上跑，不用模拟器、不用后端。
 *
 * 这正是"依赖 [AccountSource] 接口而不是具体实现"换来的好处：
 * 想让它成功就成功、想让它失败就失败，还能数一数**到底调了几次**
 * （防重复提交那条就是靠这个验证的）。
 *
 * 另外注意：**这些测试一条都没碰界面**。校验规则、"能不能点提交"这些
 * 最容易出错的地方全在 [LoginContract.State] 里，测起来是纯函数。
 *
 * ## ⚠️ 为什么每次 setIntent 之后都要推进调度器
 *
 * [com.tscaffold.common.ui.viewmodel.BaseViewModel] 是这么接线的：
 *
 * ```
 * fun setIntent(intent) { viewModelScope.launch { uiIntent.emit(intent) } }   // 投递
 * init { viewModelScope.launch { uiIntent.collect { handleIntent(it) } } }    // 处理
 * ```
 *
 * —— `setIntent` 只是**把操作丢进 Flow**，真正处理它的是另一个协程。
 * `StandardTestDispatcher` 又是"排好队等你发话才跑"的，所以：
 *
 * - `advanceUntilIdle()`：把排队的活全跑完（包括虚拟时间里的 `delay`）；
 * - `runCurrent()`：只跑到"当前时刻能跑的"，用来观察**中间态**（比如"提交中"）。
 *
 * 不推进就断言，读到的一定是旧状态 —— 这一点踩过一次，写在这儿免得下次再踩。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * 可控的假账号源：能指定成败、能数调用次数、能抛异常。
     *
     * 里面那个 `delay` 不是装饰：它让登录"真的挂起一下"，
     * 这样测试才能停在"提交中"这个中间态上做断言（见防重复提交那条）。
     */
    private class FakeAccountSource(
        private val result: Boolean = true,
        private val delayMillis: Long = 10,
    ) : AccountSource {
        var callCount = 0
        var lastUsername: String? = null
        var throwOnLogin: Exception? = null

        override suspend fun login(username: String, password: String): Boolean {
            callCount++
            lastUsername = username
            delay(delayMillis)
            throwOnLogin?.let { throw it }
            return result
        }
    }

    private fun viewModel(source: AccountSource) = LoginViewModel(source)

    /** 把账号密码填好并让状态落定，供"提交"类的用例复用。 */
    private fun LoginViewModel.fillIn(username: String, password: String) {
        setIntent(LoginContract.Intent.UsernameChanged(username))
        setIntent(LoginContract.Intent.PasswordChanged(password))
    }

    // ==================== 校验规则：都是"算出来的" ====================

    @Test
    fun `刚进页面：不报错、也不能提交`() = runTest(dispatcher) {
        val state = viewModel(FakeAccountSource()).uiState.value

        assertNull("还没输就不该报错，否则一进来就满屏红字", state.usernameError)
        assertNull(state.passwordError)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `账号太短会算出错误，改长了错误自己就没了`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource())

        vm.setIntent(LoginContract.Intent.UsernameChanged("ab"))
        advanceUntilIdle()
        assertEquals("账号至少 3 个字符", vm.uiState.value.usernameError)

        vm.setIntent(LoginContract.Intent.UsernameChanged("abc"))
        advanceUntilIdle()
        assertNull("错误提示是算出来的，输入一变它就该跟着变", vm.uiState.value.usernameError)
    }

    @Test
    fun `密码太短会算出错误`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource())

        vm.setIntent(LoginContract.Intent.PasswordChanged("12345"))
        advanceUntilIdle()
        assertEquals("密码至少 6 位", vm.uiState.value.passwordError)

        vm.setIntent(LoginContract.Intent.PasswordChanged("123456"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.passwordError)
    }

    @Test
    fun `两个都合法才能提交`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource())

        vm.setIntent(LoginContract.Intent.UsernameChanged("admin"))
        advanceUntilIdle()
        assertFalse("密码还没填，不能提交", vm.uiState.value.canSubmit)

        vm.setIntent(LoginContract.Intent.PasswordChanged("123456"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSubmit)
    }

    // ==================== 提交 ====================

    @Test
    fun `登录成功：loggedIn 变真，并把账号原样传给数据源`() = runTest(dispatcher) {
        val source = FakeAccountSource(result = true)
        val vm = viewModel(source)

        vm.fillIn("admin", "123456")
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loggedIn)
        assertFalse(vm.uiState.value.submitting)
        assertEquals("admin", source.lastUsername)
    }

    @Test
    fun `登录失败：loggedIn 保持假，给出提示`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource(result = false))

        vm.fillIn("admin", "wrongpass")
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        assertFalse("失败不能算登录成功", vm.uiState.value.loggedIn)
        assertFalse(vm.uiState.value.submitting)
        assertEquals("账号或密码不对", vm.uiState.value.message)
    }

    @Test
    fun `数据源抛异常：转成一句提示，不能崩`() = runTest(dispatcher) {
        val source = FakeAccountSource().apply {
            throwOnLogin = IllegalStateException("网络断了")
        }
        val vm = viewModel(source)

        vm.fillIn("admin", "123456")
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loggedIn)
        assertEquals("登录失败：网络断了", vm.uiState.value.message)
    }

    // ==================== 防重复提交 ====================

    @Test
    fun `提交中连点两下：只发一次请求`() = runTest(dispatcher) {
        val source = FakeAccountSource(result = true)
        val vm = viewModel(source)

        vm.fillIn("admin", "123456")
        advanceUntilIdle()

        vm.setIntent(LoginContract.Intent.Submit)
        // 只跑到"当前时刻能跑的"：提交开始了，但卡在数据源的 delay 里 —— 正好是"提交中"
        runCurrent()
        assertTrue("点了就该进入提交中", vm.uiState.value.submitting)
        assertFalse("提交中不能再点", vm.uiState.value.canSubmit)

        // 用户手快又点了一下
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        assertEquals("连点两下也只会发一次请求", 1, source.callCount)
        assertTrue(vm.uiState.value.loggedIn)
    }

    @Test
    fun `状态不合法时点提交：一个请求都不发`() = runTest(dispatcher) {
        val source = FakeAccountSource()
        val vm = viewModel(source)

        vm.setIntent(LoginContract.Intent.UsernameChanged("ab"))
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        assertEquals(0, source.callCount)
        assertFalse(vm.uiState.value.submitting)
    }

    // ==================== 提示的清理 ====================

    @Test
    fun `输入一变就把上一次的提示清掉`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource(result = false))

        vm.fillIn("admin", "wrongpass")
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()
        assertEquals("账号或密码不对", vm.uiState.value.message)

        vm.setIntent(LoginContract.Intent.UsernameChanged("admin2"))
        advanceUntilIdle()
        assertNull("开始改了，旧提示就该消失", vm.uiState.value.message)
    }

    @Test
    fun `界面显示完提示后回报一下，提示被清掉`() = runTest(dispatcher) {
        val vm = viewModel(FakeAccountSource(result = false))

        vm.fillIn("admin", "wrongpass")
        vm.setIntent(LoginContract.Intent.Submit)
        advanceUntilIdle()

        vm.setIntent(LoginContract.Intent.MessageShown)
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)
    }
}
