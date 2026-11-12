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

include(":fast-prism")
include(":fast-prism:core")
include(":fast-prism:engine")
include(":fast-prism:testing")
include(":fast-prism:sample")

// FastPrism 后续子模块，当前不启用。
// include(":fast-prism:android")
// include(":fast-prism:okhttp")
// include(":fast-prism:ktx")
// include(":fast-prism:rx")
// include(":fast-prism:notification")
// include(":fast-prism:migrator")
// include(":fast-prism:benchmark")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    """
    本工程需要 JDK 17+，当前是 ${JavaVersion.current()}。
    Java Home: ${System.getProperty("java.home")}
    """.trimIndent()
}
