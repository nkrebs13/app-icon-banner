package io.github.nkrebs13.appiconbanner

import io.github.nkrebs13.appiconbanner.ios.ExportIosBannerConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AppIconBannerPluginTest {

    @Test
    fun `applies extension and registers the iOS export task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.nkrebs13.app-icon-banner")

        assertNotNull(project.extensions.findByName("appIconBanner"))
        assertNotNull(project.tasks.findByName("exportIosBannerConfig"))
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
