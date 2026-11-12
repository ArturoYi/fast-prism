plugins {
    alias(libs.plugins.sdk.android.application)
}

android {
    namespace = "com.arturoyi.fastprism.sample"

    defaultConfig {
        applicationId = "com.arturoyi.fastprism.sample"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":fast-prism:engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
