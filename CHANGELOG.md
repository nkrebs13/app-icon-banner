# Changelog

## [Unreleased]

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

[0.1.0]: https://github.com/nkrebs13/app-icon-banner/releases/tag/v0.1.0
