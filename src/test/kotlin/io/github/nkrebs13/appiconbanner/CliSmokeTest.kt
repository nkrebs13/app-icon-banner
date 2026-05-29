package io.github.nkrebs13.appiconbanner

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
 * Exercises the bundled `app-icon-banner` CLI end to end. Requires a Freetype-enabled ImageMagick on
 * PATH. Excluded from the default `test` task; run via `./gradlew cliSmokeTest` on macOS.
 */
@Tag("cli-smoke")
class CliSmokeTest {

    @Test
    fun `stamps a banner, is idempotent, and leaves unmatched configs pristine`(@TempDir dir: File) {
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

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
