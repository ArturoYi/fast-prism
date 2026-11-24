pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // jcenter/bintray 已关停，不要添加 jcenter() 或 com.jfrog.bintray。
    }
}

rootProject.name = "fast-prism"

include(":fast-download")
include(":fast-download:core")
include(":fast-download:engine")
include(":fast-download:testing")
include(":fast-download:sample")
include(":fast-download:android")
include(":fast-download:api")
include(":fast-download:notify")
include(":fast-download:cronet")
include(":fast-download:rx")
include(":fast-download:bom")

include(":album-cleaner")
include(":album-cleaner:core")
include(":album-cleaner:storage")
include(":album-cleaner:scan")
include(":album-cleaner:sample")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    """
    本工程需要 JDK 17+，当前是 ${JavaVersion.current()}。
    Java Home: ${System.getProperty("java.home")}
    """.trimIndent()
}
