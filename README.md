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

## 四、和 MVI 的官方定义逐条对照

MVI 不是随口叫的名字，它有两个明确出处：

- **André Staltz**（Cycle.js 作者）2015 年提出，核心是"界面、状态、用户意图"三者绕成一个闭环；
- **Hannes Dorfmann**（Mosby 作者）2017 年那组《Reactive Apps with Model-View-Intent》把它落到安卓上，定下了下面这几条。

这个工程是照着这几条对过的，结果如下：

| # | MVI 的要求（大白话） | 本工程 | 说明 |
|---|---|---|---|
| 1 | 数据只朝一个方向走：操作 → 状态 → 界面 → 操作 | ✅ 符合 | 界面只上报操作，改状态只有 ViewModel 能改 |
| 2 | **界面是状态的函数**：自己不存东西、不做判断 | ✅ 符合 | XML 版是 `render(state)`；Compose 版的界面只接 `state` 和 `onIntent`，连 ViewModel 都拿不到 |
| 3 | Intent 表示"用户想干什么" | ✅ 符合 | `TaskContract.Intent` 就是"用户点了什么、勾了哪条" |
| 4 | 状态不可变，而且是**唯一的事实来源** | ✅ 符合 | 状态用 `data class` 装 `List`；能从别的字段算出来的一律现算（`loading`、`total`、`doneCount`），不另存一份 |
| 5 | 状态变化要由纯函数算：新状态 = reduce(旧状态, 结果) | ⚠️ 简化了 | 见下面「第一处不同」 |
| 6 | 网络、弹窗这类"跟外界打交道的事"不能写在状态计算和界面里 | ✅ 基本符合 | 取数据在 ViewModel 里；弹提示由界面执行（界面本来就是干这个的地方） |

### 第一处不同：没有单独的 reduce 函数，而是写在处理操作的旁边

严格版（Mosby 那套）会拆成两步：

```
用户操作 → 业务逻辑算出"结果" → 一个纯函数把"旧状态 + 结果"算成"新状态"
```

我们这个骨架里，`setState { copy(...) }` 的那个**大括号本身就是那个纯函数**（给同样的旧状态，一定算出同样的新状态），只是没有单独抽成一个方法。Airbnb 的 Mavericks 也是这个路子，所以这么写不算跑偏。

- **换来的是**：代码短，不用每个页面都多写一个 reduce 方法，上手快。
- **代价是**：动作多了以后，"这个状态会被哪些地方改"没法一眼看全，也做不了"操作回放"这类调试。
- **什么时候该升级**：一页有十几个操作了，或者你要做操作回放调试，就把 reduce 单独拆出来。

### 第二处不同：Effect（一次性事件）这条通道，标准 MVI 里没有

标准 MVI 只有"状态"一个事实来源，连"弹一次提示"也要放进状态里（比如 `state.toastMessage`，弹完再上报一个"清掉提示"的操作）。

我们额外加了一条 Channel 通道，原因很实在：用状态表示"弹一次提示"，得额外配一个"弹完请清掉"的操作，写起来啰嗦、还容易忘，一忘就会重复弹。

- **代价是要守住一条纪律**：事件只在一处收。示例里只有 Activity 收 `uiEffect`，Fragment 只收 `uiState`。两处都收的话，一条提示会被两边抢着消费，表现就是"有时候弹有时候不弹"。

### 核对的时候顺手修掉的三个坑（记在这，免得以后又踩）

1. **基类不能在自己构造的时候去调子类的 `initializeState()`**。
   基类的初始化比子类构造参数的赋值更早，子类要是在 `initializeState()` 里用了自己的构造参数，
   对象类型会直接空指针，数字类型会**静默变成 0**——查起来非常费劲。
   已经在 `BaseViewModel` 里改成"用到的时候才初始化"，并补了测试盯住它（测试先跑失败、改完才通过）。
2. **同一个事实不要存两份**。
   原来 `loading` 和 `loadStatus = Loading` 说的是一件事，哪天改了一处忘了另一处就对不上。
   现在 `loading` 直接由 `loadStatus` 现算，状态里只留最原始的那几样。
3. **Compose 的界面不该拿到 ViewModel**。
   原来整个 ViewModel 传进了界面函数，界面就不是"状态的函数"了，也没法单独预览。
   现在界面只接 `state` 和 `onIntent`，于是可以直接造一份假状态来预览
   （`TaskComposeActivity.kt` 末尾有两个预览：有数据的样子、加载失败的样子，不用跑 App 就能看）。

---

## 五、核心代码长什么样

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

        // 界面只接"当前状态"和"往哪儿上报操作"，自己不碰 ViewModel
        YourScreen(
            state = state,
            onIntent = viewModel::setIntent,
        )
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
| `BaseViewModelTest` | 5 | 基类本身的行为：初始状态能不能用子类构造参数、上报的操作会不会被收到、状态是不是"换一份新的"、一次性事件是不是取走就没、界面中途才开始订阅能不能立刻拿到当前值 |
| `TaskViewModelTest` | 8 | 示例的数据流：进页面自动加载、加载中的中间状态、加载完成弹提示、连着三次加载第三次失败、失败后重试能恢复、勾选只动一条、清空只动已完成那几条、没有已完成时只弹提示不改数据 |

仓库里那个"等 800 毫秒"在测试里是**虚拟时间**，不用真等；这也正是"逻辑都收在 ViewModel 里"的好处 —— 不用模拟器就能把每条分支都验一遍。

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
| lifecycle（runtime / viewmodel / compose） | 2.11.0 | |
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

---

## 十、你自己的组件往哪儿填

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
