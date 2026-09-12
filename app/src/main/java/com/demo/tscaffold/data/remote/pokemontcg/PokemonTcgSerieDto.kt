package com.demo.tscaffold.data.remote.pokemontcg

import com.google.gson.annotations.SerializedName

/**
 * 系列（Serie）的网络模型。
 *
 * 层级关系是：**系列 → 套牌 → 卡片**。
 * 例如系列 `base`（Base）下面有 `base1`(Base Set)、`base2`(Jungle)、`base5`(Team Rocket) …
 *
 * 只有两级界面导航（比如"系列列表 → 套牌列表 → 卡片网格"）时才需要它；
 * 如果直接平铺展示所有套牌，只用 [SetDto] 那一套就够了。
 */

/**
 * 系列的简要对象：只有 `id` 和 `name`。
 *
 * 它同时被 [SetDto.serie] 复用 —— 套牌里内嵌的"所属系列"就是这个形状。
 */
data class SerieBriefDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
)

/**
 * 系列详情（`GET /{lang}/series/{serieId}`）。
 *
 * ⚠️ [firstSet] / [lastSet] / [releaseDate] 这三个字段**官方文档里没有列**，
 * 但真实响应里有。以真实响应为准，所以这里都建了模。
 * （`firstSet` / `lastSet` 就是 [SetBriefDto]。）
 */
data class SerieDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,

    /** 系列 Logo，**不带扩展名**，用法同 `Card.image`。 */
    @SerializedName("logo") val logo: String? = null,

    /** 格式 `yyyy-mm-dd`。 */
    @SerializedName("releaseDate") val releaseDate: String? = null,

    /** 这个系列里最早的一个套牌。 */
    @SerializedName("firstSet") val firstSet: SetBriefDto? = null,

    /** 这个系列里最新的一个套牌。 */
    @SerializedName("lastSet") val lastSet: SetBriefDto? = null,

    /** 这个系列下的全部套牌（简要形式）。 */
    @SerializedName("sets") val sets: List<SetBriefDto>? = null,
)
