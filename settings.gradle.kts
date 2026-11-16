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

include(":fast-dowload")
include(":fast-dowload:core")
include(":fast-dowload:engine")
include(":fast-dowload:testing")
include(":fast-dowload:sample")
include(":fast-dowload:android")
include(":fast-dowload:api")
include(":fast-dowload:notify")
include(":fast-dowload:okhttp")
include(":fast-dowload:ktx")
include(":fast-dowload:cronet")
include(":fast-dowload:rx")
include(":fast-dowload:bom")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    """
    本工程需要 JDK 17+，当前是 ${JavaVersion.current()}。
    Java Home: ${System.getProperty("java.home")}
    """.trimIndent()
}
