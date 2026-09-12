package com.demo.tscaffold.ui.detail.compose

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.demo.tscaffold.model.Article
import com.demo.tscaffold.ui.detail.contract.DetailContract
import com.demo.tscaffold.ui.detail.viewmodel.DetailViewModel
import com.tscaffold.common.ui.compose.observeState

/**
 * 详情页 —— Compose 写的，专门演示两件事：
 *
 * 1. **参数怎么进来**：外面用 [createIntent] 把 id 带上，进来后作为一个"操作"报给 ViewModel。
 * 2. **结果怎么回去**：用户在这里点删除，删成功后带着 `RESULT_OK` 关掉自己，
 *    列表页收到结果，把这一条从列表里也去掉。
 *
 * 关掉自己这件事也是由**状态**驱动的：删成功 → 状态里 `deleted = true` → 界面看到就收工。
 * 不是"删完直接 finish"，所以不会出现"状态还没更新界面就走了"的情况。
 */
class DetailActivity : ComponentActivity() {

    private val viewModel: DetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ① 参数从外面的 Intent 里取出来，作为一次"操作"报给 ViewModel
        val id = intent.getIntExtra(EXTRA_ID, 0)
        viewModel.setIntent(DetailContract.Intent.Load(id))

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.observeState()
                    val context = LocalContext.current

                    // 状态里有话就弹一次，弹完回报一句让它清掉
                    LifecycleResumeEffect(state.message) {
                        state.message?.let {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            viewModel.setIntent(DetailContract.Intent.MessageShown)
                        }
                        onPauseOrDispose { }
                    }

                    // ② 状态说"删掉了"，就带着结果收工
                    LaunchedEffect(state.deleted) {
                        if (state.deleted) {
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(EXTRA_DELETED, true),
                            )
                            finish()
                        }
                    }

                    DetailScreen(
                        state = state,
                        onIntent = viewModel::setIntent,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_DELETED = "extra_deleted"

        /** 打开详情页的统一入口：带上要看的那一条 id。 */
        fun createIntent(context: Context, id: Int): Intent =
            Intent(context, DetailActivity::class.java).putExtra(EXTRA_ID, id)
    }
}

/** 界面本体：只认"现在是什么状态"和"用户做了什么"，碰不到 ViewModel。 */
@Composable
private fun DetailScreen(
    state: DetailContract.State,
    onIntent: (DetailContract.Intent) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when {
            state.loading -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.failMessage != null -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(text = state.failMessage, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { onIntent(DetailContract.Intent.Retry) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("重试")
                    }
                }
            }

            state.article != null -> {
                val article = state.article
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(text = article.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = article.author,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(text = article.summary, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "（这是第 ${article.id} 条假数据，用来演示带参数进来 + 把结果带回去）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("返回")
            }
            Button(
                onClick = { onIntent(DetailContract.Intent.Delete) },
                enabled = state.article != null && !state.loading,
                modifier = Modifier.weight(1f),
            ) {
                Text("删除这条")
            }
        }
    }
}

// ==================== 预览：造一份假状态就能看，不用跑 App ====================

@Preview(showBackground = true, name = "有内容")
@Composable
private fun DetailScreenPreview() {
    MaterialTheme {
        DetailScreen(
            state = DetailContract.State(
                id = 1,
                article = Article(1, "第 1 篇文章：把界面和逻辑分开", "假数据摘要", "作者 A"),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "加载失败")
@Composable
private fun DetailScreenFailedPreview() {
    MaterialTheme {
        DetailScreen(
            state = DetailContract.State(id = 1, failMessage = "网络开小差了"),
            onIntent = {},
            onBack = {},
        )
    }
}
