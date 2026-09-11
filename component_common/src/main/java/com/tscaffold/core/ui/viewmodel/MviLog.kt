package com.tscaffold.core.ui.viewmodel

/**
 * 调试用的日志开关 —— 把"收到了什么操作""状态从什么样变成了什么样"打出来。
 *
 * 为什么需要它：这套写法里状态是一层层算出来的，出问题时最想知道的就是
 * "界面到底上报了什么"和"状态在哪一步变成了这个样子"。打出来一眼就看到了。
 *
 * 默认**什么都不打印**（所以单元测试不受影响）。App 启动时把它换成自己的打印方式即可，
 * 例如在 Application 的 onCreate 里写：
 *
 * ```
 * MviLog.printer = { Log.d("MVI", it) }
 * ```
 *
 * 打完用 Logcat 过滤标签 `MVI` 就能看到整条链路。
 *
 * 两条注意（文档里也专门提醒过）：
 * 1. 想打更长的内容（比如把状态转成 JSON）会花时间，别在正式包里一直开着；
 * 2. **别打敏感数据**（token、手机号、身份证之类），日志是能被别人看到的。
 */
object MviLog {

    /** 换掉它就打开了日志；默认是空的，什么都不做。 */
    var printer: (String) -> Unit = {}

    /** 由基类调用，外部一般不用管。 */
    internal fun print(message: () -> String) {
        val current = printer
        // 用 lambda 传进来，是为了在"没打开日志"的时候连字符串拼接都不做
        current(message())
    }
}
