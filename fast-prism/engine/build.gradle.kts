plugins {
    alias(libs.plugins.sdk.jvm.library)
}

group = "io.fastprism"
version = "0.1.0"

dependencies {
    api(project(":fast-prism:core"))
    // OkHttp 类型不出现在公开签名里，调用方不需要编译期依赖它。
    implementation(libs.okhttp)
    testImplementation(project(":fast-prism:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
