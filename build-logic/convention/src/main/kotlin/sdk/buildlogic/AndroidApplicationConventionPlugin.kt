package sdk.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply(AndroidLintConventionPlugin::class.java)

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this, explicitApi = false)
                defaultConfig {
                    targetSdk = versionInt("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            afterEvaluate {
                if (pluginManager.hasPlugin("com.vanniktech.maven.publish") ||
                    pluginManager.hasPlugin("sdk.android.library.published")
                ) {
                    throw GradleException("$path 是 application，禁止参与 Maven 发布")
                }
            }
        }
    }
}
