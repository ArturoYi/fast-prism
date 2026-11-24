plugins {
    alias(libs.plugins.sdk.jvm.library)
    alias(libs.plugins.sdk.jvm.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-engine")
    displayName.set("FastDownload Engine")
    description.set("下载、命令队列、断点和 OkHttp 客户端")
}

dependencies {
    api(project(":fast-download:core"))
    // OkHttp 类型不出现在公开签名里，调用方不需要编译期依赖它。
    implementation(libs.okhttp)
    testImplementation(project(":fast-download:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
