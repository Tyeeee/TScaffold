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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tscaffold.component.business.basic.ui.task.contract.TaskContract
import com.tscaffold.component.business.basic.ui.task.data.Task
import com.tscaffold.component.business.basic.ui.task.viewmodel.TaskViewModel
import com.tscaffold.component.common.ui.compose.observeEffect
import com.tscaffold.component.common.ui.compose.observeState

/**
 * 任务列表页的 Compose 版。
 *
 * 和 XML 版用的是**同一个** [TaskViewModel]、同一份[TaskContract]，
 * ViewModel 里一行代码都不用改 —— 换界面写法不影响逻辑，这就是这套写法的意义。
 *
 * 注意这里的分工（和 MVI 的要求一致）：
 * - 这个 Activity 负责"接线"：订阅状态、处理一次性事件、把用户的动作转给 ViewModel；
 * - 下面的 [TaskScreen] 只接收"当前状态"和"上报操作的口子"，自己不碰 ViewModel。
 *   所以它是**状态的函数**：给同样的状态，画出来的一定是同样的界面。
 *   好处很直接 —— 想预览某个样子，直接造一个假状态丢进去就行（见文件末尾的预览）。
 */
class TaskComposeActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.observeState()
                    val context = LocalContext.current

                    // 一次性事件：页面可见时才收，退到后台不会突然弹提示
                    viewModel.observeEffect { effect ->
                        when (effect) {
                            is TaskContract.Effect.ShowToast ->
                                Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    TaskScreen(
                        state = state,
                        onIntent = viewModel::setIntent,
                    )
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

/**
 * 界面本体。
 *
 * 它只认两样东西：现在是[state]什么样、用户做了什么要往哪儿报（[onIntent]）。
 * 它不知道 ViewModel 长什么样，也不知道数据是从假仓库还是真接口来的。
 */
@Composable
private fun TaskScreen(
    state: TaskContract.State,
    onIntent: (TaskContract.Intent) -> Unit,
) {
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

        // 中间这块：加载中 / 列表 / 空 / 出错，四种样子都从 state 里推出来
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
                        TaskRow(
                            task = task,
                            onToggle = { onIntent(TaskContract.Intent.Toggle(task.id)) },
                        )
                    }
                }
            }
        }

        if (state.loadStatus == TaskContract.LoadStatus.Failed) {
            Button(
                onClick = { onIntent(TaskContract.Intent.Load) },
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
                onClick = { onIntent(TaskContract.Intent.Refresh) },
                modifier = Modifier.weight(1f),
            ) {
                Text("重新加载")
            }
            Button(
                onClick = { onIntent(TaskContract.Intent.ClearDone) },
                modifier = Modifier.weight(1f),
            ) {
                Text("清掉已完成")
            }
        }
    }
}

/** 列表里的一行。同样的道理：只吃数据和"点了要报什么"，不碰 ViewModel。 */
@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(checked = task.done, onCheckedChange = null)
        Text(text = task.title, modifier = Modifier.padding(start = 8.dp))
    }
}

// ==================== 预览：不需要跑 App，也不需要 ViewModel ====================

@Preview(showBackground = true, name = "有数据")
@Composable
private fun TaskScreenPreview() {
    MaterialTheme {
        TaskScreen(
            state = TaskContract.State(
                tasks = listOf(
                    Task(1, "看一遍 BaseViewModel 里那三样东西", done = true),
                    Task(2, "照着这个示例写一个自己的页面"),
                ),
                loadStatus = TaskContract.LoadStatus.Success,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true, name = "加载失败")
@Composable
private fun TaskScreenFailedPreview() {
    MaterialTheme {
        TaskScreen(
            state = TaskContract.State(
                loadStatus = TaskContract.LoadStatus.Failed,
                failMessage = "网络开小差了",
            ),
            onIntent = {},
        )
    }
}
