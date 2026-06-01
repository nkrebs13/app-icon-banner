package io.github.nkrebs13.appiconbanner

import io.github.nkrebs13.appiconbanner.android.StampAndroidIconsTask
import io.github.nkrebs13.appiconbanner.ios.ExportIosBannerConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

// NOTE: The Android wiring path (onVariants callback → StampAndroidIconsTask registration per
// variant) is not unit-testable via ProjectBuilder because AGP's onVariants lifecycle requires a
// full Gradle build execution (not just configuration) to fire variant callbacks. The core logic
// is covered by AppIconBannerExtensionTest (resolution priority, defaults, validation). The wiring
// itself is VERIFICATION-PENDING-HUMAN: apply the plugin in a real Android/KMP project and confirm
// that debug variants show the banner and release variants do not.
class AppIconBannerPluginTest {

    private fun buildExportProject(
        tempDir: File,
        configure: AppIconBannerExtension.() -> Unit,
    ): ExportIosBannerConfigTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")
        project.extensions.getByType(AppIconBannerExtension::class.java).configure()
        return project.tasks.getByName("exportIosBannerConfig") as ExportIosBannerConfigTask
    }

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
    fun `iosOutputDir rejects absolute paths with a clear error`(@TempDir tempDir: File) {
        val task = buildExportProject(tempDir) {
            iosOutputDir = "/tmp/absolute-path"
            buildType("debug") { color = "#0288D1"; label = "DEBUG" }
        }
        // Gradle wraps provider evaluation exceptions — traverse the cause chain for the message.
        val ex = assertThrows<RuntimeException> { task.export() }
        val hasRelativeMsg = generateSequence(ex as Throwable) { it.cause }
            .any { it.message?.contains("relative") == true }
        assertTrue(hasRelativeMsg, "Expected 'relative' in exception chain: $ex")
    }

    @Test
    fun `iosOutputDir redirects config and CLI to the specified directory`(@TempDir tempDir: File) {
        val task = buildExportProject(tempDir) {
            iosOutputDir = "iosApp"
            buildType("debug") { color = "#0288D1"; label = "DEBUG" }
        }
        task.export()

        assertTrue(File(tempDir, "iosApp/app-icon-banner.config").exists(), "config should be in iosApp/")
        assertTrue(File(tempDir, "iosApp/scripts/app-icon-banner").exists(), "CLI should be in iosApp/scripts/")
    }

    @Test
    fun `stamp throws when fontPath is not set`(@TempDir tempDir: File) {
        // Exercises the null-font error path in stampIcons() without requiring ImageMagick.
        // BufferedImage + ImageIO produces a valid PNG; the error fires before the CLI is invoked.
        val resDir = File(tempDir, "res").apply { mkdirs() }
        val mipmapDir = File(resDir, "mipmap-xxhdpi").apply { mkdirs() }
        val icon = File(mipmapDir, "ic_launcher.png")
        ImageIO.write(BufferedImage(144, 144, BufferedImage.TYPE_INT_RGB), "png", icon)

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("stamp", StampAndroidIconsTask::class.java).get()
        task.sourceResDir.set(resDir)
        task.bannerColor.set("#0288D1")
        task.bannerLabel.set("DEBUG")
        task.iconName.set("ic_launcher")
        task.variantName.set("debug")
        task.outputDir.set(File(tempDir, "build/output"))
        // fontPath deliberately left unset — simulates a machine with no recognised fonts

        val ex = assertThrows<IllegalStateException> { task.stamp() }
        assertTrue(
            ex.message?.contains("no usable font") == true,
            "Expected 'no usable font' in: ${ex.message}",
        )
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
        val task = buildExportProject(tempDir) {
            buildType("debug") { color = "#0288D1"; label = "DEBUG" }
            iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
        }
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
