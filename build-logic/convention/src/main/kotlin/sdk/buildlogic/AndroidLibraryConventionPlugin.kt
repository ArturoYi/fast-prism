package sdk.buildlogic

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply(AndroidLintConventionPlugin::class.java)

            val consumerRules = file("consumer-rules.pro")
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this, explicitApi = true)
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    if (consumerRules.exists()) {
                        consumerProguardFiles(consumerRules.name)
                    }
                }
                resourcePrefix = androidResourcePrefix()
            }
        }
    }
}
