package com.demo.tscaffold.data.remote.pokemontcg

import com.google.gson.annotations.SerializedName

/**
 * TCGdex 里被多个对象**共用**的几个小结构。
 *
 * 把它们单独放一个文件，是为了避免"卡片引用套牌的字段、套牌又引用卡片的字段"这种
 * 互相穿越的写法 —— 这些叶子模型谁都能用，但谁都不属于谁。
 *
 * 字段命名和可空性遵循工程约定：**一律可空 + 给默认值**。
 * 原因见 [CardBriefDto] 的注释（Gson 不走构造函数）。
 */

/**
 * 套牌 / 卡片的数量统计。
 *
 * 注意：`official` / `total` 在所有地方都有，但 `reverse` / `holo` / `firstEd` / `normal`
 * **只有在"套牌详情"里才出现**（列表里的 SetBrief 只有前两个）。
 * 所以这里全部写成可空，用的时候记得兜底。
 */
data class CardCountDto(
    @SerializedName("official") val official: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("normal") val normal: Int? = null,
    @SerializedName("reverse") val reverse: Int? = null,
    @SerializedName("holo") val holo: Int? = null,
    @SerializedName("firstEd") val firstEd: Int? = null,
)

/**
 * 赛制合法性。套牌和卡片上都有这个字段，但**含义不同**：
 * 套牌上是"这个套牌能不能用"，卡片上是"这张卡能不能用"。
 *
 * 还有 `unlimited` 这个键在部分数据里出现，官方文档没列，先留着可空。
 */
data class LegalDto(
    @SerializedName("standard") val standard: Boolean? = null,
    @SerializedName("expanded") val expanded: Boolean? = null,
    @SerializedName("unlimited") val unlimited: Boolean? = null,
)

/**
 * 这张卡存在哪些印刷版本。
 *
 * **这个字段是本项目里最重要的一个** —— 渲染全息效果时，用哪套材质、多强的反光，
 * 靠的就是它（而不是只靠 `rarity`）。
 *
 * `wPromo` 没有出现在官方文档里，但真实响应里有（"with Promo"?），所以留着可空。
 */
data class VariantsDto(
    @SerializedName("normal") val normal: Boolean? = null,
    @SerializedName("reverse") val reverse: Boolean? = null,
    @SerializedName("holo") val holo: Boolean? = null,
    @SerializedName("firstEdition") val firstEdition: Boolean? = null,
    @SerializedName("wPromo") val wPromo: Boolean? = null,
)

/**
 * 一张卡出现在哪个补充包里。套牌和卡片对象上共用同一个形状。
 *
 * 只有较新的套牌会有这个数据，老卡（如 base1）的响应里根本没有这个键。
 */
data class BoosterDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("logo") val logo: String? = null,
    @SerializedName("artwork_front") val artworkFront: String? = null,
    @SerializedName("artwork_back") val artworkBack: String? = null,
)

/**
 * 套牌的官方缩写。
 *
 * 真实响应形如 `{"official": "BS"}`，官方文档里压根没提这个字段。
 * 写成独立类型而不是 `String`，是因为它确实是个对象 —— 直接声明成 String 会解析失败。
 */
data class AbbreviationDto(
    @SerializedName("official") val official: String? = null,
)
