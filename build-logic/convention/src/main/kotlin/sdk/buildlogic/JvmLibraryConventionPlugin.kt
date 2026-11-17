package sdk.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(
                !pluginManager.hasPlugin("com.android.application") &&
                    !pluginManager.hasPlugin("com.android.library"),
            ) {
                "$path 是纯 JVM 模块，不能再应用 Android 插件"
            }
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            // :fast-dowload:core → fast-dowload-core，避免多个 SDK 的 core 构件重名。
            extensions.configure<BasePluginExtension> {
                archivesName.set(path.removePrefix(":").replace(':', '-'))
            }
            // JDK 17 只用来编译。发布出去的字节码是 11，和 Android 库一致。
            extensions.configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(SDK_JDK))
                }
                sourceCompatibility = SDK_JAVA_VERSION
                targetCompatibility = SDK_JAVA_VERSION
            }
            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(SDK_JDK)
                explicitApi()
                compilerOptions {
                    jvmTarget.set(SDK_JVM_TARGET)
                    freeCompilerArgs.add("-Xjdk-release=${SDK_JAVA_VERSION.majorVersion}")
                    allWarningsAsErrors.set(stringProperty("warningsAsErrors") == "true")
                }
            }
            tasks.withType<JavaCompile>().configureEach {
                options.release.set(SDK_JAVA_VERSION.majorVersion.toInt())
            }
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                }
            }
            registerAndroidFreeCheck()
        }
    }
}

private fun Project.registerAndroidFreeCheck() {
    val compileClasspath = configurations.named("compileClasspath")
    val testCompileClasspath = configurations.named("testCompileClasspath")
    val sourceDir = layout.projectDirectory.dir("src")
    val assertJvmOnly = tasks.register("assertJvmOnly") {
        group = "verification"
        description = "确认编译 classpath 和源码都不引用 Android。"
        inputs.files(compileClasspath)
        inputs.files(testCompileClasspath)
        inputs.dir(sourceDir)
        doLast {
            listOf(compileClasspath.get(), testCompileClasspath.get()).forEach { configuration ->
                val blocked = configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    val id = component.id
                    if (id is ModuleComponentIdentifier && isAndroidGroup(id.group)) id.toString() else null
                }
                check(blocked.isEmpty()) {
                    "$path 的 ${configuration.name} 含有 Android 构件: ${blocked.joinToString()}"
                }
            }
            val importPattern = Regex("""(?m)^\s*import\s+(android|androidx|com\.android)\b""")
            sourceDir.asFile.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    check(!importPattern.containsMatchIn(file.readText())) {
                        "${file.path} 不能 import Android API"
                    }
                }
        }
    }
    tasks.named("check").configure {
        dependsOn(assertJvmOnly)
    }
}

private fun isAndroidGroup(group: String): Boolean {
    return group == "android" ||
        group.startsWith("androidx.") ||
        group.startsWith("com.android.") ||
        group.startsWith("com.google.android.")
}
