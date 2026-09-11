package com.tscaffold.component.common.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.tscaffold.component.common.ui.viewmodel.BaseViewModel

/**
 * 用 XML 写页面时的 Fragment 基类，用法和 BaseActivity 几乎一样。
 *
 * 它替你做三件重复的事：
 * 1. 创建视图时按你给的写法加载 XML，并放进 [viewBinding]；
 * 2. 视图准备好之后调用你实现的两个方法；
 * 3. 视图销毁时把 [viewBinding] 清空，避免内存泄漏。
 *
 * 子类长这样（示例见 component_business_basic 的 TaskFragment）：
 *
 * ```
 * class TaskFragment :
 *     BaseFragment<BusinessBasicFragmentTaskBinding, TaskViewModel>(
 *         BusinessBasicFragmentTaskBinding::inflate
 *     ) {
 *
 *     // 想和 Activity 共用同一份数据就写 activityViewModels()，想自己独立一份就写 viewModels()
 *     override val viewModel: TaskViewModel by activityViewModels()
 *
 *     override fun initialize(savedInstanceState: Bundle?) { 绑定点击事件 }
 *
 *     override fun observe() { 订阅状态，画到界面上 }
 * }
 * ```
 */
abstract class BaseFragment<VB : ViewBinding, VM : BaseViewModel<*, *, *>>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
) : Fragment() {

    private var innerBinding: VB? = null

    /** 这一页的布局对象，XML 里的控件都用它取。视图销毁后不要再访问。 */
    protected val viewBinding: VB get() = requireNotNull(innerBinding) { "视图已经销毁，不能再访问 viewBinding" }

    /** 这一页的 ViewModel，由子类提供（一般写 `by viewModels()`）。 */
    protected abstract val viewModel: VM

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = inflate(inflater, container, false)
        innerBinding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize(savedInstanceState)
        observe()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 视图没了就把引用放掉，否则 Fragment 会一直拽着它导致内存泄漏。
        innerBinding = null
    }

    /** 视图第一次创建时执行：绑定点击事件、给控件设初值等。 */
    protected abstract fun initialize(savedInstanceState: Bundle?)

    /** 订阅 [viewModel] 的状态和事件，把结果画到界面上。 */
    protected abstract fun observe()
}
