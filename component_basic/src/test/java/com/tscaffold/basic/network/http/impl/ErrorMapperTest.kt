package com.tscaffold.basic.network.http.impl

import com.tscaffold.basic.network.http.ApiException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapperTest {

    @Test
    fun `断网翻译成 NoNetwork，并给出给用户看的一句话`() {
        val mapped = ErrorMapper.toApiException(UnknownHostException("no host"))

        assertTrue(mapped is ApiException.NoNetwork)
        assertEquals("网络不可用，检查一下连接", mapped.message)
    }

    @Test
    fun `超时翻译成 Timeout，而不是被当成普通 IO 问题`() {
        // SocketTimeoutException 也是 IOException，映射顺序写错就会走到 NoNetwork
        val mapped = ErrorMapper.toApiException(SocketTimeoutException("timeout"))

        assertTrue(mapped is ApiException.Timeout)
    }

    @Test
    fun `连接被拒也归到 NoNetwork`() {
        assertTrue(ErrorMapper.toApiException(ConnectException("refused")) is ApiException.NoNetwork)
    }

    @Test
    fun `HTTP 500 带状态码，提示说服务器的问题`() {
        val mapped = ErrorMapper.toApiException(httpException(500))

        assertTrue(mapped is ApiException.Http)
        assertEquals(500, (mapped as ApiException.Http).code)
        assertEquals("服务器开小差了，稍后再试", mapped.userMessage)
    }

    @Test
    fun `HTTP 401 提示去重新登录（走的是 Http，不是 Auth）`() {
        assertEquals("登录已失效，请重新登录", ErrorMapper.toApiException(httpException(401)).message)
    }

    @Test
    fun `已经翻译过的异常不再包一层`() {
        val business = ApiException.Business(1001, "没有权限")

        assertSame(business, ErrorMapper.toApiException(business))
    }

    @Test
    fun `没归类的异常兜底成 Unknown，不会把底层细节丢给用户`() {
        val mapped = ErrorMapper.toApiException(IllegalStateException("boom"))

        assertTrue(mapped is ApiException.Unknown)
        assertEquals("出了点问题，稍后再试", mapped.message)
    }

    private fun httpException(code: Int): HttpException = HttpException(
        Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType()))
    )
}
