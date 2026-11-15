plugins {
    alias(libs.plugins.sdk.jvm.library)
}

dependencies {
    api(project(":fast-dowload:core"))
    api(libs.junit.jupiter.api)
    api(libs.okhttp.mockwebserver)
}
