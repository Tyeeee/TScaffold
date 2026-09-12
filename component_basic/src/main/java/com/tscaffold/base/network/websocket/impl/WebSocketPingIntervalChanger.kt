package com.tscaffold.base.network.websocket.impl

import com.tscaffold.base.network.HttpClient
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import okhttp3.WebSocket

/**
 * 运行时改 WebSocket 心跳间隔 —— **尽力而为的反射**，单独关在这一个文件里。
 *
 * ## 为什么是"尽力而为"
 *
 * 心跳是 OkHttp 的**客户端级**设置，而且两个大版本的内部结构完全不同：
 *
 * | | OkHttp 4.x | OkHttp 5.x |
 * |---|---|---|
 * | 心跳调度 | `RealWebSocket.executor`（ScheduledExecutorService，非 final）+ 内部类 `PingRunnable` | `RealWebSocket.pingIntervalMillis`（**final**）+ 内部类 `WriterTask` |
 * | 反射能不能改 | 能：换掉调度器、按新间隔重排 | **不能**：建连时就把间隔按值捕获进调度任务（`initReaderAndWriter$lambda$0$0` 直接 `lreturn` 那个 long），改字段不影响已排定的心跳，也没有可替换的执行器 |
 *
 * 所以母工程那段 `changePingInterval` 在 OkHttp 5 上会一路 `NoSuchFieldException` 被 catch 掉、
 * **静默变成空操作**（而且它全工程从未被任何业务调用过）。
 *
 * ## 这里的做法
 *
 * - 探测到 OkHttp 4.x 形状 → 真改，返回 `true`；
 * - 否则 → **不做危险的事**（不去硬改 final 字段），返回 `false` 并说明原因；
 * - 任何异常都在内部吞掉，只记日志，**永远不会崩**。
 */
internal object WebSocketPingIntervalChanger {

    private const val REAL_WEB_SOCKET = "okhttp3.internal.ws.RealWebSocket"

    /** OkHttp 4.x 的字段名。 */
    private const val EXECUTOR_FIELD = "executor"

    /** OkHttp 4.x 的内部类名。 */
    private const val PING_RUNNABLE = "PingRunnable"

    /**
     * 当前 OkHttp 版本下，反射能不能真的改掉**已经连着**的心跳。
     *
     * 这不是给业务用的，是给测试当"哨兵"：OkHttp 一升级、内部结构再变，这条会失败，
     * 提醒我们回来复核这个文件，而不是让它悄悄退化成空操作。
     */
    fun canChangeLivePing(): Boolean = runCatching {
        val clazz = Class.forName(REAL_WEB_SOCKET)
        clazz.getDeclaredField(EXECUTOR_FIELD)
        clazz.declaredClasses.any { it.simpleName == PING_RUNNABLE }
    }.getOrDefault(false)

    /**
     * 尽力把 [socket] 的心跳间隔改成 [intervalMillis]。
     *
     * @return `true` = 已经改掉并生效；`false` = 这个 OkHttp 版本上改不了（不是出错，是做不到）
     */
    fun apply(socket: WebSocket?, intervalMillis: Long): Boolean {
        if (socket == null || intervalMillis <= 0) return false
        return runCatching { swapScheduler(socket, intervalMillis) }
            .onFailure { log("改心跳做不到：${it.javaClass.simpleName} ${it.message}") }
            .getOrDefault(false)
    }

    /** OkHttp 4.x 那一套：换掉 RealWebSocket 里的心跳调度器。 */
    private fun swapScheduler(socket: WebSocket, intervalMillis: Long): Boolean {
        val clazz = Class.forName(REAL_WEB_SOCKET)
        // 假实现或别的实现都不是 RealWebSocket，直接判为做不到
        if (!clazz.isInstance(socket)) return false

        val executorField = clazz.getDeclaredField(EXECUTOR_FIELD).apply { isAccessible = true }
        val oldService = executorField.get(socket) as? ScheduledExecutorService ?: return false

        val pingRunnableClass = clazz.declaredClasses
            .firstOrNull { it.simpleName == PING_RUNNABLE } ?: return false

        val pingRunnable = pingRunnableClass
            .getDeclaredConstructor(clazz)
            .apply { isAccessible = true }
            .newInstance(socket) as Runnable

        val newService = ScheduledThreadPoolExecutor(1) { runnable -> Thread(runnable, "web-socket-ping") }
        newService.scheduleAtFixedRate(pingRunnable, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS)

        executorField.set(socket, newService)
        runCatching { oldService.shutdown() }
        log("心跳间隔已改为 ${intervalMillis}ms（反射替换调度器）")
        return true
    }

    /** 日志走网络底座那一个 printer（在 `Network.init` 里一处配好），并且它本身也不允许抛。 */
    private fun log(message: String) {
        runCatching { HttpClient.logger(message) }
    }
}
