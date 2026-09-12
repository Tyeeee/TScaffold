package com.tscaffold.basic.mmkv

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * MMKV 的封装 —— 全工程唯一碰 MMKV 的地方。
 *
 * 写法照着携程那套（`ctripcorp/mmkv-kotlin`）：**不另造数据结构、不做 JSON 转换**，
 * 只是把 MMKV 的 `encode/decode` 换成读起来顺手的名字：写是 `set`，读是 `takeXxx`。
 * 对象序列化那种事归以后的 GsonUtil 管，这里不管。
 *
 * 用法：
 * ```
 * MMKVUtils.set("token", token)
 * val token = MMKVUtils.takeString("token")
 *
 * MMKVUtils.set("readIds", setOf("1", "2"))
 * val count = MMKVUtils.takeInt("count", 0)
 * ```
 *
 * 初始化只有 Startup 一条路：component_basic 用 AndroidX Startup 挂了 [MMKVInitializer]，
 * 进程一起来就配好了，**不用（也不要）自己在 Application 里写初始化**。要换文件名或者加密，见 [init] 的说明。
 */
object MMKVUtils {

    private const val DEFAULT_STORAGE_ID = "tscaffold"

    private var context: Context? = null
    private var storageId: String = DEFAULT_STORAGE_ID
    private var cryptKey: String? = null

    /**
     * 真正的 MMKV 实例，第一次读写时才建。
     *
     * [init] 只把配置记下来，不打开文件 —— Startup 那一步要尽量便宜；
     * 也因为这样，要加密只是在 Startup 里换个配置，不会撞上"文件已经用别的 key 打开过"。
     */
    private var opened: MMKV? = null

    private val store: MMKV
        get() = opened ?: open()

    /**
     * 初始化。**只在 Startup 的 Initializer 里调**（默认那次是 [MMKVInitializer] 调的）。
     *
     * 要换 storageId 或者加密，也不要写到 Application 里去 —— 再写一个 Initializer，
     * 在 `dependencies()` 里声明依赖 [MMKVInitializer]，这样它先跑（把默认配置铺好），
     * 你再按自己的参数覆盖一次。这时还没有人读写过，覆盖是安全的：
     * ```
     * class MyInitializer : Initializer<Unit> {
     *     override fun create(context: Context) {
     *         MMKVUtils.init(context, storageId = "my_app", cryptKey = "0123456789abcdef")
     *     }
     *     override fun dependencies() = listOf(MMKVInitializer::class.java)
     * }
     * ```
     * 别忘了在清单里加一条指向它的 meta-data（照抄 component_basic 里那条）。
     *
     * @param storageId 存储文件名，不同 id 互相隔离
     * @param cryptKey 传了就整份加密（不超过 16 字节），登录态这类东西建议传
     */
    fun init(context: Context, storageId: String = DEFAULT_STORAGE_ID, cryptKey: String? = null) {
        // applicationContext：别让谁拿 Activity 进来把页面漏住
        this.context = context.applicationContext
        this.storageId = storageId
        this.cryptKey = cryptKey
        this.opened = null
    }

    private fun open(): MMKV {
        val context = context ?: error(
            "MMKVUtils 还没初始化：Startup 会跑 MMKVInitializer，会走到这说明清单里那条 " +
                "meta-data 被去掉了（或者在它之前就有人读写了存储）",
        )
        MMKV.initialize(context)
        val kv = if (cryptKey.isNullOrEmpty()) {
            MMKV.mmkvWithID(storageId)
        } else {
            MMKV.mmkvWithID(storageId, MMKV.SINGLE_PROCESS_MODE, cryptKey)
        }
        opened = kv
        return kv
    }

    // ---------------- 写 ----------------

    operator fun set(key: String, value: Boolean) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: Int) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: Long) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: Float) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: Double) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: String) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: Set<String>) {
        store.encode(key, value)
    }

    operator fun set(key: String, value: ByteArray) {
        store.encode(key, value)
    }

    // ---------------- 读 ----------------

    fun takeBoolean(key: String, default: Boolean = false): Boolean = store.decodeBool(key, default)

    fun takeInt(key: String, default: Int = 0): Int = store.decodeInt(key, default)

    fun takeLong(key: String, default: Long = 0L): Long = store.decodeLong(key, default)

    fun takeFloat(key: String, default: Float = 0f): Float = store.decodeFloat(key, default)

    fun takeDouble(key: String, default: Double = 0.0): Double = store.decodeDouble(key, default)

    fun takeString(key: String, default: String = ""): String = store.decodeString(key, default) ?: default

    fun takeStringSet(key: String, default: Set<String> = emptySet()): Set<String> =
        store.decodeStringSet(key, default) ?: default

    /** 没写过就是 null。 */
    fun takeBytes(key: String): ByteArray? = store.decodeBytes(key)

    // ---------------- 管理 ----------------

    fun contains(key: String): Boolean = store.containsKey(key)

    fun allKeys(): List<String> = store.allKeys()?.toList().orEmpty()

    /** 存了多少个 key。 */
    fun count(): Long = store.count()

    fun remove(key: String) {
        store.removeValueForKey(key)
    }

    fun clear() {
        store.clearAll()
    }

    /** 立刻落盘。正常写是异步的，杀进程前想确保写下去就调它。 */
    fun sync() {
        store.sync()
    }
}
