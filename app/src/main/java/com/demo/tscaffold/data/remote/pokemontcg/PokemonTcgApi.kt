package com.demo.tscaffold.data.remote.pokemontcg

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * ==================== TCGdex：宝可梦卡牌数据的接口声明 ====================
 *
 * 数据源是 [TCGdex](https://tcgdex.dev)，**免费、不需要 API Key、无需认证、无硬性限流**，
 * 其数据仓库 `tcgdex/cards-database` 是 **MIT 协议**（可商用，保留版权声明即可）。
 *
 * 下面的端点清单是**按真实响应逐个验证过的**，不是照抄文档 —— 文档里至少有两处是错的，
 * 详见本节末尾的「已核实 / 已排除」。
 *
 * ## 一、用之前必须先把 baseUrl 设成这个
 *
 * ```
 * Network.init(baseUrl = "https://api.tcgdex.net/v2/")
 * ```
 *
 * ⚠️ **这里有个绕不开的冲突，接之前先想清楚：**
 *
 * [com.tscaffold.basic.network.http.RetrofitService] 是**单例 object，全 App 只有一个 baseUrl**。
 * 一旦把它设成本文件的 TCGdex 地址，同一进程里 [com.demo.tscaffold.data.remote.article.ArticleApi]
 * 那些相对路径就会全部打到 TCGdex 上去（那是本工程的示例后端，不是真接口，所以现在无所谓；
 * 但接了真后端就会打架）。
 *
 * 三条出路，按推荐顺序：
 * 1. **等缓存方案定了一起做**（当前推荐）：数据层落地后，TCGdex 只在"同步/预置数据"时用，
 *    运行期读本地，两者不需要共存于一个 Retrofit 实例；
 * 2. 给网络底座加"多命名 Retrofit 实例"的能力（改 `RetrofitService`，属于底座改动）；
 * 3. 把下面的注解路径换成**绝对地址**（`@GET("https://api.tcgdex.net/v2/{lang}/cards")`）——
 *    Retrofit 支持，能绕过单 baseUrl 限制，代价是路径散落在注解里、可读性变差。
 *
 * ## 二、过滤 / 排序 / 分页（所有返回数组的端点都支持）
 *
 * ### 过滤：`?字段名=值`，靠**前缀**决定匹配方式
 *
 * | 前缀 | 含义 | 例子 |
 * |---|---|---|
 * | 无 / `like:` | 模糊包含，大小写不敏感 | `name=abo` → Abomasnow、Pumpkaboo |
 * | `*` | 首尾锚定 | `name=fu*` → Furret（不含 Stufful） |
 * | `not:` / `notlike:` | 模糊排除 | `name=not:fu` |
 * | `eq:` | **严格相等** | `name=eq:Furret` |
 * | `neq:` | 严格不等 | |
 * | `gte:` `lte:` `gt:` `lt:` | 数值比较 | `hp=gte:50` |
 * | `null:` / `notnull:` | 空 / 非空 | |
 * | `\|` | 多值或 | `name=eq:Furret\|Pikachu` |
 *
 * 字段名可以是**响应里没有的**字段 —— 这一点很关键：
 * `CardBriefDto` 不含 `rarity`，但 `rarity=eq:Rare Holo` 照样能过滤。
 * 所以想拿到"全库带稀有度的清单"，就遍历 `rarities()` 的结果逐个过滤，而不是发两万次单卡请求。
 *
 * 用法（`filters` 就是那个可变的 [Map]）：
 * ```
 * api.cards(LANG_EN, filters = mapOf("rarity" to "eq:Rare Holo"))
 * api.cards(LANG_EN, filters = mapOf("set.id" to "base1", "hp" to "gte:100"))
 * api.card(LANG_EN, "base1-1")
 * ```
 *
 * ### 排序
 * `sortField` 传对象的任意字段名（必须真实存在），`sortOrder` 传 [SORT_ASC] / [SORT_DESC]。
 * 默认顺序是 `releaseDate > localId > id`。
 *
 * ### 分页：**不加参数会一次返回全部**
 * 这是最反直觉的一点。官方原话是 "Pagination is not done automatically"：
 * - **完全不加** `page` / `itemsPerPage` → 一次性返回**所有**结果；
 * - 只要加了 `page` → `itemsPerPage` 默认变成 **100**。
 *
 * 实测 `itemsPerPage = 1000` 有效（一次真能返回 1000 条）。
 * 全库约 2 万张，用 1000 一页大约 20 个请求能拉完 —— 但**别在 UI 主路径上这么干**，
 * 这是给构建期/后台同步用的。
 *
 * ## 三、图片地址要自己拼（这是另一个 host）
 *
 * 所有 `image` / `logo` / `symbol` 字段**故意不带扩展名**，形如
 * `https://assets.tcgdex.net/en/base/base1/1`。要自己补上 `/{quality}.{ext}`：
 *
 * | 维度 | 取值 |
 * |---|---|
 * | `quality` | `high` = 600×825 ／ `low` = 245×337 |
 * | `ext` | `webp`（推荐，透明+体积小）／ `png`（透明）／ `jpg`（黑底，不推荐） |
 *
 * ⚠️ `high` 只有 600×825，做全屏全息渲染偏小；需要更大分辨率时用
 * `https://images.pokemontcg.io/{setId}/{localId}_hires.png`（另一个源，实测国内更快）。
 *
 * ## 四、错误响应有两种形状（`ErrorMapper` 要都认）
 *
 * - **资源不存在** → `{"error": "Endpoint or id not found"}`（简化版）
 * - **语言或路径非法** → `{"type":"…/errors/not-found","title":"…","status":404,"endpoint":"…","method":"GET"}`
 *
 * 现有 [com.tscaffold.basic.network.http.impl.ErrorMapper] 是按 HTTP 状态码翻译的，
 * 不看响应体 —— 对这两种都能给出"内容不存在"，够用。要拿服务端的原文再说时再改。
 *
 * ## 五、已核实 / 已排除（都不是照文档抄的）
 *
 * ✅ **已核实可用（本接口里的 13 个）**，全部返回过 HTTP 200。
 *
 * ❌ **`hps` —— 文档写错了，实际是 `hp`（单数）。**
 * 文档 `/rest/other-fields` 写的是 `hps`，实测 `GET /v2/en/hps` 返回 **404**，
 * 而 `GET /v2/en/hp` 返回 200 和 `[10,30,40,…]`。这里按真实端点写成 [hpValues]。
 *
 * ❌ **`status` —— 不是 JSON 接口，已排除。**
 * 文档提到的项目状态页在 `https://api.tcgdex.net/status`（**注意不在 `/v2` 下**），
 * 返回的是 **HTML 网页**（447 KB），不是 JSON。放进 Retrofit 只会解析失败。
 *
 * ❌ **GraphQL（`POST /v2/graphql`）—— 已排除。**
 * 端点存在，但官方文档标注 "Full documentation in progress"，且实测连续两次
 * 60 秒超时、拿不到响应。按"只放有效接口"的原则不收进来；
 * 将来确认可用、且确实需要按字段投影时再单独加。
 *
 * ## 六、稳定性提醒（设计时就得认）
 *
 * 这个 API 免费且内容完整，但**响应时间完全不可预测**：实测同一个端点
 * `GET /v2/en/sets/base1` 一次 6.2 秒成功、下一次 15 秒超时；
 * 单个 `/v2/en/cards/base1-1` 在 1.9s ~ 9.6s 之间跳。
 *
 * 所以：`readTimeout` 别低于 30 秒（[com.tscaffold.basic.network.Network.init] 的默认值正好是 30 秒），
 * 并且**不要**在页面打开时同步等它 —— 这正是接下来要做缓存的原因。
 */
interface PokemonTcgApi {

    companion object {

        /**
         * 语言代码。**它是 URL 的一部分**（`/v2/{lang}/...`），不是请求头。
         *
         * 英语是数据最完整的一档。其他语言覆盖度参差不齐 ——
         * 实测 `zh-tw` 连 `base1-1` 都还是 404（基础系列没翻译），
         * 所以要上中文界面的话，数据仍取 `en`，只在展示层做术语映射。
         */
        const val LANG_EN = "en"

        /** 法语（覆盖较全）。 */
        const val LANG_FR = "fr"

        /** 西班牙语。 */
        const val LANG_ES = "es"

        /** 德语。 */
        const val LANG_DE = "de"

        /** 意大利语。 */
        const val LANG_IT = "it"

        /** 巴西葡萄牙语。 */
        const val LANG_PT_BR = "pt-br"

        /** 日语。 */
        const val LANG_JA = "ja"

        /** 繁体中文（**部分数据缺失**）。 */
        const val LANG_ZH_TW = "zh-tw"

        /** 印尼语。 */
        const val LANG_ID = "id"

        /** 泰语。 */
        const val LANG_TH = "th"

        /** `sort:order` 的升序值。 */
        const val SORT_ASC = "ASC"

        /** `sort:order` 的降序值。 */
        const val SORT_DESC = "DESC"

        /**
         * 分页的每页条数上限 —— 实测 1000 能真的返回 1000 条。
         *
         * 再往上会变成"让服务端一次吐 2 MB"的请求，很容易超时，不要用。
         */
        const val PAGE_SIZE_MAX = 1000
    }

    // ==================================================================
    // 卡片
    // ==================================================================

    /**
     * 搜索 / 列表卡片，返回 [CardBriefDto]（**只有 id / localId / name / image，没有 rarity**）。
     *
     * ```
     * GET /v2/{lang}/cards
     * GET /v2/{lang}/cards?name=pikachu
     * GET /v2/{lang}/cards?rarity=eq:Rare Holo&pagination:page=1&pagination:itemsPerPage=1000
     * ```
     *
     * ⚠️ 不传分页参数 = 一次性返回全库约 2 万条。列表页请务必显式传 [page] / [itemsPerPage]。
     *
     * @param filters 任意字段过滤，键是字段名（支持 `set.id` 这种嵌套写法），值可带前缀
     *                （`eq:` / `gte:` / `not:` …，见接口头上那张表）。
     */
    @GET("{lang}/cards")
    suspend fun cards(
        @Path("lang") lang: String,
        @Query("pagination:page") page: Int? = null,
        @Query("pagination:itemsPerPage") itemsPerPage: Int? = null,
        @Query("sort:field") sortField: String? = null,
        @Query("sort:order") sortOrder: String? = null,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): List<CardBriefDto>

    /**
     * 取**单张卡的完整数据** —— 这是唯一能拿到 `rarity` 和 `variants` 的途径。
     *
     * ```
     * GET /v2/{lang}/cards/base1-1
     * ```
     *
     * 实测单卡响应约 3.3 KB（其中大部分是这里**故意没建模**的 `variants_detailed` 价格块，
     * 理由见 [CardDto] 的注释）。
     *
     * @param cardId 形如 `"base1-1"`、`"swsh3-136"`。
     */
    @GET("{lang}/cards/{cardId}")
    suspend fun card(
        @Path("lang") lang: String,
        @Path("cardId") cardId: String,
    ): CardDto

    // ==================================================================
    // 套牌
    // ==================================================================

    /**
     * 套牌列表，返回 [SetBriefDto]。
     *
     * ```
     * GET /v2/{lang}/sets
     * GET /v2/{lang}/sets?sort:field=releaseDate&sort:order=DESC&pagination:page=1&pagination:itemsPerPage=20
     * ```
     *
     * 实测共 **218 个套牌**，整个列表约 35 KB —— 这个数据量**一次全拉下来存本地**完全合理，
     * 是缓存的第一优先目标。
     */
    @GET("{lang}/sets")
    suspend fun sets(
        @Path("lang") lang: String,
        @Query("pagination:page") page: Int? = null,
        @Query("pagination:itemsPerPage") itemsPerPage: Int? = null,
        @Query("sort:field") sortField: String? = null,
        @Query("sort:order") sortOrder: String? = null,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): List<SetBriefDto>

    /**
     * 套牌详情，**内嵌该套牌的全部卡片**（简要形式）。
     *
     * ```
     * GET /v2/{lang}/sets/base1
     * ```
     *
     * 实测 `base1`（102 张）约 11 KB，平均每张卡约 108 字节。
     * "一个套牌一个请求"是很划算的缓存粒度 —— 拉一次就能离线浏览整个套牌。
     *
     * ⚠️ 内嵌的 [SetDto.cards] **不含 `rarity`**，要稀有度还得逐张调 [card]。
     */
    @GET("{lang}/sets/{setId}")
    suspend fun set(
        @Path("lang") lang: String,
        @Path("setId") setId: String,
    ): SetDto

    /**
     * 按「套牌 ID + 卡面编号」取单卡完整数据。
     *
     * ```
     * GET /v2/{lang}/sets/base1/1        → 等价于 GET /v2/{lang}/cards/base1-1
     * GET /v2/{lang}/sets/swsh3/136      → 等价于 GET /v2/{lang}/cards/swsh3-136
     * ```
     *
     * 跟 [card] 取到的东西一模一样，只是入口不同。已经有了套牌的 `id` 和卡片的 `localId`
     * 时，用它不用自己拼 `"${setId}-${localId}"`。
     *
     * ⚠️ [localId] 声明成 `String`：它可能是 `"1"` 也可能是 `"XY95"`、`"TG01"`，
     * 不是纯数字，别用 Int。
     */
    @GET("{lang}/sets/{setId}/{localId}")
    suspend fun cardInSet(
        @Path("lang") lang: String,
        @Path("setId") setId: String,
        @Path("localId") localId: String,
    ): CardDto

    // ==================================================================
    // 系列
    // ==================================================================

    /**
     * 系列列表，返回 [SerieBriefDto]（**只有 id 和 name**）。
     *
     * ```
     * GET /v2/{lang}/series
     * ```
     *
     * 实测约 1.6 KB，可以直接全量存本地。
     */
    @GET("{lang}/series")
    suspend fun series(
        @Path("lang") lang: String,
        @Query("pagination:page") page: Int? = null,
        @Query("pagination:itemsPerPage") itemsPerPage: Int? = null,
        @Query("sort:field") sortField: String? = null,
        @Query("sort:order") sortOrder: String? = null,
        @QueryMap filters: Map<String, String> = emptyMap(),
    ): List<SerieBriefDto>

    /**
     * 系列详情，**内嵌该系列下的全部套牌**。
     *
     * ```
     * GET /v2/{lang}/series/base
     * ```
     *
     * 实测约 1.6 KB（Base 系列只有几个套牌）。用它做"系列 → 套牌"的两级导航。
     */
    @GET("{lang}/series/{serieId}")
    suspend fun serie(
        @Path("lang") lang: String,
        @Path("serieId") serieId: String,
    ): SerieDto

    // ==================================================================
    // 字段枚举（做筛选面板用，全部无参数、体积极小）
    // ==================================================================

    /**
     * 卡牌类别。实测返回 `["Energy","Pokemon","Trainer"]`（约 30 字节）。
     *
     * ```
     * GET /v2/{lang}/categories
     * ```
     */
    @GET("{lang}/categories")
    suspend fun categories(@Path("lang") lang: String): List<String>

    /**
     * 全部 HP 取值。实测返回 `[10,30,40,…,330]`（约 137 字节）。
     *
     * ```
     * GET /v2/{lang}/hp
     * ```
     *
     * ⚠️ **路径是 `hp` 单数**。官方文档写的是 `hps`，那是错的 —— `hps` 实测 404。
     */
    @GET("{lang}/hp")
    suspend fun hpValues(@Path("lang") lang: String): List<Int>

    /**
     * 全部画师。实测约 5.9 KB。
     *
     * ```
     * GET /v2/{lang}/illustrators
     * ```
     */
    @GET("{lang}/illustrators")
    suspend fun illustrators(@Path("lang") lang: String): List<String>

    /**
     * 全部稀有度。实测返回 **40 个**字符串（约 571 字节）。
     *
     * ```
     * GET /v2/{lang}/rarities
     * ```
     *
     * 配合 [cards] 的 `rarity=eq:xxx` 过滤，是"批量拿到带稀有度的全库清单"的关键：
     * 遍历这个列表逐个过滤，约 40 个请求就能覆盖全库，而不用发两万次单卡请求。
     */
    @GET("{lang}/rarities")
    suspend fun rarities(@Path("lang") lang: String): List<String>

    /**
     * 全部撤退费用取值。实测返回 `[1,2,3,4,5]`（约 11 字节）。
     *
     * ```
     * GET /v2/{lang}/retreats
     * ```
     */
    @GET("{lang}/retreats")
    suspend fun retreatCosts(@Path("lang") lang: String): List<Int>

    /**
     * 全部属性（草/火/水…）。实测约 105 字节。
     *
     * ```
     * GET /v2/{lang}/types
     * ```
     *
     * ⚠️ 这个端点在实测里**偶尔会慢到 12 秒**，别放在启动主路径上同步等。
     */
    @GET("{lang}/types")
    suspend fun types(@Path("lang") lang: String): List<String>
}
