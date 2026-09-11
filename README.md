# TScaffold —— 只保留"页面怎么写"的安卓骨架工程

一句话说明白：**这是一个只保留 UI 状态和用户操作这一套写法的安卓工程。**

没有网络请求、没有图片加载、没有弹窗控件库、没有工具类大礼包——那些都清掉了。
留下的是一套官方推荐的写法，外加**一个能从取数据一路跑到界面的完整示例**。
你自己做的组件，后面往预留的位置里填就行。

---

## 一、先说清楚这套写法是什么（大白话版）

写一个页面，绕不开两件事，这套写法就是把它们分开放、不许混着写：

| 两件事 | 大白话 | 放在哪儿 | 示例里的例子 |
|---|---|---|---|
| **状态**（State） | 这一页现在长什么样 | 一个 data class | 列表有几条、正在转圈、还是加载失败、要不要弹句话 |
| **操作**（Intent） | 用户在这一页干了什么 | 一组 sealed interface 分支 | 点了"重新加载"、勾了某一条、点了"清掉已完成" |

它们的走动方向是固定的，永远只有这一条：

```
        用户点了按钮
             │
             ▼
   界面 ──上报操作──► ViewModel ──算出新状态──► 界面重新画一遍
                          ▲                        │
                          └──── 界面回报"我显示过了" ┘
```

**注意最后那一条回报线**。"弹一句提示"这类事情，不是由 ViewModel 直接命令界面去弹，
而是把"要说的那句话"当成状态的一部分（`state.message`）。界面看到状态里有话就显示出来，
显示完回报一句 `MessageShown`，ViewModel 收到后把这句话清掉。

这不是我们自己想出来的规矩，是官方文档明确要求的（"Do not send events from the ViewModel to the UI."，
强推荐），理由很直白：**状态要在每一刻都如实反映屏幕上显示的东西**。这么做还有个实际好处——
转屏、从后台回来，这句话不会丢，也不会重复弹。

**这样写有什么好处？**

1. 界面里不用写 if/else 的判断逻辑，只负责"显示"和"上报操作"，看代码快。
2. 所有逻辑都收在 ViewModel 一个文件里，写单元测试不用启动模拟器。
3. 状态只有一个来源，不会出现"两个地方各改一半、显示对不上"的问题。

---

## 二、工程结构

```
TScaffold
├── app                       应用外壳：只有一个首页，放两个按钮进示例
├── component_business_basic   ★ 你以后写页面的地方（现在是"任务列表"示例）
├── component_common           ★ 这套写法的核心（全部家当都在这）
└── component_basic            最底层，留给你放基础能力（现在几乎是空的）
```

依赖方向是一层压一层，上面的能用下面的，下面的不能反过来用上面的：

```
app  →  component_business_basic  →  component_common  →  component_basic
（外壳）      （你的页面）            （核心写法）        （基础能力）
```

### component_common（核心，一共 5 个文件）

| 文件 | 作用 |
|---|---|
| `ui/viewmodel/BaseViewModel.kt` | 状态和操作的定义 + 所有 ViewModel 的父类。**想弄懂这套写法，看这一个文件就够** |
| `ui/contract/BaseContract.kt` | 一个页面约定的空白模板，照抄着改成自己的 |
| `ui/activity/BaseActivity.kt` | 用 XML 写页面时的 Activity 父类，帮你加载布局、按顺序调用你的代码 |
| `ui/fragment/BaseFragment.kt` | 同上，Fragment 版 |
| `ui/compose/MviCompose.kt` | 用 Compose 写页面时的小工具：`observeState()` |

### component_business_basic（示例，6 个源码文件 + 3 个布局）

"任务列表"这一个示例，用两种界面写法各做了一遍，**共用同一份逻辑**：

| 文件 | 作用 |
|---|---|
| `ui/task/contract/TaskContract.kt` | 这个页面的状态 / 操作 |
| `ui/task/data/TaskRepository.kt` | 数据从哪来。**现在是假数据源**，加 Retrofit 时换这里 |
| `ui/task/viewmodel/TaskViewModel.kt` | 这个页面的全部逻辑 |
| `ui/task/view/TaskActivity.kt` | **XML 版**：`BaseActivity`，顶部统计 + 承担"弹提示"，并承载下面的 Fragment |
| `ui/task/view/TaskFragment.kt` | **XML 版**：`BaseFragment`，列表本体 + 各按钮 |
| `ui/task/compose/TaskComposeActivity.kt` | **Compose 版**：同样一份逻辑，换成 Compose 画界面 |

> 注意：XML 版里 Activity 和 Fragment **共用同一个 ViewModel**（Activity 用 `by viewModels()` 建，Fragment 用 `by activityViewModels()` 拿），
> 所以顶部的"已完成 x / y"和下面列表永远是同一份数据。
> 而"弹提示"只由 Activity 一处负责——两处都做的话，一句话会被弹两次。

### 测试（在电脑上跑，不用模拟器）

| 文件 | 作用 |
|---|---|
| `component_common/src/test/.../BaseViewModelTest.kt` | 盯住基类本身的行为（5 个用例） |
| `component_business_basic/src/test/.../TaskViewModelTest.kt` | 盯住示例的数据流（8 个用例） |

### component_basic

| 文件 | 作用 |
|---|---|
| `BasicApplication.kt` | 应用启动入口，现在是空的，专门留给你做初始化 |
| `extensions/ViewModel.kt` | 想让多个页面共用同一份数据时用（App 级 ViewModel） |

---

## 三、数据流是怎么跑一圈的（对着示例看）

拿"点一下重新加载"举例，数据是这样走完一圈的：

| 步骤 | 发生什么 | 在哪个文件 |
|---|---|---|
| ① | 按钮被点 → 只上报"用户想重新加载" | `TaskActivity.kt` / `TaskFragment.kt` 里的 `setIntent(...)` |
| ② | 收到操作 → 先把状态改成"加载中" | `TaskViewModel.handleIntent` → `load()` 里的 `setState` |
| ③ | 界面立刻显示转圈，因为状态变了 | `TaskFragment.render()` |
| ④ | 去取数据（现在是假数据源，等 800 毫秒） | `TaskRepository.loadTasks()` |
| ⑤ | 拿到了 → 把数据写进状态，界面自动变成列表 | `TaskViewModel.load()` 的 `setState` |
| ⑤' | 出错了 → 把错误信息和一句提示写进状态，界面自动显示错误 + "重试" | 同上，走 `catch` 分支 |
| ⑥ | 状态里有了"要提示的话"，界面弹一次 | `TaskActivity.showMessageOnce()` |
| ⑦ | 弹完回报 `MessageShown`，ViewModel 把那句话清掉 | 回到 `TaskViewModel.handleIntent` |

### 你可以自己点一遍验证（都是实测过的）

1. 打开首页 → 点"示例一：XML 写的（Activity + Fragment）"
   → 先看到转圈，然后出现 4 条任务，顶部显示"已完成 1 / 4"，并弹一句"加载完成，共 4 条"。
2. 勾掉某一条 → 顶部统计立刻跟着变（**这就是 Activity 和 Fragment 共用同一份状态**）。
3. 点"清掉已完成" → 已完成的那几条从列表消失，顶部统计同步变化。
4. 连点两次"重新加载" → 第一次条数变多；**第二次会故意失败**（假数据源里写死了每第 3 次报错），
   界面出现红色的错误说明和"重试"按钮，同时弹一句提示。
5. 点"重试" → 恢复正常，列表又出来了。这就是"失败之后要能自己恢复"。
6. 回首页 → 点"示例二：Compose 写的" → 同样的数据和同样的行为，只是界面换成了 Compose。

> 那个"每第 3 次故意报错"是特意加的：不然出错这条分支你没法验证，
> 等接了真网络以后这种错天天会碰到，先在这儿把处理写好。

---

## 四、和安卓官方文档的对照

### 先说一件要紧的事：官网没有"MVI"的定义

用 Google 搜 `site:developer.android.com MVI`，出来的要么是第三方博客（CSDN、掘金、Medium 上的个人文章），
要么是官方的"状态持有者""架构推荐"页面。把官方那几页架构文档的正文抓下来全文搜，
**`MVI`、`Model-View-Intent` 一次都没出现**。

官方用的说法叫 **单向数据流（UDF, Unidirectional Data Flow）**，出处是官方架构文档：

- [UI 层（UI layer）](https://developer.android.google.cn/topic/architecture/ui-layer)
- [UI 事件（UI events）](https://developer.android.google.cn/topic/architecture/ui-layer/events)
- [状态持有者与 UI 状态](https://developer.android.google.cn/topic/architecture/ui-layer/stateholders)
- [安卓架构推荐（Recommendations）](https://developer.android.google.cn/topic/architecture/recommendations)

> 上面链接是 Google 官方的中国站域名；本机连不上 `developer.android.com` 主站（DNS 能解析但连接超时），
> 中国站是同一个官方文档站的镜像，内容一致。

官方对 UDF 的原话是：

> "The pattern where the state flows down and the events flow up is called a unidirectional data flow (UDF)."
> （状态往下走、事件往上走，这个模式就叫单向数据流。）

官方把它拆成四句话，这个工程就是照着这四句写的：

1. ViewModel 持有并对外暴露 UI 状态，界面订阅它；
2. 界面把用户的操作告诉 ViewModel；
3. ViewModel 处理这些操作并更新状态；
4. 更新后的状态再回到界面，界面照着画一遍，如此循环。

### 逐条对照官方写明的规则

| 官方原话 | 优先级 | 本工程 |
|---|---|---|
| "Follow Unidirectional Data Flow (UDF) ... ViewModels expose UI state using the observer pattern and receive actions from the UI through method calls." | 强推荐 | ✅ 状态是 `StateFlow`，操作是 `setIntent(...)` 方法调用 |
| "Do not send events from the ViewModel to the UI. ... Process the event immediately in the ViewModel and cause a state update with the result." | 强推荐 | ✅ **上一版违反，已改**，见下面"改了什么" |
| "Use lifecycle-aware UI state collection ... `collectAsStateWithLifecycle`." | 强推荐 | ✅ XML 版用 `repeatOnLifecycle`，Compose 版用 `observeState()`（内部就是它） |
| "Keep ViewModels independent of the Android lifecycle ... Don't pass `Activity`, `Context`, or `Resources` as a dependency." | 强推荐 | ✅ ViewModel 只拿到一个仓库，不碰任何界面类型 |
| "Use ViewModels at screen level. Do not use ViewModels in reusable pieces of UI." | 强推荐 | ✅ ViewModel 只挂在 Activity 上 |
| "Expose a UI state ... through a single property called `uiState`." / "Make `uiState` a `StateFlow`." | 推荐 | ✅ 就叫 `uiState`，类型是 `StateFlow` |
| "The UI state is an immutable snapshot of the details needed for the UI to render."（UI 层页） | — | ✅ 状态是 `data class` + 只读 `List`，能推算的字段现算 |
| "Keep UI logic in the UI, not in the ViewModel, particularly when it involves UI types like `Context`."（UI 层页） | — | ✅ ViewModel 只说"有句话要提示"，用 Toast 还是 Snackbar 由界面决定 |
| "DO NOT pass a ViewModel instance to a plain state holder class."（状态持有者页） | — | ✅ Compose 的界面只接 `state` 和 `onIntent` |
| "Test StateFlows ... Assert on the `value` property." / "Prefer fakes to mocks." | 强推荐 | ✅ 测试断言 `uiState.value`，用真的假数据源而不是 mock |
| "Use dependency injection ... mainly constructor injection when possible." | 强推荐 | ✅ 仓库由构造参数传进来（示例里给了默认值图省事） |

### 上一版错在哪、这次改了什么

（诚实记一笔，免得以后又照着错的说）

- **错的**：我之前引的是 André Staltz（Cycle.js，JS 那边）和 Hannes Dorfmann（Mosby）的文章，
  还给"ViewModel 往界面发一次性事件"（`uiEffect` 那条 Channel 通道）找了个"合理扩展"的说法。
  按官方文档，**这条是明确违反强推荐规则的**，不是"扩展"，是"不要这么干"。
- **改了什么**：
  1. 基类去掉了 `UiEffect` / `uiEffect` / `setEffect`，泛型从三个减到两个（`State` 和 `Intent`）；
  2. 提示改成状态里的一个字段 `message: String?`，加一个操作 `MessageShown`；
  3. 界面显示完那句话之后回报 `MessageShown`，ViewModel 把它清掉。Compose 版用的是官方推荐的
     `LifecycleResumeEffect`（只在页面可见时执行）；
  4. 13 个单元测试同步改掉，仍然全过。
- **顺带撤掉一个说法**：上一版我说"我们简化了 reducer，和官方定义有差别"。
  官方文档**从头到尾没有要求"纯函数 reducer"**，只要求"状态是被 ViewModel 转换过的应用数据"。
  所以 `setState { copy(...) }` 完全符合官方要求，不用当成缺陷。

---

## 五、核心代码长什么样

`BaseViewModel` 一共就四样东西，很好记：

```kotlin
abstract class BaseViewModel<State : UiState, Intent : UiIntent> : ViewModel() {

    val uiState: StateFlow<State>   // 页面状态：界面订阅它

    protected abstract fun initializeState(): State      // 页面刚打开时是什么样
    protected abstract fun handleIntent(intent: Intent)  // 用户操作之后要干什么

    protected fun setState(...)      // 改状态
    fun setIntent(intent: Intent)    // 界面上报操作
}
```

拿"清掉已完成"举例，逻辑就这么点：

```kotlin
override fun handleIntent(intent: TaskContract.Intent) {
    when (intent) {
        TaskContract.Intent.ClearDone -> clearDone()
        TaskContract.Intent.MessageShown -> setState { copy(message = null) }  // 界面弹完了，清掉
        // ...
    }
}

private fun clearDone() {
    setState {
        copy(
            tasks = tasks.filterNot { it.done },
            message = "清掉了 $doneCount 条已完成的",   // 要提示的话，写进状态
        )
    }
}
```

### 怎么写一个自己的页面（XML 版）

**第 1 步**：抄一份 `TaskContract.kt`，改成你这一页的状态和操作。

**第 2 步**：抄 `TaskViewModel.kt`，实现 `initializeState()` 和 `handleIntent()`，逻辑全写这儿。

**第 3 步**：抄 `TaskActivity.kt`（和 `TaskFragment.kt`，如果想把列表拆出来）：

```kotlin
class YourActivity :
    BaseActivity<ActivityYourBinding, YourViewModel>(ActivityYourBinding::inflate) {

    override val viewModel: YourViewModel by viewModels()   // 这一行系统帮你管好

    override fun initialize(savedInstanceState: Bundle?) {
        // 只做一件事：把按钮接到"操作"上，不写任何计算
        viewBinding.btnXxx.setOnClickListener { viewModel.setIntent(YourContract.Intent.Xxx) }
    }

    override fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // 把 state 画到界面上
                    // 状态里有要提示的话，就显示出来，然后回报一句
                    state.message?.let {
                        Toast.makeText(this@YourActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.setIntent(YourContract.Intent.MessageShown)
                    }
                }
            }
        }
    }
}
```

**第 4 步**：在 `component_business_basic/src/main/AndroidManifest.xml` 里登记这个页面，
并给它挂上 `@style/business_basic_theme`（用 XML 写页面必须挂 AppCompat 主题，否则一打开就闪退）。

### 怎么写一个自己的页面（Compose 版）

前两步完全一样（Contract 和 ViewModel 都不用改），第三步换成：

```kotlin
setContent {
    MaterialTheme {
        val state by viewModel.observeState()      // 状态变了自动重画

        // 状态里有要提示的话，页面可见时弹出来，然后回报一句
        LifecycleResumeEffect(state.message) {
            state.message?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                viewModel.setIntent(YourContract.Intent.MessageShown)
            }
            onPauseOrDispose { }
        }

        // 界面只接"当前状态"和"往哪儿上报操作"，自己不碰 ViewModel
        YourScreen(state = state, onIntent = viewModel::setIntent)
    }
}

@Composable
private fun YourScreen(state: YourContract.State, onIntent: (YourContract.Intent) -> Unit) {
    // 给同样的 state，画出来的一定是同样的界面
    Button(onClick = { onIntent(YourContract.Intent.Xxx) }) { Text("点我") }
}
```

这样拆的好处：想预览某个样子，直接造一份假状态丢进去就行，不用启动 App、也不用真连数据。

---

## 六、单元测试

13 个测试，全跑在电脑上，不用模拟器，一秒多就跑完：

```bash
./gradlew test
```

| 测试文件 | 个数 | 盯住什么 |
|---|---:|---|
| `BaseViewModelTest` | 5 | 基类本身的行为：初始状态能不能用子类构造参数、上报的操作会不会被收到、状态是不是"换一份新的"、状态里那句提示回报之后会不会被清掉、界面中途才开始订阅能不能立刻拿到当前值 |
| `TaskViewModelTest` | 8 | 示例的数据流：进页面自动加载、加载中的中间状态、加载完成留下提示、连着三次加载第三次失败、失败后重试能恢复、勾选只动一条、清空只动已完成那几条、没有已完成时只多一句提示不改数据 |

仓库里那个"等 800 毫秒"在测试里是**虚拟时间**，不用真等；这也正是"逻辑都收在 ViewModel 里"的好处 ——
不用模拟器就能把每条分支都验一遍。

---

## 七、依赖版本（全部是当前最新稳定版）

下面这些是 2026-09-11 从 Google Maven / Maven Central 上查到的**最新稳定版**（已经排除 alpha / beta / rc）：

| 东西 | 版本 | 说明 |
|---|---|---|
| Gradle | 9.7.1 | 构建工具本体 |
| AGP（Android Gradle 插件） | 9.4.0 | |
| Kotlin | 2.4.20 | 顺带决定 Compose 编译器插件版本 |
| compileSdk / targetSdk | 37 | minSdk 24 |
| core-ktx | 1.19.0 | |
| appcompat | 1.8.0 | XML 页面的主题依赖它 |
| fragment-ktx | 1.9.0 | `activityViewModels()` 来自它 |
| lifecycle（runtime / viewmodel / compose） | 2.11.0 | `LifecycleResumeEffect`、`collectAsStateWithLifecycle` 都在这 |
| activity（ktx / compose） | 1.13.0 | |
| Compose BOM | 2026.09.00 | 所有 Compose 库的版本由它统一决定 |
| kotlinx-coroutines（android / test） | 1.11.0 | test 那个是单元测试用的 |
| junit | 4.13.2 | 单元测试框架 |

改版本只改一个文件：`gradle/libs.versions.toml`。

---

## 八、怎么编译和运行

```bash
./gradlew test                    # 13 个单元测试，跑在电脑上，不用模拟器
./gradlew :app:assembleDebug      # 产出：app/build/outputs/apk/debug/app-debug.apk

# 装到设备上
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **本机说明（换机器要看这一段）**
> 这台机器直连 `dl.google.com` 和 `repo1.maven.org` 经常 TLS 握手失败或卡住，
> 所以 `settings.gradle.kts` 里解析仓库**只用阿里云镜像**，没有回落到官方源；
> 换到网络正常的机器上，把 `google()` 和 `mavenCentral()` 加回列表即可。
> 另外 Gradle 发行包从官方地址下载极慢，本机是用腾讯镜像（`mirrors.cloud.tencent.com/gradle/`）下好放进
> `~/.gradle/wrapper/dists/` 的（校验和与官方一致）。网络正常的话直接 `./gradlew` 即可自动下载。
> `gradle.properties` 里登记了本地 JDK 路径并关闭了工具链自动下载，换机器可能要改。

---

## 九、下一步：加 Retrofit

现在的数据是 `TaskRepository` 里的假数据。加 Retrofit 时**只需要动这一层**：

1. `gradle/libs.versions.toml` 里加上 Retrofit、OkHttp、Gson 的最新版本，在 `component_basic` 里声明依赖；
2. 在 `component_basic` 里建一个网络客户端（Retrofit 实例 + 拦截器 + 日志）；
3. 定义接口（例如 `interface TaskApi { @GET("tasks") suspend fun tasks(): List<TaskDto> }`）；
4. 把 `TaskRepository` 改成"调接口 → 把返回的数据转成 `Task`"，方法名保持 `loadTasks()` 不变。

这样 **Contract、ViewModel、三份界面一行都不用改** —— 这就是把"取数据"单独放一层的意义。

顺带一提：官方还建议 ViewModel 的依赖走构造参数注入（现在是给了个默认值图省事），
等依赖多起来（网络、存储、日志）再考虑加一个手动依赖容器或 Hilt。

---

## 十、你自己的组件往哪儿填

| 你要加的东西 | 放哪儿 |
|---|---|
| 新页面（状态 / 操作 / 界面） | `component_business_basic`，照抄 `ui/task` 的结构 |
| 通用 UI 控件（弹窗、进度条、自定义 View……） | `component_common`，新建 `ui/widget` 目录 |
| 网络、本地存储、日志、工具类 | `component_basic` |
| 应用启动时要做的初始化 | `component_basic` 的 `BasicApplication.onCreate()` |
| 新增第三方库 | 只改 `gradle/libs.versions.toml`，然后在对应模块 `build.gradle.kts` 里引用 |

**几个注意点**

1. 用 XML 写页面时，Activity 必须挂一个 AppCompat 主题（示例用的是 `business_basic_theme`），否则打开就闪退。
2. 新模块的资源名记得加前缀（示例模块用的是 `business_basic_`），免得以后模块多了资源重名打架。
3. 界面里不要写业务判断，全部塞进 `handleIntent`；要提示的话写进状态，不要自己造一条"发事件"的通道。
4. 能从别的字段算出来的，别再在状态里存一份（示例里 `loading`、`total`、`doneCount` 都是现算的）。
5. 示例里的列表是"清空重建"的写法，为的是让人一眼看懂；条数多了要换成 RecyclerView（那属于你自己要做的组件）。
6. 名字都统一成 TScaffold 了：`rootProject.name`、`app` 的 `applicationId` 与 `namespace`、各模块 `namespace`、包名 `com.tscaffold`、应用显示名 `app_name`、Compose 主题 `TScaffoldTheme`、XML 主题 `Theme.TScaffold`。以后换正式名字，按这几处一次替掉即可。
