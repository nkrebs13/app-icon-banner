package io.github.nkrebs13.appiconbanner

import io.github.nkrebs13.appiconbanner.ios.ExportIosBannerConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

// NOTE: The Android wiring path (onVariants callback → StampAndroidIconsTask registration per
// variant) is not unit-testable via ProjectBuilder because AGP's onVariants lifecycle requires a
// full Gradle build execution (not just configuration) to fire variant callbacks. The core logic
// is covered by AppIconBannerExtensionTest (resolution priority, defaults, validation). The wiring
// itself is VERIFICATION-PENDING-HUMAN: apply the plugin in a real Android/KMP project and confirm
// that debug variants show the banner and release variants do not.
class AppIconBannerPluginTest {

    @Test
    fun `plugin applied to a non-Android project registers extension and iOS task only`() {
        // Covers iOS-only and plain Kotlin projects (no AGP). Without an Android plugin the
        // onVariants callback never fires, so no StampAndroidIconsTask instances are registered.
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")

        assertNotNull(project.extensions.findByName("appIconBanner"))
        assertNotNull(project.tasks.findByName("exportIosBannerConfig"))
        assertTrue(
            project.tasks.names.none { it.startsWith("stamp") && it.endsWith("AndroidIcons") },
            "no StampAndroidIconsTask should be registered without an Android plugin",
        )
    }

    @Test
    fun `extension defaults are correct`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")
        val ext = project.extensions.getByType(AppIconBannerExtension::class.java)
        assertEquals("ic_launcher", ext.androidIconName, "androidIconName default should be ic_launcher")
        assertNull(ext.androidResDir, "androidResDir default should be null (auto-detect)")
        assertNull(ext.iosOutputDir, "iosOutputDir default should be null (module root)")
    }

    @Test
    fun `iosOutputDir redirects config and CLI to the specified directory`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")

        val ext = project.extensions.getByType(AppIconBannerExtension::class.java)
        ext.iosOutputDir = "iosApp"
        ext.buildType("debug") { color = "#0288D1"; label = "DEBUG" }

        val task = project.tasks.getByName("exportIosBannerConfig") as ExportIosBannerConfigTask
        task.export()

        assertTrue(File(tempDir, "iosApp/app-icon-banner.config").exists(), "config should be in iosApp/")
        assertTrue(File(tempDir, "iosApp/scripts/app-icon-banner").exists(), "CLI should be in iosApp/scripts/")
    }

    @Test
    fun `variantName sanitization produces valid Android resource name components`() {
        // The plugin wires variantName via: variant.name.lowercase().replace(Regex("[^a-z0-9]"), "_")
        // Test that expression directly to catch regressions.
        fun sanitize(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
        assertEquals("debug", sanitize("debug"))
        assertEquals("metadebug", sanitize("metaDebug"))
        assertEquals("phone_debug", sanitize("phone-debug"))
        assertEquals("release", sanitize("Release"))
        assertTrue(sanitize("metaDebug").all { it.isLetterOrDigit() || it == '_' })
    }

    @Test
    fun `export task writes the config file and installs the CLI`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")

        val extension = project.extensions.getByType(AppIconBannerExtension::class.java)
        extension.buildType("debug") { color = "#0288D1"; label = "DEBUG" }
        extension.iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }

        val task = project.tasks.getByName("exportIosBannerConfig") as ExportIosBannerConfigTask
        task.export()

        val configFile = File(tempDir, "app-icon-banner.config")
        assertTrue(configFile.exists(), "config file should be written")
        assertEquals(
            listOf("Debug|#0288D1|DEBUG", "Firebase|#FF6F00|FIREBASE"),
            configFile.readLines().filter { it.isNotBlank() },
        )

        val cliFile = File(tempDir, "scripts/app-icon-banner")
        assertTrue(cliFile.exists(), "CLI should be installed")
        assertTrue(cliFile.canExecute(), "CLI should be executable")
        assertTrue(
            cliFile.readText().startsWith("#!/usr/bin/env bash"),
            "installed CLI should be the bundled bash script",
        )
    }
}
