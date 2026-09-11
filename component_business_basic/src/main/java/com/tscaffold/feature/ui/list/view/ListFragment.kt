package com.tscaffold.feature.ui.list.view

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tscaffold.feature.R
import com.tscaffold.feature.databinding.BusinessBasicFragmentListBinding
import com.tscaffold.feature.ui.list.contract.ListContract
import com.tscaffold.feature.ui.list.viewmodel.ListViewModel
import com.tscaffold.core.ui.fragment.BaseFragment
import kotlinx.coroutines.launch

/**
 * 列表本体 —— 用 **RecyclerView + 下拉刷新** 的完整形态。
 *
 * 这里只做两件事：
 * 1. 把状态画出来：转圈、空、失败、列表、底部那一行，全部由状态决定；
 * 2. 把用户的操作报上去：点一条、长按一条、下拉、滑到底、点底部重试。
 *
 * 弹提示、弹确认框、打开详情页这三件事不在这个文件里，交给 [ListActivity]。
 * 原因是它们都属于"只做一次"的动作，放在一处处理才不会重复做。
 */
class ListFragment :
    BaseFragment<BusinessBasicFragmentListBinding, ListViewModel>(
        BusinessBasicFragmentListBinding::inflate
    ) {

    override val viewModel: ListViewModel by activityViewModels()

    private lateinit var adapter: ListAdapter

    override fun initialize(savedInstanceState: Bundle?) {
        adapter = ListAdapter(
            onItemClick = { id -> viewModel.setIntent(ListContract.Intent.Click(id)) },
            onItemLongClick = { id -> viewModel.setIntent(ListContract.Intent.LongClick(id)) },
            onFooterClick = { viewModel.setIntent(ListContract.Intent.LoadMore) },
        )
        viewBinding.rvList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.rvList.adapter = adapter

        // 下拉刷新
        viewBinding.swipeRefresh.setOnRefreshListener {
            viewModel.setIntent(ListContract.Intent.Refresh)
        }

        // 快滑到底的时候自动加载下一页
        viewBinding.rvList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lastVisible = (recyclerView.layoutManager as LinearLayoutManager)
                    .findLastVisibleItemPosition()
                val total = adapter.itemCount
                if (total > 0 && lastVisible >= total - 2) {
                    viewModel.setIntent(ListContract.Intent.LoadMore)
                }
            }
        })

        // 整页失败时中间那个"重试"
        viewBinding.btnRetry.setOnClickListener {
            viewModel.setIntent(ListContract.Intent.Load)
        }
    }

    override fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /** 把状态画成界面：这里只有"怎么显示"，没有"该不该显示"。 */
    private fun render(state: ListContract.State) {
        // 下拉刷新的转圈：由状态决定什么时候收起来
        viewBinding.swipeRefresh.isRefreshing = state.refreshing

        // 第一次进来的整页转圈
        viewBinding.pbFirstLoading.visibility = if (state.firstLoading) View.VISIBLE else View.GONE

        // 中间的提示：空列表 或 整页失败
        val centerTip = when {
            state.empty -> getString(R.string.business_basic_list_empty)
            state.failed -> state.failMessage ?: getString(R.string.business_basic_list_failed)
            else -> null
        }
        viewBinding.groupCenterTip.visibility = if (centerTip == null) View.GONE else View.VISIBLE
        viewBinding.tvCenterTip.text = centerTip.orEmpty()
        viewBinding.btnRetry.visibility = if (state.failed) View.VISIBLE else View.GONE

        // 列表：状态 → 要显示哪几行，然后交给适配器
        val rows = buildList {
            state.articles.forEach { add(ListRow.Item(it)) }
            if (state.articles.isNotEmpty()) add(ListRow.Footer(state.moreStatus))
        }
        adapter.submitList(rows)
    }
}
