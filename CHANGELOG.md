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

### Docs (post-initial-dogfood)
- Added the three-tier `debug` / `internal` / `release` recipe (with `applicationIdSuffix`
  for side-by-side install and the `matchingFallbacks` gotcha that always bites when a new
  buildType is introduced).
- Added a KMP / multi-module recipe for redirecting `exportIosBannerConfig` outputs to
  `iosApp/` instead of the applying Android module's directory.

### Planned
- Swift Package Manager build-tool plugin (vendoring the same CLI) to remove the manual Xcode Run
  Script step.
