# Configuration Reference

## DSL overview

```kotlin
appIconBanner {
    // Build-type banner (applies to every variant of that build type)
    buildType("debug")    { color = "#0288D1"; label = "DEBUG" }
    buildType("internal") { color = "#FF6F00"; label = "INTERNAL" }
    // release → no banner (no entry = pristine icon)

    // Flavor banner (applies to every variant of that flavor)
    flavor("staging")     { color = "#7B1FA2"; label = "STAGING" }

    // Variant-level override — most specific wins
    variant("stagingDebug") { color = "#1565C0"; label = "STAGING·DBG" }

    // iOS-only: map a custom Xcode configuration with no Android equivalent
    iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
}
```

## Resolution priority

**Most specific wins — configs are never stacked.**

| Priority | Scope | Example |
|---|---|---|
| 1 (highest) | `variant()` | `variant("stagingDebug")` |
| 2 | `flavor()` | `flavor("staging")` |
| 3 | `buildType()` | `buildType("debug")` |
| 4 (lowest) | built-in debug default | blue DEBUG banner, always on |

A `stagingDebug` variant with a `flavor("staging")` entry and a `buildType("debug")` entry resolves to the flavor — not both combined. Set `variant("stagingDebug")` explicitly when you need a unique look for that exact combination.

## Color and label rules

- `color` must be `#RRGGBB` (six hex digits). Defaults to `#0288D1` (blue) when omitted.
- `label` must not contain `|` (config-file field separator) or `%` (ImageMagick format specifier). Defaults to the slot name when omitted.

## Extension properties

| Property | Type | Default | Description |
|---|---|---|---|
| `debugDefault` | `Boolean` | `true` | When true, debug builds with no explicit entry get a blue DEBUG banner. Set to `false` to opt out entirely. |
| `androidResDir` | `String?` | `null` | Path to the Android `res/` directory, relative to the module root. Auto-detected when null: checks `src/androidMain/res` (Compose Multiplatform) then `src/main/res` (traditional Android). |
| `androidIconName` | `String` | `"ic_launcher"` | Base name of the Android launcher icon files. The plugin derives `ic_launcher_foreground`, `ic_launcher_round`, etc. from this name. Change only if your project uses a non-standard icon name. |
| `iosOutputDir` | `String?` | `null` | Root directory for iOS outputs (`app-icon-banner.config` and `scripts/app-icon-banner`), relative to the module root. Null defaults to the module directory. See [iOS Setup](ios-setup.md#kmp-projects-redirect-outputs-to-iosapp). |

## `iosConfiguration()`

Maps a custom Xcode configuration name that has no Android equivalent:

```kotlin
appIconBanner {
    buildType("debug") { color = "#0288D1"; label = "DEBUG" }
    // "Firebase" is a custom Xcode configuration; no matching Android build type
    iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
}
```

iOS resolution: `iosConfiguration` (exact name match) → `buildType` (case-insensitive, "Debug" matches "debug") → built-in debug default.

`buildType("debug")` automatically maps to Xcode's "Debug" configuration — add `iosConfiguration` only when you have configurations beyond the standard Debug/Release set.

## Android icon variants

The plugin stamps these icon files per density bucket. Each behaves differently:

| File | Stamped? | Reason |
|---|---|---|
| `ic_launcher.{png,webp}` | Yes | Legacy launcher icon |
| `ic_launcher_round.{png,webp}` | Yes | Round launcher icon |
| `ic_launcher_foreground.{png,webp}` | Yes | Adaptive icon foreground (safe-zone geometry applied) |
| `ic_launcher_monochrome.{png,webp}` | **No** | The launcher applies a wallpaper-derived solid tint at display time (Material You / Android 13+). A color band would be invisible under the system tint, so the plugin copies it unmodified. |

If your project uses XML vector drawables for the adaptive icon foreground instead of raster PNGs, the plugin generates a layer-list overlay that composites the original vector with a banner PNG per density.

## Advanced: variant-level override

Use `variant()` when you need a unique banner for one specific combination of flavor + build type:

```kotlin
appIconBanner {
    buildType("debug")      { color = "#0288D1"; label = "DEBUG" }
    flavor("staging")       { color = "#7B1FA2"; label = "STAGING" }

    // stagingDebug gets its own banner; stagingRelease stays pristine
    variant("stagingDebug") { color = "#1565C0"; label = "STAGING·DBG" }
}
```

Variant names must match AGP's camelCase convention (`stagingDebug`, not `staging_debug`).
