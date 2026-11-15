plugins {
    alias(libs.plugins.sdk.android.library)
}

group = "io.fastdowload"
version = "1.0.0-rc"

android {
    namespace = "com.arturoyi.fastdowload.api"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:android"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":fast-dowload:testing"))
}
