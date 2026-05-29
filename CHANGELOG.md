# Changelog

## [Unreleased]

## [0.1.0] — 2026-05-28

### Added
- Initial release of `io.github.nkrebs13.app-icon-banner` Gradle plugin.
- `appIconBanner { }` DSL with `buildType`, `flavor`, `variant`, `iosConfiguration` blocks and a `debugDefault` toggle.
- Android: wraps `com.starter.easylauncher` 6.4.1, rendering one resolved ribbon per variant (no flavor+buildType stacking).
- iOS: `exportIosBannerConfig` task installs a bash + ImageMagick CLI and writes `app-icon-banner.config`. The CLI is idempotent (regenerates from a pristine `*-base.appiconset`) and forces sRGB/TrueColor so grayscale source icons render a colored band.
- KMP multi-module support: override `exportIosBannerConfig` output paths to place files next to `iosApp/`.
- Three-tier `debug` / `internal` / `release` recipe with `applicationIdSuffix` for side-by-side device install.
- iOS squircle-safe defaults: band height 18%, bottom inset 8% — tested on iPhone 17 simulator with no descender clipping.
- CLI tuning flags: `--height-pct`, `--bottom-inset-pct`, `--text-pct`, `--font`.
- Color validation in both Kotlin DSL (fail-fast at configuration time) and bash CLI (catches corrupt config values before handing them to ImageMagick).
- Binary Compatibility Validator baseline for API surface enforcement.
- GitHub Actions: build + test on Ubuntu + macOS; publish to Gradle Plugin Portal on `v*.*.*` tags.

[0.1.0]: https://github.com/nkrebs13/app-icon-banner/releases/tag/v0.1.0
