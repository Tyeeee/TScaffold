package com.tscaffold.feature.data

/**
 * 数据来源的"接口" —— 页面只认这一份约定，不认具体实现。
 *
 * 好处有两个：
 * 1. 以后换成真网络（Retrofit 实现同一个接口），页面几层一行都不用改；
 * 2. 写测试时可以塞一个"假的实现"进去，想让它返回什么就返回什么、
 *    还能数一数"到底查了几次"（比如验证防抖）。
 */
interface ArticleSource {

    /** 一页给几条。这是数据源自己定的规矩，页面不该猜。 */
    val pageSize: Int

    /** 取第 [page] 页（页码从 1 开始），不足一页说明到底了。 */
    suspend fun loadPage(page: Int): List<Article>

    /** 按关键字搜，空关键字返回空。 */
    suspend fun search(keyword: String): List<Article>

    /** 取某一条的详情。 */
    suspend fun loadDetail(id: Int): Article?

    /** 删掉某一条。 */
    suspend fun delete(id: Int): Boolean
}
