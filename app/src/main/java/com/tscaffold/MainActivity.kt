package com.tscaffold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tscaffold.component.business.basic.ui.list.view.ListActivity
import com.tscaffold.component.business.basic.ui.login.view.LoginActivity
import com.tscaffold.component.business.basic.ui.search.compose.SearchActivity
import com.tscaffold.ui.theme.TScaffoldTheme

/**
 * 应用首页：四个示例页面的入口。
 *
 * 四个页面分别对应不同的形态和场景，写自己的页面时挑最像的那个照抄：
 * 列表页（RecyclerView + 分页）、登录表单（输入校验）、搜索（Compose + 防抖）、
 * 详情（带参数进来 + 把结果带回去）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TScaffoldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onList = { ListActivity.start(this) },
                        onLogin = { LoginActivity.start(this) },
                        onSearch = { SearchActivity.start(this) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onList: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "TScaffold · 四个示例页面",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "四页用的是同一套写法，形态和场景各不相同。详情页从列表或搜索里点进去。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        Button(
            onClick = onList,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Text("列表页：下拉刷新 / 分页 / 长按删除")
        }

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Text("表单页：边输边校验 / 防重复提交")
        }

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("搜索页：输入防抖（Compose）")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    TScaffoldTheme {
        HomeScreen(onList = {}, onLogin = {}, onSearch = {})
    }
}
