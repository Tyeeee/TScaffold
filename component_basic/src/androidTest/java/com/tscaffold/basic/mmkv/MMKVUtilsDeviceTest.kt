package com.tscaffold.basic.mmkv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tencent.mmkv.MMKV
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MMKV 这层只能在设备上测：它的核是 C++ 编出来的 so，JVM 单测里加载不了，
 * 所以不能像网络底座那样在电脑上跑，也没有假实现 —— 测的就是真 MMKV、真写文件。
 *
 * 跑法（先接设备或开模拟器）：
 * ```
 * ./gradlew :component_basic:connectedDebugAndroidTest
 * ```
 *
 * 两个注意点：
 * 1. MMKV 2.x 只有 64 位库（AAR 里就 arm64-v8a / x86_64），32 位设备上加载不了。
 * 2. 方法名里不能带空格（所以这里用下划线）：androidTest 要编成 dex，
 *    而 DEX 040 以前的版本不允许方法名带空格。`src/test` 里的 JVM 测试不编 dex，没这限制。
 */
@RunWith(AndroidJUnit4::class)
class MMKVUtilsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 每个用例换一个干净的存储，免得被上一次跑剩下的数据影响。 */
    private fun use(storageId: String, cryptKey: String? = null) {
        MMKVUtils.init(context, storageId = storageId, cryptKey = cryptKey)
        MMKVUtils.clear()
    }

    @Test
    fun `各种类型写进去读出来`() {
        use("device-test-types")

        MMKVUtils.set("b", true)
        MMKVUtils.set("i", 42)
        MMKVUtils.set("l", 1_700_000_000_000L)
        MMKVUtils.set("f", 1.5f)
        MMKVUtils.set("d", 9.75)
        MMKVUtils.set("s", "abc")
        MMKVUtils.set("set", setOf("a", "b"))
        MMKVUtils.set("bytes", byteArrayOf(1, 2, 3))

        assertTrue(MMKVUtils.takeBoolean("b"))
        assertEquals(42, MMKVUtils.takeInt("i"))
        assertEquals(1_700_000_000_000L, MMKVUtils.takeLong("l"))
        assertEquals(1.5f, MMKVUtils.takeFloat("f"), 0f)
        assertEquals(9.75, MMKVUtils.takeDouble("d"), 0.0)
        assertEquals("abc", MMKVUtils.takeString("s"))
        assertEquals(setOf("a", "b"), MMKVUtils.takeStringSet("set"))
        assertArrayEquals(byteArrayOf(1, 2, 3), MMKVUtils.takeBytes("bytes"))
    }

    @Test
    fun `没写过的key给默认值`() {
        use("device-test-default")

        assertEquals("", MMKVUtils.takeString("nope"))
        assertEquals("兜底", MMKVUtils.takeString("nope", "兜底"))
        assertEquals(-1, MMKVUtils.takeInt("nope", -1))
        assertEquals(7L, MMKVUtils.takeLong("nope", 7L))
        assertEquals(0.5f, MMKVUtils.takeFloat("nope", 0.5f), 0f)
        assertEquals(2.5, MMKVUtils.takeDouble("nope", 2.5), 0.0)
        assertTrue(MMKVUtils.takeBoolean("nope", true))
        assertEquals(setOf("默认"), MMKVUtils.takeStringSet("nope", setOf("默认")))
        assertNull(MMKVUtils.takeBytes("nope"))
    }

    @Test
    fun `写完磁盘上真的有文件_重新打开也读得到`() {
        use("device-test-persist")
        MMKVUtils.set("token", "abc")
        MMKVUtils.set("count", 7)
        // 默认异步写，这里强制落盘再去看文件
        MMKVUtils.sync()

        val file = File(MMKV.initialize(context), "device-test-persist")
        assertTrue("存储文件应该在磁盘上：${file.absolutePath}", file.exists())
        assertTrue("存储文件不该是空的", file.length() > 0)

        // 重新配置一遍（实例会重建、重新打开同一份文件），数据还在
        MMKVUtils.init(context, storageId = "device-test-persist")
        assertEquals("abc", MMKVUtils.takeString("token"))
        assertEquals(7, MMKVUtils.takeInt("count"))
    }

    @Test
    fun `不同的storageId互相隔离`() {
        use("device-test-a")
        MMKVUtils.set("k", "A")

        use("device-test-b")
        MMKVUtils.set("k", "B")

        // 回到 a：没被 b 覆盖，也没被 b 的 clear 清掉
        MMKVUtils.init(context, storageId = "device-test-a")
        assertEquals("A", MMKVUtils.takeString("k"))
    }

    @Test
    fun `加密存储也读写正常`() {
        // MMKV 的 key 不超过 16 字节，这里正好 16
        use("device-test-crypt", cryptKey = "0123456789abcdef")

        MMKVUtils.set("token", "secret-value")
        MMKVUtils.sync()
        assertEquals("secret-value", MMKVUtils.takeString("token"))

        // 同一个 key 重新打开，还能读到
        MMKVUtils.init(context, storageId = "device-test-crypt", cryptKey = "0123456789abcdef")
        assertEquals("secret-value", MMKVUtils.takeString("token"))
    }

    @Test
    fun `删除_判断存在_列key_计数_清空`() {
        use("device-test-manage")
        MMKVUtils.set("a", "1")
        MMKVUtils.set("b", 2)

        assertEquals(2L, MMKVUtils.count())
        assertTrue(MMKVUtils.contains("a"))
        assertEquals(setOf("a", "b"), MMKVUtils.allKeys().toSet())

        MMKVUtils.remove("a")
        assertFalse(MMKVUtils.contains("a"))
        assertEquals("", MMKVUtils.takeString("a"))
        assertEquals(1L, MMKVUtils.count())

        MMKVUtils.clear()
        assertTrue(MMKVUtils.allKeys().isEmpty())
        assertEquals(0L, MMKVUtils.count())
    }
}
