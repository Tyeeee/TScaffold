# TScaffold —— 只保留 MVI 的安卓骨架工程

一句话说明白：**这是一个只保留"页面怎么写"的最小安卓工程。**

没有网络请求、没有图片加载、没有弹窗控件库、没有工具类大礼包——那些都清掉了。
留下的是一套写页面的固定套路（MVI），外加**一个能完整跑起来、能从数据一路走到界面的示例页面**。
你自己做的组件，后面往预留的位置里填就行。

---

## 一、先说清楚 MVI 是什么（大白话版）

写一个页面，其实绕不开三件事，MVI 就是把它们分开放、不许混着写：

| 三件事 | 大白话 | 放在哪儿 | 示例里的例子 |
|---|---|---|---|
| **状态**（State） | 这一页现在长什么样 | 一个 data class | 列表有几条、正在转圈、还是加载失败 |
| **操作**（Intent） | 用户在这一页干了什么 | 一组 sealed interface 分支 | 点了"重新加载"、勾了某一条、点了"清掉已完成" |
| **事件**（Effect） | 只需要做一次的事 | 一组 sealed interface 分支 | 弹一次提示 |

它们之间的走动方向是固定的，永远只有这一条：

```
        用户点了按钮
             │
             ▼
   界面 ──上报操作──► ViewModel ──算出新状态──► 界面重新画一遍
                          │
                          └──发出一次性事件──► 弹提示 / 跳页面
```

**这样写有什么好处？**

1. 界面里不用写 if/else 的判断逻辑，只负责"显示"和"上报操作"，看代码快。
2. 所有逻辑都收在 ViewModel 一个文件里，写单元测试不用启动模拟器。
3. 状态只有一个来源，不会出现"两个地方各改一半、显示对不上"的问题。

**为什么"事件"要单独拎出来？**
因为它跟状态不一样。状态是"现在是几就是几"，界面随时可以重新读一遍；
而"弹一次提示"这种事一旦被重新执行一次，用户就会看到弹两次。
所以事件走单独的通道，谁拿走就没了，不会重复。

---

## 二、工程结构

```
TScaffold
├── app                       应用外壳：只有一个首页，放两个按钮进示例
├── component_business_basic   ★ 你以后写页面的地方（现在是"任务列表"示例）
├── component_common           ★ MVI 核心（这套写法的全部家当都在这）
└── component_basic            最底层，留给你放基础能力（现在几乎是空的）
```

依赖方向是一层压一层，上面的能用下面的，下面的不能反过来用上面的：

```
app  →  component_business_basic  →  component_common  →  component_basic
（外壳）      （你的页面）            （MVI 写法）        （基础能力）
```

### component_common（MVI 核心，一共 5 个文件）

| 文件 | 作用 |
|---|---|
| `ui/viewmodel/BaseViewModel.kt` | 三样东西的定义 + 所有 ViewModel 的父类。**想弄懂这套写法，看这一个文件就够** |
| `ui/contract/BaseContract.kt` | 一个页面约定的空白模板，照抄着改成自己的 |
| `ui/activity/BaseActivity.kt` | 用 XML 写页面时的 Activity 父类，帮你加载布局、按顺序调用你的代码 |
| `ui/fragment/BaseFragment.kt` | 同上，Fragment 版 |
| `ui/compose/MviCompose.kt` | 用 Compose 写页面时的两个小工具：`observeState()` / `observeEffect()` |

### component_business_basic（示例，6 个源码文件 + 3 个布局）

"任务列表"这一个示例，用两种界面写法各做了一遍，**共用同一份逻辑**：

| 文件 | 作用 |
|---|---|
| `ui/task/contract/TaskContract.kt` | 这个页面的状态 / 操作 / 事件 |
| `ui/task/data/TaskRepository.kt` | 数据从哪来。**现在是假数据源**，加 Retrofit 时换这里 |
| `ui/task/viewmodel/TaskViewModel.kt` | 这个页面的全部逻辑 |
| `ui/task/view/TaskActivity.kt` | **XML 版**：`BaseActivity`，顶部统计 + 承载下面的 Fragment |
| `ui/task/view/TaskFragment.kt` | **XML 版**：`BaseFragment`，列表本体 + 各按钮 |
| `ui/task/compose/TaskComposeActivity.kt` | **Compose 版**：同样一份逻辑，换成 Compose 画界面 |

> 注意：XML 版里 Activity 和 Fragment **共用同一个 ViewModel**（Activity 用 `by viewModels()` 建，Fragment 用 `by activityViewModels()` 拿），
> 所以顶部的"已完成 x / y"和下面列表永远是同一份数据。
> 另外 **一次性事件只在 Activity 一处收**——如果两处都收，一条提示会被抢着消费，出现"有时候弹有时候不弹"的怪现象。记住：状态可以多处订阅，事件只在一处处理。

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
| ⑤' | 出错了 → 把错误信息写进状态，界面自动显示错误 + "重试" | 同上，走 `catch` 分支 |
| ⑥ | 顺手弹一次提示（只弹一次，不重复） | `setEffect { ShowToast(...) }` → Activity 收下并弹 Toast |

### 你可以自己点一遍验证（都是实测过的）

1. 打开首页 → 点"示例一：XML 写的（Activity + Fragment）"
   → 先看到转圈，然后出现 4 条任务，顶部显示"已完成 1 / 4"。
2. 勾掉某一条 → 顶部统计立刻跟着变（**这就是 Activity 和 Fragment 共用同一份状态**）。
3. 点"清掉已完成" → 已完成的那几条从列表消失，顶部统计同步变化。
4. 连点两次"重新加载" → 第一次条数变多；**第二次会故意失败**（假数据源里写死了每第 3 次报错），
   界面出现红色的错误说明和"重试"按钮，同时弹一条提示。
5. 点"重试" → 恢复正常，列表又出来了。这就是"失败之后要能自己恢复"。
6. 回首页 → 点"示例二：Compose 写的" → 同样的数据和同样的行为，只是界面换成了 Compose。

> 那个"每第 3 次故意报错"是特意加的：不然出错这条分支你没法验证，
> 等接了真网络以后这种错天天会碰到，先在这儿把处理写好。

---

## 四、核心代码长什么样

`BaseViewModel` 一共就四样东西，很好记：

```kotlin
abstract class BaseViewModel<State : UiState, Intent : UiIntent, Effect : UiEffect> : ViewModel() {

    val uiState: StateFlow<State>      // 页面状态：界面订阅它
    val uiEffect: Flow<Effect>         // 一次性事件：界面订阅它

    protected abstract fun initializeState(): State      // 页面刚打开时是什么样
    protected abstract fun handleIntent(intent: Intent)  // 用户操作之后要干什么

    protected fun setState(...)   // 改状态
    fun setIntent(intent: Intent) // 界面上报操作
    fun setEffect(...)            // 发一次性事件
}
```

### 怎么写一个自己的页面（XML 版）

**第 1 步**：抄一份 `TaskContract.kt`，改成你这一页的三样东西。

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
                launch { viewModel.uiState.collect { state -> /* 把 state 画到界面上 */ } }
                launch { viewModel.uiEffect.collect { effect -> /* 弹提示、跳页面 */ } }
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
        viewModel.observeEffect { effect -> ... }  // 处理一次性事件
        // 下面正常写你的 Composable
    }
}
```

---

## 五、依赖版本（全部是当前最新稳定版）

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
| lifecycle（runtime / viewmodel / compose） | 2.11.0 | |
| activity（ktx / compose） | 1.13.0 | |
| Compose BOM | 2026.09.00 | 所有 Compose 库的版本由它统一决定 |
| kotlinx-coroutines | 1.11.0 | 现在还没用到，加 Retrofit 时要用 |
| junit / androidx.test / espresso | 4.13.2 / 1.3.0 / 3.7.0 | 测试用 |

改版本只改一个文件：`gradle/libs.versions.toml`。

---

## 六、怎么编译和运行

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

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

## 七、下一步：加 Retrofit

现在的数据是 `TaskRepository` 里的假数据。加 Retrofit 时**只需要动这一层**：

1. `gradle/libs.versions.toml` 里加上 Retrofit、OkHttp、Gson 的最新版本，在 `component_basic` 里声明依赖；
2. 在 `component_basic` 里建一个网络客户端（Retrofit 实例 + 拦截器 + 日志）；
3. 定义接口（例如 `interface TaskApi { @GET("tasks") suspend fun tasks(): List<TaskDto> }`）；
4. 把 `TaskRepository` 改成"调接口 → 把返回的数据转成 `Task`"，方法名保持 `loadTasks()` 不变。

这样 **Contract、ViewModel、三份界面一行都不用改** —— 这就是把"取数据"单独放一层的意义。

---

## 八、你自己的组件往哪儿填

| 你要加的东西 | 放哪儿 |
|---|---|
| 新页面（Contract / ViewModel / 界面） | `component_business_basic`，照抄 `ui/task` 的结构 |
| 通用 UI 控件（弹窗、进度条、自定义 View……） | `component_common`，新建 `ui/widget` 目录 |
| 网络、本地存储、日志、工具类 | `component_basic` |
| 应用启动时要做的初始化 | `component_basic` 的 `BasicApplication.onCreate()` |
| 新增第三方库 | 只改 `gradle/libs.versions.toml`，然后在对应模块 `build.gradle.kts` 里引用 |

**几个注意点**

1. 用 XML 写页面时，Activity 必须挂一个 AppCompat 主题（示例用的是 `business_basic_theme`），否则打开就闪退。
2. 新模块的资源名记得加前缀（示例模块用的是 `business_basic_`），免得以后模块多了资源重名打架。
3. 界面里不要写业务判断，全部塞进 `handleIntent`，这是这套写法唯一需要守的规矩。
4. 示例里的列表是"清空重建"的写法，为的是让人一眼看懂；条数多了要换成 RecyclerView（那属于你自己要做的组件）。
5. 名字都统一成 TScaffold 了：`rootProject.name`、`app` 的 `applicationId` 与 `namespace`、各模块 `namespace`、包名 `com.tscaffold`、应用显示名 `app_name`、Compose 主题 `TScaffoldTheme`、XML 主题 `Theme.TScaffold`。以后换正式名字，按这几处一次替掉即可。
