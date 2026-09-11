package com.tscaffold.component.common.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ============================ 这套写法怎么理解 ============================
 *
 * 一页界面被拆成三样东西，各走各的路，互不干扰：
 *
 * 1. State（页面状态）—— "这一页现在长什么样"。
 *    比如计数器页面的状态就是"现在数字是几"。它永远有一个最新值，
 *    界面每次拿到新值就把自己重画一遍。用 StateFlow 承载。
 *
 * 2. Intent（用户操作）—— "用户在这一页做了什么"。
 *    比如"点了加号""下拉刷新了"。界面只负责把动作报上来，
 *    具体怎么处理由 ViewModel 决定。用 SharedFlow 承载。
 *
 * 3. Effect（一次性事件）—— "只需要做一次的事"。
 *    比如"弹一次提示""跳到另一个页面"。这类事做完就没了、不能重来，
 *    所以用只发一次的 Channel 承载，谁拿走就没有了。
 *
 * 数据是单向流动的：
 *
 *     界面 --(用户操作 Intent)--> ViewModel --(新的 State)------> 界面重新画
 *                                       \--(一次性事件 Effect)--> 界面弹提示/跳页
 *
 * 好处：界面只管"显示"和"上报操作"，不写任何业务判断；
 * 所有逻辑都收在 ViewModel 里，可以单独写测试，不用启动模拟器。
 * =========================================================================
 */

/** 页面状态的标记接口。你写的每个页面的状态类都要实现它。 */
interface UiState

/** 用户操作的标记接口。你写的每个页面的操作类都要实现它。 */
interface UiIntent

/** 一次性事件的标记接口。你写的每个页面的事件类都要实现它。 */
interface UiEffect

/**
 * 所有页面 ViewModel 的基类。
 *
 * 你只需要回答两个问题：
 * - [initializeState]：这一页刚打开时，状态是什么？
 * - [handleIntent]：用户做了某个操作，状态该怎么变？
 *
 * 想改状态就调用 [setState]；想弹提示、跳页面就调用 [setEffect]。
 */
abstract class BaseViewModel<State : UiState, Intent : UiIntent, Effect : UiEffect> : ViewModel() {

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(initializeState())

    /** 页面状态：界面订阅它，拿到新值就把自己重画一遍。 */
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _uiIntent: MutableSharedFlow<Intent> = MutableSharedFlow()

    /** 用户操作流：界面不用订阅，基类内部会自己收。 */
    val uiIntent: SharedFlow<Intent> = _uiIntent.asSharedFlow()

    private val _uiEffect: Channel<Effect> = Channel(Channel.BUFFERED)

    /** 一次性事件流：界面订阅它，用来弹提示、跳页面。 */
    val uiEffect = _uiEffect.receiveAsFlow()

    /** 这一页刚打开时的初始状态。 */
    protected abstract fun initializeState(): State

    /** 用户做了一个操作之后要干什么，全写在这里。 */
    protected abstract fun handleIntent(intent: Intent)

    init {
        // 把"用户操作"这条线接通：收到一个操作就交给 handleIntent 处理。
        viewModelScope.launch {
            uiIntent.collect { handleIntent(it) }
        }
    }

    /**
     * 更新页面状态。
     * 用法：`setState { copy(count = count + 1) }`
     * 意思是"拿旧状态改一个字段，其余字段照旧，返回一份新的"。
     */
    protected fun setState(reduce: State.() -> State) {
        _uiState.update(reduce)
    }

    /** 界面上报一个用户操作。 */
    fun setIntent(intent: Intent) {
        viewModelScope.launch {
            _uiIntent.emit(intent)
        }
    }

    /** 发一个一次性事件，比如弹提示、跳页面。 */
    fun setEffect(builder: () -> Effect) {
        viewModelScope.launch {
            _uiEffect.send(builder())
        }
    }
}
