plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.ArturoYi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-ktx")
    displayName.set("FastDownload Ktx")
    description.set("协程 await 和进度 Flow")
}

android {
    namespace = "com.arturoyi.fastdownload.ktx"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:api"))
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
