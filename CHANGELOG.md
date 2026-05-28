# Changelog

## [Unreleased]

### Added
- Initial release: `io.github.nkrebs13.app-icon-banner` Gradle plugin.
- `appIconBanner { }` DSL with `buildType`, `flavor`, `variant`, and `iosConfiguration` blocks; a
  `debugDefault` toggle.
- Android: wraps `com.starter.easylauncher`, rendering one resolved ribbon per variant (no
  flavor+buildType stacking).
- iOS: `exportIosBannerConfig` task installs a bash + ImageMagick CLI and writes
  `app-icon-banner.config`. The CLI is idempotent (regenerates from a pristine `*-base.appiconset`)
  and forces sRGB/TrueColor so grayscale source icons render a colored band.

### CLI (post-initial-dogfood — iOS clipping fix)
- iOS app icons are masked by a squircle; the original 22% band at the bottom edge had letter
  descenders clipped by the curve. Re-tuned defaults to be **squircle-safe out of the box**:
  band height 18% (was 22%), bottom inset 8% (was 0%). Tested with Vector on iPhone 17 simulator
  — DEBUG letters fully visible on the home screen.
- Added `--height-pct`, `--bottom-inset-pct`, `--text-pct` to the CLI for users who want to
  override the new defaults (e.g., flush-bottom band for non-iOS assets, taller band, etc.).

### Docs (post-initial-dogfood)
- Added the three-tier `debug` / `internal` / `release` recipe (with `applicationIdSuffix`
  for side-by-side install and the `matchingFallbacks` gotcha that always bites when a new
  buildType is introduced).
- Added a KMP / multi-module recipe for redirecting `exportIosBannerConfig` outputs to
  `iosApp/` instead of the applying Android module's directory.

### Planned
- Swift Package Manager build-tool plugin (vendoring the same CLI) to remove the manual Xcode Run
  Script step.
