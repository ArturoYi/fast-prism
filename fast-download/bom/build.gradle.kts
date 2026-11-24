plugins {
    alias(libs.plugins.sdk.bom)
}

group = "io.github.arturoyi"
version = libs.versions.fastdownload.get()

sdkPublish {
    artifactId.set("fast-download-bom")
    displayName.set("FastDownload BOM")
    description.set("对齐 FastDownload 各模块版本")
}

dependencies {
    val sdkVersion = libs.versions.fastdownload.get()
    constraints {
        listOf(
            "core",
            "engine",
            "android",
            "api",
            "notify",
            "cronet",
            "rx",
        ).forEach { module ->
            api("io.github.arturoyi:fast-download-$module:$sdkVersion")
        }
    }
}
