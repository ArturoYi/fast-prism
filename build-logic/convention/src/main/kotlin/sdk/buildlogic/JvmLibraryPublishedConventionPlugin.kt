package sdk.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryPublishedConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("java-library") {
                if (pluginManager.hasPlugin("com.android.library") ||
                    pluginManager.hasPlugin("com.android.application")
                ) {
                    throw GradleException(
                        "$path 是 Android 模块，请使用 sdk.android.library.published",
                    )
                }
                configureJvmLibraryPublishing()
            }
            afterEvaluate {
                if (!pluginManager.hasPlugin("com.vanniktech.maven.publish")) {
                    throw GradleException(
                        "$path 未挂上发布插件。请同时应用 sdk.jvm.library 与 sdk.jvm.library.published",
                    )
                }
            }
        }
    }
}
