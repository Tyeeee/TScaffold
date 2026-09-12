package com.tscaffold.base.network.websocket

/**
 * WebSocket 连接状态。
 *
 * 它是**状态**不是事件：任何时候读到的都是当前真实情况，
 * 转屏 / 重新订阅都能立刻拿到（这正是我们那套写法要求的）。
 */
sealed interface WebSocketState {

    /** 还没开始连。 */
    data object Idle : WebSocketState

    /** 正在握手（含每次重连的尝试）。 */
    data object Connecting : WebSocketState

    /** 已连上，可以发消息了。 */
    data object Connected : WebSocketState

    /**
     * 断了，正在等下一次重连。
     *
     * @param attempt 第几次重连（从 1 开始）
     * @param delayMillis 这次要等多久
     */
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : WebSocketState

    /**
     * 连接已结束。
     *
     * @param code 1000 表示正常关闭（主动关、或对端正常说再见）；这两种都不再重连。
     */
    data class Closed(val code: Int, val reason: String?) : WebSocketState

    /** 重连次数用完/不可恢复，放弃。 */
    data class Failed(val userMessage: String, val cause: Throwable?) : WebSocketState
}
