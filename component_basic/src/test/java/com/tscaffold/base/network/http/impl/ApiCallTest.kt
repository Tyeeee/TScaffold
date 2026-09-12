package com.tscaffold.base.network.http.impl

import com.tscaffold.base.network.http.ApiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

class ApiCallTest {

    @Test
    fun `成功时原样返回，不做任何包装`() = runTest {
        assertEquals(7, apiCall { 7 })
    }

    @Test
    fun `底层异常会被翻译成 ApiException`() = runTest {
        val error = catchError { apiCall<Unit> { throw UnknownHostException("no net") } }

        assertTrue(error is ApiException.NoNetwork)
    }

    @Test
    fun `取消原样抛出去，不会被当成请求失败`() = runTest {
        // 这一条很要紧：页面退出、搜索防抖取消都靠这个信号，
        // 一旦被翻译成 ApiException，界面就会在已经离开之后还去改状态、弹提示。
        val error = catchError { apiCall<Unit> { throw CancellationException("cancelled") } }

        assertTrue(error is CancellationException)
        assertTrue(error !is ApiException)
    }

    @Test
    fun `正常返回时不会误报错误`() = runTest {
        val error = catchError { apiCall { "ok" } }

        assertNull(error)
    }

    private suspend fun catchError(block: suspend () -> Any?): Throwable? =
        try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
}
