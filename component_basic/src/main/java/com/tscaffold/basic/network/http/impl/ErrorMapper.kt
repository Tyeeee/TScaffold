package com.tscaffold.basic.network.http.impl

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.tscaffold.basic.network.http.ApiException
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把各种底层异常翻译成 [ApiException] —— **全工程只有这一处做这件事**。
 *
 * 以后后端加了新的错误约定、或者要把某类错误换个说法，
 * 只改这个文件；页面和 ViewModel 一行都不用动。
 *
 * 判断顺序很要紧，几条容易踩的：
 * 1. `SocketTimeoutException` 和 `EOFException` 都是 `IOException`，得排在前面；
 * 2. **解析失败不是网络问题**：响应体是空的、JSON 语法错、类型不符时，Gson 抛的是
 *    `JsonParseException` 或干脆一个 `EOFException`（message 形如
 *    "End of input at line 1 column 1 path $"）。它们都属于 IOException 家族，
 *    但这时候网是通的 —— 统一给"网络不可用"会把人带偏（实测就踩到了）。
 */
internal object ErrorMapper {

    fun toApiException(throwable: Throwable): ApiException = when {
        // 已经是翻译过的就别再包一层
        throwable is ApiException -> throwable
        throwable is UnknownHostException || throwable is ConnectException ->
            ApiException.NoNetwork(throwable)

        throwable is SocketTimeoutException -> ApiException.Timeout(throwable)

        throwable is HttpException -> ApiException.Http(
            code = throwable.code(),
            userMessage = httpMessage(throwable.code()),
            cause = throwable,
        )

        // 注意：必须排在 IOException 之前
        isParseFailure(throwable) -> ApiException.Unknown(throwable)

        throwable is IOException -> ApiException.NoNetwork(throwable)
        else -> ApiException.Unknown(throwable)
    }

    /** HTTP 状态码 → 给用户看的一句话。 */
    fun httpMessage(code: Int): String = when (code) {
        400 -> "请求有误"
        401, 403 -> "登录已失效，请重新登录"
        404 -> "内容不存在"
        in 500..599 -> "服务器开小差了，稍后再试"
        else -> "请求失败（$code）"
    }

    /**
     * 是不是"服务端给的数据解析不了"。
     *
     * Gson 在读空响应体或截断的 JSON 时直接抛裸的 `EOFException`，只能靠 message 认出来
     * （格式是 "… at line N column M path …"）；语法错、类型不符则抛 `JsonParseException`。
     */
    private fun isParseFailure(throwable: Throwable): Boolean = when {
        throwable is JsonParseException || throwable is MalformedJsonException -> true
        throwable is EOFException -> throwable.message?.contains("at line ") == true
        else -> false
    }
}
