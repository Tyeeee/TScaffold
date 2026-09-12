package com.demo.tscaffold.data.remote.pokemontcg

import com.google.gson.annotations.SerializedName

/**
 * 卡片的网络模型。
 *
 * ## 关于字段可空性（这个文件里很重要，先说清楚）
 *
 * **所有 String / 对象 / 数组字段一律声明成可空。**
 *
 * 原因不是"后端可能不给"，而是 Gson 的机制：Gson 用 `UnsafeAllocator` 绕过构造函数直接
 * 造对象，所以 Kotlin 里写的 `val id: String = ""` 这个默认值**根本不会生效** ——
 * 字段缺失时它就是 `null`，然后你在 Kotlin 侧当非空用，就是一次 NPE。
 * 声明成可空、在转换层兜底，才是安全写法（跟 [com.demo.tscaffold.data.remote.article.ArticleDto] 一个规矩）。
 *
 * 只有 `Int` / `Boolean` 这类基本类型可以放心写非空（缺省时是 0 / false），
 * 但这里为了语义清楚（"没有这个字段"和"值是 0"不是一回事）也大多写成可空。
 */

/**
 * 卡片**简要**对象。
 *
 * 三个地方会返回它：
 * 1. `GET /{lang}/cards` 的列表；
 * 2. `GET /{lang}/sets/{setId}` 里内嵌的 `cards` 数组；
 * 3. `GET /{lang}/series/{serieId}` 间接带出来的套牌里。
 *
 * ⚠️ **它只有 4 个字段，没有 `rarity`。**
 * 这是本 API 最容易踩的坑：列表接口拿不到稀有度，想要 `rarity` 必须单独请求
 * `GET /{lang}/cards/{cardId}`（见 [PokemonTcgApi.card]）。
 * 好在 `GET /{lang}/cards` 支持 `rarity=eq:xxx` 服务端过滤 —— 按稀有度逐个拉，
 * 就能在只有简要字段的情况下反推出每张卡的稀有度，不用发两万次请求。
 */
data class CardBriefDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("localId") val localId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("image") val image: String? = null,
)

/**
 * 卡片**完整**对象（`GET /{lang}/cards/{cardId}`、`GET /{lang}/sets/{setId}/{localId}`）。
 *
 * 这个对象是"什么卡都有"的合集：`Pokemon` / `Trainer` / `Energy` 三类卡的专有字段
 * 都堆在同一个类型上，靠 [category] 区分。这是 API 的设计，不是这里偷懒 ——
 * 拆成三个子类的话 Gson 得多态反序列化，反而更脆。
 *
 * 字段按类别标注：
 * - 通用：所有卡都有
 * - Pokemon：只有 `category == "Pokemon"` 才有
 * - Trainer：只有 `category == "Trainer"` 才有
 * - Energy：只有 `category == "Energy"` 才有
 *
 * ## 关于 `variants_detailed`：**故意没有建模**
 *
 * 真实响应里还有一个 `variants_detailed` 数组，里面是**按印刷版本拆开的价格**
 * （cardmarket / tcgplayer 各自的 productId 和历史均价）。它占了单卡响应 3.3 KB 里的
 * 绝大部分 —— 建模它，就等于每次取一张卡的详情都要在内存里多背三倍的结构。
 *
 * 官方 FAQ 也明确说这个字段"in development"（后续还会改结构）。
 * 所以这里**不声明它**：Gson 遇到不认识的键会直接忽略，不声明就等于白拿一份瘦身，
 * 而需要价格时再单独加。要用的话告诉我，我补上。
 */
data class CardDto(
    // ---------- 通用 ----------
    @SerializedName("id") val id: String? = null,
    @SerializedName("localId") val localId: String? = null,
    @SerializedName("name") val name: String? = null,

    /**
     * 卡图地址，**故意不带扩展名**，形如
     * `https://assets.tcgdex.net/en/base/base1/1`。
     *
     * 要自己拼上 `/{quality}.{ext}`：quality 取 `high`(600x825) / `low`(245x337)，
     * ext 取 `webp`（推荐，透明且小）/ `png`（透明）/ `jpg`（黑底）。
     */
    @SerializedName("image") val image: String? = null,

    /** `"Pokemon"` / `"Trainer"` / `"Energy"`。用它决定下面哪组字段有效。 */
    @SerializedName("category") val category: String? = null,

    @SerializedName("illustrator") val illustrator: String? = null,

    /**
     * 稀有度。**决定全息效果用哪个变体的主字段**。
     *
     * ⚠️ 它是**可空**的：promo 之类的卡可能压根没有这个字段。
     * 渲染层必须准备一个默认分支，否则遇到这种卡会变成白板。
     */
    @SerializedName("rarity") val rarity: String? = null,

    /** 所属套牌（简要形式）。**一直有**，是卡片和套牌之间的关联点。 */
    @SerializedName("set") val set: SetBriefDto? = null,

    /** 这张卡存在哪些印刷版本。判断"是不是闪卡"应该看它，而不是只看 rarity。 */
    @SerializedName("variants") val variants: VariantsDto? = null,

    @SerializedName("boosters") val boosters: List<BoosterDto>? = null,

    @SerializedName("legal") val legal: LegalDto? = null,

    /**
     * 市场价格（Cardmarket / TCGPlayer）。
     *
     * 很多卡压根没有这个字段（官方 FAQ 明说：老 EX/Full Art 卡在两个市场都没单独挂单）。
     * 如果只做卡面展示，忽略它就行 —— 见 [PricingDto] 的注释。
     */
    @SerializedName("pricing") val pricing: PricingDto? = null,

    /** ISO8601，卡面数据（**不含价格**）最后一次更新的时间。可用于增量同步。 */
    @SerializedName("updated") val updated: String? = null,

    // ---------- Pokemon 专有 ----------
    /** 全国图鉴编号。注意是个**数组**（有些卡对应多个图鉴号）。 */
    @SerializedName("dexId") val dexId: List<Int>? = null,

    @SerializedName("hp") val hp: Int? = null,

    /** 属性，如 `["Psychic"]`、`["Fire","Water"]`。 */
    @SerializedName("types") val types: List<String>? = null,

    @SerializedName("evolveFrom") val evolveFrom: String? = null,

    /** 图鉴描述（flavor text）。 */
    @SerializedName("description") val description: String? = null,

    /** 等级，LV.X 这类卡才有，值是 `"X"`。 */
    @SerializedName("level") val level: String? = null,

    /** 进化阶段：`"Basic"` / `"Stage1"` / `"Stage2"`。 */
    @SerializedName("stage") val stage: String? = null,

    /** 卡名后缀，如 `"V"`、`"VMAX"`、`"ex"`。 */
    @SerializedName("suffix") val suffix: String? = null,

    /** 携带的道具。 */
    @SerializedName("item") val item: ItemDto? = null,

    @SerializedName("attacks") val attacks: List<AttackDto>? = null,

    /** 特性（老卡叫 "Pokemon Power"）。 */
    @SerializedName("abilities") val abilities: List<AbilityDto>? = null,

    /** 弱点。`value` 形如 `"×2"`，注意是带乘号的字符串，不是数字。 */
    @SerializedName("weaknesses") val weaknesses: List<WeaknessDto>? = null,

    /** 抗性。结构和弱点一样，但**大部分卡没有这个字段**。 */
    @SerializedName("resistances") val resistances: List<WeaknessDto>? = null,

    /** 撤退需要的能量数。 */
    @SerializedName("retreat") val retreat: Int? = null,

    /** 规则标记（D / E / F ...），新卡才有。 */
    @SerializedName("regulationMark") val regulationMark: String? = null,

    // ---------- Trainer 专有 ----------
    /** 训练家卡的效果文本。 */
    @SerializedName("effect") val effect: String? = null,

    /** 训练家卡的子类型，如 `"Supporter"` / `"Item"` / `"Stadium"`。 */
    @SerializedName("trainerType") val trainerType: String? = null,

    // ---------- Energy 专有 ----------
    /** 能量类型：`"Basic"` / `"Special"`。（效果文本共用上面的 [effect]。） */
    @SerializedName("energyType") val energyType: String? = null,
)

/** 一次攻击。 */
data class AttackDto(
    /** 能量消耗，如 `["Psychic","Psychic","Colorless"]`。 */
    @SerializedName("cost") val cost: List<String>? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("effect") val effect: String? = null,
    /** 伤害值。老卡里可能是纯数字，也有的是 `"80+"` 这种，所以这里用 Int 接不住时会是缺省。 */
    @SerializedName("damage") val damage: Int? = null,
)

/** 特性 / 老卡的 Pokemon Power。 */
data class AbilityDto(
    /** `"Ability"` / `"Pokemon Power"` / `"Poke-BODY"` 等。 */
    @SerializedName("type") val type: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("effect") val effect: String? = null,
)

/** 弱点 / 抗性。两者形状相同。 */
data class WeaknessDto(
    /** 属性，如 `"Psychic"`。 */
    @SerializedName("type") val type: String? = null,
    /** 倍率，如 `"×2"` / `"-20"`。是字符串，别当数字解析。 */
    @SerializedName("value") val value: String? = null,
)

/** 携带道具。 */
data class ItemDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("effect") val effect: String? = null,
)
