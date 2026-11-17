plugins {
    alias(libs.plugins.sdk.jvm.library)
    alias(libs.plugins.sdk.jvm.library.published)
}

group = "io.github.ArturoYi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-core")
    displayName.set("FastDownload Core")
    description.set("请求、分块、状态、错误码，以及 HTTP 与写出目标契约")
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
