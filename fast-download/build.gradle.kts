// FastDownload 这个 SDK。实现在子模块里，这里只把验证收成 :fast-download:check。
// 对外入口是 :fast-download:api。这一阶段还不发布。
plugins {
    base
}

tasks.named("check") {
    dependsOn(
        ":fast-download:core:check",
        ":fast-download:engine:check",
        ":fast-download:testing:check",
        ":fast-download:sample:check",
        ":fast-download:android:check",
        ":fast-download:api:check",
        ":fast-download:notify:check",
        ":fast-download:okhttp:check",
        ":fast-download:ktx:check",
        ":fast-download:cronet:check",
        ":fast-download:rx:check",
        ":fast-download:bom:check",
    )
}
