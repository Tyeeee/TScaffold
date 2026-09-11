package com.tscaffold.component.business.basic.ui.search.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tscaffold.component.business.basic.data.Article
import com.tscaffold.component.business.basic.ui.detail.compose.DetailActivity
import com.tscaffold.component.business.basic.ui.search.contract.SearchContract
import com.tscaffold.component.business.basic.ui.search.viewmodel.SearchViewModel
import com.tscaffold.component.common.ui.compose.observeState

/**
 * 搜索页 —— Compose 写的，专门演示**输入防抖**。
 *
 * 防抖的逻辑在 ViewModel 里（打字时一个请求都不发，停下来 300 毫秒才搜）。
 * 这个文件要做的事很单纯：把输入框的值交给状态，把状态里的结果显示出来。
 *
 * 输入框的值也来自状态（`state.keyword`），不是界面自己存的 ——
 * 所以转屏、从后台回来，用户输入的字不会丢。
 */
class SearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.observeState()
                    val context = LocalContext.current

                    LifecycleResumeEffect(state.message) {
                        state.message?.let {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                            viewModel.setIntent(SearchContract.Intent.MessageShown)
                        }
                        onPauseOrDispose { }
                    }

                    // 状态说"要看某一条"，就打开详情页，然后回报一句
                    LaunchedEffect(state.openDetailId) {
                        val id = state.openDetailId ?: return@LaunchedEffect
                        viewModel.setIntent(SearchContract.Intent.DetailOpened)
                        context.startActivity(DetailActivity.createIntent(context, id))
                    }

                    SearchScreen(
                        state = state,
                        onIntent = viewModel::setIntent,
                        onBack = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SearchActivity::class.java))
        }
    }
}

@Composable
private fun SearchScreen(
    state: SearchContract.State,
    onIntent: (SearchContract.Intent) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.keyword,
            onValueChange = { onIntent(SearchContract.Intent.KeywordChanged(it)) },
            singleLine = true,
            label = { Text("搜文章标题或作者，比如：1、作者 A") },
            trailingIcon = {
                TextButton(onClick = { onIntent(SearchContract.Intent.Clear) }) {
                    Text("清空")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )

        Text(
            text = "打字时不会发请求，停下来 300 毫秒才搜一次（防抖）",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.searching -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                state.noResult -> Text(
                    text = "没搜到「${state.keyword}」，换个词试试",
                    modifier = Modifier.align(Alignment.Center),
                )

                state.results.isEmpty() -> Text(
                    text = "输入关键字开始搜索",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.id }) { article ->
                        SearchResultRow(
                            article = article,
                            onClick = { onIntent(SearchContract.Intent.Click(article.id)) },
                        )
                    }
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp),
        ) {
            Text("返回首页")
        }
    }
}

@Composable
private fun SearchResultRow(article: Article, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = article.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = article.author,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}

// ==================== 预览：造一份假状态就能看 ====================

@Preview(showBackground = true, name = "搜到结果")
@Composable
private fun SearchScreenPreview() {
    MaterialTheme {
        SearchScreen(
            state = SearchContract.State(
                keyword = "第 1",
                results = listOf(
                    Article(1, "第 1 篇文章：把界面和逻辑分开", "摘要", "作者 A"),
                    Article(2, "第 10 篇文章：状态只有一个来源", "摘要", "作者 B"),
                ),
                hasSearched = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, name = "没搜到")
@Composable
private fun SearchScreenEmptyPreview() {
    MaterialTheme {
        SearchScreen(
            state = SearchContract.State(keyword = "找不到的词", hasSearched = true),
            onIntent = {},
            onBack = {},
        )
    }
}
