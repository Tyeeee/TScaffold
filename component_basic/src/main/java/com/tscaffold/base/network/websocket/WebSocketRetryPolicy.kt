package com.tscaffold.base.network.websocket

import kotlin.random.Random

/**
 * 重连策略：第 [delayMillis] 次重连前该等多久，返回 `null` 表示放弃、不再重连。
 *
 * 做成接口是为了能按业务换（现场直播可能希望一直重连，IM 可能希望有限次数就提示用户）。
 */
interface WebSocketRetryPolicy {

    /**
     * @param attempt 第几次重连，从 1 开始
     * @return 等待毫秒数；`null` = 不重连了
     */
    fun delayMillis(attempt: Int): Long?
}

/**
 * 默认策略：**指数退避 + 上限 + 抖动**。
 *
 * 比起"固定间隔一直重连"（母工程是 <2 次 0ms、≤5 次 2s、之后永远 30s，**没有次数上限**），
 * 这里的取舍是：
 * - 指数退避：连不上时不要拼命敲服务器；
 * - 抖动：避免多个客户端同时掉线后同时重连（惊群）；
 * - 上限：到次数就停，把"连不上"变成一个能展示给用户的状态，而不是永远在后台偷偷转。
 */
class ExponentialBackoffRetryPolicy(
    private val maxAttempts: Int = 8,
    private val baseDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 30_000,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default,
) : WebSocketRetryPolicy {

    override fun delayMillis(attempt: Int): Long? {
        if (attempt !in 1..maxAttempts) return null
        val raw = (baseDelayMillis shl (attempt - 1)).coerceAtMost(maxDelayMillis)
        if (jitterRatio <= 0.0) return raw
        val jitter = (raw * jitterRatio).toLong()
        if (jitter <= 0) return raw
        return raw - jitter + random.nextLong(0, jitter * 2 + 1)
    }
}
