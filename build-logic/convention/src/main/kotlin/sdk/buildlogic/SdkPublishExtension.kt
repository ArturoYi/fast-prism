package sdk.buildlogic

import org.gradle.api.provider.Property

abstract class SdkPublishExtension {
    abstract val artifactId: Property<String>
    abstract val displayName: Property<String>
    abstract val description: Property<String>
}
