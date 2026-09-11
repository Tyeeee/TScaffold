package com.tscaffold.component.business.basic.ui.counter.viewmodel

import com.tscaffold.component.business.basic.ui.counter.contract.CounterContract
import com.tscaffold.component.common.ui.viewmodel.BaseViewModel

/**
 * 计数器页面的逻辑，全都在这里。界面换成 XML 还是 Compose，这个文件都不用改。
 *
 * 想加功能就三步：
 * 1. 在 CounterContract.Intent 里加一个新动作；
 * 2. 在下面的 when 里写这个动作要做什么；
 * 3. 在界面上调用 viewModel.setIntent(新动作)。
 */
class CounterViewModel :
    BaseViewModel<CounterContract.State, CounterContract.Intent, CounterContract.Effect>() {

    /** 页面刚打开时的样子。 */
    override fun initializeState(): CounterContract.State = CounterContract.State()

    /** 用户做了某个操作之后要做什么。 */
    override fun handleIntent(intent: CounterContract.Intent) {
        when (intent) {
            CounterContract.Intent.Increase ->
                setState { copy(count = count + 1, message = "加了一次") }

            CounterContract.Intent.Decrease ->
                setState { copy(count = count - 1, message = "减了一次") }

            CounterContract.Intent.Reset -> {
                setState { copy(count = 0, message = "已经归零") }
                // 弹提示属于"只做一次的事"，所以用 setEffect 而不是改状态。
                setEffect { CounterContract.Effect.ShowToast("数字已清零") }
            }
        }
    }
}
