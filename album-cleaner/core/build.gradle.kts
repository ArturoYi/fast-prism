plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.albumcleaner.get()

sdkPublish {
    artifactId.set("album-cleaner")
    displayName.set("AlbumCleaner")
    description.set("端侧相册清理。宿主自绘界面，SDK 不联网")
}

android {
    namespace = "com.arturoyi.albumcleaner"

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":album-cleaner:storage"))
    implementation(project(":album-cleaner:scan"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
}
