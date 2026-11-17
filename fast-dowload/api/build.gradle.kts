plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-api")
    displayName.set("FastDowload API")
    description.set("FastDownloader 门面、主线程回调、Kotlin DSL 与 Java Builder")
}

android {
    namespace = "com.arturoyi.fastdowload.api"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:android"))
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":fast-dowload:testing"))
}
