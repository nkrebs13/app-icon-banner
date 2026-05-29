package io.github.nkrebs13.appiconbanner.android

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

private val STAMPABLE_EXTENSIONS = setOf("png", "webp")

/**
 * Stamps the color+label banner onto all Android launcher icons for one variant, writing
 * stamped copies into a generated resource directory that AGP merges with highest priority.
 *
 * Icon types processed per density bucket (`mipmap-{mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi}/`):
 *
 * - `ic_launcher.{png,webp}` — legacy launcher icon (flat geometry, no inset needed).
 * - `ic_launcher_round.{png,webp}` — round launcher icon (same flat geometry).
 * - `ic_launcher_foreground.{png,webp}` — adaptive icon foreground; stamped inside the
 *   72/108 dp safe zone (≈20% bottom inset) so the banner survives all launcher mask shapes
 *   (circle, squircle, teardrop, etc.).
 *
 * Intentionally skipped:
 * - `ic_launcher_monochrome.{png,webp}` — Material You themed icon: the launcher applies a
 *   solid wallpaper-derived tint over the entire layer at display time. A color+label banner
 *   would be invisible or visually broken under that tint.
 * - `mipmap-anydpi*` directories — contain only XML adaptive-icon wrapper files, never raster.
 * - `*.xml` files — adaptive icon definitions and vector drawables.
 * - Background drawables (`ic_launcher_background.*`) — never shown directly to users.
 *
 * Config-cache safe: holds no [org.gradle.api.Project] reference. The CLI is extracted from
 * the plugin jar on every task execution.
 */
abstract class StampAndroidIconsTask : DefaultTask() {

    /** The Android `res/` directory to read source icons from (e.g. `src/androidMain/res`). */
    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceResDir: DirectoryProperty

    /** Ribbon background color — must be `#RRGGBB`. */
    @get:Input
    abstract val bannerColor: Property<String>

    /** Text drawn on the ribbon. Must not contain `|` or `%`. */
    @get:Input
    abstract val bannerLabel: Property<String>

    /**
     * Base name of the launcher icon files (default `ic_launcher`). Foreground and round
     * variants are derived as `<name>_foreground` and `<name>_round` respectively.
     */
    @get:Input
    abstract val iconName: Property<String>

    /** Generated resource directory — AGP merges its contents with highest priority. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stamp() {
        val source = sourceResDir.get().asFile
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        // Work area alongside the output directory — outside res/ so AGP won't process it.
        val workRoot = File(output.parentFile, "stamp-work").also {
            it.deleteRecursively(); it.mkdirs()
        }

        val cli = extractCli(output.parentFile)
        val color = bannerColor.get()
        val label = bannerLabel.get()
        val base = iconName.get()   // e.g. "ic_launcher"

        // Geometry tiers:
        //   Legacy (flat): ic_launcher + ic_launcher_round — no inset, plain rectangular icon.
        //   Adaptive foreground: ic_launcher_foreground — must stay inside the 72/108 dp safe
        //     zone. The adaptive canvas is 108 dp; safe zone is 72 dp centered, leaving 18 dp
        //     (≈ 16.7%) on each side masked. We use 20% to clear with margin.
        //   Monochrome (ic_launcher_monochrome): copied without stamping — the launcher applies
        //     a solid wallpaper-derived tint, making any color+label banner invisible.
        val legacyNames = setOf(base, "${base}_round")
        val adaptiveNames = setOf("${base}_foreground")
        val monochromeNames = setOf("${base}_monochrome")

        var totalStamped = 0

        source.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("mipmap-") && !it.name.contains("anydpi") }
            ?.forEach { mipmapDir ->
                val outMipmapDir = File(output, mipmapDir.name).apply { mkdirs() }

                val legacyFiles = findIcons(mipmapDir, legacyNames)
                if (legacyFiles.isNotEmpty()) {
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "${mipmapDir.name}-legacy").also { it.mkdirs() },
                        sources = legacyFiles,
                        outputDir = outMipmapDir,
                        color = color, label = label,
                        heightPct = 18, bottomInsetPct = 0, textPct = 55,
                    )
                }

                val adaptiveFiles = findIcons(mipmapDir, adaptiveNames)
                if (adaptiveFiles.isNotEmpty()) {
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "${mipmapDir.name}-adaptive").also { it.mkdirs() },
                        sources = adaptiveFiles,
                        outputDir = outMipmapDir,
                        color = color, label = label,
                        heightPct = 22, bottomInsetPct = 20, textPct = 55,
                    )
                }

                // Copy monochrome without stamping.
                findIcons(mipmapDir, monochromeNames)
                    .forEach { it.copyTo(File(outMipmapDir, it.name), overwrite = true) }
            }

        workRoot.deleteRecursively()

        logger.lifecycle(
            "app-icon-banner: stamped ${totalStamped} Android icon(s) for '${label}' (${color})",
        )
    }

    /**
     * Copies [sources] into [workDir], runs the CLI on that directory with the given geometry,
     * then writes the stamped results to [outputDir].
     *
     * A per-tier work directory isolates icons by geometry so the CLI stamps only the target
     * files (e.g. foreground-only or legacy-only) without affecting other icons in the same
     * output directory.
     */
    private fun stampIcons(
        cli: File,
        workDir: File,
        sources: List<File>,
        outputDir: File,
        color: String,
        label: String,
        heightPct: Int,
        bottomInsetPct: Int,
        textPct: Int,
    ): Int {
        sources.forEach { it.copyTo(File(workDir, it.name), overwrite = true) }

        val process = ProcessBuilder(
            cli.absolutePath,
            "--appiconset", workDir.absolutePath,
            "--no-base",
            "--color", color,
            "--label", label,
            "--height-pct", heightPct.toString(),
            "--bottom-inset-pct", bottomInsetPct.toString(),
            "--text-pct", textPct.toString(),
        ).redirectErrorStream(true).start()

        val cliOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error("app-icon-banner CLI failed (exit $exitCode):\n$cliOutput")
        }

        sources.forEach { src ->
            File(workDir, src.name).copyTo(File(outputDir, src.name), overwrite = true)
        }

        return sources.size
    }

    private fun findIcons(dir: File, baseNames: Set<String>): List<File> =
        dir.listFiles()?.filter { f ->
            f.extension in STAMPABLE_EXTENSIONS && f.nameWithoutExtension in baseNames
        } ?: emptyList()

    private fun extractCli(parentDir: File): File {
        val cli = File(parentDir, "app-icon-banner")
        javaClass.getResourceAsStream("/app-icon-banner")
            ?.use { input -> cli.outputStream().use { input.copyTo(it) } }
            ?: error("Bundled CLI resource missing from the plugin jar")
        cli.setExecutable(true, false)
        return cli
    }
}
