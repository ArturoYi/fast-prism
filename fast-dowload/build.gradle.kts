// FastDowload 这个 SDK。实现在子模块里，这里只把验证收成 :fast-dowload:check。
// 对外入口是 :fast-dowload:api。这一阶段还不发布。
plugins {
    base
}

tasks.named("check") {
    dependsOn(
        ":fast-dowload:core:check",
        ":fast-dowload:engine:check",
        ":fast-dowload:testing:check",
        ":fast-dowload:sample:check",
        ":fast-dowload:android:check",
        ":fast-dowload:api:check",
        ":fast-dowload:notify:check",
        ":fast-dowload:okhttp:check",
        ":fast-dowload:ktx:check",
        ":fast-dowload:cronet:check",
        ":fast-dowload:rx:check",
        ":fast-dowload:bom:check",
    )
}
