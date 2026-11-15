# Changelog

## Unreleased

- FastDowload 1.0.0-rc：12 个可替换接口都有真实实现和替身实现，换实现不改 `core`。
- 可选模块：OkHttp、Cronet、RxJava 3。接入方用 `fast-dowload-bom` 对齐版本。
- 兼容性结论写在文档站：minSdk 26、targetSdk 34、R8 不靠类名反射、只写应用内部目录或调用方给出的 Uri、多进程不是目标。

- FastDowload 0.9.0-rc：失败按指数退避重试，默认最多 3 次；没有网络时进入等待网络，不消耗次数。
- 全局和单任务令牌桶限速。`setGlobalSpeedLimit` 会作用到正在进行的下载。
- 完成前按 SHA-256、MD5 或 `Content-MD5` 校验。不一致时不把临时文件改成最终文件，错误码是 `INVALID_CONTENT_HASH`。
- 后台保活可选进程内、前台服务或 WorkManager。三者都不保证进程被杀掉后自动续跑，下次启动靠断点恢复。
- 诊断日志带阶段名和错误码。`dumpState()` 不输出完整地址。
- 基准门禁：2MiB 在 1/2/4/8 分片下，偏移写回次数相对分片数偏差不超过 10%。100MiB 用 `-Dbenchmark.profile=full` 另跑。1GiB 样本不放进每次提交，避免在内存里放下整份正文。

- FastDowload 0.1.0 对外门面：`FastDownloader.tasks` / `groups` / `query`，主线程回调，Kotlin DSL 与 Java Builder。
- 重复入队按 `EnqueueAction` 处理。`INCREMENT_FILE_NAME` 会换成 `名字 (1).扩展名`，不覆盖已有文件。
- 未注册 HTTP 客户端时使用 `HttpURLConnection`，并写一条警告。OkHttp 要显式传入 `OkHttpHttpClient`。
- `public_id`、`ErrorCode.code`、`Status.value` 从这一版起不再改值、也不把旧值挪给别的常量。

- 建立 FastDowload 最小闭环：纯 JVM 的 core / engine、MockWebServer 夹具，以及只写入应用内部目录的 sample。
- FastDowload 作为单个 SDK 放在 `fast-dowload/`，core、engine、testing、sample 是它的子模块。
- 不提供暂停或停止，不申请存储、通知和前台服务权限。

## 0.1.0 - 2026-11-09

- 初始化 Monorepo：`build-logic` convention、可发布模块 `:example`、不发布的 `:sample`。
- 发布走 `com.vanniktech.maven.publish` 0.28.0，模块使用独立版本。
