package com.tscaffold.basic.mmkv

import android.content.Context
import androidx.startup.Initializer

/**
 * 把 MMKV 的初始化挂到进程启动上（AndroidX Startup）—— 这是唯一的初始化入口，
 * `Application` 里一行都不用写。
 *
 * 它和 component_basic 的 AndroidManifest 里那条 `meta-data` 是一对：清单里注册的是这个类，
 * 这里干的是调一次 [MMKVUtils.init]。
 *
 * 想换 storageId 或加密，不要跑去 Application 里调 init，另外写一个 Initializer，
 * 在 [dependencies] 里声明依赖这个类，按 Startup 的顺序覆盖配置 —— 具体写法见 [MMKVUtils.init]。
 */
class MMKVInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        MMKVUtils.init(context)
    }

    /** 没有前置依赖，可以最先跑。 */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
