plugins {
    alias(libs.plugins.sdk.jvm.library)
}

dependencies {
    testImplementation(project(":fast-dowload:engine"))
    testImplementation(project(":fast-dowload:okhttp"))
    testImplementation(project(":fast-dowload:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
