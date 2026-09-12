package com.tscaffold.basic.network.http

/**
 * 网络层往外抛的**唯一**异常类型。
 *
 * 关键点：[userMessage] 同时被放进 `message`，所以页面上原有的
 * `failMessage = e.message ?: "未知错误"` 不用改一个字就能用。
 *
 * 分成几种是为了以后能分开处理（比如 Auth 要踢回登录页、
 * Timeout 可以给个"重试"按钮），现在页面统一按 `Exception` 接住也不会漏。
 */
sealed class ApiException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /** 连不上：没网、DNS 失败、连接被拒。 */
    class NoNetwork(
        cause: Throwable? = null,
        userMessage: String = "网络不可用，检查一下连接",
    ) : ApiException(userMessage, cause)

    /** 连上了但迟迟没回。 */
    class Timeout(
        cause: Throwable? = null,
        userMessage: String = "请求超时，稍后再试",
    ) : ApiException(userMessage, cause)

    /** HTTP 层失败（4xx / 5xx）。 */
    class Http(
        val code: Int,
        userMessage: String,
        cause: Throwable? = null,
    ) : ApiException(userMessage, cause)

    /** 业务层失败：HTTP 是 200，但后端返回的业务码表示这次操作没成功。 */
    class Business(
        val code: Int,
        userMessage: String,
    ) : ApiException(userMessage)

    /** 登录失效，需要重新登录。 */
    class Auth(
        userMessage: String = "登录已失效，请重新登录",
    ) : ApiException(userMessage)

    /** 其他没归类的问题，兜底用。 */
    class Unknown(
        cause: Throwable? = null,
        userMessage: String = "出了点问题，稍后再试",
    ) : ApiException(userMessage, cause)
}
