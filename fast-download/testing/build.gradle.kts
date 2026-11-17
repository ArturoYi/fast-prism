plugins {
    alias(libs.plugins.sdk.jvm.library)
}

dependencies {
    api(project(":fast-download:core"))
    api(libs.junit.jupiter.api)
    api(libs.okhttp.mockwebserver)
}
