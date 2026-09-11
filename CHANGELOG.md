# Changelog

## [Unreleased]

### Fixed
- Android XML foreground overlays now use a transparent full adaptive-icon canvas with a
  proportionally positioned banner. This keeps debug/internal banners visible when a launcher
  normalizes the adaptive foreground below its 108dp source bounds, without obscuring the base
  icon outside the banner band.

## [0.1.3] — 2026-06-03

### Added
- `BannerSpec.textSizePct` — override the default 55% text-size-to-band-height ratio per build type / flavor / variant (Android). Useful for long labels that would otherwise be auto-shrunk.
- `BannerSpec.bold` — prefer bold system font variants for Android icon stamping. On macOS, selects explicit bold font files (e.g. `Arial Bold.ttf`); on Linux, prefers `DejaVuSans-Bold.ttf` and `LiberationSans-Bold.ttf`.
- CLI `--safe-width-pct` argument — confines the label text to a centered sub-region of the band. When `< 100`, the font is auto-shrunk (0.6 × fontsize per character estimate) until the label fits within the safe area. The colored band itself remains full-width.

### Fixed
- **Circle launcher text clipping** — the "I" and "L" in labels like "INTERNAL" were cut off on circle-masked homescreen icons. Two root causes fixed:
  - `ic_launcher_round` legacy icons now stamp with `bottomInsetPct=10` (lifted from 0) to keep the band in the wider part of the circular mask. The task also computes and passes `--safe-width-pct` based on the circle geometry at the band center, so auto-shrink engages for unusually long labels.
  - `ic_launcher_foreground` (adaptive icon) now passes `--safe-width-pct` derived from the 66dp-diameter circle safe zone inscribed in the 108dp adaptive canvas. The XML overlay path (vector foreground) applies the same constraint via the Kotlin-level banner PNG generation.

## [0.1.2] — 2026-06-01

### Added
- `iosOutputDir` extension property — redirect iOS outputs (`app-icon-banner.config` + `scripts/app-icon-banner`) to any directory relative to the module root. KMP projects can now set `iosOutputDir = "../iosApp"` instead of importing the internal `ExportIosBannerConfigTask` class.
- Linux font auto-detection for Android icon stamping. DejaVu, Liberation, FreeSans, and Ubuntu fonts are now probed automatically on Linux, enabling `assembleDebug` on GitHub Actions `ubuntu-latest` without manual `--font` configuration.
- Label length cap: labels longer than 100 characters now throw an `IllegalArgumentException` at DSL configuration time with a clear message, rather than producing nonsensical ImageMagick output.
- `ExportIosBannerConfigTask` warns when no banner configurations are found and the exported config file will be empty.
- `docs/configuration.md`: Android icon variants table documenting which icon files are stamped and why monochrome icons are intentionally skipped (Material You / Android 13+ compatibility).
- `CONTRIBUTING.md`: testing strategy table and `onVariants`/ProjectBuilder limitation explanation.

### Changed
- `exportIosBannerConfig` output paths now use `convention()` instead of `set()`, so task-level overrides via `tasks.named<ExportIosBannerConfigTask>` still take precedence for unusual project layouts.
- `StampAndroidIconsTask` resolves the font path before invoking the bundled CLI and passes it explicitly via `--font`, ensuring consistent font resolution behavior across macOS and Linux.
- `androidResDir` now rejects absolute paths and paths containing `..` at configuration time, symmetric with the existing `iosOutputDir` guard.
- `CLI_RESOURCE` constant consolidated into a shared `PluginConstants.kt` (was duplicated between `ExportIosBannerConfigTask` and `StampAndroidIconsTask`).
- README restructured: iOS setup walkthrough moved to `docs/ios-setup.md`; DSL reference moved to `docs/configuration.md`.

### Fixed
- Android banner stamping on Linux CI would fail silently (no font found) because the font candidate list was macOS-only. Both the Kotlin task and the bundled bash CLI now include Linux font paths.

## [0.1.0] — 2026-05-28

### Added
- Initial release of `io.github.nkrebs13.app-icon-banner` Gradle plugin.
- `appIconBanner { }` DSL with `buildType`, `flavor`, `variant`, `iosConfiguration` blocks and a `debugDefault` toggle.
- Android: uses ImageMagick (same renderer as iOS) for visual consistency across platforms.
  Stamps all launcher icon density buckets (`mipmap-{mdpi|hdpi|xhdpi|xxhdpi|xxxhdpi}/`):
  - `ic_launcher.{png,webp}` — legacy launcher icon, flat geometry (no bottom inset).
  - `ic_launcher_round.{png,webp}` — round launcher icon, same flat geometry.
  - `ic_launcher_foreground.{png,webp}` — adaptive icon foreground, stamped inside the
    72/108 dp safe zone (22% height, 20% bottom inset) to survive all launcher mask shapes.
  - `ic_launcher_monochrome.{png,webp}` — copied without stamping; the launcher applies a
    wallpaper-derived solid tint at display time, making a banner invisible.
  Generated into `build/` via `variant.sources.res?.addGeneratedSourceDirectory` (highest AGP
  merge priority). No committed files, no gitignore needed.
- iOS: `exportIosBannerConfig` task installs a bash + ImageMagick CLI and writes `app-icon-banner.config`. The CLI is idempotent (regenerates from a pristine `*-base.appiconset`) and forces sRGB/TrueColor so grayscale source icons render a colored band.
- KMP multi-module support: override `exportIosBannerConfig` output paths to place files next to `iosApp/`.
- Three-tier `debug` / `internal` / `release` recipe with `applicationIdSuffix` for side-by-side device install.
- iOS squircle-safe defaults: band height 18%, bottom inset 8% — tested on iPhone 17 simulator with no descender clipping.
- CLI tuning flags: `--height-pct`, `--bottom-inset-pct`, `--text-pct`, `--font`.
- Color validation in both Kotlin DSL (fail-fast at configuration time) and bash CLI (catches corrupt config values before handing them to ImageMagick).
- Binary Compatibility Validator baseline for API surface enforcement.
- GitHub Actions: build + test on Ubuntu + macOS; publish to Gradle Plugin Portal on `v*.*.*` tags.

[0.1.2]: https://github.com/nkrebs13/app-icon-banner/releases/tag/v0.1.2
[0.1.1]: https://github.com/nkrebs13/app-icon-banner/releases/tag/v0.1.1
[0.1.0]: https://github.com/nkrebs13/app-icon-banner/releases/tag/v0.1.0
