package com.demo.tscaffold.data.remote

import com.google.gson.annotations.SerializedName
import com.tscaffold.basic.network.http.ApiException

/**
 * 后端统一的响应外壳。
 *
 * 字段名和成功码按你实际的后端改（这里用最常见的 code / msg / data + 0 表示成功）。
 * 它描述的是"这家后端的线上格式"，所以放在数据层，不放网络底座里 ——
 * 换后端时只动这个文件和 [ArticleApi]。
 */
data class Envelope<T>(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("msg") val message: String? = null,
    @SerializedName("data") val data: T? = null,
) {
    companion object {
        /** 业务成功的码。 */
        const val CODE_OK = 0
    }
}

/** 只要业务码对就行，不关心返回体时用它（比如删除接口）。 */
fun Envelope<*>.checkOk() {
    if (code != Envelope.CODE_OK) {
        throw ApiException.Business(code, message ?: "请求失败（$code）")
    }
}

/** 业务码对，而且**必须有数据**；没有数据算失败。 */
fun <T : Any> Envelope<T>.unwrap(): T {
    checkOk()
    return data ?: throw ApiException.Business(code, message ?: "服务端没有返回数据")
}

/** 业务码对，数据**可以为空**；为空时返回 null，交给调用方决定怎么显示。 */
fun <T : Any> Envelope<T>.unwrapOrNull(): T? {
    checkOk()
    return data
}
