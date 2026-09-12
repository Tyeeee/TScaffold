package com.demo.tscaffold.model

/**
 * 页面用的文章模型。
 *
 * **放在 `model` 而不是 `data`**：它是一个纯粹的领域模型，描述"一篇文章长什么样"，
 * 跟"从哪儿取"没有关系。列表页、搜索页、详情页、假数据源、网络层都用它。
 *
 * 它和网络模型 [com.demo.tscaffold.data.remote.article.ArticleDto] 是两回事：
 * DTO 贴着后端 JSON 的字段（还带着 `@SerializedName`），这个只贴业务。
 * 两者之间在 `ArticleDto.toArticle()` 一处转换 —— 后端改字段时只动那一处。
 *
 * （它本来寄居在 `ArticleRepository.kt` 里，2026-09-12 搬出来的。）
 */
data class Article(
    val id: Int,
    val title: String,
    val summary: String,
    val author: String,
)
