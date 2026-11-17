# FastDowload

单仓库可以放多个 SDK。每个 SDK 占仓库根目录下的一个目录，它自己的实现、测试夹具和 Demo 放在这个目录里面。

当前的 SDK 是 FastDowload：把文件下到应用内部目录。对外入口是 `FastDownloader`，任务、分组、查询和配置分开放。回调在主线程。样本应用只声明 `INTERNET`。

## FastDowload

| 模块 | 类型 | 现在做什么 |
| --- | --- | --- |
| `:fast-dowload` | 聚合，不发布 | SDK 入口。`:fast-dowload:check` 跑它下面的子模块 |
| `:fast-dowload:core` | 纯 JVM | 请求、分块、状态、错误码、HTTP 与写出目标契约 |
| `:fast-dowload:engine` | 纯 JVM | 下载、命令队列、断点。默认 HTTP 客户端是 `HttpURLConnection` |
| `:fast-dowload:android` | Android 库 | Room、网络状态、内容 Uri 写入 |
| `:fast-dowload:api` | Android 库 | `FastDownloader` 门面、主线程回调、Kotlin DSL 与 Java Builder |
| `:fast-dowload:notify` | Android 库，可选 | 前台服务、通知、WorkManager 保活。不依赖时不合并这些权限 |
| `:fast-dowload:okhttp` | 纯 JVM | 显式注册的 OkHttp 客户端 |
| `:fast-dowload:ktx` | Android 库 | `await`、进度 Flow |
| `:fast-dowload:testing` | 纯 JVM，不发布 | MockWebServer 夹具 |
| `:fast-dowload:sample` | Application，不发布 | 一个按钮、进度条和已保存任务列表 |

另一个 SDK 另开根目录，例如 `pager/`，不要再把 `fast-dowload-core` 这类名字铺到仓库根上。

## 非目标

- 不保活，不承诺进程被杀掉之后继续下
- 不做多进程
- 默认任务仍写入应用内部相对路径

## 命令

```bash
./gradlew :fast-dowload:core:test :fast-dowload:engine:test :fast-dowload:api:testDebugUnitTest
./gradlew :fast-dowload:sample:assembleDebug
./gradlew ciCheck
```

核心模块的 `check` 会跑 `assertJvmOnly`，编译 classpath 里不能出现 Android 构件。

构建脚本是 Gradle Kotlin DSL。发布约定留在 `build-logic`。除 `testing` 和 `sample` 外，FastDowload 模块都已接上发布插件，当前预览版是 `1.0.0-rc`。
