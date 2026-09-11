package com.tscaffold.component.business.basic.ui.counter.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tscaffold.component.business.basic.ui.counter.contract.CounterContract
import com.tscaffold.component.business.basic.ui.counter.viewmodel.CounterViewModel
import com.tscaffold.component.common.ui.compose.observeEffect
import com.tscaffold.component.common.ui.compose.observeState

/**
 * 计数器示例 —— 用 Compose 写的版本。
 *
 * 和 XML 版的区别只有"界面怎么写"：
 * - 状态：`val state by viewModel.observeState()`，state 一变界面自动重画；
 * - 操作：按钮直接调用 viewModel.setIntent(...)；
 * - 事件：`viewModel.observeEffect { ... }` 处理弹提示、跳页面。
 *
 * 注意 [CounterViewModel] 是同一个类，一行都没有改过。
 */
class CounterComposeActivity : ComponentActivity() {

    private val viewModel: CounterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CounterScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        /** 打开这个页面的统一入口。 */
        fun start(context: Context) {
            context.startActivity(Intent(context, CounterComposeActivity::class.java))
        }
    }
}

/**
 * 页面的样子。这里只接收"当前状态"和"用户点了什么"，自己不保存任何数据，
 * 所以可以随便拿假数据预览、也可以单独测试。
 */
@Composable
private fun CounterScreen(viewModel: CounterViewModel) {
    val state by viewModel.observeState()
    val context = LocalContext.current

    // 一次性事件：只在页面可见时接收，所以不会在后台突然弹提示。
    viewModel.observeEffect { effect ->
        when (effect) {
            is CounterContract.Effect.ShowToast ->
                Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = state.count.toString(), style = MaterialTheme.typography.displayMedium)
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(
            onClick = { viewModel.setIntent(CounterContract.Intent.Increase) },
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text("加一")
        }
        Button(
            onClick = { viewModel.setIntent(CounterContract.Intent.Decrease) },
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text("减一")
        }
        Button(onClick = { viewModel.setIntent(CounterContract.Intent.Reset) }) {
            Text("归零")
        }
    }
}
