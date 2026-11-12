package sdk.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryPublishedConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.library") {
                configureAndroidLibraryPublishing()
            }
            afterEvaluate {
                if (!pluginManager.hasPlugin("com.vanniktech.maven.publish")) {
                    throw GradleException(
                        "$path 未挂上发布插件。请同时应用 sdk.android.library 与 sdk.android.library.published",
                    )
                }
            }
        }
    }
}
