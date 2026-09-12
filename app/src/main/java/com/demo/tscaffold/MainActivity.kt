package com.demo.tscaffold

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.demo.tscaffold.ui.list.view.ListActivity
import com.demo.tscaffold.ui.search.compose.SearchActivity
import com.demo.tscaffold.ui.theme.TScaffoldTheme
import com.tscaffold.business.basic.login.view.LoginActivity

/**
 * 应用首页：示例页面的入口。
 *
 * 列表页、搜索页、详情页在 `app` 里（`com.demo.tscaffold.ui.*`）。
 *
 * **登录页不在 app 里** —— 它是"业务基础页面"，住在 `component_business_basic` 模块，
 * 由各业务复用（`com.tscaffold.business.basic.login.view.LoginActivity`）。
 * 这里顺便演示了怎么用它：启动、拿结果、自己决定登录成功后去哪。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TScaffoldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // 登录页只回报 RESULT_OK / RESULT_CANCELED，它不知道下一个页面是谁。
                    // "登录成功之后去哪"是调用方（也就是这里）的事。
                    val loginLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                            // 真实项目里通常在这里进首页 / 刷新登录态
                        }
                    }

                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onList = { ListActivity.start(this) },
                        onLogin = { loginLauncher.launch(LoginActivity.intent(this)) },
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
            text = "TScaffold · 示例入口",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "列表页和搜索页在 app 里；登录页在 component_business_basic 模块里，" +
                "启动它、拿到结果再决定去哪 —— 这就是业务基础页面的用法。",
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
            Text("登录页（来自 component_business_basic）")
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
