# TScaffold —— 只保留 MVI 的安卓骨架工程

一句话说明白：**这是一个只保留"页面怎么写"的最小安卓工程。**

没有网络请求、没有图片加载、没有弹窗控件库、没有工具类大礼包——那些都清掉了。
留下的是一套写页面的固定套路（MVI），外加两个照着抄就能用的示例页面。
你自己做的组件，后面往预留的位置里填就行。

---

## 一、先说清楚 MVI 是什么（大白话版）

写一个页面，其实绕不开三件事，MVI 就是把它们分开放、不许混着写：

| 三件事 | 大白话 | 放在哪儿 | 举个例（计数器页面） |
|---|---|---|---|
| **状态**（State） | 这一页现在长什么样 | 一个 data class | 数字是几、下面提示是什么 |
| **操作**（Intent） | 用户在这一页干了什么 | 一组 sealed interface 分支 | 点了"加一"、点了"归零" |
| **事件**（Effect） | 只需要做一次的事 | 一组 sealed interface 分支 | 弹一次提示、跳一次页面 |

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
├── component_business_basic   ★ 你以后写页面的地方（现在是两个示例页面）
├── component_common           ★ MVI 核心（这套写法的全部家当都在这）
└── component_basic            最底层，留给你放基础能力（现在几乎是空的）
```

依赖方向是一层压一层，上面的能用下面的，下面的不能反过来用上面的：

```
app  →  component_business_basic  →  component_common  →  component_basic
（外壳）      （你的页面）            （MVI 写法）        （基础能力）
```

### 每个文件是干什么的

**component_common（MVI 核心，一共 5 个文件）**

| 文件 | 作用 |
|---|---|
| `ui/viewmodel/BaseViewModel.kt` | 三样东西的定义 + 所有 ViewModel 的父类。**想弄懂这套写法，看这一个文件就够** |
| `ui/contract/BaseContract.kt` | 一个页面约定的模板文件，照着抄改自己的 |
| `ui/activity/BaseActivity.kt` | 用 XML 写页面时的 Activity 父类，帮你加载布局、按顺序调用你的代码 |
| `ui/fragment/BaseFragment.kt` | 同上，Fragment 版 |
| `ui/compose/MviCompose.kt` | 用 Compose 写页面时的两个小工具：`observeState()` / `observeEffect()` |

**component_business_basic（示例，一共 4 个文件）**

| 文件 | 作用 |
|---|---|
| `ui/counter/contract/CounterContract.kt` | 计数器页面的状态 / 操作 / 事件 |
| `ui/counter/viewmodel/CounterViewModel.kt` | 计数器的全部逻辑，只有 20 行 |
| `ui/counter/view/CounterActivity.kt` | **示例一**：用 XML 写的界面 |
| `ui/counter/compose/CounterComposeActivity.kt` | **示例二**：用 Compose 写的界面 |

两个示例共用同一个 `CounterViewModel`，**逻辑一行都没有重复**——这就是这套写法的价值。

**component_basic**

| 文件 | 作用 |
|---|---|
| `BasicApplication.kt` | 应用启动入口，现在是空的，专门留给你做初始化 |
| `extensions/ViewModel.kt` | 想让多个页面共用同一份数据时用（App 级 ViewModel） |

---

## 三、核心代码长什么样

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

拿计数器举例，全部逻辑就这么点：

```kotlin
class CounterViewModel :
    BaseViewModel<CounterContract.State, CounterContract.Intent, CounterContract.Effect>() {

    override fun initializeState() = CounterContract.State()

    override fun handleIntent(intent: CounterContract.Intent) {
        when (intent) {
            CounterContract.Intent.Increase -> setState { copy(count = count + 1) }
            CounterContract.Intent.Decrease -> setState { copy(count = count - 1) }
            CounterContract.Intent.Reset -> {
                setState { copy(count = 0) }                                  // 改状态
                setEffect { CounterContract.Effect.ShowToast("数字已清零") }   // 弹提示
            }
        }
    }
}
```

---

## 四、怎么加一个自己的页面

### 版本一：用 XML 写界面

**第 1 步**，新建 `YourContract.kt`，把这一页的三样东西写出来：

```kotlin
class YourContract {
    data class State(val loading: Boolean = false, val list: List<String> = emptyList()) : UiState

    sealed interface Intent : UiIntent {
        data object Load : Intent                 // 用户做的事
        data class OnItemClick(val position: Int) : Intent
    }

    sealed interface Effect : UiEffect {
        data class ShowToast(val message: String) : Effect   // 只做一次的事
    }
}
```

**第 2 步**，新建 `YourViewModel.kt`，继承 `BaseViewModel`，实现那两个方法。

**第 3 步**，新建 `YourActivity.kt`：

```kotlin
class YourActivity :
    BaseActivity<ActivityYourBinding, YourViewModel>(ActivityYourBinding::inflate) {

    override val viewModel: YourViewModel by viewModels()   // 这一行系统帮你管好

    override fun initialize(savedInstanceState: Bundle?) {
        // 只做一件事：把按钮接到"操作"上，不写任何计算
        viewBinding.btnLoad.setOnClickListener { viewModel.setIntent(YourContract.Intent.Load) }
    }

    override fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {   // 状态变了就重画界面
                    viewModel.uiState.collect { state -> /* 把 state 画到界面上 */ }
                }
                launch {   // 一次性事件
                    viewModel.uiEffect.collect { effect -> /* 弹提示、跳页面 */ }
                }
            }
        }
    }
}
```

**第 4 步**，在 `component_business_basic/src/main/AndroidManifest.xml` 里登记这个页面，
并给它挂上 `@style/business_basic_theme`（用 XML 写页面必须挂 AppCompat 主题，否则一打开就闪退）。

### 版本二：用 Compose 写界面

前两步完全一样（Contract 和 ViewModel 都不变），第三步换成：

```kotlin
class YourComposeActivity : ComponentActivity() {

    private val viewModel: YourViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.observeState()       // 状态变了自动重画
                viewModel.observeEffect { effect -> /* 弹提示、跳页面 */ }
                // 下面正常写你的 Composable
            }
        }
    }
}
```

> 两个版本的区别只有"界面怎么写"，Contract 和 ViewModel 一个字都不用改。

---

## 五、怎么编译

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

装到手机上打开，首页有两个按钮，分别进 XML 版和 Compose 版的示例页面。
点"加一 / 减一 / 归零"能看到数字变化，"归零"还会弹一条提示。

> **本机说明**：这台机器访问部分 Maven 源不稳定，`settings.gradle.kts` 里前置了阿里云镜像；
> `gradle.properties` 里登记了本地 JDK 路径并关闭了自动下载。换台机器可能要改这两处。

---

## 六、你自己的组件往哪儿填

| 你要加的东西 | 放哪儿 |
|---|---|
| 新页面（Contract / ViewModel / 界面） | `component_business_basic`，照抄 `ui/counter` 的结构 |
| 通用 UI 控件（弹窗、进度条、自定义 View……） | `component_common`，新建 `ui/widget` 目录 |
| 网络、本地存储、日志、工具类 | `component_basic` |
| 应用启动时要做的初始化 | `component_basic` 的 `BasicApplication.onCreate()` |
| 新增第三方库 | 只改 `gradle/libs.versions.toml`，然后在对应模块 `build.gradle.kts` 里引用 |

**几个注意点**

1. 用 XML 写页面时，Activity 必须挂一个 AppCompat 主题（示例用的是 `business_basic_theme`），否则打开就闪退。
2. 新模块的资源名记得加前缀（示例模块用的是 `business_basic_`），免得以后模块多了资源重名打架。
3. 界面里不要写业务判断，全部塞进 `handleIntent`，这是这套写法唯一需要守的规矩。
4. 名字都统一成 TScaffold 了：`settings.gradle.kts` 的 `rootProject.name`、`app` 的 `applicationId` 与 `namespace`、各模块 `namespace`、包名 `com.tscaffold`、应用显示名 `app_name`、Compose 主题 `TScaffoldTheme`、XML 主题 `Theme.TScaffold`。以后要换成正式名字，按这几处一次替掉即可。
