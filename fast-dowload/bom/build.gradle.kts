plugins {
    alias(libs.plugins.sdk.bom)
}

group = "io.fastdowload"
version = libs.versions.fastdowload.get()

sdkPublish {
    artifactId.set("fast-dowload-bom")
    displayName.set("FastDowload BOM")
    description.set("对齐 FastDowload 各模块版本")
}

dependencies {
    val sdkVersion = libs.versions.fastdowload.get()
    constraints {
        listOf(
            "core",
            "engine",
            "android",
            "api",
            "notify",
            "okhttp",
            "ktx",
            "cronet",
            "rx",
        ).forEach { module ->
            api("io.fastdowload:fast-dowload-$module:$sdkVersion")
        }
    }
}
