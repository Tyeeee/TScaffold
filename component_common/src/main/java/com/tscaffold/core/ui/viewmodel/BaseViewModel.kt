package com.tscaffold.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ====================== 这套写法怎么理解（对照安卓官方文档） ======================
 *
 * 官方文档里没有 "MVI" 这个说法，它讲的叫**单向数据流（UDF）**，原话是
 * "The pattern where the state flows down and the events flow up is called a
 * unidirectional data flow (UDF)"（状态往下流、事件往上走）。
 * 出处：developer.android.com/topic/architecture/ui-layer
 *
 * 官方对它的解释是四句话：
 * 1. ViewModel 持有并对外暴露 UI 状态，界面订阅它；
 * 2. 界面把用户的操作告诉 ViewModel；
 * 3. ViewModel 处理这些操作，更新状态；
 * 4. 更新后的状态再回到界面，界面照着画一遍，如此循环。
 *
 * 所以这里只有两条通道：
 *
 *   界面 ──上报操作(Intent)──► ViewModel ──算出新状态(State)──► 界面重新画一遍
 *
 * 注意**没有第三条"一次性事件"通道**。官方在架构推荐里明确写着：
 * "Do not send events from the ViewModel to the UI."（强推荐）
 * 意思是：像"弹一句提示"这种事，不要从 ViewModel 里"发事件"给界面，
 * 而是把它变成状态的一部分 —— 状态里放一句 [提示文字]，界面显示完再回报一句"显示过了"，
 * ViewModel 收到后把这句话清掉。
 *
 * 官方给的理由是：**即使这句话转瞬即逝，UI 状态也要在每一刻都如实反映屏幕上显示的东西**。
 * 这么做还有个实际好处：转屏、从后台回来之后，这句话不会丢、也不会重复弹。
 *
 * 位置：androidx.lifecycle 的 ViewModel，业务逻辑写在这里，界面只负责显示。
 * ============================================================================
 */

/** 页面状态的标记接口。你写的每个页面的状态类都要实现它。 */
interface UiState

/** 用户操作的标记接口。你写的每个页面的操作类都要实现它。 */
interface UiIntent

/**
 * 所有页面 ViewModel 的基类。
 *
 * 你只需要回答两个问题：
 * - [initializeState]：这一页刚打开时，状态是什么？
 * - [handleIntent]：用户做了某个操作，状态该怎么变？
 *
 * 想改状态就调用 [setState]。
 */
abstract class BaseViewModel<State : UiState, Intent : UiIntent> : ViewModel() {

    /**
     * 页面状态存在这里。
     *
     * 为什么用 `by lazy` 而不是直接 `MutableStateFlow(initializeState())`：
     * 基类的初始化**早于**子类构造参数的赋值。如果在这行直接调 initializeState()，
     * 子类里一旦写了依赖自己构造参数的初始状态（例如 `initializeState() = State(repo.title)`），
     * 读到的就是还没赋值的默认值 —— 对象类型会直接空指针，数字类型会静默变成 0，非常难查。
     * 改成 lazy 之后，initializeState() 会推迟到真正用到状态时才执行，那时子类早就构造完了。
     */
    private val mutableState: MutableStateFlow<State> by lazy { MutableStateFlow(initializeState()) }

    /**
     * 页面状态：界面订阅它，拿到新值就把自己重画一遍。
     *
     * 对外只给"只读"的这一份，外界改不了；而且它永远保存着当前值，
     * 所以转屏之后界面重建、中途才来订阅，也能立刻拿到最新状态。
     */
    val uiState: StateFlow<State> get() = mutableState

    /** 用户操作流：界面不用管它，基类内部会自己收。 */
    private val uiIntent = MutableSharedFlow<Intent>()

    /**
     * 打日志时用的名字，取子类的类名，一眼能看出是哪个页面。
     *
     * 写成 getter 而不是属性：直接在协程的 lambda 里写 `javaClass` 会取到协程对象
     * （打出来是 StandaloneCoroutine 这种名字，不是页面的名字），必须显式取本类的运行时类型。
     */
    private val logName: String get() = javaClass.simpleName

    /** 这一页刚打开时的初始状态。 */
    protected abstract fun initializeState(): State

    /** 用户做了一个操作之后要干什么，全写在这里。 */
    protected abstract fun handleIntent(intent: Intent)

    init {
        // 把"用户操作"这条线接通：收到一个操作就交给 handleIntent 处理。
        viewModelScope.launch {
            uiIntent.collect { intent ->
                // 调试用：把收到的每个操作打出来（没打开日志就什么都不做）
                MviLog.print { "【$logName】收到操作: $intent" }
                handleIntent(intent)
            }
        }
    }

    /**
     * 更新页面状态。
     * 用法：`setState { copy(count = count + 1) }`
     * 意思是"拿旧状态改一个字段，其余字段照旧，返回一份新的"。
     */
    protected fun setState(reduce: State.() -> State) {
        val before = mutableState.value
        mutableState.update(reduce)
        val after = mutableState.value
        if (before != after) {
            // 调试用：把每次状态变化打出来，一眼能看出是哪一步把状态改成这样的
            MviLog.print { "【$logName】状态: $before -> $after" }
        }
    }

    /** 界面上报一个用户操作。 */
    fun setIntent(intent: Intent) {
        viewModelScope.launch {
            uiIntent.emit(intent)
        }
    }
}
