// FastDowload 这个 SDK。实现在子模块里，这里只把验证收成 :fast-dowload:check。
// 这一阶段没有对外门面，也不发布。
plugins {
    base
}

tasks.named("check") {
    dependsOn(
        ":fast-dowload:core:check",
        ":fast-dowload:engine:check",
        ":fast-dowload:testing:check",
        ":fast-dowload:sample:check",
    )
}
