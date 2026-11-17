plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-notify")
    displayName.set("FastDownload Notify")
    description.set("通知、dataSync 前台服务，以及 WorkManager 退路")
}

android {
    namespace = "com.arturoyi.fastdownload.notify"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(project(":fast-download:testing"))
}
