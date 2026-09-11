package com.tscaffold.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tscaffold.core.ui.viewmodel.BaseViewModel
import com.tscaffold.core.ui.viewmodel.UiIntent
import com.tscaffold.core.ui.viewmodel.UiState

/**
 * 用 Compose 写页面时的一个小工具，和 XML 版的 `BaseActivity.observe()` 作用一样。
 *
 * 用法（示例见 component_business_basic 模块的 SearchActivity）：
 *
 * ```
 * val state by viewModel.observeState()   // 拿到页面状态，状态一变界面自动重画
 * ```
 *
 * 它内部用的是官方推荐的 `collectAsStateWithLifecycle()`：页面不可见时停止收集，
 * 不会在后台白干活。
 *
 * 状态里要是有"弹一次提示"这类内容（比如 `state.message`），
 * 界面显示完之后记得回报一句（例如 `setIntent(MessageShown)`），
 * 让 ViewModel 把它清掉 —— 这样状态永远如实反映屏幕上显示的东西。
 */
@Composable
fun <S : UiState, I : UiIntent> BaseViewModel<S, I>.observeState(): State<S> =
    uiState.collectAsStateWithLifecycle()
