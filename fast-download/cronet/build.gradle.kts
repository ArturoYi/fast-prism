plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.github.ArturoYi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-cronet")
    displayName.set("FastDownload Cronet")
    description.set("Cronet 客户端，引擎由接入方提供")
}

android {
    namespace = "com.arturoyi.fastdownload.cronet"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-download:engine"))
    // 只有接口。原生引擎由接入方提供，不要改成带实现的 cronet 构件。
    api(libs.cronet.api)

    testImplementation(libs.junit)
}
