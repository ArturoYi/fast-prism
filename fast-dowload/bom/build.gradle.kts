plugins {
    alias(libs.plugins.sdk.bom)
}

group = "io.fastdowload"
version = "1.0.0-rc"

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
            "okhttp",
            "ktx",
            "cronet",
            "rx",
        ).forEach { module ->
            api("io.fastdowload:fast-dowload-$module:$sdkVersion")
        }
    }
}
