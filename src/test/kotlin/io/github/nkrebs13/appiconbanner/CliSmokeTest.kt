package io.github.nkrebs13.appiconbanner

import io.github.nkrebs13.appiconbanner.android.StampAndroidIconsTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest

/**
 * Exercises the bundled `app-icon-banner` CLI end to end. Requires a Freetype-enabled ImageMagick
 * on PATH. Excluded from the default `test` task; run via `./gradlew cliSmokeTest` on macOS.
 */
@Tag("cli-smoke")
class CliSmokeTest {

    @Test
    fun `iOS — stamps a banner, is idempotent, and leaves unmatched configs pristine`(@TempDir dir: File) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        val cli = extractCli(dir)
        val appiconset = File(dir, "App/Assets.xcassets/AppIcon.appiconset").apply { mkdirs() }
        val icon = File(appiconset, "icon-256.png")
        run(dir, listOf(magick!!, "-size", "256x256", "gradient:#1a73e8-#34a853", icon.path))
        File(dir, "app-icon-banner.config").writeText("Debug|#0288D1|DEBUG\n")

        val env = mapOf("SRCROOT" to dir.path)
        val args = listOf(cli.path, "--config", "Debug", "--appiconset", appiconset.path)

        assertEquals(0, run(dir, args, env), "first stamp should succeed")
        val firstSha = sha256(icon)
        val baseIcon = File(dir, "App/Assets.xcassets/AppIcon-base.appiconset/icon-256.png")
        assertTrue(baseIcon.exists(), "pristine base set should be created")
        assertNotEquals(sha256(baseIcon), firstSha, "stamped icon must differ from pristine base")

        assertEquals(0, run(dir, args, env), "second stamp should succeed")
        assertEquals(firstSha, sha256(icon), "re-running must be byte-for-byte idempotent")

        val releaseArgs = listOf(cli.path, "--config", "Release", "--appiconset", appiconset.path)
        assertEquals(0, run(dir, releaseArgs, env), "unmatched config should no-op")
        assertEquals(sha256(baseIcon), sha256(icon), "unmatched config must leave icons pristine")
    }

    @Test
    fun `--safe-width-pct constrains text to a sub-region and exits 0`(@TempDir dir: File) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        val cli = extractCli(dir)
        val mipmapDir = File(dir, "mipmap-xxhdpi").apply { mkdirs() }
        val icon = File(mipmapDir, "ic_launcher_round.png")
        run(dir, listOf(magick!!, "-size", "144x144", "gradient:#1a73e8-#34a853", icon.path))

        val args = listOf(
            cli.path,
            "--appiconset", mipmapDir.path,
            "--no-base",
            "--color", "#FF6F00",
            "--label", "INTERNAL",
            "--safe-width-pct", "70",
        )
        assertEquals(0, run(dir, args), "should stamp with --safe-width-pct and exit 0")
        assertTrue(icon.length() > 0, "icon should be non-empty after stamp")
    }

    @Test
    fun `Android --no-base mode stamps icons directly and exits 0`(@TempDir dir: File) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        val cli = extractCli(dir)

        // Simulate a mipmap directory with a legacy launcher icon.
        val mipmapDir = File(dir, "mipmap-xxhdpi").apply { mkdirs() }
        val icon = File(mipmapDir, "ic_launcher.png")
        run(dir, listOf(magick!!, "-size", "144x144", "gradient:#1a73e8-#34a853", icon.path))
        val originalSha = sha256(icon)

        val args = listOf(
            cli.path,
            "--appiconset", mipmapDir.path,
            "--no-base",
            "--color", "#0288D1",
            "--label", "DEBUG",
        )
        assertEquals(0, run(dir, args), "should stamp and exit 0")
        assertNotEquals(originalSha, sha256(icon), "icon should be modified by the stamp")

        // Re-run is idempotent: starting from the same stamped file, result should be same.
        val stampedSha = sha256(icon)
        assertEquals(0, run(dir, args), "second run should also succeed")
        assertEquals(stampedSha, sha256(icon), "repeated --no-base stamps are idempotent via -strip")
    }

    @Test
    fun `StampAndroidIconsTask stamps legacy and adaptive icons, copies monochrome unmodified`(
        @TempDir dir: File,
    ) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        // Build a minimal res/ structure: one density bucket with legacy, foreground, and monochrome.
        val resDir = File(dir, "res").apply { mkdirs() }
        val mipmapDir = File(resDir, "mipmap-xxhdpi").apply { mkdirs() }

        val legacyIcon = File(mipmapDir, "ic_launcher.png")
        val roundIcon = File(mipmapDir, "ic_launcher_round.png")
        val foregroundIcon = File(mipmapDir, "ic_launcher_foreground.png")
        val monochromeIcon = File(mipmapDir, "ic_launcher_monochrome.png")

        // 144×144 standard launcher size for xxhdpi; 432×432 for foreground/monochrome.
        val im = magick!!
        run(dir, listOf(im, "-size", "144x144", "gradient:#1a73e8-#34a853", legacyIcon.path))
        run(dir, listOf(im, "-size", "144x144", "gradient:#1a73e8-#34a853", roundIcon.path))
        run(dir, listOf(im, "-size", "432x432", "gradient:#1a73e8-#34a853", foregroundIcon.path))
        run(dir, listOf(im, "-size", "432x432", "gradient:#888888-#444444", monochromeIcon.path))

        val originalLegacySha = sha256(legacyIcon)
        val originalRoundSha = sha256(roundIcon)
        val originalForegroundSha = sha256(foregroundIcon)
        val originalMonochromeSha = sha256(monochromeIcon)

        // Instantiate the task via ProjectBuilder (supplies the Gradle object model; no plugin needed).
        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        val task = project.tasks.register(
            "stampDebugAndroidIcons",
            StampAndroidIconsTask::class.java,
        ).get()

        val font = StampAndroidIconsTask.FONT_CANDIDATES.firstOrNull { File(it).exists() }
        assumeTrue(font != null, "No system font found")

        val outputDir = File(dir, "build/generated/app-icon-banner/debug/res")
        task.sourceResDir.set(resDir)
        task.bannerColor.set("#0288D1")
        task.bannerLabel.set("DEBUG")
        task.iconName.set("ic_launcher")
        task.variantName.set("debug")
        task.outputDir.set(outputDir)
        task.fontPath.set(font)

        task.stamp()

        val outMipmap = File(outputDir, "mipmap-xxhdpi")
        assertTrue(outMipmap.exists(), "output mipmap directory should be created")

        val outLegacy = File(outMipmap, "ic_launcher.png")
        val outRound = File(outMipmap, "ic_launcher_round.png")
        val outForeground = File(outMipmap, "ic_launcher_foreground.png")
        val outMonochrome = File(outMipmap, "ic_launcher_monochrome.png")

        assertTrue(outLegacy.exists(), "legacy icon should be in output")
        assertNotEquals(originalLegacySha, sha256(outLegacy), "legacy icon should be stamped")

        assertTrue(outRound.exists(), "round icon should be in output")
        assertNotEquals(originalRoundSha, sha256(outRound),
            "round icon should be stamped (new path: bottomInsetPct=10, circle-safe width)")
        assertNotEquals(sha256(outLegacy), sha256(outRound),
            "round icon band position (bottomInsetPct=10) must differ from square (bottomInsetPct=0)")

        assertTrue(outForeground.exists(), "adaptive foreground should be in output")
        assertNotEquals(originalForegroundSha, sha256(outForeground), "foreground should be stamped")

        assertTrue(outMonochrome.exists(), "monochrome should be copied to output")
        assertEquals(originalMonochromeSha, sha256(outMonochrome), "monochrome must NOT be stamped")
    }

    @Test
    fun `StampAndroidIconsTask handles XML vector foreground via layer-list overlay`(
        @TempDir dir: File,
    ) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        // Build a res/ tree with a raster legacy icon and an XML vector foreground
        // (no raster ic_launcher_foreground.png — triggers the XML overlay path).
        val resDir = File(dir, "res").apply { mkdirs() }
        val mipmapDir = File(resDir, "mipmap-xxhdpi").apply { mkdirs() }
        val anydpiDir = File(resDir, "mipmap-anydpi-v26").apply { mkdirs() }
        val drawableDir = File(resDir, "drawable").apply { mkdirs() }

        // Legacy launcher icon (raster).
        val legacyIcon = File(mipmapDir, "ic_launcher.png")
        run(dir, listOf(magick!!, "-size", "144x144", "gradient:#1a73e8-#34a853", legacyIcon.path))

        // XML vector foreground (stub — real content doesn't matter, just needs to exist as XML).
        File(drawableDir, "ic_launcher_foreground.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?><vector xmlns:android="http://schemas.android.com/apk/res/android"/>""",
        )
        // Background drawable (referenced from adaptive-icon XML).
        File(drawableDir, "ic_launcher_background.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?><shape xmlns:android="http://schemas.android.com/apk/res/android"/>""",
        )
        // Adaptive-icon XML referencing the XML vector foreground.
        File(anydpiDir, "ic_launcher.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>""",
        )

        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        val task = project.tasks.register(
            "stampDebugAndroidIcons",
            StampAndroidIconsTask::class.java,
        ).get()

        val font = StampAndroidIconsTask.FONT_CANDIDATES.firstOrNull { File(it).exists() }
        assumeTrue(font != null, "No system font found")

        val outputDir = File(dir, "build/generated/app-icon-banner/debug/res")
        task.sourceResDir.set(resDir)
        task.bannerColor.set("#0288D1")
        task.bannerLabel.set("DEBUG")
        task.iconName.set("ic_launcher")
        task.variantName.set("debug")
        task.outputDir.set(outputDir)
        task.fontPath.set(font)

        task.stamp()

        // Legacy icon should be stamped.
        val outLegacy = File(outputDir, "mipmap-xxhdpi/ic_launcher.png")
        assertTrue(outLegacy.exists(), "legacy icon should be in output")

        // The adaptive overlay must be a full transparent canvas: AdaptiveIconDrawable may
        // normalize its foreground bounds below 108dp, so a strip-only drawable with absolute
        // layer-list insets can collapse and disappear.
        val outBannerPng = File(outputDir, "mipmap-xxhdpi/app_icon_banner_debug.png")
        assertTrue(outBannerPng.exists(), "banner PNG for adaptive overlay should be generated")
        assertTrue(outBannerPng.length() > 0, "banner PNG should be non-empty")
        assertEquals("324x324", identify(dir, magick, outBannerPng, "%wx%h"))
        assertEquals(
            "srgba(0,0,0,0)",
            identify(dir, magick, outBannerPng, "%[pixel:p{0,0}]"),
            "transparent canvas must not hide the original adaptive foreground",
        )
        assertTrue(
            identify(dir, magick, outBannerPng, "%[pixel:p{0,200}]").contains("2,136,209"),
            "banner band must remain opaque at its proportional canvas position",
        )

        // Layer-list XML should be written with the correct references.
        val layerList = File(outputDir, "drawable/ic_launcher_foreground_debug.xml")
        assertTrue(layerList.exists(), "layer-list XML should be written")
        val layerListContent = layerList.readText()
        assertTrue("@drawable/ic_launcher_foreground" in layerListContent, "layer-list must reference original foreground")
        assertTrue("@mipmap/app_icon_banner_debug" in layerListContent, "layer-list must reference banner PNG")
        assertTrue("android:top" !in layerListContent, "overlay position belongs in the proportional canvas")

        // Updated adaptive-icon XML should reference the new layer-list foreground.
        val updatedAdaptive = File(outputDir, "mipmap-anydpi-v26/ic_launcher.xml")
        assertTrue(updatedAdaptive.exists(), "updated adaptive-icon XML should be written")
        val adaptiveContent = updatedAdaptive.readText()
        assertTrue("@drawable/ic_launcher_foreground_debug" in adaptiveContent, "adaptive icon must use layer-list foreground")
        assertTrue("@drawable/ic_launcher_background" in adaptiveContent, "background ref should be preserved")
    }

    private fun extractCli(dir: File): File {
        val cli = File(dir, "app-icon-banner")
        javaClass.getResourceAsStream("/app-icon-banner")!!.use { input ->
            cli.outputStream().use { input.copyTo(it) }
        }
        cli.setExecutable(true)
        return cli
    }

    private fun imageMagickWithFreetype(): String? {
        for (candidate in listOf("magick", "convert")) {
            val tmp = File.createTempFile("imcheck", ".out").apply { deleteOnExit() }
            val ok = ProcessBuilder(candidate, "-version")
                .redirectOutput(tmp)
                .redirectErrorStream(true)
                .runCatching { start().waitFor() }
                .getOrNull()
            if (ok == 0 && tmp.readText().contains("freetype", ignoreCase = true)) return candidate
        }
        return null
    }

    private fun run(dir: File, command: List<String>, env: Map<String, String> = emptyMap()): Int {
        val pb = ProcessBuilder(command).directory(dir).redirectErrorStream(true)
        pb.environment().putAll(env)
        val process = pb.start()
        process.inputStream.bufferedReader().readText()
        return process.waitFor()
    }

    private fun identify(dir: File, magick: String, file: File, format: String): String {
        val process = ProcessBuilder(magick, file.path, "-format", format, "info:")
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "ImageMagick identify should succeed: $output")
        return output.trim()
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `StampAndroidIconsTask stamps a drawable-only adaptive foreground when no density mipmaps exist`(
        @TempDir dir: File,
    ) {
        val magick = imageMagickWithFreetype()
        assumeTrue(magick != null, "Freetype-enabled ImageMagick not available")

        // minSdk >= 26 layout: NO mipmap-{density} dirs at all — only the adaptive XMLs in
        // mipmap-anydpi and a raster foreground in drawable/. Regression: the task used to
        // early-return on "no mipmap-* directories" before reaching the drawable fallback,
        // silently shipping unbanned debug/internal builds.
        val resDir = File(dir, "res").apply { mkdirs() }
        val drawableDir = File(resDir, "drawable").apply { mkdirs() }
        val anydpiDir = File(resDir, "mipmap-anydpi").apply { mkdirs() }
        val foregroundIcon = File(drawableDir, "ic_launcher_foreground.png")
        run(dir, listOf(magick!!, "-size", "432x432", "gradient:#1a73e8-#34a853", foregroundIcon.path))
        File(anydpiDir, "ic_launcher.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
""",
        )
        val originalForegroundSha = sha256(foregroundIcon)

        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        val task = project.tasks.register(
            "stampInternalAndroidIcons",
            StampAndroidIconsTask::class.java,
        ).get()

        val font = StampAndroidIconsTask.FONT_CANDIDATES.firstOrNull { File(it).exists() }
        assumeTrue(font != null, "No system font found")

        val outputDir = File(dir, "build/generated/app-icon-banner/internal/res")
        task.sourceResDir.set(resDir)
        task.bannerColor.set("#FF6F00")
        task.bannerLabel.set("INTERNAL")
        task.iconName.set("ic_launcher")
        task.variantName.set("internal")
        task.outputDir.set(outputDir)
        task.fontPath.set(font)

        task.stamp()

        val outForeground = File(outputDir, "drawable/ic_launcher_foreground.png")
        assertTrue(outForeground.exists(), "drawable foreground should be stamped into generated res")
        assertNotEquals(
            originalForegroundSha,
            sha256(outForeground),
            "foreground must be stamped even with zero density mipmap dirs",
        )
    }
}
