plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.albumcleaner.get()

sdkPublish {
    artifactId.set("album-cleaner-scan")
    displayName.set("AlbumCleaner Scan")
    description.set("读取媒体库元数据，计算指纹，分组并选出保留的照片")
}

android {
    namespace = "com.arturoyi.albumcleaner.scan"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":album-cleaner:storage"))
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
