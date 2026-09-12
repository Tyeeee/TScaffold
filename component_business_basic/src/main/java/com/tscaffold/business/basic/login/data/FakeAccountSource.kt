package com.tscaffold.business.basic.login.data

import kotlinx.coroutines.delay

/**
 * [AccountSource] 的**假实现**，让登录页脱离后端也能单独跑起来。
 *
 * 约定：账号 `admin`、密码 `123456` 才能登进去。
 * 其它组合一律返回失败 —— 这样"登录失败"那条分支才有得看、有得测。
 *
 * 它是个 `object`（单例）而不是 class：默认实现无状态、也没有需要注入的东西。
 * 业务侧要换成真接口时，实现 [AccountSource] 再传给 ViewModel 即可，
 * **不用改这个文件**。
 */
object FakeAccountSource : AccountSource {

    /** 假装在等网络。免得点了按钮立刻返回，看不出"提交中"的状态。 */
    private const val FAKE_DELAY_MILLIS = 1200L

    override suspend fun login(username: String, password: String): Boolean {
        delay(FAKE_DELAY_MILLIS)
        return username == "admin" && password == "123456"
    }
}
