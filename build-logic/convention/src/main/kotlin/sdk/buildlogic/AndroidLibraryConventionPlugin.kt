package sdk.buildlogic

import com.android.build.gradle.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply(AndroidLintConventionPlugin::class.java)

            val publicApiRules = layout.buildDirectory.file("generated/sdk-consumer-rules/public-api.pro")
            val namespaceValue = objects.property(String::class.java)
            val generateRules = tasks.register("generateSdkConsumerRules") {
                inputs.property("namespace", namespaceValue)
                outputs.file(publicApiRules)
                doLast {
                    val file = publicApiRules.get().asFile
                    file.parentFile.mkdirs()
                    file.writeText(publicApiConsumerRules(namespaceValue.get()))
                }
            }
            tasks.matching { task ->
                task.name == "preBuild" || task.name.contains("ConsumerProguard")
            }.configureEach {
                dependsOn(generateRules)
            }

            val localRules = file("consumer-rules.pro")
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this, explicitApi = true)
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles(publicApiRules.get().asFile)
                    if (localRules.exists()) {
                        consumerProguardFiles(localRules)
                    }
                }
                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
                resourcePrefix = androidResourcePrefix()
            }

            afterEvaluate {
                val namespace = extensions.getByType(LibraryExtension::class.java).namespace
                if (namespace.isNullOrBlank()) {
                    throw GradleException("$path 必须设置 android.namespace，才能生成公开 API 的 consumer rules")
                }
                namespaceValue.set(namespace)
            }
        }
    }
}

private fun publicApiConsumerRules(namespace: String): String {
    return """
        # 宿主开启 R8 时保留本库公开 API，不改名、不删除。
        -keep class $namespace.** { public *; }
        -keep interface $namespace.** { *; }
        -keepclassmembers class $namespace.** { public <init>(...); }
        -keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
    """.trimIndent() + "\n"
}
