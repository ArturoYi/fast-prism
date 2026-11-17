plugins {
    alias(libs.plugins.sdk.jvm.library)
    alias(libs.plugins.sdk.jvm.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-okhttp")
    displayName.set("FastDownload OkHttp")
    description.set("显式注册的 OkHttp 客户端")
}

dependencies {
    api(project(":fast-download:engine"))
    testImplementation(project(":fast-download:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
