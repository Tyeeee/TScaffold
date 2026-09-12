package com.tscaffold.basic.network.websocket

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketRetryPolicyTest {

    @Test
    fun `等待时间按指数增长，到上限就不再涨`() {
        val policy = ExponentialBackoffRetryPolicy(
            maxAttempts = 10,
            baseDelayMillis = 1_000,
            maxDelayMillis = 8_000,
            jitterRatio = 0.0,
        )

        assertEquals(1_000L, policy.delayMillis(1))
        assertEquals(2_000L, policy.delayMillis(2))
        assertEquals(4_000L, policy.delayMillis(3))
        assertEquals(8_000L, policy.delayMillis(4))
        assertEquals(8_000L, policy.delayMillis(5)) // 封顶
    }

    @Test
    fun `超过次数就返回 null，表示放弃重连`() {
        val policy = ExponentialBackoffRetryPolicy(maxAttempts = 3, jitterRatio = 0.0)

        assertNotNull(policy.delayMillis(3))
        assertNull(policy.delayMillis(4))
    }

    @Test
    fun `抖动只在上下限之内浮动，不会失控`() {
        val policy = ExponentialBackoffRetryPolicy(
            maxAttempts = 5,
            baseDelayMillis = 1_000,
            maxDelayMillis = 10_000,
            jitterRatio = 0.2,
            random = Random(42),
        )

        repeat(50) {
            val delay = policy.delayMillis(3)!! // raw = 4000，抖动 ±800
            assertTrue("抖动越界: $delay", delay in 3_200L..4_800L)
        }
    }

    @Test
    fun `非法次数（0 或负数）直接判为不重连`() {
        val policy = ExponentialBackoffRetryPolicy()

        assertNull(policy.delayMillis(0))
        assertNull(policy.delayMillis(-1))
    }
}
