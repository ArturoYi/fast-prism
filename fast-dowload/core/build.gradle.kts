plugins {
    alias(libs.plugins.sdk.jvm.library)
}

// M0 不发布。坐标先定稿，接上 published convention 后再发。
group = "io.fastdowload"
version = "0.1.0"

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
