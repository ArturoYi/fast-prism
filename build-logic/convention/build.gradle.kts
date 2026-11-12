import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "sdk.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.vanniktech.mavenPublish)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "sdk.android.library"
            implementationClass = "sdk.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryPublished") {
            id = "sdk.android.library.published"
            implementationClass = "sdk.buildlogic.AndroidLibraryPublishedConventionPlugin"
        }
        register("androidApplication") {
            id = "sdk.android.application"
            implementationClass = "sdk.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLint") {
            id = "sdk.android.lint"
            implementationClass = "sdk.buildlogic.AndroidLintConventionPlugin"
        }
        register("bom") {
            id = "sdk.bom"
            implementationClass = "sdk.buildlogic.BomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "sdk.jvm.library"
            implementationClass = "sdk.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
