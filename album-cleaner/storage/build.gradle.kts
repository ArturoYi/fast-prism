plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
    alias(libs.plugins.ksp)
}

group = "io.github.arturoyi"
version = libs.versions.albumcleaner.get()

sdkPublish {
    artifactId.set("album-cleaner-storage")
    displayName.set("AlbumCleaner Storage")
    description.set("相册索引。Room 数据库")
}

android {
    namespace = "com.arturoyi.albumcleaner.storage"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
