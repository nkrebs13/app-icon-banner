# Contributing

## Prerequisites

- **JDK 17** — auto-provisioned by the Gradle toolchain (via [Foojay Disco](https://foojay.io/)); no manual install required on most machines.
- **ImageMagick with Freetype** — required only to run the CLI smoke test. Install on macOS:
  ```bash
  brew install imagemagick
  ```

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build
```

This compiles, validates the plugin descriptor, and runs all unit tests (the CLI smoke test is excluded from the default run).

## Tests

```bash
# Unit tests + integration tests (no ImageMagick needed)
./gradlew test

# CLI smoke test — macOS only, requires ImageMagick with Freetype
./gradlew cliSmokeTest
```

The `CliSmokeTest` is tagged `cli-smoke` and excluded from `./gradlew test` by default. CI runs it on `macos-latest` after installing ImageMagick.

## Testing strategy

| File | Scope | Notes |
|---|---|---|
| `AppIconBannerExtensionTest` | DSL resolution logic, input validation | Pure unit — no Gradle wiring |
| `AppIconBannerPluginTest` | Plugin registration, task wiring via `ProjectBuilder` | Configuration-phase only |
| `CliSmokeTest` | End-to-end CLI invocation, stamp output, idempotency | Requires ImageMagick; tagged `cli-smoke` |

**Why `AppIconBannerPluginTest` skips Android variant wiring:** `onVariants { }` is an AGP lifecycle hook that fires during the execution phase. `ProjectBuilder` exercises configuration only — it never fires `onVariants`. As a result, `StampAndroidIconsTask` registration cannot be unit-tested with `ProjectBuilder`; it requires a real `./gradlew assemble` in a consumer Android or KMP project to verify.

## API changes

This project uses [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) to catch unintentional breaking changes. If you intentionally change the public API of `AppIconBannerExtension` or `AppIconBannerPlugin`, regenerate the baseline after your change:

```bash
./gradlew apiDump
```

Commit the updated `api/app-icon-banner.api`. The `apiCheck` task (part of `./gradlew build`) will fail if the public API drifts from the committed baseline without an explicit `apiDump`.

## Publishing

Plugin Portal credentials go in `~/.gradle/gradle.properties` (`gradle.publish.key` / `gradle.publish.secret`). See `CLAUDE.md` for details. Credentials are not part of this repository.

## PR checklist

- [ ] `./gradlew build` passes (all non-CLI tests green, `apiCheck` passes)
- [ ] `./gradlew cliSmokeTest` passes on macOS (if CLI was modified)
- [ ] CHANGELOG.md updated under `[Unreleased]`
- [ ] `./gradlew apiDump` run and committed if public API changed
- [ ] README version number matches `version =` in `build.gradle.kts` (required on every release)
