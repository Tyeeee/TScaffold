package com.demo.tscaffold.ui.list.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.tscaffold.R
import com.demo.tscaffold.model.Article
import com.demo.tscaffold.databinding.ItemArticleBinding
import com.demo.tscaffold.databinding.ItemListFooterBinding
import com.demo.tscaffold.ui.list.contract.ListContract

/**
 * 列表里的一行。有两种：正常的文章，以及最下面那一行"加载中 / 加载失败 / 没有更多了"。
 *
 * 为什么要专门定义一个类型：界面拿到的只有"状态"，
 * 所以"状态 → 要显示哪几行"这件事在界面里算一遍，算完交给列表控件去画。
 * 这也让"只刷新变化的那几行"成为可能（DiffUtil 会自己比对）。
 */
sealed interface ListRow {
    data class Item(val article: Article) : ListRow
    data class Footer(val status: ListContract.MoreStatus) : ListRow
}

/**
 * 列表控件用的适配器。
 *
 * - 点一条 → 上报"用户想看这条"
 * - 长按一条 → 上报"用户想删这条"（弹不弹确认框由 ViewModel 决定）
 * - 点底部那一行（只在加载失败时能点）→ 上报"重试"
 *
 * 这里不保存任何业务数据，数据变了就 submitList，DiffUtil 会算出哪几行要重画。
 */
class ListAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Unit,
    private val onFooterClick: () -> Unit,
) : ListAdapter<ListRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListRow.Item -> TYPE_ITEM
        is ListRow.Footer -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ITEM) {
            ItemHolder(ItemArticleBinding.inflate(inflater, parent, false))
        } else {
            FooterHolder(ItemListFooterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ListRow.Item -> (holder as ItemHolder).bind(row.article)
            is ListRow.Footer -> (holder as FooterHolder).bind(row.status)
        }
    }

    inner class ItemHolder(
        private val binding: ItemArticleBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            binding.tvTitle.text = article.title
            binding.tvSummary.text = article.summary
            binding.tvAuthor.text = article.author
            binding.root.setOnClickListener { onItemClick(article.id) }
            binding.root.setOnLongClickListener {
                onItemLongClick(article.id)
                true
            }
        }
    }

    inner class FooterHolder(
        private val binding: ItemListFooterBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(status: ListContract.MoreStatus) {
            when (status) {
                ListContract.MoreStatus.Loading -> {
                    binding.pbFooter.visibility = android.view.View.VISIBLE
                    binding.tvFooter.setText(R.string.list_loading_more)
                    binding.root.isClickable = false
                }

                ListContract.MoreStatus.Failed -> {
                    binding.pbFooter.visibility = android.view.View.GONE
                    binding.tvFooter.setText(R.string.list_more_failed)
                    binding.root.isClickable = true
                    binding.root.setOnClickListener { onFooterClick() }
                }

                ListContract.MoreStatus.NoMore -> {
                    binding.pbFooter.visibility = android.view.View.GONE
                    binding.tvFooter.setText(R.string.list_no_more)
                    binding.root.isClickable = false
                }

                ListContract.MoreStatus.Idle -> {
                    // 还能继续加载但还没滑到底：这一行先不显示内容
                    binding.pbFooter.visibility = android.view.View.GONE
                    binding.tvFooter.text = ""
                    binding.root.isClickable = false
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ListRow>() {
        override fun areItemsTheSame(oldItem: ListRow, newItem: ListRow): Boolean = when {
            oldItem is ListRow.Item && newItem is ListRow.Item -> oldItem.article.id == newItem.article.id
            oldItem is ListRow.Footer && newItem is ListRow.Footer -> true
            else -> false
        }

        override fun areContentsTheSame(oldItem: ListRow, newItem: ListRow): Boolean = oldItem == newItem
    }

    private companion object {
        const val TYPE_ITEM = 0
        const val TYPE_FOOTER = 1
    }
}
