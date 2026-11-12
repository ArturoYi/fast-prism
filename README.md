# FastPrism

单仓库可以放多个 SDK。每个 SDK 占仓库根目录下的一个目录，它自己的实现、测试夹具和 Demo 放在这个目录里面。

当前的 SDK 是 FastPrism：只把文件下到应用内部目录。最小闭环是一个 URL、一条连接，写入 `filesDir` 下的相对路径，并持续回调进度。没有暂停、取消或停止。进程结束，下载就结束。样本应用只声明 `INTERNET`。

## FastPrism

| 模块 | 类型 | 现在做什么 |
| --- | --- | --- |
| `:fast-prism` | 聚合，不发布 | SDK 入口。`:fast-prism:check` 跑它下面的子模块 |
| `:fast-prism:core` | 纯 JVM | 请求、分块、状态、错误码。不依赖 Android |
| `:fast-prism:engine` | 纯 JVM | 单连接下载、`HttpURLConnection`、落盘后 `sync` |
| `:fast-prism:testing` | 纯 JVM，不发布 | MockWebServer 夹具 |
| `:fast-prism:sample` | Application，不发布 | 一个按钮和进度条 |

以后的 `android`、`okhttp`、`ktx` 也放在 `fast-prism/` 下面。另一个 SDK 另开根目录，例如 `pager/`，不要再把 `fast-prism-core` 这类名字铺到仓库根上。

## 非目标

- 不提供暂停、取消、停止
- 不把文件写到外部存储或 `content://`
- 不保活，不承诺进程被杀掉之后继续下
- 不做多进程、Room、分块并行、对外稳定 API

## 命令

```bash
./gradlew :fast-prism:core:test :fast-prism:engine:test
./gradlew :fast-prism:sample:assembleDebug
./gradlew ciCheck
```

核心模块的 `check` 会跑 `assertJvmOnly`，编译 classpath 里不能出现 Android 构件。

构建脚本是 Gradle Kotlin DSL。发布约定留在 `build-logic`。FastPrism 这一阶段还没有接上发布插件。
