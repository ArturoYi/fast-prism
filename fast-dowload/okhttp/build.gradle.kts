plugins {
    alias(libs.plugins.sdk.jvm.library)
}

group = "io.fastdowload"
version = "1.0.0-rc"

dependencies {
    api(project(":fast-dowload:engine"))
    testImplementation(project(":fast-dowload:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
