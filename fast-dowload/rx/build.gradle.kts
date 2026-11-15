plugins {
    alias(libs.plugins.sdk.android.library)
}

group = "io.fastdowload"
version = "1.0.0-rc"

android {
    namespace = "com.arturoyi.fastdowload.rx"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:ktx"))
    api(libs.rxjava)
    implementation(libs.kotlinx.coroutines.rx3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
