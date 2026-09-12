package com.tscaffold.basic.provider

import android.content.Context
import androidx.startup.Initializer
import com.tscaffold.basic.mmkv.MMKVUtils

/**
 * 把 MMKV 的初始化挂到进程启动（AndroidX Startup）。
 *
 * `provider` 这个包**专门放启动初始化**：一个能力一个 Initializer，
 * 清单里 `androidx.startup.InitializationProvider` 的 meta-data 指向它们。
 * 能力自己的代码留在自己的包里（MMKV 的封装在 `mmkv/`，网络底座在 `network/`），
 * 这样"什么时候初始化"和"怎么用"是两处，各自看得清。
 *
 * 它和 component_basic 的 AndroidManifest 里那条 `meta-data` 是一对：清单里注册的是这个类，
 * 这里干的是调一次 [MMKVUtils.init]。
 *
 * 想换 storageId 或加密，不要跑去 Application 里调 init，另外写一个 Initializer，
 * 在 [dependencies] 里声明依赖这个类，按 Startup 的顺序覆盖配置 —— 写法见 [MMKVUtils.init]。
 */
class MMKVInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        MMKVUtils.init(context)
    }

    /** 没有前置依赖，可以最先跑。 */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
