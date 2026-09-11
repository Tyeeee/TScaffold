package com.scaffold.component.basic.extensions

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

/**
 * 让"整个 App 共用一份 ViewModel"变得简单的小工具。
 *
 * 平时 `by viewModels()` 拿到的 ViewModel 是跟着页面走的：页面关掉就没了。
 * 如果你有多个页面要共用同一份数据（例如登录状态、购物车），
 * 就把状态放在这种 App 级的 ViewModel 里，哪个页面都能读到同一份。
 *
 * 用法（和系统自带的写法一样，只是名字多了 application 前缀）：
 *
 * ```
 * class LoginViewModel : BaseViewModel<...>() { ... }
 *
 * class SomeActivity : ComponentActivity() {
 *     private val loginViewModel: LoginViewModel by applicationViewModels()
 * }
 * ```
 */

/** 存放 App 级 ViewModel 的地方，生命周期跟随整个应用。 */
val applicationViewModelStore by lazy { ViewModelStore() }

/** 在 Activity 里拿 App 级的 ViewModel。 */
@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.applicationViewModels(
    noinline factoryProducer: () -> ViewModelProvider.Factory = { defaultViewModelProviderFactory }
): Lazy<VM> = createApplicationViewModelLazy(factoryProducer)

/** 在 Fragment 里拿 App 级的 ViewModel。 */
@MainThread
inline fun <reified VM : ViewModel> Fragment.applicationViewModels(
    noinline factoryProducer: () -> ViewModelProvider.Factory = { defaultViewModelProviderFactory }
): Lazy<VM> = createApplicationViewModelLazy(factoryProducer)

@MainThread
inline fun <reified VM : ViewModel> createApplicationViewModelLazy(
    noinline factoryProducer: () -> ViewModelProvider.Factory
) = ViewModelLazy(VM::class, { applicationViewModelStore }, factoryProducer)
