plugins {
    alias(libs.plugins.sdk.android.application)
}

android {
    namespace = "com.arturoyi.fastdowload.sample"

    defaultConfig {
        applicationId = "com.arturoyi.fastdowload.sample"
        versionCode = 1
        versionName = "1.0.0-rc"
    }
}

dependencies {
    implementation(project(":fast-dowload:api"))
    implementation(project(":fast-dowload:ktx"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
