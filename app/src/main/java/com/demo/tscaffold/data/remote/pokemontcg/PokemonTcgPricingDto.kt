package com.demo.tscaffold.data.remote.pokemontcg

import com.google.gson.annotations.SerializedName

/**
 * 卡价信息（卡片对象上的 `pricing` 字段）。
 *
 * 数据来自两个欧洲/北美市场：Cardmarket（EUR）和 TCGPlayer（USD）。
 *
 * ## 两个必须知道的坑
 *
 * **1. 官方文档在这个对象上写错了类型。**
 * 文档把 `updated` 和 `unit` 标成 `Number`，但真实响应里
 * `updated` 是 ISO8601 字符串（`"2026-09-11T10:22:57.566Z"`）、`unit` 是货币代码字符串（`"EUR"`）。
 * 这里**以真实响应为准写成 String** —— 照着文档写就会解析失败。
 *
 * **2. 字段名里带连字符。**
 * `avg-holo` / `reverse-holofoil` / `1st-edition-holofoil` 这些在 Kotlin 里不是合法标识符，
 * 必须靠 `@SerializedName` 映射。所以这个文件里**每个字段都显式写了 `@SerializedName`**，
 * 不是啰嗦，是必须。
 *
 * 另：如果只做卡面展示、不做比价，整个 `pricing` 都可以忽略 ——
 * Gson 遇到 JSON 里有、模型里没有的键会直接跳过，不声明就是天然的瘦身。
 */
data class PricingDto(
    @SerializedName("cardmarket") val cardmarket: CardmarketDto? = null,
    @SerializedName("tcgplayer") val tcgplayer: TcgPlayerDto? = null,
)

/**
 * Cardmarket（欧洲市场）价格。
 *
 * 非闪和闪卡的价格是**两套平行字段**：不带后缀的是非闪，带 `-holo` 后缀的是闪卡。
 */
data class CardmarketDto(
    /** ISO8601 时间字符串（文档说是 Number，是错的）。 */
    @SerializedName("updated") val updated: String? = null,

    /** 货币代码，如 `"EUR"`（文档说是 Number，是错的）。 */
    @SerializedName("unit") val unit: String? = null,

    /** Cardmarket 的商品 ID，可用于跳转到对应商品页。 */
    @SerializedName("idProduct") val idProduct: Int? = null,

    // ---- 非闪卡 ----
    @SerializedName("avg") val avg: Double? = null,
    @SerializedName("low") val low: Double? = null,
    @SerializedName("trend") val trend: Double? = null,
    @SerializedName("avg1") val avg1: Double? = null,
    @SerializedName("avg7") val avg7: Double? = null,
    @SerializedName("avg30") val avg30: Double? = null,

    // ---- 闪卡（后缀 -holo） ----
    @SerializedName("avg-holo") val avgHolo: Double? = null,
    @SerializedName("low-holo") val lowHolo: Double? = null,
    @SerializedName("trend-holo") val trendHolo: Double? = null,
    @SerializedName("avg1-holo") val avg1Holo: Double? = null,
    @SerializedName("avg7-holo") val avg7Holo: Double? = null,
    @SerializedName("avg30-holo") val avg30Holo: Double? = null,
)

/**
 * TCGPlayer（北美市场）价格。
 *
 * 与 Cardmarket 不同，这边是**按印刷版本分组**的：每个变体各有一个价格对象，
 * 而且是**哪个变体有货才有那个键** —— 所以下面七个字段全是可空，且通常只有一两个非空。
 */
data class TcgPlayerDto(
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("unit") val unit: String? = null,

    @SerializedName("normal") val normal: TcgPlayerPriceDto? = null,
    @SerializedName("holofoil") val holofoil: TcgPlayerPriceDto? = null,
    @SerializedName("reverse-holofoil") val reverseHolofoil: TcgPlayerPriceDto? = null,
    @SerializedName("1st-edition") val firstEdition: TcgPlayerPriceDto? = null,
    @SerializedName("1st-edition-holofoil") val firstEditionHolofoil: TcgPlayerPriceDto? = null,
    @SerializedName("unlimited") val unlimited: TcgPlayerPriceDto? = null,
    @SerializedName("unlimited-holofoil") val unlimitedHolofoil: TcgPlayerPriceDto? = null,
)

/** TCGPlayer 里单个印刷版本的价格档位。 */
data class TcgPlayerPriceDto(
    /** TCGPlayer 的商品 ID。官方文档没列，但真实响应里有。 */
    @SerializedName("productId") val productId: Int? = null,

    @SerializedName("lowPrice") val lowPrice: Double? = null,
    @SerializedName("midPrice") val midPrice: Double? = null,
    @SerializedName("highPrice") val highPrice: Double? = null,
    @SerializedName("marketPrice") val marketPrice: Double? = null,
    @SerializedName("directLowPrice") val directLowPrice: Double? = null,
)
