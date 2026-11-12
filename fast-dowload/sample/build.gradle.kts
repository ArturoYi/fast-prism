plugins {
    alias(libs.plugins.sdk.android.application)
}

android {
    namespace = "com.arturoyi.fastdowload.sample"

    defaultConfig {
        applicationId = "com.arturoyi.fastdowload.sample"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":fast-dowload:engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
