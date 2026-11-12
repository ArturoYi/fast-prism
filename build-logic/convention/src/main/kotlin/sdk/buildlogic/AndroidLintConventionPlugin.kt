package sdk.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") ->
                    extensions.configure<ApplicationExtension> {
                        lint { configureSdkLint() }
                    }

                pluginManager.hasPlugin("com.android.library") ->
                    extensions.configure<LibraryExtension> {
                        lint { configureSdkLint() }
                    }

                else -> error("$path 需要先应用 Android application 或 library 插件，才能配置 lint")
            }
        }
    }
}

private fun Lint.configureSdkLint() {
    abortOnError = true
    checkReleaseBuilds = false
    textReport = true
    textOutput = File("stdout")
    xmlReport = true
    htmlReport = true
    disable += setOf("GradleOverrides")
}
