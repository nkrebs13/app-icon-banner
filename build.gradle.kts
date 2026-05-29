plugins {
    `kotlin-dsl`
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.binary.compat.validator)
}

group = "io.github.nkrebs13"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    compileOnly(libs.agp.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    // AGP types are compileOnly for consumers, but ProjectBuilder tests need them loadable so the
    // plugin's findByType(AndroidComponentsExtension) resolves to null instead of NoClassDefFound.
    testImplementation(libs.agp.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    website = "https://github.com/nkrebs13/app-icon-banner"
    vcsUrl = "https://github.com/nkrebs13/app-icon-banner"
    plugins {
        create("appIconBanner") {
            id = "io.github.nkrebs13.app-icon-banner"
            displayName = "App Icon Banner"
            description =
                "Stamp a color + label banner onto Android and iOS app icons per build variant / " +
                "Xcode configuration. Uses ImageMagick on both platforms for visual consistency."
            tags = listOf("android", "ios", "kmp", "launcher", "icon", "banner", "variant")
            implementationClass = "io.github.nkrebs13.appiconbanner.AppIconBannerPlugin"
        }
    }
}

apiValidation {
    // The Kotlin DSL `register<T>` inline function generates a synthetic SAM adapter for the
    // task configuration Action — an implementation detail, not a public API surface.
    ignoredClasses.add("io.github.nkrebs13.appiconbanner.AppIconBannerPlugin\$apply\$1\$inlined\$sam\$i\$org_gradle_api_Action\$0")
}

// Scope the exclusion only to the default `test` task. Using `withType<Test>().configureEach`
// would also configure `cliSmokeTest`, causing a tag include+exclude collision where JUnit
// Platform resolves the tag as excluded (exclude wins) and silently runs 0 tests.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("cli-smoke")
    }
}

tasks.register<Test>("cliSmokeTest") {
    group = "verification"
    description = "Runs the CLI smoke test (requires Freetype-enabled ImageMagick on PATH)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("cli-smoke") }
}
