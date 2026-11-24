plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-api")
    displayName.set("FastDownload API")
    description.set("FastDownloader 门面、主线程回调、协程 await、进度 Flow、Kotlin DSL 与 Java Builder")
}

android {
    namespace = "com.arturoyi.fastdownload.api"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:android"))
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime)
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":fast-download:testing"))
}
