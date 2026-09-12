package com.tscaffold.feature.data.remote

import com.tscaffold.base.network.http.RetrofitService
import com.tscaffold.feature.data.Article
import com.tscaffold.feature.data.ArticleSource

/** 整个 App 只建一次接口代理；不要每次请求都 create 一个。 */
private val sharedArticleApi: ArticleApi by lazy { RetrofitService.create() }

/**
 * [ArticleSource] 的真实网络实现。
 *
 * 页面、ViewModel、Contract 全都不知道它的存在 —— 它们只认 [ArticleSource]。
 * 想从假数据切到真网络，把 ViewModel 的默认参数换成这个类即可，别的一行不改。
 *
 * 它包了三件事，别的层都不用管：
 * 1. 调 [ArticleApi]（`apiCall` 负责把底层异常翻译成 `ApiException`）；
 * 2. 拆后端外壳 [Envelope]，把"业务码不是成功"变成异常；
 * 3. 把网络模型 [ArticleDto] 转成页面用的 [Article]。
 */
class ArticleRemoteSource(
    private val api: ArticleApi = sharedArticleApi,
) : ArticleSource {

    override val pageSize: Int = 10

    override suspend fun loadPage(page: Int): List<Article> = RetrofitService.call {
        api.page(page, pageSize)
            .unwrap()
            .list
            .orEmpty()
            .map { it.toArticle() }
    }

    override suspend fun search(keyword: String): List<Article> {
        // 空关键字不发请求：这是 ArticleSource 约定好的规矩，实现里也要守住
        if (keyword.isBlank()) return emptyList()
        return RetrofitService.call {
            api.search(keyword)
                .unwrap()
                .map { it.toArticle() }
        }
    }

    override suspend fun loadDetail(id: Int): Article? = RetrofitService.call {
        // 后端说"没有这一条"时返回 null，页面上已经有"这条内容不见了"的分支在等它
        api.detail(id)
            .unwrapOrNull()
            ?.toArticle()
    }

    override suspend fun delete(id: Int): Boolean = RetrofitService.call {
        // 业务码是成功就算删掉了；失败会在 checkOk 里抛出去
        api.delete(id).checkOk()
        true
    }
}
