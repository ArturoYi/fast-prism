plugins {
    alias(libs.plugins.sdk.android.library)
}

group = "io.fastdowload"
version = "1.0.0-rc"

android {
    namespace = "com.arturoyi.fastdowload.notify"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:api"))
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(":fast-dowload:testing"))
}
