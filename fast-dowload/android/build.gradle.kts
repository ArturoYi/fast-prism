plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
    alias(libs.plugins.ksp)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-android")
    displayName.set("FastDowload Android")
    description.set("Room 存储、网络状态和内容 Uri 写入")
}

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
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(":fast-dowload:testing"))
}
