package com.tscaffold.component.business.basic.ui.task.compose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.viewmodel.TaskViewModel
import com.tscaffold.component.common.ui.compose.observeEffect
import com.tscaffold.component.common.ui.compose.observeState

/**
 * 任务列表页的 Compose 版。
 *
 * 和三份 XML 界面用的是**同一个** [TaskViewModel]、同一份 Contract，
 * ViewModel 里一行代码都不用改 —— 换界面写法不影响逻辑，这就是这套写法的意义。
 *
 * 对照着看：
 * - 状态：`val state by viewModel.observeState()`
 * - 操作：`viewModel.setIntent(...)`
 * - 事件：`viewModel.observeEffect { ... }`
 */
class TaskComposeActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskScreen(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, TaskComposeActivity::class.java))
        }
    }
}

@Composable
private fun TaskScreen(viewModel: TaskViewModel) {
    val state by viewModel.observeState()
    val context = LocalContext.current

    // 一次性事件：页面可见时才收，退到后台不会突然弹提示
    viewModel.observeEffect { effect ->
        when (effect) {
            is TaskContract.Effect.ShowToast ->
                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "已完成 ${state.doneCount} / ${state.total}",
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 中间这块：加载中 / 列表 / 空 / 出错，四种样子都从 state 里来
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                state.tasks.isEmpty() -> Text(
                    text = when (state.loadStatus) {
                        TaskContract.LoadStatus.Empty -> "一条任务都没有"
                        TaskContract.LoadStatus.Failed -> state.failMessage ?: "加载失败"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.tasks.forEach { task ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setIntent(TaskContract.Intent.Toggle(task.id))
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(checked = task.done, onCheckedChange = null)
                            Text(text = task.title, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (state.loadStatus == TaskContract.LoadStatus.Failed) {
            Button(
                onClick = { viewModel.setIntent(TaskContract.Intent.Load) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("重试")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Button(
                onClick = { viewModel.setIntent(TaskContract.Intent.Refresh) },
                modifier = Modifier.weight(1f),
            ) {
                Text("重新加载")
            }
            Button(
                onClick = { viewModel.setIntent(TaskContract.Intent.ClearDone) },
                modifier = Modifier.weight(1f),
            ) {
                Text("清掉已完成")
            }
        }
    }
}
