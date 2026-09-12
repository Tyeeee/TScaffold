package com.demo.tscaffold.data.remote.pokemontcg

import com.tscaffold.basic.network.http.RetrofitService
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PokemonTcgApi] 的"真实场景"测试：真起本地服务器，走真的 Retrofit + 真的 Gson。
 *
 * ## 为什么这个测试必须存在
 *
 * 光"编译通过"证明不了这个接口是对的。下面这些都只有**在真发一次请求**时才会暴露：
 *
 * 1. **Kotlin 的默认参数值能不能穿过 Retrofit 的动态代理。**
 *    接口里写了 `page: Int? = null`，但 Retrofit 是靠反射看注解的 —— 默认值到底有没有生效、
 *    省略的参数会不会被当成 `null` 拼进 URL，只能实测。
 *
 * 2. **`pagination:page` 里的冒号。**
 *    Retrofit / OkHttp 会把查询参数名里的 `:` 编码成 `%3A`，也就是实际发出去的是
 *    `?pagination%3Apage=1`。这个 API 认不认（实测是认的），这里用断言钉住。
 *
 * 3. **`@QueryMap` 拼出来的任意字段过滤。**
 *
 * 4. **`localId` 是"字符串或数字"。** 后端偶尔给数字，而 DTO 里声明的是 `String?` ——
 *    Gson 到底会不会替我们把数字转成字符串，这个不测就是赌。
 *
 * 5. **故意没建模的 `variants_detailed` 不会导致解析失败。**
 */
class PokemonTcgApiScenarioTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PokemonTcgApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // 用工程自己的 RetrofitService，测的就是真实那套 OkHttp + Gson 配置
        RetrofitService.baseUrl = server.url("/v2/").toString()
        api = RetrofitService.create()
    }

    @After
    fun tearDown() {
        server.close()
    }

    // ==================== 1. 默认参数 / 路径拼接 ====================

    @Test
    fun `只传语言：默认参数真的生效，没用到参数不会出现在 URL 上`() = runBlocking {
        server.enqueue(json("[]"))

        api.cards(PokemonTcgApi.LANG_EN)

        val request = server.takeRequest()
        assertEquals("/v2/en/cards", request.url.encodedPath)
        // 默认值生效的证据：没有 NPE，也真的发出去了一次请求
        assertEquals("GET", request.method)
        // null 参数应该被彻底省略，而不是发成 ?page=null
        assertNull(request.url.queryParameter("pagination:page"))
        assertNull(request.url.queryParameter("pagination:itemsPerPage"))
        assertNull(request.url.queryParameter("sort:field"))
    }

    @Test
    fun `分页和排序：带冒号的参数名照样拼对`() = runBlocking {
        server.enqueue(json("[]"))

        api.cards(
            PokemonTcgApi.LANG_EN,
            page = 2,
            itemsPerPage = PokemonTcgApi.PAGE_SIZE_MAX,
            sortField = "releaseDate",
            sortOrder = PokemonTcgApi.SORT_DESC,
        )

        val request = server.takeRequest()
        // queryParameter() 会自动解码，所以能拿回带冒号的原名 —— 证明确实往返对上了
        assertEquals("2", request.url.queryParameter("pagination:page"))
        assertEquals("1000", request.url.queryParameter("pagination:itemsPerPage"))
        assertEquals("releaseDate", request.url.queryParameter("sort:field"))
        assertEquals("DESC", request.url.queryParameter("sort:order"))

        // 钉住编码行为：实际发出去的是 %3A。这个 API 接受这种编码（实测过），
        // 但如果哪天换成不接受编码冒号的后端，这行断言会第一时间炸出来。
        assertTrue(
            "实际 query = ${request.url.encodedQuery}",
            request.url.encodedQuery!!.contains("pagination%3Apage=2"),
        )
    }

    @Test
    fun `QueryMap 过滤：任意字段都能当查询参数`() = runBlocking {
        server.enqueue(json("[]"))

        api.cards(
            PokemonTcgApi.LANG_EN,
            filters = mapOf("rarity" to "eq:Rare Holo", "set.id" to "base1"),
        )

        val request = server.takeRequest()
        assertEquals("eq:Rare Holo", request.url.queryParameter("rarity"))
        assertEquals("base1", request.url.queryParameter("set.id"))
    }

    @Test
    fun `单卡：cardId 正确替换进路径`() = runBlocking {
        server.enqueue(json(CARD_JSON))

        val card = api.card(PokemonTcgApi.LANG_EN, "swsh3-136")

        assertEquals("/v2/en/cards/swsh3-136", server.takeRequest().url.encodedPath)
        assertEquals("Alakazam", card.name)
        assertEquals("Rare", card.rarity)
    }

    @Test
    fun `按套牌取卡：setId 和 localId 都进路径`() = runBlocking {
        server.enqueue(json(CARD_JSON))

        api.cardInSet(PokemonTcgApi.LANG_EN, "base1", "XY95")

        // localId 不一定是数字（"XY95" / "TG01"），所以路径参数必须是 String
        assertEquals("/v2/en/sets/base1/XY95", server.takeRequest().url.encodedPath)
    }

    // ==================== 2. 反序列化：Gson 的那些坑 ====================

    @Test
    fun `localId 后端给数字时也能收成字符串`() = runBlocking {
        // 接口文档写的是 "String or Number"，这里模拟后端给数字的情况
        server.enqueue(json("""[{"id":"base1-1","localId":1,"name":"Alakazam"}]"""))

        val first = api.cards(PokemonTcgApi.LANG_EN).single()

        assertEquals("1", first.localId)
    }

    @Test
    fun `故意没建模的 variants_detailed 不会让解析失败`() = runBlocking {
        // 真实响应里这个数组才是体积大头（约 3 KB），我们没给它建模型。
        // Gson 遇到模型里没有的键会跳过，这个测试就是确认"跳过"不会出问题。
        server.enqueue(json(CARD_JSON))

        val card = api.card(PokemonTcgApi.LANG_EN, "base1-1")

        assertEquals("Rare", card.rarity)
        assertNotNull(card.pricing)
        assertEquals("EUR", card.pricing!!.cardmarket!!.unit)
    }

    @Test
    fun `套牌详情：内嵌的 cards 数组和 cardCount 细分项都能解析`() = runBlocking {
        server.enqueue(
            json(
                """
                {
                  "id": "base1", "name": "Base Set",
                  "cardCount": {"official":102,"total":103,"holo":64,"reverse":0,"firstEd":103},
                  "releaseDate": "1999-01-09",
                  "serie": {"id":"base","name":"Base"},
                  "legal": {"standard":false,"expanded":false},
                  "cards": [
                    {"id":"base1-1","localId":"1","name":"Alakazam",
                     "image":"https://assets.tcgdex.net/en/base/base1/1"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val set = api.set(PokemonTcgApi.LANG_EN, "base1")

        assertEquals("/v2/en/sets/base1", server.takeRequest().url.encodedPath)
        assertEquals("Base Set", set.name)

        val counts = set.cardCount!!
        assertEquals(103, counts.total)
        assertEquals(64, counts.holo)

        assertEquals("Base", set.serie!!.name)

        val cards = set.cards!!
        assertEquals(1, cards.size)
        assertEquals("Alakazam", cards.first().name)
    }

    // ==================== 3. 字段枚举端点的返回类型 ====================

    @Test
    fun `数值型枚举：返回 List Int 而不是字符串数组`() = runBlocking {
        server.enqueue(json("[10,30,40,50]"))

        val hps = api.hpValues(PokemonTcgApi.LANG_EN)

        // 路径是 hp 单数（文档里写的 hps 是错的，实测 404）
        assertEquals("/v2/en/hp", server.takeRequest().url.encodedPath)
        assertEquals(listOf(10, 30, 40, 50), hps)
    }

    @Test
    fun `撤退费用枚举同样是 List Int`() = runBlocking {
        server.enqueue(json("[1,2,3,4,5]"))

        assertEquals(listOf(1, 2, 3, 4, 5), api.retreatCosts(PokemonTcgApi.LANG_EN))
        assertEquals("/v2/en/retreats", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `字符串型枚举：稀有度列表`() = runBlocking {
        server.enqueue(json("""["ACE SPEC Rare","Amazing Rare","Common","Rare Holo"]"""))

        val rarities = api.rarities(PokemonTcgApi.LANG_EN)

        assertEquals("/v2/en/rarities", server.takeRequest().url.encodedPath)
        assertEquals(4, rarities.size)
        assertTrue("Rare Holo" in rarities)
    }

    @Test
    fun `系列详情：内嵌的 sets 数组是 SetBrief`() = runBlocking {
        server.enqueue(
            json(
                """
                {
                  "id":"base","name":"Base",
                  "firstSet":{"id":"base1","name":"Base Set","cardCount":{"official":102,"total":102}},
                  "lastSet":{"id":"base5","name":"Team Rocket","cardCount":{"official":82,"total":83}},
                  "sets":[
                    {"id":"base1","name":"Base Set","cardCount":{"official":102,"total":102}}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val serie = api.serie(PokemonTcgApi.LANG_EN, "base")

        assertEquals("/v2/en/series/base", server.takeRequest().url.encodedPath)
        assertEquals("Base", serie.name)
        assertEquals("base5", serie.lastSet!!.id)
        assertEquals(1, serie.sets!!.size)
    }

    // ==================== 4. 全部 13 个端点的路径一次性钉住 ====================

    @Test
    fun `全部端点都打在正确的路径上`() = runBlocking {
        // 逐个断言而不是塞进 listOf：这些调用的返回类型各不相同
        // （List<String> / List<Int> / List<SetBriefDto> …），放进集合会推不出公共类型。
        assertPath("/v2/en/categories") { api.categories(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/illustrators") { api.illustrators(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/rarities") { api.rarities(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/types") { api.types(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/hp") { api.hpValues(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/retreats") { api.retreatCosts(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/series") { api.series(PokemonTcgApi.LANG_EN) }
        assertPath("/v2/en/sets") { api.sets(PokemonTcgApi.LANG_EN) }
    }

    /** 起一个空数组响应，发一次请求，断言落在哪个路径上。 */
    private suspend fun assertPath(expectedPath: String, call: suspend () -> Any?) {
        server.enqueue(json("[]"))
        call()
        assertEquals(expectedPath, server.takeRequest().url.encodedPath)
    }

    // ==================== 工具 ====================

    private fun json(body: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type: application/json")
        .body(body)
        .build()

    private companion object {
        /**
         * 一张贴近真实响应的卡（截取自 `base1-1` 的真实结构）。
         * 特意保留了 `variants_detailed` —— 因为我们没有给它建模，
         * 要确认它存在时解析仍然正常。
         */
        val CARD_JSON = """
            {
              "category": "Pokemon",
              "id": "base1-1",
              "illustrator": "Ken Sugimori",
              "image": "https://assets.tcgdex.net/en/base/base1/1",
              "localId": "1",
              "name": "Alakazam",
              "rarity": "Rare",
              "set": {
                "cardCount": {"official":102,"total":102},
                "id": "base1",
                "logo": "https://assets.tcgdex.net/en/base/base1/logo",
                "name": "Base Set"
              },
              "variants": {"firstEdition":true,"holo":true,"normal":false,"reverse":false,"wPromo":false},
              "variants_detailed": [
                {
                  "type": "holo",
                  "subtype": "unlimited",
                  "size": "standard",
                  "thirdParty": {"cardmarket":273696,"tcgplayer":42346},
                  "variantId": "4ffrmhcfiaejakhepqdkx7o"
                }
              ],
              "dexId": [65],
              "hp": 80,
              "types": ["Psychic"],
              "evolveFrom": "Kadabra",
              "description": "Its brain can outperform a supercomputer.",
              "stage": "Stage2",
              "attacks": [
                {"cost":["Psychic","Psychic","Psychic"],"name":"Confuse Ray",
                 "effect":"Flip a coin. If heads, the Defending Pokemon is now Confused.","damage":30}
              ],
              "weaknesses": [{"type":"Psychic","value":"x2"}],
              "retreat": 3,
              "legal": {"standard":false,"expanded":false},
              "pricing": {
                "cardmarket": {"updated":"2026-09-11T10:22:57.566Z","unit":"EUR","avg":68.73,"avg-holo":7.99},
                "tcgplayer": {"updated":"2026-09-11T10:22:57.890Z","unit":"USD",
                              "holofoil":{"productId":42346,"lowPrice":41.99,"marketPrice":55.93}}
              },
              "updated": "2026-08-20T08:25:49+01:00"
            }
        """.trimIndent()
    }
}
