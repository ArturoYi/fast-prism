plugins {
    alias(libs.plugins.sdk.jvm.library)
    alias(libs.plugins.sdk.jvm.library.published)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-okhttp")
    displayName.set("FastDowload OkHttp")
    description.set("显式注册的 OkHttp 客户端")
}

dependencies {
    api(project(":fast-dowload:engine"))
    testImplementation(project(":fast-dowload:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
