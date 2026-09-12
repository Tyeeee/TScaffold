package com.demo.tscaffold.data.remote.article

import com.demo.tscaffold.data.remote.Envelope
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 文章相关的接口声明。
 *
 * 三点跟"另一种常见写法"不一样，都是有意为之：
 * 1. 全部用 Retrofit 原生的 `suspend` —— **不需要任何自定义 CallAdapter**；
 * 2. 返回类型里老老实实写着后端的外壳 [Envelope]，拆壳交给数据层（见 [ArticleRemoteSource]），
 *    而不是在接口声明里藏起来；
 * 3. 路径是照示例写的占位，接真后端时按这个形状改。
 */
interface ArticleApi {

    @GET("article/page")
    suspend fun page(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): Envelope<ArticlePageDto>

    @GET("article/search")
    suspend fun search(@Query("keyword") keyword: String): Envelope<List<ArticleDto>>

    @GET("article/{id}")
    suspend fun detail(@Path("id") id: Int): Envelope<ArticleDto>

    /**
     * 删除。返回体用 `Any` 是因为这里只关心业务码：
     * 声明成具体类型的话，后端返回的类型一变就会解析失败，而那个值我们根本不用。
     */
    @DELETE("article/{id}")
    suspend fun delete(@Path("id") id: Int): Envelope<Any>
}
