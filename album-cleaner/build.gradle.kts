// AlbumCleaner 这个 SDK。实现在子模块里，这里只把验证收成 :album-cleaner:check。
// 对外入口是 :album-cleaner:core。
plugins {
    base
}

tasks.named("check") {
    dependsOn(
        ":album-cleaner:core:check",
        ":album-cleaner:storage:check",
        ":album-cleaner:scan:check",
        ":album-cleaner:sample:check",
    )
}
