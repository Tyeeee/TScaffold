package com.scaffold.component.common.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import com.scaffold.component.common.ui.viewmodel.BaseViewModel
import com.scaffold.component.common.ui.viewmodel.UiEffect
import com.scaffold.component.common.ui.viewmodel.UiIntent
import com.scaffold.component.common.ui.viewmodel.UiState

/**
 * 用 Compose 写页面时的两个小工具，和 XML 版的 BaseActivity.observe() 作用一样。
 *
 * 用法（示例见 component_business_basic 的 CounterComposeActivity）：
 *
 * ```
 * val state by viewModel.observeState()        // 拿到页面状态，状态一变界面自动重画
 * viewModel.observeEffect { effect -> ... }     // 处理弹提示、跳页面这类一次性事件
 * ```
 */

/** 订阅页面状态。状态变了，用到它的界面会自动重新画。 */
@Composable
fun <S : UiState, I : UiIntent, E : UiEffect> BaseViewModel<S, I, E>.observeState(): State<S> =
    uiState.collectAsStateWithLifecycle()

/**
 * 订阅一次性事件。
 *
 * 这里只在页面"可见"的时候接收（页面被切到后台就不再收），
 * 避免用户看不见的时候突然弹提示、跳页面。
 *
 * @param key 换一个 key 就会重新开始接收，一般不用传。
 */
@Composable
fun <S : UiState, I : UiIntent, E : UiEffect> BaseViewModel<S, I, E>.observeEffect(
    key: Any? = Unit,
    onEffect: (E) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key, lifecycleOwner) {
        uiEffect
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .collect { onEffect(it) }
    }
}
