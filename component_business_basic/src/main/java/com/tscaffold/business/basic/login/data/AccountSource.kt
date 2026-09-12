package com.tscaffold.business.basic.login.data

/**
 * 账号数据的**接口** —— 登录页只认这一份约定，不认具体实现。
 *
 * 这是这个模块能被复用的关键：登录页属于"业务基础页面"，要被多个业务共用，
 * 所以它**不能**直接依赖某个具体的账号实现（更不能依赖网络库或某个后端）。
 * 谁用它，谁就把自己的实现塞进来。
 *
 * 典型接法：
 * ```
 * // 业务侧
 * class MyAccountSource(private val api: MyAccountApi) : AccountSource {
 *     override suspend fun login(username: String, password: String): Boolean { … }
 * }
 *
 * // 用的时候
 * val viewModel: LoginViewModel by viewModels {
 *     LoginViewModel.factory(MyAccountSource(api))
 * }
 * ```
 *
 * 约定：**登录失败靠返回值 `false`，网络等异常直接抛**。
 * ViewModel 会把异常接住并转成给用户看的一句话，页面不用管。
 */
interface AccountSource {

    /**
     * 校验账号密码。
     *
     * @return 成功返回 `true`；账号或密码不对返回 `false`。
     *         网络不可用之类的异常**直接抛出去**，由 ViewModel 统一处理。
     */
    suspend fun login(username: String, password: String): Boolean
}
