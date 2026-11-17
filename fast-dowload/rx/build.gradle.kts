plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-rx")
    displayName.set("FastDowload Rx")
    description.set("用 RxJava 3 订阅下载")
}

android {
    namespace = "com.arturoyi.fastdowload.rx"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:ktx"))
    api(libs.rxjava)
    implementation(libs.kotlinx.coroutines.rx3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
