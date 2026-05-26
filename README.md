# App Icon Banner

A Gradle plugin that stamps a **color + label banner** onto your app icons per build variant —
on **both Android and iOS** — from a single DSL. Tell debug builds apart from release at a glance on
the home screen.

- **Android:** wraps the excellent [easylauncher](https://github.com/usefulness/easylauncher-gradle-plugin)
  plugin, rendering one ribbon per variant.
- **iOS:** ships a small CLI you wire into an Xcode "Run Script" build phase; it stamps the icon set
  for the current Xcode configuration.

One `appIconBanner { }` block drives both platforms.

## Requirements

- Gradle 8.4+, Android Gradle Plugin 8.4+, JDK 17
- iOS stamping needs a Freetype-enabled [ImageMagick](https://imagemagick.org) on the build machine
  (`brew install imagemagick`)

## Install

Apply the plugin in your Android application module, **after** the Android application plugin:

```kotlin
// app/build.gradle.kts (or app/android/build.gradle.kts)
plugins {
    id("com.android.application")
    id("io.github.nkrebs13.app-icon-banner") version "0.1.0"
}
```

That's it for the default behaviour: **debug builds get a blue `DEBUG` banner, release builds stay
clean.** No configuration required.

## Configuration

```kotlin
appIconBanner {
    // Applies to every variant of this build type (e.g. phoneDebug + metaDebug)
    buildType("debug") { color = "#0288D1"; label = "DEBUG" }

    // Applies to every variant of this flavor (e.g. metaDebug + metaRelease)
    flavor("meta") { color = "#FF6F00"; label = "META" }

    // Applies to exactly one variant — most specific, wins over flavor/buildType
    variant("metaDebug") { color = "#7B1FA2"; label = "META·DBG" }

    // Disable the built-in debug default entirely
    // debugDefault = false

    // iOS only: when an Xcode configuration name doesn't match an Android build type
    // (e.g. a custom "Firebase" configuration), map it explicitly:
    iosConfiguration("Firebase") { color = "#FF6F00"; label = "FIREBASE" }
}
```

### Resolution order

For each variant the **most specific** match wins — banners never stack:

`variant` > `flavor` > `buildType` > built-in debug default > *(no banner)*

- `color` defaults to `#0288D1` (blue) when omitted.
- `label` defaults to the slot name (e.g. `"debug"`) when omitted.

## iOS setup

The Android side is automatic. iOS needs a one-time wiring (icons are built by Xcode, not Gradle).

1. **Split the icon set into a pristine base.** Copy your committed
   `AppIcon.appiconset` to `AppIcon-base.appiconset` and gitignore the generated output set:

   ```bash
   cd YourApp/Assets.xcassets
   cp -R AppIcon.appiconset AppIcon-base.appiconset
   echo "AppIcon.appiconset/" >> ../../.gitignore   # adjust path to your repo
   git add AppIcon-base.appiconset
   ```

   The CLI regenerates `AppIcon.appiconset` from `AppIcon-base.appiconset` on every build, so it is
   **idempotent** — re-running never double-stamps.

2. **Export the config + CLI** from your Android module:

   ```bash
   ./gradlew exportIosBannerConfig
   ```

   This writes `app-icon-banner.config` and installs `scripts/app-icon-banner`. Commit both.

3. **Add a Run Script build phase** in Xcode (target → Build Phases → + → New Run Script Phase),
   placed **before** "Copy Bundle Resources", with "Based on dependency analysis" unchecked:

   ```bash
   "${SRCROOT}/scripts/app-icon-banner" \
       --config "$CONFIGURATION" \
       --appiconset "$SRCROOT/YourApp/Assets.xcassets/AppIcon.appiconset"
   ```

The script reads `$CONFIGURATION` (e.g. `Debug`, `Release`, `Firebase`), looks it up in
`app-icon-banner.config`, and stamps accordingly. Configurations with no entry are left pristine.

### CLI options

```
app-icon-banner --config <name> --appiconset <path/AppIcon.appiconset>
                [--base <path/AppIcon-base.appiconset>]   # default: <name>-base.appiconset
                [--config-file <app-icon-banner.config>]  # default: $SRCROOT/app-icon-banner.config
                [--font <path-to.ttf>]                    # default: a macOS system font
```

## How it works

| | Android | iOS |
|---|---|---|
| Renderer | easylauncher (Java2D) | ImageMagick CLI |
| When | during the variant's resource generation | Xcode "Run Script" phase |
| Source of truth | `appIconBanner { }` | same DSL, exported to `app-icon-banner.config` |
| Idempotency | build intermediates (automatic) | regenerated from `*-base.appiconset` |

## Publishing (maintainers)

Plugin Portal credentials live in `~/.gradle/gradle.properties`:

```properties
gradle.publish.key=...
gradle.publish.secret=...
```

```bash
./gradlew publishPlugins
```

## Roadmap

- **v2:** a Swift Package Manager build-tool plugin that vendors the same CLI, removing the manual
  Xcode Run Script step for SPM-based iOS apps.

## License

MIT — see [LICENSE](LICENSE).
