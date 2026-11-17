package sdk.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** 编译器用 JDK 17。打进 AAR / JAR 的字节码是 11，宿主不必跟着升到 17。 */
internal const val SDK_JDK = 17

internal val SDK_JAVA_VERSION: JavaVersion = JavaVersion.VERSION_11

internal val SDK_JVM_TARGET: JvmTarget = JvmTarget.JVM_11

internal fun Project.versionInt(name: String): Int {
    val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val version = catalog.findVersion(name)
    check(version.isPresent) { "gradle/libs.versions.toml 缺少 [versions].$name" }
    return version.get().requiredVersion.toInt()
}

internal fun Project.stringProperty(name: String): String? {
    return findProperty(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun Project.androidResourcePrefix(): String {
    return path.split(Regex("\\W"))
        .drop(1)
        .distinct()
        .joinToString(separator = "_")
        .lowercase() + "_"
}

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
    explicitApi: Boolean,
) {
    commonExtension.apply {
        compileSdk = versionInt("compileSdk")
        defaultConfig.minSdk = versionInt("minSdk")
        compileOptions {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = SDK_JAVA_VERSION
            targetCompatibility = SDK_JAVA_VERSION
        }
        buildFeatures {
            buildConfig = false
        }
        testOptions.animationsDisabled = true
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(SDK_JDK)
        if (explicitApi) {
            explicitApi()
        }
        compilerOptions {
            jvmTarget.set(SDK_JVM_TARGET)
            allWarningsAsErrors.set(stringProperty("warningsAsErrors") == "true")
        }
    }

    val desugar = extensions.getByType<VersionCatalogsExtension>()
        .named("libs")
        .findLibrary("desugar-jdk-libs")
    check(desugar.isPresent) { "gradle/libs.versions.toml 缺少 desugar-jdk-libs" }
    dependencies.addProvider("coreLibraryDesugaring", desugar.get())
}
