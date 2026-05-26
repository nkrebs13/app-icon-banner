rootProject.name = "app-icon-banner"

plugins {
    // Auto-provisions the Java 17 toolchain on any machine (CI, contributors) so the plugin can
    // target Java 17 bytecode regardless of the JDK that launches Gradle.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
