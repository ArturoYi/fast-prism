package sdk.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class BomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-platform")
            configureBomPublishing()
        }
    }
}
