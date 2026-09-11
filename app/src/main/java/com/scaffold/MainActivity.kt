package com.scaffold

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
import com.scaffold.component.business.basic.ui.counter.compose.CounterComposeActivity
import com.scaffold.component.business.basic.ui.counter.view.CounterActivity
import com.scaffold.ui.theme.ScaffoldTheme

/**
 * 应用首页。
 *
 * 它只做一件事：放两个按钮，分别打开两份示例页面。
 * 等你写好自己的页面，把这两个按钮换成你自己的入口就行。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScaffoldTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        onOpenXmlPage = { CounterActivity.start(this) },
                        onOpenComposePage = { CounterComposeActivity.start(this) },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenXmlPage: () -> Unit,
    onOpenComposePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MVI 骨架",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "下面两个示例的逻辑完全一样，只有界面写法不同。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        Button(
            onClick = onOpenXmlPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Text("示例一：XML 写的页面")
        }

        Button(
            onClick = onOpenComposePage,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("示例二：Compose 写的页面")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ScaffoldTheme {
        HomeScreen(onOpenXmlPage = {}, onOpenComposePage = {})
    }
}
