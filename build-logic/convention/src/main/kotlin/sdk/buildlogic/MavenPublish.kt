package sdk.buildlogic

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.Platform
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * jcenter/bintray 已关停，本仓库不配置 com.jfrog.bintray。
 *
 * 默认主机是 [SonatypeHost.CENTRAL_PORTAL]。Release 由插件上传到 Central Portal，
 * 发布完成后从 [MAVEN_CENTRAL_REPOSITORY] 解析。
 * com.vanniktech.maven.publish 0.28.0 不能把 SNAPSHOT 发到 Central Portal。
 *
 * [SonatypeHost.DEFAULT] 与 [SonatypeHost.S01] 仍指向已关闭的 OSSRH。
 * SNAPSHOT 分别上传到 [OSSRH_SNAPSHOT_REPOSITORY]、[S01_SNAPSHOT_REPOSITORY]。
 * Release 不能直接写 releases 仓库，close & release 之后才会进入
 * [OSSRH_RELEASE_REPOSITORY]、[S01_RELEASE_REPOSITORY]。
 */
internal const val CENTRAL_PORTAL_URL = "https://central.sonatype.com"

internal const val MAVEN_CENTRAL_REPOSITORY = "https://repo1.maven.org/maven2/"

internal const val OSSRH_SNAPSHOT_REPOSITORY =
    "https://oss.sonatype.org/content/repositories/snapshots/"

internal const val OSSRH_RELEASE_REPOSITORY =
    "https://oss.sonatype.org/content/repositories/releases/"

internal const val S01_SNAPSHOT_REPOSITORY =
    "https://s01.oss.sonatype.org/content/repositories/snapshots/"

internal const val S01_RELEASE_REPOSITORY =
    "https://s01.oss.sonatype.org/content/repositories/releases/"

private val REQUIRED_POM_PROPERTIES = listOf(
    "POM_URL",
    "POM_SCM_URL",
    "POM_SCM_CONNECTION",
    "POM_SCM_DEV_CONNECTION",
    "POM_LICENCE_NAME",
    "POM_LICENCE_URL",
    "POM_LICENCE_DIST",
    "POM_DEVELOPER_ID",
    "POM_DEVELOPER_NAME",
    "POM_INCEPTION_YEAR",
)

internal fun Project.configureAndroidLibraryPublishing() {
    configureMavenPublishing(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            // AGP 8.7 的 Dokka 读不了 Kotlin 2.2 sealed class 的 PermittedSubclasses。
            publishJavadocJar = false,
        ),
    )
}

internal fun Project.configureJvmLibraryPublishing() {
    configureMavenPublishing(
        JavaLibrary(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = true,
        ),
    )
}

internal fun Project.configureBomPublishing() {
    configureMavenPublishing(JavaPlatform())
}

internal fun Project.configureMavenPublishing(platform: Platform) {
    // jcenter/bintray 已关停，禁止应用 com.jfrog.bintray。
    pluginManager.withPlugin("com.jfrog.bintray") {
        throw GradleException("jcenter/bintray 已关停，禁止应用 com.jfrog.bintray")
    }

    pluginManager.apply("com.vanniktech.maven.publish")
    if (platform is AndroidSingleVariantLibrary) {
        // 必须在 signAllPublications 之前挂上，签名才会包含这个空 jar。
        val emptyJavadocJar = tasks.register<Jar>("emptyJavadocJar") {
            archiveClassifier.set("javadoc")
        }
        extensions.configure<PublishingExtension>("publishing") {
            publications.withType<MavenPublication>().configureEach {
                artifact(emptyJavadocJar)
            }
        }
    }
    val publishExtension = extensions.create("sdkPublish", SdkPublishExtension::class.java)
    val automaticRelease = stringProperty("sdk.automaticRelease") == "true"

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        configure(platform)
        publishToMavenCentral(resolveSonatypeHost(), automaticRelease)
        signAllPublications()
    }

    val publishingProject = this
    gradle.taskGraph.whenReady {
        if (!publishingProject.unscopedRemotePublishRequested()) return@whenReady
        val selected = allTasks.any { task ->
            task.project == publishingProject && isRemotePublishTask(task.name)
        }
        if (selected) {
            throw GradleException(
                "禁止不带模块路径的远程发布，那会把所有模块一起发出去。请指定模块，例如 ./gradlew :example:publish",
            )
        }
    }

    afterEvaluate {
        finalizeSdkCoordinates(publishExtension)
    }
}

private fun Project.resolveSonatypeHost(): SonatypeHost {
    return when (stringProperty("sdk.sonatypeHost") ?: "CENTRAL_PORTAL") {
        "CENTRAL_PORTAL" -> SonatypeHost.CENTRAL_PORTAL
        "DEFAULT" -> SonatypeHost.DEFAULT
        "S01" -> SonatypeHost.S01
        else -> throw GradleException(
            "sdk.sonatypeHost 只支持 CENTRAL_PORTAL（central.sonatype.com）、DEFAULT（oss.sonatype.org）或 S01（s01.oss.sonatype.org）",
        )
    }
}

private data class PublishRepositories(
    val snapshotRepository: String,
    val releaseRepository: String,
)

private fun SonatypeHost.publishRepositories(): PublishRepositories {
    return when (this) {
        SonatypeHost.CENTRAL_PORTAL -> PublishRepositories(
            snapshotRepository = "unsupported",
            releaseRepository = MAVEN_CENTRAL_REPOSITORY,
        )
        SonatypeHost.S01 -> PublishRepositories(
            snapshotRepository = S01_SNAPSHOT_REPOSITORY,
            releaseRepository = S01_RELEASE_REPOSITORY,
        )
        SonatypeHost.DEFAULT -> PublishRepositories(
            snapshotRepository = OSSRH_SNAPSHOT_REPOSITORY,
            releaseRepository = OSSRH_RELEASE_REPOSITORY,
        )
        else -> throw GradleException("无法识别的 Sonatype 主机: $this")
    }
}

private fun Project.finalizeSdkCoordinates(publishExtension: SdkPublishExtension) {
    val versionMode = stringProperty("sdk.versionMode") ?: "independent"
    if (versionMode == "unified") {
        version = stringProperty("sdk.version")
            ?: throw GradleException("sdk.versionMode=unified 时必须在 gradle.properties 设置 sdk.version")
    } else if (versionMode != "independent") {
        throw GradleException("sdk.versionMode 只支持 independent 或 unified，当前是 $versionMode")
    }

    val declared = version.toString()
    if (declared.isBlank() || declared == "unspecified") {
        throw GradleException(
            "$path 没有 version。独立模式请在模块里写 version = \"x.y.z\"；统一模式请设置 sdk.versionMode=unified",
        )
    }

    val baseVersion = declared.removeSuffix("-SNAPSHOT")
    val snapshot = stringProperty("sdk.snapshot") == "true"
    version = if (snapshot) "$baseVersion-SNAPSHOT" else baseVersion

    val resolvedGroup = resolvePublishGroup()
    group = resolvedGroup
    val artifactId = publishExtension.artifactId.orNull?.takeIf { it.isNotBlank() } ?: name
    val pomDescription = publishExtension.description.orNull.orEmpty()
    if (pomDescription.isBlank()) {
        throw GradleException("$path 缺少 sdkPublish.description，Maven Central 要求 POM description")
    }

    val missingPom = REQUIRED_POM_PROPERTIES.filter { stringProperty(it) == null }
    if (missingPom.isNotEmpty()) {
        throw GradleException("gradle.properties 缺少 POM 字段：${missingPom.joinToString()}")
    }

    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
        coordinates(resolvedGroup, artifactId, version.toString())
        pom {
            name.set(publishExtension.displayName.orElse(artifactId))
            description.set(pomDescription)
        }
    }

    val publishedVersion = version.toString()
    tasks.withType<PublishToMavenRepository>().configureEach {
        doFirst {
            val url = repository.url.toString()
            if (url.contains("bintray.com") || url.contains("jcenter.bintray")) {
                throw GradleException("jcenter/bintray 已关停，拒绝发布到 $url")
            }
            logger.lifecycle(
                "发布 ${publication.groupId}:${publication.artifactId}:${publication.version} -> $url",
            )
        }
    }

    val coordinatesLine = "$resolvedGroup:$artifactId:$publishedVersion"
    val automaticRelease = stringProperty("sdk.automaticRelease") == "true"
    val sonatypeHost = resolveSonatypeHost()
    val repositories = sonatypeHost.publishRepositories()
    tasks.register("printSdkCoordinates") {
        group = "publishing"
        description = "打印本模块的 GAV 和 Sonatype 仓库，不上传。"
        doLast {
            println("sdk.coordinates=$coordinatesLine")
            println("sdk.sonatypeHost=${sonatypeHostName(sonatypeHost)}")
            println("sdk.portal=${portalUrl(sonatypeHost)}")
            println("sdk.snapshotRepository=${repositories.snapshotRepository}")
            println("sdk.releaseRepository=${repositories.releaseRepository}")
            println("sdk.snapshot=$snapshot")
            println("sdk.automaticRelease=$automaticRelease")
        }
    }
}

private fun sonatypeHostName(host: SonatypeHost): String {
    return when (host) {
        SonatypeHost.CENTRAL_PORTAL -> "CENTRAL_PORTAL"
        SonatypeHost.S01 -> "S01"
        SonatypeHost.DEFAULT -> "DEFAULT"
        else -> host.toString()
    }
}

private fun portalUrl(host: SonatypeHost): String {
    return when (host) {
        SonatypeHost.CENTRAL_PORTAL -> CENTRAL_PORTAL_URL
        SonatypeHost.S01 -> "https://s01.oss.sonatype.org"
        SonatypeHost.DEFAULT -> "https://oss.sonatype.org"
        else -> host.toString()
    }
}

private fun Project.resolvePublishGroup(): String {
    val current = group.toString()
    if (current.isNotBlank() && current != rootProject.name && current != "unspecified") {
        return current
    }
    return stringProperty("sdk.group")
        ?: throw GradleException("$path 没有 group，也没有 gradle.properties 里的 sdk.group")
}

private fun Project.unscopedRemotePublishRequested(): Boolean {
    return gradle.startParameter.taskNames.any { requested ->
        if (requested.contains(':')) return@any false
        val leaf = requested.substringAfterLast(':')
        leaf == "publish" || leaf.contains("MavenCentral")
    }
}

private fun isRemotePublishTask(name: String): Boolean {
    return name == "publish" || name.contains("MavenCentral")
}
