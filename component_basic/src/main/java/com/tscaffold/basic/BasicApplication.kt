package com.tscaffold.basic

import android.app.Application

/**
 * 应用的 Application，直接用这个类就行（app 的清单里 `android:name` 指向它），
 * 不用为了初始化再继承一层。
 *
 * **初始化不写在这里。** 这个工程里所有"进程一起来就要做"的事都挂到 AndroidX Startup 上：
 * 写一个 `Initializer`，再去清单里 `androidx.startup.InitializationProvider` 的 meta-data
 * 加一条指向它。比如：
 *
 * - 基础能力：`com.tscaffold.basic.mmkv.MMKVInitializer`（MMKV，component_basic 的清单里挂着）
 * - 本 App 的配置：`com.demo.tscaffold.AppInitializer`（调试日志、接口地址，app 的清单里挂着）
 *
 * 这么放的好处是顺序可控（`dependencies()` 里声明先后）、多进程不会重复跑、
 * 也不用再去想"初始化到底写在哪个 Application 里"。
 */
open class BasicApplication : Application()
