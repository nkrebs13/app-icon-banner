package io.github.nkrebs13.appiconbanner.android

import io.github.nkrebs13.appiconbanner.CLI_RESOURCE
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File

private val STAMPABLE_EXTENSIONS = setOf("png", "webp")

// Adaptive icon canvas is always 108dp. Pixel width per density qualifier.
// ADAPTIVE_DENSITY_FACTOR is derived as canvasPx / 108.0 — no second map needed.
private val ADAPTIVE_CANVAS_PX = mapOf(
    "mdpi" to 108,
    "hdpi" to 162,
    "xhdpi" to 216,
    "xxhdpi" to 324,
    "xxxhdpi" to 432,
)

// Banner geometry for adaptive icons — must stay inside the 72/108 dp safe zone.
//   108dp canvas; safe zone is 72dp centered (18dp on each side ≈ 16.7%).
//   We use 20% inset and 22% height to clear the outer parallax/mask region with margin.
private const val ADAPTIVE_HEIGHT_PCT = 22
private const val ADAPTIVE_BOTTOM_INSET_PCT = 20

// Banner geometry for round (circle-masked) legacy icons.
//   bottomInsetPct=10 lifts the band away from the icon bottom, placing it where the circle
//   mask chord is ~78% of the icon width — wide enough for standard labels at default font size.
private const val ROUND_HEIGHT_PCT = 18
private const val ROUND_BOTTOM_INSET_PCT = 10

// Adaptive-canvas geometry. The XML-overlay banner is a transparent 108dp canvas, rather than
// a 24dp strip inset with absolute layer-list dimensions. AdaptiveIconDrawable is permitted to
// give its foreground fewer than 108dp of bounds; absolute 62dp + 22dp insets can then consume
// the entire child and make the strip disappear. A full-canvas overlay scales with foreground
// bounds exactly like the raster-foreground stamping path.
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
 * - `ic_launcher_round.{png,webp}` — round launcher icon, stamped with a 10% bottom inset and
 *   a circle-safe text width so the label is not clipped by circular launcher masks.
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

    /**
     * Path to a TrueType or TrueType Collection font file for banner text rendering. Must be set
     * by the plugin (via [FONT_CANDIDATES] probe) to be part of the task's cache key — different
     * fonts produce different outputs, so this must be tracked as an `@Input`.
     *
     * Leave unset to use the probe result from [AppIconBannerPlugin]; the task throws with a
     * clear message if unset (null) at execution time.
     */
    @get:Input
    @get:Optional
    abstract val fontPath: Property<String>

    /**
     * Text size as a percentage of band height. Overrides the default 55% when set. Range 1–100.
     * Sourced from [BannerSpec.textSizePct]; leave unset to use the default.
     */
    @get:Input
    @get:Optional
    abstract val bannerTextSizePct: Property<Int>

    companion object {
        // Candidates checked in order; first found wins. Fontconfig is often absent on macOS
        // ImageMagick builds, so we use explicit paths rather than font names. Linux paths cover
        // GitHub Actions ubuntu-latest where fonts-dejavu-core is pre-installed.
        internal val FONT_CANDIDATES = listOf(
            // macOS
            "/System/Library/Fonts/Helvetica.ttc",
            "/System/Library/Fonts/HelveticaNeue.ttc",
            "/System/Library/Fonts/SFNS.ttf",
            "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/Library/Fonts/Arial.ttf",
            // Linux (Ubuntu/Debian — pre-installed on GitHub Actions ubuntu-latest)
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
            "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf",
        )

        // Bold-specific candidates prepended before the regular list. Composed rather than copied
        // so any new FONT_CANDIDATES entry automatically appears as a bold fallback. Internal for testability.
        internal val BOLD_SPECIFIC_CANDIDATES = listOf(
            // macOS — explicit bold files
            "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
            "/Library/Fonts/Arial Bold.ttf",
            // Linux — bold variants
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf",
        )
        internal val BOLD_FONT_CANDIDATES = BOLD_SPECIFIC_CANDIDATES + FONT_CANDIDATES
    }

    @TaskAction
    fun stamp() {
        val source = sourceResDir.get().asFile
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        // Compute both slices from one directory listing.
        val allSourceDirs = source.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val mipmapDirs = allSourceDirs.filter {
            it.name.startsWith("mipmap-") && !it.name.contains("anydpi")
        }
        val anydpiDirs = allSourceDirs.filter { it.name.startsWith("mipmap-anydpi") }

        // Use Gradle's managed temporaryDir for scratch space — it is excluded from output
        // snapshotting, not a sibling of the @OutputDirectory, and cleaned by ./gradlew clean.
        val workRoot = File(temporaryDir, "stamp-work").apply { mkdirs() }
        val cli = extractCli(temporaryDir)
        val color = bannerColor.get()
        val label = bannerLabel.get()
        val base = iconName.get()
        val variant = variantName.get()
        // fontPath is wired by AppIconBannerPlugin via FONT_CANDIDATES probe (tracked as @Input
        // so different fonts on different machines produce different cache keys). When manually
        // instantiated in tests, fontPath may be unset — null propagates to stampIcons() / overlay.
        val font: String? = fontPath.orNull

        val squareNames = setOf(base)
        val roundNames = setOf("${base}_round")
        val adaptiveNames = setOf("${base}_foreground")
        val monochromeNames = setOf("${base}_monochrome")

        // textSizePct from DSL (BannerSpec.textSizePct), or 55% default.
        val effectiveTextPct = bannerTextSizePct.getOrElse(55)

        val roundSafeWidthPct = roundSafeWidthPct(ROUND_HEIGHT_PCT, ROUND_BOTTOM_INSET_PCT)
        val adaptiveSafeWidthPct = adaptiveSafeWidthPct(ADAPTIVE_HEIGHT_PCT, ADAPTIVE_BOTTOM_INSET_PCT)

        var totalStamped = 0
        var rasterForegroundFound = false

        try {
            mipmapDirs.forEach { mipmapDir ->
                val outMipmapDir = File(output, mipmapDir.name).apply { mkdirs() }

                // List the directory once and partition in memory — avoids 4 separate listFiles() calls.
                val allFiles = mipmapDir.listFiles()
                    ?.filter { it.extension in STAMPABLE_EXTENSIONS }
                    ?: emptyList()
                val squareFiles    = allFiles.filter { it.nameWithoutExtension in squareNames }
                val roundFiles     = allFiles.filter { it.nameWithoutExtension in roundNames }
                val adaptiveFiles  = allFiles.filter { it.nameWithoutExtension in adaptiveNames }
                val monoFiles      = allFiles.filter { it.nameWithoutExtension in monochromeNames }

                // Square legacy icon (ic_launcher): no circle-safe constraint needed.
                if (squareFiles.isNotEmpty()) {
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "${mipmapDir.name}-legacy").also { it.mkdirs() },
                        sources = squareFiles,
                        outputDir = outMipmapDir,
                        color = color, label = label,
                        heightPct = 18, bottomInsetPct = 0, textPct = effectiveTextPct,
                        safeWidthPct = 100,
                        font = font,
                    )
                }

                // Round legacy icon (ic_launcher_round): lifted inset + circle-safe text width
                // so the label is never clipped by the circular launcher mask.
                if (roundFiles.isNotEmpty()) {
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "${mipmapDir.name}-round").also { it.mkdirs() },
                        sources = roundFiles,
                        outputDir = outMipmapDir,
                        color = color, label = label,
                        heightPct = ROUND_HEIGHT_PCT, bottomInsetPct = ROUND_BOTTOM_INSET_PCT,
                        textPct = effectiveTextPct,
                        safeWidthPct = roundSafeWidthPct,
                        font = font,
                    )
                }

                if (adaptiveFiles.isNotEmpty()) {
                    rasterForegroundFound = true
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "${mipmapDir.name}-adaptive").also { it.mkdirs() },
                        sources = adaptiveFiles,
                        outputDir = outMipmapDir,
                        color = color, label = label,
                        heightPct = ADAPTIVE_HEIGHT_PCT,
                        bottomInsetPct = ADAPTIVE_BOTTOM_INSET_PCT,
                        textPct = effectiveTextPct,
                        safeWidthPct = adaptiveSafeWidthPct,
                        font = font,
                    )
                }

                // Copy monochrome without stamping.
                monoFiles.forEach { it.copyTo(File(outMipmapDir, it.name), overwrite = true) }
            }

            // Raster foreground in drawable/ (density-independent WebP/PNG). Some projects store
            // the adaptive icon foreground directly in drawable/ rather than per-density mipmap
            // dirs. Stamp it and output to drawable/ in the generated res so AGP merges it with
            // highest priority over the original.
            if (!rasterForegroundFound) {
                val drawableDir = File(source, "drawable")
                val drawableForeground = findIcons(drawableDir, adaptiveNames)
                if (drawableForeground.isNotEmpty()) {
                    rasterForegroundFound = true
                    val outDrawableDir = File(output, "drawable").apply { mkdirs() }
                    totalStamped += stampIcons(
                        cli = cli,
                        workDir = File(workRoot, "drawable-adaptive").also { it.mkdirs() },
                        sources = drawableForeground,
                        outputDir = outDrawableDir,
                        color = color, label = label,
                        heightPct = ADAPTIVE_HEIGHT_PCT,
                        bottomInsetPct = ADAPTIVE_BOTTOM_INSET_PCT,
                        textPct = effectiveTextPct,
                        safeWidthPct = adaptiveSafeWidthPct,
                        font = font,
                    )
                }
            }

            // If there are no raster foreground images across any density bucket, check whether
            // the adaptive icon XML references an XML vector foreground. Reading from the adaptive
            // icon XML directly is authoritative — avoids guessing the drawable name/location.
            if (!rasterForegroundFound && mipmapDirs.isNotEmpty()) {
                val xmlForeground = detectXmlVectorForeground(source, anydpiDirs)
                if (xmlForeground != null) {
                    generateXmlForegroundOverlay(
                        output = output,
                        anydpiDirs = anydpiDirs,
                        color = color, label = label,
                        base = base, variant = variant,
                        mipmapDirs = mipmapDirs,
                        font = font,
                        effectiveTextPct = effectiveTextPct,
                        safeWidthPct = adaptiveSafeWidthPct,
                    )
                    totalStamped += mipmapDirs.size
                }
            }
        } finally {
            workRoot.deleteRecursively()
        }

        if (totalStamped == 0) {
            logger.warn(
                "app-icon-banner: nothing stamped in ${source.path}. Supported layouts: " +
                    "raster icons in mipmap-{density}/, a raster adaptive foreground in " +
                    "drawable/ (minSdk >= 26 apps often have ONLY this), or an XML vector " +
                    "foreground referenced from mipmap-anydpi*/ (requires density mipmap dirs " +
                    "for the banner overlay PNGs). Check that androidResDir points to your " +
                    "Android res/ directory — default: src/androidMain/res (Compose " +
                    "Multiplatform) or src/main/res.",
            )
            return
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
     *
     * Banner PNGs are generated via the same ImageMagick binary the CLI uses (magick / convert),
     * with the same `-colorspace sRGB -strip` flags for consistency and idempotency.
     */
    private fun generateXmlForegroundOverlay(
        output: File,
        anydpiDirs: List<File>,
        color: String, label: String,
        base: String, variant: String,
        mipmapDirs: List<File>,
        font: String?,
        effectiveTextPct: Int,
        safeWidthPct: Int,
    ) {
        if (font == null) {
            logger.warn(
                "app-icon-banner: no usable font found; cannot generate adaptive-icon banner overlay. " +
                    "Install a system font (macOS: Homebrew ImageMagick; Linux: fonts-dejavu-core).",
            )
            return
        }

        val bannerResourceName = "app_icon_banner_$variant"
        val foregroundLayerName = "${base}_foreground_$variant"

        // Detect which ImageMagick binary is available — same logic as the bundled CLI.
        // Use runCatching to handle IOException when the binary is not on PATH (start() throws,
        // it does not return a non-zero exit code).
        val im = listOf("magick", "convert").firstOrNull { bin ->
            runCatching {
                ProcessBuilder(bin, "-version").redirectErrorStream(true).start()
                    .also { it.inputStream.use { s -> s.readBytes() } }
                    .waitFor() == 0
            }.getOrDefault(false)
        } ?: run {
            logger.warn("app-icon-banner: ImageMagick not found; adaptive-icon overlay not generated.")
            return
        }

        // 1. Generate a full adaptive-canvas PNG per density, transparent apart from the banner.
        //    It must fill the layer-list item: foreground bounds can be smaller than 108dp on
        //    launchers that normalize/mask adaptive icons, so fixed dp insets can collapse a
        //    strip-only child to zero height.
        mipmapDirs.forEach { mipmapDir ->
            val density = mipmapDir.name.removePrefix("mipmap-")
            val canvasPx = ADAPTIVE_CANVAS_PX[density] ?: return@forEach
            val densityFactor = canvasPx / 108.0

            val bannerW = canvasPx
            val bannerH = canvasPx
            val bannerBandH = (BANNER_HEIGHT_DP * densityFactor).toInt().coerceAtLeast(1)
            val bannerTop = (BANNER_TOP_DP * densityFactor).toInt()

            val safeW = (bannerW * safeWidthPct / 100.0).toInt().coerceAtLeast(1)

            // Auto-shrink font (same 0.6×/char heuristic as the CLI) until text fits safeW.
            var fontsize = (bannerBandH * effectiveTextPct / 100).coerceAtLeast(6)
            val labelLen = label.length
            while (fontsize > 6) {
                val estW = labelLen * fontsize * 6 / 10
                if (estW <= safeW) break
                fontsize--
            }

            val outMipmapDir = File(output, mipmapDir.name).apply { mkdirs() }
            val bannerPng = File(outMipmapDir, "$bannerResourceName.png")

            // Three-layer composite: transparent adaptive canvas, full-width colored band, and
            // a safeW-wide text layer. The final NorthWest geometry puts the band at its
            // proportional canvas position before Android applies launcher-specific bounds.
            val process = ProcessBuilder(
                im,
                "-size", "${bannerW}x${bannerH}", "xc:none",
                "(", "-size", "${bannerW}x${bannerBandH}", "xc:$color",
                    "(", "-size", "${safeW}x${bannerBandH}", "xc:none",
                        "-font", font,
                        "-fill", "white",
                        "-gravity", "center",
                        "-pointsize", fontsize.toString(),
                        "-annotate", "0", label,
                    ")", "-gravity", "center", "-composite",
                ")", "-gravity", "northwest", "-geometry", "+0+$bannerTop", "-composite",
                "-colorspace", "sRGB",
                // Preserve the transparent canvas. TrueColor would make the non-banner area
                // opaque black and hide the original adaptive foreground beneath this overlay.
                "-type", "TrueColorAlpha",
                "-strip",
                bannerPng.absolutePath,
            ).redirectErrorStream(true).start()

            val out = process.inputStream.bufferedReader().readText()
            if (process.waitFor() != 0) error("app-icon-banner: banner PNG generation failed:\n$out")
        }

        // 2. Write the layer-list XML that stacks original foreground + full-canvas banner.
        //    Do not put fixed dp insets on the banner item: the canvas handles positioning
        //    proportionally when AdaptiveIconDrawable narrows the foreground bounds.
        val drawableOut = File(output, "drawable").apply { mkdirs() }
        File(drawableOut, "$foregroundLayerName.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/${base}_foreground"/>
    <item android:drawable="@mipmap/$bannerResourceName"/>
</layer-list>
""",
        )

        // 3. Write updated adaptive-icon XMLs using the new layer-list foreground.
        //    Extract the background ref from the original XML via findAll (no trailing-space
        //    assumption) so color references, non-conventional names, and inline formatting all work.
        anydpiDirs.forEach { anydpiDir ->
            val xmlFiles = anydpiDir.listFiles()
                ?.filter { it.extension == "xml" && it.nameWithoutExtension.startsWith(base) }
                ?: return@forEach

            val outAnydpiDir = File(output, anydpiDir.name).apply { mkdirs() }
            xmlFiles.forEach { xmlFile ->
                val original = xmlFile.readText()
                val allRefs = Regex("""android:drawable="(@[^"]+)"""")
                    .findAll(original).map { it.groupValues[1] }.toList()
                val backgroundRef = allRefs.firstOrNull { "background" in it }
                    ?: "@drawable/${base}_background"

                // Preserve <monochrome> if present — Android 13+ themed icons use it.
                // Dropping it would silently break Material You icon theming for the stamped variant.
                val monochromeRef = allRefs.firstOrNull { "monochrome" in it }
                val monochromeElement = monochromeRef
                    ?.let { "\n    <monochrome android:drawable=\"$it\"/>" }
                    ?: ""

                File(outAnydpiDir, xmlFile.name).writeText(
                    """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="$backgroundRef"/>
    <foreground android:drawable="@drawable/$foregroundLayerName"/>$monochromeElement
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
        safeWidthPct: Int = 100,
        font: String?,
    ): Int {
        val resolvedFont = font ?: error(
            "app-icon-banner: no usable font found for Android icon stamping. " +
                "On macOS: install ImageMagick via Homebrew. " +
                "On Linux: install fonts-dejavu-core (sudo apt-get install -y fonts-dejavu-core).",
        )

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
            "--font", resolvedFont,
            "--safe-width-pct", safeWidthPct.toString(),
        ).redirectErrorStream(true).start()

        val cliOutput = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) error("app-icon-banner CLI failed (exit $exitCode):\n$cliOutput")

        sources.forEach { src ->
            File(workDir, src.name).copyTo(File(outputDir, src.name), overwrite = true)
        }
        return sources.size
    }

    // Geometry helpers are file-level internal functions so they can be unit-tested
    // without requiring a full Gradle task instance.
    private fun roundSafeWidthPct(heightPct: Int, bottomInsetPct: Int) =
        roundSafeWidthPctInternal(heightPct, bottomInsetPct)

    private fun adaptiveSafeWidthPct(heightPct: Int, bottomInsetPct: Int) =
        adaptiveSafeWidthPctInternal(heightPct, bottomInsetPct)

    /**
     * Returns the XML foreground file if the adaptive icon XML references one, or null if the
     * foreground is a raster (no overlay needed) or not found.
     *
     * Reads the `<foreground android:drawable="@type/name"/>` attribute from the first adaptive
     * icon XML in [anydpiDirs] and checks whether `res/type/name.xml` exists in [source].
     * This is authoritative — avoids guessing drawable names or locations.
     */
    private fun detectXmlVectorForeground(source: File, anydpiDirs: List<File>): File? {
        val base = iconName.get()
        // Filter to XMLs whose name starts with `base` — same set that generateXmlForegroundOverlay
        // iterates — so detection and generation operate on the same file, not a random sibling.
        val adaptiveIconXml = anydpiDirs
            .flatMap { it.listFiles()?.filter { f -> f.extension == "xml" && f.nameWithoutExtension.startsWith(base) } ?: emptyList() }
            .firstOrNull() ?: return null

        val xml = adaptiveIconXml.readText()
        val foregroundSection = xml.substringAfter("<foreground", "").ifBlank { return null }
        val foregroundRef = Regex("""android:drawable="(@[^"]+)"""")
            .find(foregroundSection)
            ?.groupValues?.getOrNull(1) ?: return null

        // foregroundRef is "@type/name" — resolve to source/type/name.xml
        val parts = foregroundRef.removePrefix("@").split("/", limit = 2)
        if (parts.size != 2) return null
        return File(source, "${parts[0]}/${parts[1]}.xml").takeIf { it.exists() }
    }

    private fun findIcons(dir: File, baseNames: Set<String>): List<File> =
        dir.listFiles()?.filter { f ->
            f.extension in STAMPABLE_EXTENSIONS && f.nameWithoutExtension in baseNames
        } ?: emptyList()

    private fun extractCli(dir: File): File {
        val cli = File(dir, "app-icon-banner")
        javaClass.getResourceAsStream(CLI_RESOURCE)
            ?.use { input -> cli.outputStream().use { input.copyTo(it) } }
            ?: error("Bundled CLI resource missing from the plugin jar")
        cli.setExecutable(true, false)
        return cli
    }
}

/**
 * Safe text-width for round (circle-masked) legacy icons as a percentage of icon width.
 *
 * A round icon with a circular mask of radius R = icon_height/2 exposes a chord width at the
 * band's vertical center equal to `2R × sqrt(1 − (1 − 2·ycFrac)²)`, where `ycFrac` is the
 * fraction of icon height from the bottom to the band center.
 *
 * Note: measures the chord at the band CENTER, not the text bottom — the tightest constraint
 * is slightly lower (band center − half font height). The 0.6 × fontsize/char shrink heuristic
 * errs conservatively and compensates in practice.
 *
 * Internal for testability.
 */
internal fun roundSafeWidthPctInternal(heightPct: Int, bottomInsetPct: Int): Int {
    val ycFrac = (bottomInsetPct + heightPct / 2.0) / 100.0
    val oneMinusTwiceYc = 1.0 - 2.0 * ycFrac
    return Math.sqrt(Math.max(0.0, 1.0 - oneMinusTwiceYc * oneMinusTwiceYc)).asSafeWidthPct()
}

/**
 * Safe text-width for adaptive icon foreground as a percentage of the 108dp canvas width.
 *
 * Uses the tightest Android-compliant mask shape: a circle of radius 33dp centered at 54dp
 * on the 108dp adaptive canvas. Measures chord width at band center; same approximation caveat
 * as [roundSafeWidthPctInternal] applies.
 *
 * Internal for testability.
 */
internal fun adaptiveSafeWidthPctInternal(heightPct: Int, bottomInsetPct: Int): Int {
    val bandCenterFromBottom = (bottomInsetPct + heightPct / 2.0) / 100.0
    val distFromCanvasCenterFrac = Math.abs(0.5 - bandCenterFromBottom)
    val distDp = distFromCanvasCenterFrac * 108.0
    val safeHalfDp = Math.sqrt(Math.max(0.0, 33.0 * 33.0 - distDp * distDp))
    return (safeHalfDp * 2.0 / 108.0).asSafeWidthPct()
}

internal fun Double.asSafeWidthPct() = (this * 100.0).toInt().coerceAtMost(100)
