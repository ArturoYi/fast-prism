plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.ksp)
}

group = "io.fastdowload"
version = "1.0.0-rc"

android {
    namespace = "com.arturoyi.fastdowload.android"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:engine"))
    implementation(libs.room.runtime)
    implementation(libs.androidx.work.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(":fast-dowload:testing"))
}
