# App Icon Banner — Gradle Plugin (Android + iOS)

A JVM-only Gradle plugin that stamps a color+label banner onto app icons per build variant. Android
wraps `com.starter.easylauncher`; iOS ships a bash+ImageMagick CLI wired into an Xcode Run Script.

## Module map

```
build.gradle.kts                  — kotlin-dsl + java-gradle-plugin + com.gradle.plugin-publish; group io.github.nkrebs13
settings.gradle.kts               — foojay-resolver (auto-provisions JDK 17 toolchain)
src/main/kotlin/io/github/nkrebs13/appiconbanner/
  AppIconBannerPlugin.kt          — entry point; registers onVariants BEFORE applying easylauncher
  AppIconBannerExtension.kt       — appIconBanner { } DSL + resolveAndroid/resolveIos/iosConfigLines
  BannerConfig.kt                 — resolved (color, label)
  ios/ExportIosBannerConfigTask.kt — writes app-icon-banner.config + installs the CLI from resources
src/main/resources/app-icon-banner — the iOS stamping CLI (bash + ImageMagick), bundled in the jar
src/test/kotlin/...                — ExtensionTest (logic), PluginTest (ProjectBuilder), CliSmokeTest
```

## Build / test

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build      # compile + validatePlugins + tests
./gradlew test       # 12 tests; CliSmokeTest self-skips if ImageMagick lacks Freetype
```

Plugin id: `io.github.nkrebs13.app-icon-banner`. Implementation class: `…appiconbanner.AppIconBannerPlugin`.

## Key design decisions

- **Own the Android resolution, write to easylauncher's `variants` override slot.** Our `onVariants`
  callback is registered *before* `plugins.apply("com.starter.easylauncher")` so ours runs first per
  variant. We resolve one `BannerConfig` (variant > flavor > buildType > debug default) and write a
  single `ColorRibbonFilter` — this prevents easylauncher's additive flavor+buildType stacking.
- **AGP is `compileOnly`** (consumers bring it) but **`testImplementation`** too, so ProjectBuilder
  tests can load `AndroidComponentsExtension` (else `findByType` throws `NoClassDefFoundError`).
- **iOS CLI is bundled as a jar resource** (`/app-icon-banner`) and copied into the consumer's
  `scripts/`. Single source; the future SPM plugin vendors the same file.
- **CLI correctness:** `-colorspace sRGB -type TrueColor` on the base (grayscale icons would collapse
  the band to gray); `-strip` for byte-idempotency; explicit `--font` path (macOS ImageMagick often
  lacks fontconfig, so font-by-name fails); regenerates from `*-base.appiconset` so re-runs never
  double-stamp; reads `identify -format '%w %h\n'` with a trailing newline (else `set -e` aborts).

## Compatibility pins

- easylauncher `6.4.1` (builds against AGP 8.9.2 / Gradle 8.14 — compatible with consumers on AGP
  8.7–8.12+). The plugin's own build runs on Gradle 8.14.
- AGP API `compileOnly` baseline: 8.7.3.

## Publishing

Portal keys in `~/.gradle/gradle.properties` (`gradle.publish.key` / `gradle.publish.secret`), then
`./gradlew publishPlugins`. Do not commit keys.
