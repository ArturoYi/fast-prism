plugins {
    alias(libs.plugins.sdk.android.library)
    alias(libs.plugins.sdk.android.library.published)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-cronet")
    displayName.set("FastDowload Cronet")
    description.set("Cronet 客户端，引擎由接入方提供")
}

android {
    namespace = "com.arturoyi.fastdowload.cronet"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":fast-dowload:engine"))
    // 只有接口。原生引擎由接入方提供，不要改成带实现的 cronet 构件。
    api(libs.cronet.api)

    testImplementation(libs.junit)
}
