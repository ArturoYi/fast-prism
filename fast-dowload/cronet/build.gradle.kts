plugins {
    alias(libs.plugins.sdk.android.library)
}

group = "io.fastdowload"
version = "1.0.0-rc"

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
