<div align="center">

# COUI-Kit

面向 Android Jetpack Compose 的 COUI 组件与视觉效果库

[![Maven Central](https://img.shields.io/maven-central/v/io.github.itoseo/coui?label=Maven%20Central&color=blue)](https://central.sonatype.com/artifact/io.github.itoseo/coui)
[![Android API 31+](https://img.shields.io/badge/minSdk-31-3DDC84?logo=android)](docs/guide/compatibility.md)
[![MPL 2.0](https://img.shields.io/badge/License-MPL--2.0-blue)](LICENSE)
[![Telegram 讨论群组](https://img.shields.io/badge/Telegram-%E8%AE%A8%E8%AE%BA%E7%BE%A4%E7%BB%84-26A5E4?logo=telegram&logoColor=white)](https://t.me/metric_official/2949)

</div>

COUI-Kit 参考 ColorOS 17 的「凝光视效」，提供背景模糊、材质混色、轮廓光和跟手动效。

## 特点

- 组件和效果可以分别使用，也可以组合使用。
- 纯 COUI 页面可组合 `CouiTheme` 与可选的 [Scaffold](docs/components/scaffold.md)，统一栏位测量和安全区，无需 Material 3 等外部依赖。
- 可以只取用单个 COUI 组件，与 Material、Miuix 或自有设计混搭；不要求替换宿主的 Scaffold、主题和导航。
- 支持接入 App 的配色、文字样式和形状，并提供常用外观预设。
- 提供原生主题、组件默认值与可选 `coui-material3` 适配模块。核心独立于 Material 3；M3 宿主可在启动时初始化适配，见[外观与预设](docs/guide/appearance.md)。
- 基于 Android、Compose 和 AndroidX 的公开 API，不依赖厂商 SDK 或私有 SO；支持 Android 12 及以上设备，部分效果要求更高版本。

## Demo

运行 `demo` 模块，可以从目录进入组件与效果详情，并切换深浅色和外观预设。运行方法、操作说明和 CI 构建下载方式见 [Demo 指南](docs/guide/demo.md)。

## 开始使用

### 引入依赖

在已启用 Compose 的模块中添加核心依赖：

```kotlin
dependencies {
    implementation("io.github.itoseo:coui:0.0.1-alpha11")
}
```

仓库配置和工具链要求见[快速开始](docs/guide/getting-started.md)。部分效果需要更高的 Android 版本，见[兼容要求](docs/guide/compatibility.md#效果门槛)。

使用 `CouiIcons` 时，添加可选的图标依赖，详见[图标用法](docs/components/icons.md)。

### 接入 Material 3

已有 Material 3 App 可以让 COUI 的默认配色和字体跟随当前 `MaterialTheme`，插槽中的 M3 `Text`、`Icon` 直接继承组件样式，无需逐处接线或增加根主题包装。

`coui`、`coui-material3` 与 `coui-icons` 采用相同版本并同步发布。通过 Maven Central 接入 M3 时，只需添加适配模块：

```kotlin
val couiVersion = "0.0.1-alpha11"

dependencies {
    implementation("io.github.itoseo:coui-material3:$couiVersion")
}
```

适配模块会传递引入同版本核心与 M3；如果同时显式声明 `coui`，也使用该版本。

协作调试时，也可通过 [GitHub Packages](docs/guide/github-packages.md) 接入已发布的开发快照。需要本地修改库时，按[源码模块接入](docs/guide/getting-started.md#源码模块)配置工程；`project(":coui-material3")` 仅用于这种情况。

在已有 `Application.onCreate()` 中、创建 Compose UI 前初始化一次：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Coui.initialize(defaultStyle = CouiMaterial3Style())
    }
}
```

已有 Application 只需加入初始化；新建 Application 时需在 manifest 中注册。初始化选择固定策略，重复调用会报错。组件在各自的 Compose 作用域读取 M3 配色与字体，随动态色、主题动画和嵌套主题更新。

默认深浅模式跟随系统。App 有独立主题开关时，在现有主题 Provider 中提供实际的 `darkTheme`：

```kotlin
CompositionLocalProvider(LocalCouiDarkTheme provides darkTheme) {
    content()
}
```

`darkTheme` 和 `content` 来自宿主主题。嵌套 `MaterialTheme` 若独立改变深浅模式，也在该边界提供此 Local；库不会从自定义色板猜测模式。显式组件参数继续优先，局部原生主题、字体与染色规则见[外观与预设](docs/guide/appearance.md)。

## 文档

- [快速开始](docs/guide/getting-started.md)
- [组件目录](docs/components/README.md)
- [图标用法](docs/components/icons.md)
- [效果目录](docs/effects/README.md)
- [兼容要求](docs/guide/compatibility.md)
- [外观与预设](docs/guide/appearance.md)
- [与原厂的定制差异](docs/guide/customizations.md)
- [GitHub Packages 开发快照](docs/guide/github-packages.md)
- [更新记录与版本映射](CHANGELOG.md)

## 反馈与贡献

遇到问题，可以先查看[常见问题](docs/guide/getting-started.md#常见问题)。反馈时请附上库版本、Android 版本、复现步骤和相关日志；视觉问题请同时提供截图或录屏。

欢迎提交修复、组件、效果或文档改进，参与开发请阅读[贡献指南](CONTRIBUTING.md)。

## 许可与名称使用

本项目自有源码采用 [MPL 2.0](LICENSE)，第三方内容保留各自许可与版权声明，具体范围见 [NOTICE](NOTICE)。

ColorOS 及相关名称归 OPPO 所有；本项目与 OPPO 没有隶属、授权或背书关系。

修改版和分叉版的发行名称、Logo 及来源说明遵循[名称与标识使用政策](TRADEMARKS.md)。

---

[![Star History Chart](https://api.star-history.com/svg?repos=ItosEO/COUI-Kit&type=Date)](https://star-history.com/#ItosEO/COUI-Kit&Date)
