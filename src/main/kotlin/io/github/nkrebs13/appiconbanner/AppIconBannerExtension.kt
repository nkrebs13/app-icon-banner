package io.github.nkrebs13.appiconbanner

import org.gradle.api.Action

// #RRGGBB only — matches what java.awt.Color.decode() (used by easylauncher) and ImageMagick
// both accept unambiguously. 4-digit #ARGB and 8-digit #AARRGGBB throw NumberFormatException in
// java.awt.Color.decode() and are rejected here to surface the error at DSL configuration time.
private val COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

/**
 * Mutable holder configured inside a `buildType { }` / `flavor { }` / `variant { }` /
 * `iosConfiguration { }` block.
 *
 * [color] must be `#RRGGBB` (six hex digits, e.g. `#0288D1`); defaults to
 * [AppIconBannerExtension.DEFAULT_COLOR] when omitted.
 * [label] must not contain `|` (config-file field separator) or `%` (ImageMagick format specifier);
 * defaults to the slot name when omitted.
 */
class BannerSpec {
    var color: String? = null
    var label: String? = null

    internal fun toConfig(fallbackLabel: String): BannerConfig {
        val resolvedColor = color ?: AppIconBannerExtension.DEFAULT_COLOR
        val resolvedLabel = label ?: fallbackLabel
        require(COLOR_REGEX.matches(resolvedColor)) {
            "appIconBanner: invalid color '$resolvedColor' — must be #RRGGBB (six hex digits, e.g. #0288D1)"
        }
        require('|' !in resolvedLabel) {
            "appIconBanner: label '$resolvedLabel' must not contain '|' — it is used as the config file field separator"
        }
        require('%' !in resolvedLabel) {
            "appIconBanner: label '$resolvedLabel' must not contain '%' — ImageMagick interprets %-prefixed sequences in annotation text"
        }
        return BannerConfig(color = resolvedColor, label = resolvedLabel)
    }
}

/**
 * DSL for [AppIconBannerPlugin]. A single source of truth for both platforms:
 *
 * ```kotlin
 * appIconBanner {
 *     buildType("debug")   { color = "#0288D1"; label = "DEBUG" }
 *     flavor("meta")       { color = "#FF6F00"; label = "META" }
 *     variant("metaDebug") { color = "#7B1FA2"; label = "META·DBG" }
 *     iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
 * }
 * ```
 *
 * Android resolution priority (most specific wins, never stacked): `variant` > `flavor` >
 * `buildType` > debug default. iOS resolution: `iosConfiguration` (exact name) >
 * `buildType` (case-insensitive) > debug default.
 */
open class AppIconBannerExtension {

    /**
     * When true (default), debug builds with no explicit config get a blue "DEBUG" banner. Set to
     * false to opt out of the convention entirely.
     */
    var debugDefault: Boolean = true

    internal val buildTypes = linkedMapOf<String, BannerSpec>()
    internal val flavors = linkedMapOf<String, BannerSpec>()
    internal val variants = linkedMapOf<String, BannerSpec>()
    internal val iosConfigs = linkedMapOf<String, BannerSpec>()

    fun buildType(name: String, action: Action<BannerSpec>) {
        action.execute(buildTypes.getOrPut(name) { BannerSpec() })
    }

    fun flavor(name: String, action: Action<BannerSpec>) {
        action.execute(flavors.getOrPut(name) { BannerSpec() })
    }

    fun variant(name: String, action: Action<BannerSpec>) {
        action.execute(variants.getOrPut(name) { BannerSpec() })
    }

    fun iosConfiguration(name: String, action: Action<BannerSpec>) {
        action.execute(iosConfigs.getOrPut(name) { BannerSpec() })
    }

    /** Resolve the banner for an Android variant, or null for no banner. */
    internal fun resolveAndroid(
        variantName: String,
        flavorNames: List<String>,
        buildTypeName: String?,
    ): BannerConfig? {
        variants[variantName]?.let { return it.toConfig(variantName) }
        flavorNames.forEach { f -> flavors[f]?.let { return it.toConfig(f) } }
        buildTypeName?.let { bt -> buildTypes[bt]?.let { return it.toConfig(bt) } }
        // AGP always emits lowercase build type names ("debug", "release") — case-sensitive match is correct.
        if (debugDefault && buildTypeName.equals(DEBUG_BUILD_TYPE, ignoreCase = false)) {
            return DEFAULT_DEBUG
        }
        return null
    }

    /** Resolve the banner for an iOS Xcode configuration name, or null for no banner. */
    internal fun resolveIos(configurationName: String): BannerConfig? {
        iosConfigs[configurationName]?.let { return it.toConfig(configurationName) }
        // Xcode configuration names are Title-cased ("Debug", "Release") but the user writes DSL
        // build type names in lowercase — bridge the gap with case-insensitive lookup.
        buildTypes.entries.firstOrNull { it.key.equals(configurationName, ignoreCase = true) }
            ?.let { return it.value.toConfig(it.key) }
        // Same case-insensitive logic for the debug default ("Debug" Xcode config → "debug" constant).
        if (debugDefault && configurationName.equals(DEBUG_BUILD_TYPE, ignoreCase = true)) {
            return DEFAULT_DEBUG
        }
        return null
    }

    /**
     * Every iOS configuration that should get a banner, as `name|color|label` lines for the CLI's
     * config file. Keyed by Xcode configuration name (buildType names are TitleCased to match
     * Xcode's default "Debug"/"Release"). [iosConfigs] entries override derived ones.
     */
    internal fun iosConfigLines(): List<String> {
        val merged = linkedMapOf<String, BannerConfig>()
        if (debugDefault) merged["Debug"] = DEFAULT_DEBUG
        buildTypes.forEach { (name, spec) ->
            merged[name.replaceFirstChar(Char::titlecase)] = spec.toConfig(name)
        }
        iosConfigs.forEach { (name, spec) -> merged[name] = spec.toConfig(name) }
        return merged.map { (name, cfg) -> "$name|${cfg.color}|${cfg.label}" }
    }

    companion object {
        const val DEFAULT_COLOR = "#0288D1"
        private const val DEBUG_BUILD_TYPE = "debug"
        internal val DEFAULT_DEBUG = BannerConfig(color = DEFAULT_COLOR, label = "DEBUG")
    }
}
