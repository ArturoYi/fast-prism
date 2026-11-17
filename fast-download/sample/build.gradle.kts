plugins {
    alias(libs.plugins.sdk.android.application)
}

android {
    namespace = "com.arturoyi.fastdownload.sample"

    defaultConfig {
        applicationId = "com.arturoyi.fastdownload.sample"
        versionCode = 1
        versionName = "1.0.0-rc"
    }
}

dependencies {
    implementation(project(":fast-download:api"))
    implementation(project(":fast-download:notify"))
    implementation(project(":fast-download:ktx"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
