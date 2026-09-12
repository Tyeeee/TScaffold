package com.demo.tscaffold.data.remote.pokemontcg

import com.google.gson.annotations.SerializedName

/**
 * 套牌（Set）的网络模型。
 *
 * 这个 API 对"套牌"给了两个粒度，别混用：
 * - [SetBriefDto]：列表用，字段少
 * - [SetDto]：详情用，**内嵌该套牌的全部卡片**
 */

/**
 * 套牌的简要对象。
 *
 * 出现在三处：`GET /{lang}/sets` 列表、卡片对象里的 `set` 字段、系列对象里的 `sets` 数组。
 *
 * ⚠️ 注意 `logo` 和 `symbol` **是可空的**。实测 218 个套牌里，
 * 只有 157 个有 logo、169 个有 symbol —— 总有那么些套牌（尤其是早期的 promo 套牌）
 * 一个图都没有。UI 上必须给占位图。
 */
data class SetBriefDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("cardCount") val cardCount: CardCountDto? = null,

    /** 套牌 Logo，**不带扩展名**，用法同 `Card.image`：拼 `.{webp|png|jpg}`。 */
    @SerializedName("logo") val logo: String? = null,

    /** 套牌标志（卡面上那个小图标），**不带扩展名**，同上。 */
    @SerializedName("symbol") val symbol: String? = null,
)

/**
 * 套牌详情（`GET /{lang}/sets/{setId}`）。
 *
 * 比 [SetBriefDto] 多了三件事：
 * 1. [cards] —— **这个套牌的全部卡片**（简要形式，不含 rarity）；
 * 2. [serie] / [releaseDate] / [tcgOnline] / [legal] —— 归属和赛制信息；
 * 3. [cardCount] 里会多出 `reverse` / `holo` / `firstEd` / `normal` 四项细分。
 *
 * 实测：`base1`（102 张）这个响应是 11 KB，平均每张卡约 108 字节。
 * 所以"按套牌拉"是个很划算的粒度 —— 一个套牌一个请求，天然可以整体缓存。
 */
data class SetDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,

    @SerializedName("logo") val logo: String? = null,
    @SerializedName("symbol") val symbol: String? = null,

    @SerializedName("cardCount") val cardCount: CardCountDto? = null,

    /** 所属系列。只有 `id` 和 `name` 两个字段（就是 [SerieBriefDto]）。 */
    @SerializedName("serie") val serie: SerieBriefDto? = null,

    /** 格式 `yyyy-mm-dd`。 */
    @SerializedName("releaseDate") val releaseDate: String? = null,

    /** Pokémon TCG Online 里的套牌代码，如 `"BS"`。 */
    @SerializedName("tcgOnline") val tcgOnline: String? = null,

    /**
     * 套牌缩写，形如 `{"official": "BS"}`。
     * 官方文档里没有这个字段，但真实响应里有 —— 所以它是个**对象**，不是字符串。
     */
    @SerializedName("abbreviation") val abbreviation: AbbreviationDto? = null,

    @SerializedName("legal") val legal: LegalDto? = null,

    /** 这个套牌出过哪些补充包。老套牌没有这个字段。 */
    @SerializedName("boosters") val boosters: List<BoosterDto>? = null,

    /** 该套牌的全部卡片（简要形式：[CardBriefDto]，**没有 rarity**）。 */
    @SerializedName("cards") val cards: List<CardBriefDto>? = null,
)
