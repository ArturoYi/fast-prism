# FastDownload

单仓库可以放多个 SDK。每个 SDK 占仓库根目录下的一个目录，它自己的实现、测试夹具和 Demo 放在这个目录里面。

现在有两个 SDK。FastDownload 把文件下到应用内部目录。AlbumCleaner 是相册清理的骨架，目前只能初始化和释放。

## FastDownload

| 模块 | 类型 | 现在做什么 |
| --- | --- | --- |
| `:fast-download` | 聚合，不发布 | SDK 入口。`:fast-download:check` 跑它下面的子模块 |
| `:fast-download:core` | 纯 JVM | 请求、分块、状态、错误码、HTTP 与写出目标契约 |
| `:fast-download:engine` | 纯 JVM | 下载、命令队列、断点。默认 HTTP 客户端是 `HttpURLConnection` |
| `:fast-download:android` | Android 库 | Room、网络状态、内容 Uri 写入 |
| `:fast-download:api` | Android 库 | `FastDownloader` 门面、主线程回调、Kotlin DSL 与 Java Builder |
| `:fast-download:notify` | Android 库，可选 | 前台服务、通知、WorkManager 保活。不依赖时不合并这些权限 |
| `:fast-download:okhttp` | 纯 JVM | 显式注册的 OkHttp 客户端 |
| `:fast-download:ktx` | Android 库 | `await`、进度 Flow |
| `:fast-download:testing` | 纯 JVM，不发布 | MockWebServer 夹具 |
| `:fast-download:sample` | Application，不发布 | 一个按钮、进度条和已保存任务列表 |

对外入口是 `FastDownloader`，任务、分组、查询和配置分开放。回调在主线程。样本应用只声明 `INTERNET`。

另一个 SDK 另开根目录，例如 `album-cleaner/`，不要再把 `fast-download-core` 这类名字铺到仓库根上。

## FastDownload 的非目标

- 不保活，不承诺进程被杀掉之后继续下
- 不做多进程
- 默认任务仍写入应用内部相对路径

## AlbumCleaner

端侧相册清理，没有界面，也不上网。对外入口是 `AlbumCleaner`。宿主自己申请读取权限、自己画界面。

| 模块 | 类型 | 现在做什么 |
| --- | --- | --- |
| `:album-cleaner` | 聚合，不发布 | `:album-cleaner:check` 跑它下面的子模块 |
| `:album-cleaner:core` | Android 库 | `AlbumCleaner` 入口和状态机。扫描流程在 scan，索引在 storage |
| `:album-cleaner:storage` | Android 库 | 相册索引。原生 SQLite，游标翻页，重建时留下回收站 |
| `:album-cleaner:scan` | Android 库 | 元数据、指纹、重复分组和保留选择 |
| `:album-cleaner:sample` | Application，不发布 | 申请读取权限，初始化并显示状态 |

可以扫描相册、查看候选并删除。还不能监听媒体库变化。

## 命令

```bash
./gradlew :fast-download:core:test :fast-download:engine:test :fast-download:api:testDebugUnitTest
./gradlew :fast-download:sample:assembleDebug
./gradlew :album-cleaner:storage:testDebugUnitTest :album-cleaner:core:testDebugUnitTest :album-cleaner:sample:assembleDebug
./gradlew ciCheck
```

核心模块里，FastDownload 的 `core` 会跑 `assertJvmOnly`，编译 classpath 里不能出现 Android 构件。

构建脚本是 Gradle Kotlin DSL。发布约定留在 `build-logic`。除 `testing` 和 `sample` 外，FastDownload 模块都已接上发布插件，当前预览版是 `1.0.0-rc`。AlbumCleaner 的入口是 `album-cleaner`，另外还有 `album-cleaner-storage` 和 `album-cleaner-scan`，当前都是 `0.1.0`。sample 不发布。
