package com.tscaffold.basic.network.websocket

import okio.ByteString

/**
 * 从服务端收到的一帧。
 *
 * 底座只给**原始帧**，不猜你的业务信封（`{cmd, data, id}` 那套各家不一样），
 * 编解码放在业务自己的协议文件里 —— 和 HTTP 那边把 `Envelope` 放数据层是同一个道理。
 */
sealed interface WebSocketMessage {

    /** 文本帧。 */
    data class Text(val text: String) : WebSocketMessage

    /** 二进制帧（母工程那套只处理了文本帧，二进制帧会被 OkHttp 默认实现悄悄丢掉）。 */
    data class Binary(val bytes: ByteString) : WebSocketMessage
}
