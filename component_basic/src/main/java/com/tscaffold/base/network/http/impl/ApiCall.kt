package com.tscaffold.base.network.http.impl

import kotlin.coroutines.cancellation.CancellationException

/**
 * 包住一次网络调用：把底层异常翻译成 [ApiException]。
 *
 * 数据层的每个方法都套一层它就够了，不用每处自己 try/catch。
 *
 * **取消必须原样往上抛**：页面退出、搜索防抖取消都靠这个信号。
 * 如果把它当成"失败"，界面会在已经离开的时候还去改状态，
 * 用户就会看到"退出了还弹一句请求失败"。
 */
internal suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw ErrorMapper.toApiException(e)
    }
