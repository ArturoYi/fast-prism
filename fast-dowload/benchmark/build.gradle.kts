plugins {
    alias(libs.plugins.sdk.jvm.library)
}

dependencies {
    implementation(project(":fast-dowload:engine"))
    testImplementation(project(":fast-dowload:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
