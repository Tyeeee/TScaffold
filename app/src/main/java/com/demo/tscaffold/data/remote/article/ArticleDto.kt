package com.demo.tscaffold.data.remote.article

import com.google.gson.annotations.SerializedName
import com.demo.tscaffold.model.Article

/** 分页接口的返回：这一页的数据 + 总数。 */
data class ArticlePageDto(
    @SerializedName("list") val list: List<ArticleDto>? = null,
    @SerializedName("total") val total: Int = 0,
)

/**
 * 接口返回的一篇文章。
 *
 * 字段**一律写成可空的**：Gson 反序列化不走构造函数，后端少给一个字段时，
 * 声明成非空的 String 也会被塞成 null，然后在用的时候崩掉。
 * 写成可空、在 [toArticle] 里兜底，才是安全写法。
 */
data class ArticleDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("author") val author: String? = null,
) {
    /** 网络模型 → 页面用的模型。这是两者之间**唯一**的转换点。 */
    fun toArticle(): Article = Article(
        id = id,
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        author = author.orEmpty(),
    )
}
