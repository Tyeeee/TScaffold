package com.tscaffold.feature.ui.list.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tscaffold.feature.R
import com.tscaffold.feature.databinding.BusinessBasicActivityListBinding
import com.tscaffold.feature.ui.detail.compose.DetailActivity
import com.tscaffold.feature.ui.list.contract.ListContract
import com.tscaffold.feature.ui.list.viewmodel.ListViewModel
import com.tscaffold.core.ui.activity.BaseActivity
import kotlinx.coroutines.launch

/**
 * 列表页 —— 用 **BaseActivity + BaseFragment + RecyclerView** 的完整形态。
 *
 * 这一页专门演示"跟界面有关的、只做一次的动作"该怎么安排，一共三件：
 * 1. **弹一句提示** ← 状态里的 `message`
 * 2. **弹删除确认框** ← 状态里的 `pendingDeleteId`
 * 3. **打开详情页，并把结果带回来** ← 状态里的 `openDetailId` + 系统的 Activity 结果
 *
 * 这三件都只在 Activity 这一处做（列表本体不碰），做完分别回报一句把状态清掉。
 * 转屏或者从后台回来时，状态还在，所以这几件事不会丢、也不会重复做。
 */
class ListActivity :
    BaseActivity<BusinessBasicActivityListBinding, ListViewModel>(
        BusinessBasicActivityListBinding::inflate
    ) {

    override val viewModel: ListViewModel by viewModels()

    /** 上一次已经处理过的提示，避免同一句话弹两次。 */
    private var shownMessage: String? = null

    /** 正在显示的那个删除确认框（转屏时先关掉，重建后按状态再弹）。 */
    private var deleteDialog: AlertDialog? = null

    /** 正在显示确认框的那一条，用来避免同一时间弹两个。 */
    private var dialogForId: Int? = null

    /** 打开详情页，回来的时候拿到"有没有被删掉"。 */
    private val openDetail = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = lastOpenedDetailId ?: return@registerForActivityResult
        val deleted = result.data?.getBooleanExtra(DetailActivity.EXTRA_DELETED, false) ?: false
        viewModel.setIntent(ListContract.Intent.DetailClosed(id, deleted))
    }

    private var lastOpenedDetailId: Int? = null

    override fun initialize(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.list_fragment_container, ListFragment())
                .commit()
        }
    }

    override fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    showMessageOnce(state.message)
                    showDeleteDialogIfNeeded(state.pendingDeleteId)
                    openDetailIfNeeded(state.openDetailId)
                }
            }
        }
    }

    /** ① 状态里有话就弹一次，弹完回报一句把它清掉。 */
    private fun showMessageOnce(message: String?) {
        if (message == null) {
            shownMessage = null
            return
        }
        if (message == shownMessage) return
        shownMessage = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        viewModel.setIntent(ListContract.Intent.MessageShown)
    }

    /** ② 状态里说"要删某一条"就弹确认框；用户点了什么再回报给 ViewModel。 */
    private fun showDeleteDialogIfNeeded(pendingDeleteId: Int?) {
        if (pendingDeleteId == null) {
            deleteDialog?.dismiss()
            deleteDialog = null
            dialogForId = null
            return
        }
        if (dialogForId == pendingDeleteId) return      // 已经在显示了

        dialogForId = pendingDeleteId
        deleteDialog = AlertDialog.Builder(this)
            .setTitle(R.string.business_basic_list_delete_title)
            .setMessage(R.string.business_basic_list_delete_message)
            .setPositiveButton(R.string.business_basic_list_delete_ok) { _, _ ->
                viewModel.setIntent(ListContract.Intent.ConfirmDelete)
            }
            .setNegativeButton(R.string.business_basic_list_delete_cancel) { _, _ ->
                viewModel.setIntent(ListContract.Intent.CancelDelete)
            }
            .setOnCancelListener {
                viewModel.setIntent(ListContract.Intent.CancelDelete)
            }
            .create()
            .also {
                // 删除是不可逆的，手指滑到框外面不应该就把框关掉
                it.setCanceledOnTouchOutside(false)
                it.show()
            }
    }

    /** ③ 状态里说"要看某一条"就打开详情页，打开完回报一句。 */
    private fun openDetailIfNeeded(openDetailId: Int?) {
        if (openDetailId == null) return
        lastOpenedDetailId = openDetailId
        viewModel.setIntent(ListContract.Intent.DetailOpened)
        openDetail.launch(DetailActivity.createIntent(this, openDetailId))
    }

    companion object {
        /** 打开这个页面的统一入口。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, ListActivity::class.java))
        }
    }
}
