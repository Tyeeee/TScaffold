package com.demo.tscaffold.data.article

import com.demo.tscaffold.model.Article

/**
 * 示例用的假数据源 —— 列表分页、搜索、详情、删除都从这里取。
 *
 * 故意做得像真的：
 * - 每次请求都要等一会儿（模拟网络）；
 * - **每第 3 次请求会失败一次**，这样"加载失败 / 重试"才有的可测；
 * - 数据存在内存里，所以在详情页删掉一条，回到列表页它也真的没了。
 *
 * 等接上 Retrofit，把这个类内部换成真接口调用即可，页面那几层一行都不用改。
 *
 * （`Article` 这个模型 2026-09-12 从本文件抽到了 `com.demo.tscaffold.model`，
 *  它不该寄居在仓库实现里。）
 */
object ArticleRepository : ArticleSource {

    /** 内存里的一份假数据。用可变列表，这样删除是真的删掉了，列表页回来也看不到。 */
    private val all = (1..42).map { index ->
        Article(
            id = index,
            title = "第 $index 篇文章：把界面和逻辑分开",
            summary = "这一条是假数据，用来把列表撑满，好看出分页和下拉刷新的效果。",
            author = "作者 ${('A' + (index % 5))}",
        )
    }.toMutableList()

    /** 一页给几条。 */
    override val pageSize: Int = 10

    private var callTimes = 0

    /** 故意制造的失败：每第 3 次请求失败一次。 */
    private fun failIfNeeded() {
        callTimes++
        if (callTimes % 3 == 0) {
            error("网络开小差了（这是故意做的失败，用来演示失败和重试）")
        }
    }

    /** 取第 [page] 页（页码从 1 开始），不足一页说明到底了。 */
    override suspend fun loadPage(page: Int): List<Article> {
        kotlinx.coroutines.delay(800)
        failIfNeeded()
        val from = (page - 1) * pageSize
        return all.drop(from).take(pageSize)
    }

    /** 按关键字搜，空关键字返回空。 */
    override suspend fun search(keyword: String): List<Article> {
        kotlinx.coroutines.delay(400)
        if (keyword.isBlank()) return emptyList()
        return all.filter { it.title.contains(keyword) || it.author.contains(keyword) }
    }

    /** 取某一条的详情。 */
    override suspend fun loadDetail(id: Int): Article? {
        kotlinx.coroutines.delay(600)
        return all.firstOrNull { it.id == id }
    }

    /** 删掉某一条（内存里真删，所以列表页回来也看不到了）。 */
    override suspend fun delete(id: Int): Boolean {
        kotlinx.coroutines.delay(500)
        return all.removeAll { it.id == id }
    }
}
