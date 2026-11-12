plugins {
    alias(libs.plugins.sdk.jvm.library)
}

dependencies {
    api(libs.junit.jupiter.api)
    api(libs.okhttp.mockwebserver)
}
