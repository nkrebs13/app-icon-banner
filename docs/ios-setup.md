# iOS Setup

The iOS banner is stamped by a bash + ImageMagick CLI at Xcode build time. The setup is a one-time operation.

## Step 1 — Split your icon set into a pristine base

```bash
cd YourApp/Assets.xcassets
cp -R AppIcon.appiconset AppIcon-base.appiconset
echo "YourApp/Assets.xcassets/AppIcon.appiconset/" >> ../../.gitignore
git add AppIcon-base.appiconset
```

The CLI regenerates `AppIcon.appiconset` from `AppIcon-base.appiconset` on every Xcode build — re-running never double-stamps.

## Step 2 — Export the config and CLI

From your Android module root:

```bash
./gradlew exportIosBannerConfig
```

This writes `app-icon-banner.config` and installs the `scripts/app-icon-banner` CLI. Commit both files.

### KMP projects: redirect outputs to `iosApp/`

In a typical KMP project the Android module (`:composeApp` / `:androidApp`) is a sibling of `iosApp/`. Set `iosOutputDir` to redirect exports:

```kotlin
// composeApp/build.gradle.kts
appIconBanner {
    iosOutputDir = "../iosApp"   // relative to this module's root
    buildType("debug")    { color = "#0288D1"; label = "DEBUG" }
    buildType("internal") { color = "#FF6F00"; label = "INTERNAL" }
}
```

After running `exportIosBannerConfig` the config and CLI will be in `iosApp/` where the Xcode target's `$SRCROOT` points.

## Step 3 — Add a Run Script phase in Xcode

In your target: **Build Phases → + → New Run Script Phase**. Place it **before "Copy Bundle Resources"** and **uncheck "Based on dependency analysis"**.

```bash
"${SRCROOT}/scripts/app-icon-banner" \
    --config "$CONFIGURATION" \
    --appiconset "$SRCROOT/YourApp/Assets.xcassets/AppIcon.appiconset"
```

Replace `YourApp/Assets.xcassets/AppIcon.appiconset` with your actual asset catalog path.

## CLI tuning

The default geometry is sized for the iOS squircle mask — the band is inset so no descenders are clipped.

| Flag | Default | Description |
|---|---|---|
| `--height-pct N` | 18 | Band height as % of icon height |
| `--bottom-inset-pct N` | 8 | Band bottom edge sits this % above icon bottom (clears squircle curve) |
| `--text-pct N` | 55 | Text point size as % of band height |
| `--font <path>` | auto | Explicit TTF/TTC path; macOS system fonts used by default |

Example — taller band that clears an aggressive squircle mask:

```bash
"${SRCROOT}/scripts/app-icon-banner" \
    --config "$CONFIGURATION" \
    --appiconset "$SRCROOT/YourApp/Assets.xcassets/AppIcon.appiconset" \
    --height-pct 20 \
    --bottom-inset-pct 12
```
