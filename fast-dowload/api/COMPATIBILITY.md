# FastDowload 1.0.0-rc 兼容性

这一版在 0.9.0-rc 上只增加类型和方法。下面三项仍然不改值，也不把旧值挪给新的常量：

- 任务标识 `public_id`
- `ErrorCode.code`
- `Status.value`

已经发布的 `HttpClient`、`OutputTarget`、`DownloadRepository`、`RateLimiter` 和 `DownloadMonitor` 方法签名不变。新的可替换接口是新增类型。

完整的系统兼容性结论在文档站的「兼容性」一页：minSdk 26、targetSdk 34、R8、分区存储，以及多进程不做。
