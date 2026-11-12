package sdk.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

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
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        buildFeatures {
            buildConfig = false
        }
        testOptions.animationsDisabled = true
    }

    // 与 JVM 模块相同：Kotlin 编译固定用 JDK 17，不跟着运行 Gradle 的更高版本走。
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(17)
        if (explicitApi) {
            explicitApi()
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(stringProperty("warningsAsErrors") == "true")
        }
    }
}
