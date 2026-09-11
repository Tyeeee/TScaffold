package com.tscaffold.component.business.basic.data

/**
 * 示例用的假账号仓库。
 *
 * 约定：账号 `admin`、密码 `123456` 才能登录成功。
 * 其它组合都返回失败，好让"登录失败"这条分支有的可测。
 * 等接上 Retrofit，把这里换成真接口即可。
 */
object AccountRepository {

    suspend fun login(username: String, password: String): Boolean {
        kotlinx.coroutines.delay(1200)      // 假装在等网络
        return username == "admin" && password == "123456"
    }
}
