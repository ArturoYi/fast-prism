plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
    alias(libs.plugins.ksp)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-android")
    displayName.set("FastDownload Android")
    description.set("Room 存储、网络状态和内容 Uri 写入")
}

android {
    namespace = "com.arturoyi.fastdownload.android"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:engine"))
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(":fast-download:testing"))
}
