package com.tscaffold.base.network.http

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tscaffold.base.network.HttpClient
import com.tscaffold.base.network.http.impl.apiCall
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * ============================ HTTP 这一类，业务只用这一个 ============================
 *
 * 一个页面/一个数据层要发请求，就两步：
 *
 * ```
 * // 1) 拿接口实现（按接口缓存住，别每次请求都建）
 * private val api: ArticleApi by lazy { RetrofitService.create() }
 *
 * // 2) 调它；失败会被统一翻译成 ApiException（带 userMessage），协程取消原样抛出
 * val page = RetrofitService.call { api.page(page = 1, size = 10) }
 * ```
 *
 * 接口文件（`@GET/@POST` 那些）写在业务自己的协议配套文件里，例如 `feature/data/remote/ArticleApi.kt`。
 *
 * 这个包里其余的东西（`impl/` 下的 ErrorMapper、apiCall）都是内部机制，业务不用碰：
 * 它们被标成了 `internal`，别的模块的补全列表里根本不会出现。
 * ============================================================================
 */
object RetrofitService {

    /**
     * 接口地址，必须以 `/` 结尾。
     *
     * 不要在这里直接赋值 —— 用 `Network.init(baseUrl = ...)` 在 Application 里设一次。
     *
     * 运行期换环境（测试/预发/生产）时注意：Retrofit 的接口代理是**绑定在创建时的实例上**的，
     * 所以换完地址要重新 [create] 一遍（比如重建数据层），旧代理还会打旧地址。
     */
    var baseUrl: String = ""

    /** 需要自定义日期格式等，改这里。 */
    val gson: Gson = GsonBuilder().create()

    /**
     * 接口实现缓存：**baseUrl 一变就重建**。
     *
     * 为什么不是简单 `by lazy`：那样运行期切换环境（测试/预发/生产）会静默失效 ——
     * 第一次用完之后再改 [baseUrl] 就没反应了，很难查。这里按 url 记住上一次构建的结果。
     */
    @PublishedApi
    internal val retrofit: Retrofit
        get() = synchronized(this) {
            val url = baseUrl
            require(url.isNotBlank()) {
                "还没有设置接口地址，请先在 Application 里写 Network.init(baseUrl = \"https://.../\")"
            }
            val client = HttpClient.instance
            val cached = cachedRetrofit
            // 地址变了、或者 OkHttpClient 因为改超时/改心跳被重建了，都要重新造一个 Retrofit
            if (cached == null || cachedBaseUrl != url || cachedClient !== client) {
                Retrofit.Builder()
                    .baseUrl(url)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .also {
                        cachedRetrofit = it
                        cachedBaseUrl = url
                        cachedClient = client
                    }
            } else {
                cached
            }
        }

    private var cachedRetrofit: Retrofit? = null
    private var cachedBaseUrl: String? = null
    private var cachedClient: OkHttpClient? = null

    /** 创建接口实现。**用 `by lazy` 缓存住**，别每次请求都 create 一遍。 */
    inline fun <reified T : Any> create(): T = retrofit.create(T::class.java)

    /**
     * 调一次接口，把底层异常翻译成 [ApiException]。
     *
     * 成功时原样返回；失败抛 [ApiException]（`message` 就是能给用户看的那句话）；
     * **协程取消会原样往上抛**，不会被当成"请求失败"。
     */
    suspend fun <T> call(block: suspend () -> T): T = apiCall(block)
}
