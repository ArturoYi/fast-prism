plugins {
    alias(libs.plugins.sdk.jvm.library)
    alias(libs.plugins.sdk.jvm.library.published)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-engine")
    displayName.set("FastDowload Engine")
    description.set("下载、命令队列和断点")
}

dependencies {
    api(project(":fast-dowload:core"))
    // OkHttp 类型不出现在公开签名里，调用方不需要编译期依赖它。
    implementation(libs.okhttp)
    testImplementation(project(":fast-dowload:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
