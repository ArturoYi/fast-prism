plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.ArturoYi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-rx")
    displayName.set("FastDownload Rx")
    description.set("用 RxJava 3 订阅下载")
}

android {
    namespace = "com.arturoyi.fastdownload.rx"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:ktx"))
    api(libs.rxjava)
    implementation(libs.kotlinx.coroutines.rx3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
