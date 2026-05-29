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

## API changes

This project uses [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) to catch unintentional breaking changes. If you intentionally change the public API of `AppIconBannerExtension` or `AppIconBannerPlugin`, regenerate the baseline after your change:

```bash
./gradlew apiDump
```

Commit the updated `api/app-icon-banner.api`. The `apiCheck` task (part of `./gradlew build`) will fail if the public API drifts from the committed baseline without an explicit `apiDump`.

## Publishing

See [Publishing](README.md#publishing-maintainers) in the README. Plugin Portal credentials are required and are not part of this repository.

## PR checklist

- [ ] `./gradlew build` passes (all non-CLI tests green, `apiCheck` passes)
- [ ] `./gradlew cliSmokeTest` passes on macOS (if CLI was modified)
- [ ] CHANGELOG.md updated under `[Unreleased]`
- [ ] `./gradlew apiDump` run and committed if public API changed
