package com.tscaffold.core.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.tscaffold.core.ui.viewmodel.BaseViewModel

/**
 * 用 XML 写页面时的 Activity 基类。
 *
 * 它替你做三件重复的事，你只写自己的部分：
 * 1. 按你给的写法把 XML 布局加载出来，并放进 [viewBinding]，不用再写 findViewById；
 * 2. 在 onCreate 里按顺序调用你实现的两个方法；
 * 3. 把 [viewModel] 的位置留给你，通常一行 `by viewModels()` 就够了。
 *
 * 子类长这样（示例见 component_business_basic 模块的 ListActivity）：
 *
 * ```
 * class ListActivity :
 *     BaseActivity<BusinessBasicActivityListBinding, ListViewModel>(
 *         BusinessBasicActivityListBinding::inflate
 *     ) {
 *
 *     override val viewModel: ListViewModel by viewModels()   // 这一行系统帮你管好
 *
 *     override fun initialize(savedInstanceState: Bundle?) { 绑定点击事件 }
 *
 *     override fun observe() { 订阅 uiState，把状态画到界面上 }
 * }
 * ```
 *
 * @param inflate 怎么把 XML 变成 ViewBinding，直接写 `XxxBinding::inflate` 即可。
 */
abstract class BaseActivity<VB : ViewBinding, VM : BaseViewModel<*, *>>(
    private val inflate: (LayoutInflater) -> VB,
) : AppCompatActivity() {

    /** 这一页的布局对象，XML 里的控件都用它取，例如 viewBinding.tvCount。 */
    protected lateinit var viewBinding: VB
        private set

    /** 这一页的 ViewModel，由子类提供（一般写 `by viewModels()`）。 */
    protected abstract val viewModel: VM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = inflate(layoutInflater)
        setContentView(viewBinding.root)
        initialize(savedInstanceState)
        observe()
    }

    /** 页面第一次创建时执行：绑定点击事件、给控件设初值等。 */
    protected abstract fun initialize(savedInstanceState: Bundle?)

    /** 订阅 [viewModel] 的状态和事件，把结果画到界面上。 */
    protected abstract fun observe()
}
