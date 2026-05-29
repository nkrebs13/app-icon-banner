package io.github.nkrebs13.appiconbanner.android

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
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

// Adaptive icon canvas is always 108dp. Pixel width per density qualifier.
private val ADAPTIVE_CANVAS_PX = mapOf(
    "mdpi" to 108,
    "hdpi" to 162,
    "xhdpi" to 216,
    "xxhdpi" to 324,
    "xxxhdpi" to 432,
)
// Density multipliers matching the canvas sizes above.
private val ADAPTIVE_DENSITY_FACTOR = mapOf(
    "mdpi" to 1.0,
    "hdpi" to 1.5,
    "xhdpi" to 2.0,
    "xxhdpi" to 3.0,
    "xxxhdpi" to 4.0,
)

// Banner geometry for adaptive icons — must stay inside the 72/108 dp safe zone.
//   108dp canvas; safe zone is 72dp centered (18dp on each side ≈ 16.7%).
//   We use 20% inset and 22% height to clear the outer parallax/mask region with margin.
private const val ADAPTIVE_HEIGHT_PCT = 22
private const val ADAPTIVE_BOTTOM_INSET_PCT = 20
private const val ADAPTIVE_TEXT_PCT = 55

// dp values used in the layer-list XML (density-independent; same XML works at all densities).
//   banner height dp  = round(108 × 22/100) = 24dp
//   banner inset dp   = round(108 × 20/100) = 22dp
//   banner top dp     = 108 − 24 − 22 = 62dp
private const val BANNER_HEIGHT_DP = 24
private const val BANNER_BOTTOM_DP = 22
private const val BANNER_TOP_DP = 108 - BANNER_HEIGHT_DP - BANNER_BOTTOM_DP  // 62

/**
 * Stamps the color+label banner onto all Android launcher icons for one variant, writing
 * stamped copies into a generated resource directory that AGP merges with highest priority.
 *
 * **Raster icons** (PNG/WebP in `mipmap-{density}/`): stamped directly using the ImageMagick CLI.
 *
 * - `ic_launcher.{png,webp}` — legacy launcher icon, flat geometry (no inset needed).
 * - `ic_launcher_round.{png,webp}` — round launcher icon, same flat geometry.
 * - `ic_launcher_foreground.{png,webp}` — adaptive icon foreground (raster), stamped inside
 *   the 72/108 dp safe zone (22% height, 20% bottom inset) to survive all launcher mask shapes.
 * - `ic_launcher_monochrome.{png,webp}` — **not** stamped; the launcher applies its own
 *   wallpaper-derived tint, making a banner invisible.
 *
 * **XML vector foreground** (`drawable/ic_launcher_foreground.xml`): when no raster foreground
 * exists, the plugin generates a banner layer-list overlay:
 *
 * 1. A banner-only PNG per density (`mipmap-{density}/app_icon_banner_{variant}.png`).
 * 2. A layer-list XML (`drawable/ic_launcher_foreground_{variant}.xml`) that stacks the
 *    original foreground vector + the banner PNG at the correct safe-zone position.
 * 3. Updated adaptive-icon XML files in `mipmap-anydpi` directories that reference the new
 *    layer-list foreground; AGP's resource merger gives the generated files highest priority.
 *
 * Config-cache safe: holds no [org.gradle.api.Project] reference.
 */
@CacheableTask
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

    /**
     * Lowercase variant name used for generated resource names (e.g. `debug`). Must be a valid
     * Android resource name component (lowercase letters, digits, underscores only).
     */
    @get:Input
    abstract val variantName: Property<String>

    /** Generated resource directory — AGP merges its contents with highest priority. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stamp() {
        val source = sourceResDir.get().asFile
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val mipmapDirs = source.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("mipmap-") && !it.name.contains("anydpi") }
            ?: emptyList()

        if (mipmapDirs.isEmpty()) {
            logger.warn(
                "app-icon-banner: no mipmap-* directories found in ${source.path}. " +
                    "Check that androidResDir points to your Android res/ directory. " +
                    "Default: src/androidMain/res (Compose Multiplatform) or src/main/res.",
            )
            return
        }

        val workRoot = File(output.parentFile, "stamp-work").also {
            it.deleteRecursively(); it.mkdirs()
        }

        val cli = extractCli(output.parentFile)
        val color = bannerColor.get()
        val label = bannerLabel.get()
        val base = iconName.get()
        val variant = variantName.get()

        val legacyNames = setOf(base, "${base}_round")
        val adaptiveNames = setOf("${base}_foreground")
        val monochromeNames = setOf("${base}_monochrome")

        var totalStamped = 0

        try {
            mipmapDirs.forEach { mipmapDir ->
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
                        heightPct = ADAPTIVE_HEIGHT_PCT,
                        bottomInsetPct = ADAPTIVE_BOTTOM_INSET_PCT,
                        textPct = ADAPTIVE_TEXT_PCT,
                    )
                }

                // Copy monochrome without stamping.
                findIcons(mipmapDir, monochromeNames)
                    .forEach { it.copyTo(File(outMipmapDir, it.name), overwrite = true) }
            }

            // If there are no raster foreground images across any density bucket, check for an
            // XML vector foreground. If found, generate a banner overlay via a layer-list.
            val hasRasterForeground = mipmapDirs.any { findIcons(it, adaptiveNames).isNotEmpty() }
            if (!hasRasterForeground) {
                val foregroundXml = File(source, "drawable/${base}_foreground.xml")
                if (foregroundXml.exists()) {
                    generateXmlForegroundOverlay(
                        source = source,
                        output = output,
                        color = color, label = label,
                        base = base, variant = variant,
                        mipmapDirs = mipmapDirs,
                    )
                    totalStamped += mipmapDirs.size  // count density buckets affected
                }
            }
        } finally {
            workRoot.deleteRecursively()
        }

        logger.lifecycle(
            "app-icon-banner: stamped ${totalStamped} Android icon(s) for '${label}' (${color})",
        )
    }

    /**
     * Handles projects where the adaptive icon foreground is an XML vector drawable.
     *
     * Generates per-density banner PNGs, a layer-list XML that stacks the original vector
     * foreground + banner, and updated adaptive-icon XMLs that reference the new layer-list.
     * All outputs go into the generated res directory; AGP merges them with highest priority.
     */
    private fun generateXmlForegroundOverlay(
        source: File, output: File,
        color: String, label: String,
        base: String, variant: String,
        mipmapDirs: List<File>,
    ) {
        val bannerResourceName = "app_icon_banner_$variant"
        val foregroundLayerName = "${base}_foreground_$variant"
        val font = resolveFont() ?: run {
            logger.warn(
                "app-icon-banner: no usable font found; cannot generate adaptive-icon banner overlay. " +
                    "Pass --font <path> or install Helvetica/Arial on this machine.",
            )
            return
        }

        // 1. Generate a banner-only PNG at the correct dimensions for each density.
        //    Size: full adaptive canvas width × 22% height, matching the layer-list dp values.
        mipmapDirs.forEach { mipmapDir ->
            val density = mipmapDir.name.removePrefix("mipmap-")
            val canvasPx = ADAPTIVE_CANVAS_PX[density] ?: return@forEach
            val densityFactor = ADAPTIVE_DENSITY_FACTOR[density] ?: return@forEach

            val bannerW = canvasPx
            val bannerH = (BANNER_HEIGHT_DP * densityFactor).toInt().coerceAtLeast(1)
            val fontsize = (bannerH * ADAPTIVE_TEXT_PCT / 100).coerceAtLeast(6)

            val outMipmapDir = File(output, mipmapDir.name).apply { mkdirs() }
            val bannerPng = File(outMipmapDir, "$bannerResourceName.png")

            val process = ProcessBuilder(
                "magick",
                "-size", "${bannerW}x${bannerH}", "xc:$color",
                "-font", font,
                "-fill", "white",
                "-gravity", "center",
                "-pointsize", fontsize.toString(),
                "-annotate", "0", label,
                "-strip",
                bannerPng.absolutePath,
            ).redirectErrorStream(true).start()

            val out = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) error("app-icon-banner: banner PNG generation failed:\n$out")
        }

        // 2. Write the layer-list XML that stacks original foreground + banner.
        //    Layer-list uses dp units: banner_top=62dp, banner_bottom=22dp (see constants).
        val drawableOut = File(output, "drawable").apply { mkdirs() }
        File(drawableOut, "$foregroundLayerName.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/${base}_foreground"/>
    <item
        android:top="${BANNER_TOP_DP}dp"
        android:bottom="${BANNER_BOTTOM_DP}dp"
        android:drawable="@mipmap/$bannerResourceName"/>
</layer-list>
""",
        )

        // 3. Write updated adaptive-icon XMLs that reference the new layer-list foreground.
        //    Detect the background reference from the originals so we don't hard-code it.
        source.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("mipmap-anydpi") }
            ?.forEach { anydpiDir ->
                val xmlFiles = anydpiDir.listFiles()
                    ?.filter { it.extension == "xml" && it.nameWithoutExtension.startsWith(base) }
                    ?: return@forEach

                val outAnydpiDir = File(output, anydpiDir.name).apply { mkdirs() }
                xmlFiles.forEach { xmlFile ->
                    val original = xmlFile.readText()
                    val backgroundRef = Regex("""android:drawable="(@[^"]+background[^"]*)" """)
                        .find(original)?.groupValues?.getOrNull(1)
                        ?: "@drawable/${base}_background"

                    File(outAnydpiDir, xmlFile.name).writeText(
                        """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="$backgroundRef"/>
    <foreground android:drawable="@drawable/$foregroundLayerName"/>
</adaptive-icon>
""",
                    )
                }
            }

        logger.lifecycle(
            "app-icon-banner: generated adaptive-icon layer-list overlay " +
                "($foregroundLayerName.xml → @drawable/$foregroundLayerName)",
        )
    }

    private fun stampIcons(
        cli: File, workDir: File, sources: List<File>, outputDir: File,
        color: String, label: String,
        heightPct: Int, bottomInsetPct: Int, textPct: Int,
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
        if (exitCode != 0) error("app-icon-banner CLI failed (exit $exitCode):\n$cliOutput")

        sources.forEach { src ->
            File(workDir, src.name).copyTo(File(outputDir, src.name), overwrite = true)
        }
        return sources.size
    }

    private fun findIcons(dir: File, baseNames: Set<String>): List<File> =
        dir.listFiles()?.filter { f ->
            f.extension in STAMPABLE_EXTENSIONS && f.nameWithoutExtension in baseNames
        } ?: emptyList()

    /**
     * Resolves a system font path for ImageMagick text rendering. Fontconfig is often absent on
     * macOS ImageMagick builds, so we use explicit paths. Returns null if no font is found.
     */
    private fun resolveFont(): String? = listOf(
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial.ttf",
    ).firstOrNull { File(it).exists() }

    private fun extractCli(parentDir: File): File {
        val cli = File(parentDir, "app-icon-banner")
        javaClass.getResourceAsStream("/app-icon-banner")
            ?.use { input -> cli.outputStream().use { input.copyTo(it) } }
            ?: error("Bundled CLI resource missing from the plugin jar")
        cli.setExecutable(true, false)
        return cli
    }
}
