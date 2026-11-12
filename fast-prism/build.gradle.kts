// FastPrism 这个 SDK。实现在子模块里，这里只把验证收成 :fast-prism:check。
// 这一阶段没有对外门面，也不发布。
plugins {
    base
}

tasks.named("check") {
    dependsOn(
        ":fast-prism:core:check",
        ":fast-prism:engine:check",
        ":fast-prism:testing:check",
        ":fast-prism:sample:check",
    )
}
