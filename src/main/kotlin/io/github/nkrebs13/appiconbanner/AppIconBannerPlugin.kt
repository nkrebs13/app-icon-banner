package io.github.nkrebs13.appiconbanner

import com.android.build.api.variant.AndroidComponentsExtension
import io.github.nkrebs13.appiconbanner.android.StampAndroidIconsTask
import io.github.nkrebs13.appiconbanner.ios.ExportIosBannerConfigTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Wires the `appIconBanner { }` DSL to:
 *  - Android: a [StampAndroidIconsTask] per variant that stamps icons with ImageMagick and
 *    injects the results as a generated resource directory (highest merge priority). No
 *    easylauncher dependency; same ImageMagick renderer as iOS for visual consistency.
 *  - iOS: the [ExportIosBannerConfigTask], which installs the stamping CLI + its config file.
 *
 * Apply AFTER the Android application plugin in a KMP app's Android module. The iOS export task
 * is always available; Android wiring activates only when an Android plugin is present.
 */
class AppIconBannerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, AppIconBannerExtension::class.java)

        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
        if (androidComponents != null) {
            androidComponents.onVariants { variant ->
                val config = extension.resolveAndroid(
                    variantName = variant.name,
                    flavorNames = variant.productFlavors.map { it.second },
                    buildTypeName = variant.buildType,
                ) ?: return@onVariants

                val taskProvider = project.tasks.register<StampAndroidIconsTask>(
                    "stamp${variant.name.replaceFirstChar(Char::uppercaseChar)}AndroidIcons",
                ) {
                    group = "app icon banner"
                    description = "Stamps the '${variant.name}' launcher icons with the " +
                        "'${config.label}' banner using ImageMagick."
                    sourceResDir.set(resolveAndroidResDir(project, extension))
                    bannerColor.set(config.color)
                    bannerLabel.set(config.label)
                    iconName.set(extension.androidIconName)
                    // Lowercase variant name used for generated resource names (must be a valid
                    // Android resource name: lowercase letters, digits, underscores only).
                    variantName.set(variant.name.lowercase().replace(Regex("[^a-z0-9]"), "_"))
                    outputDir.set(
                        project.layout.buildDirectory.dir(
                            "generated/app-icon-banner/${variant.name}/res",
                        ),
                    )
                }

                variant.sources.res?.addGeneratedSourceDirectory(
                    taskProvider,
                    StampAndroidIconsTask::outputDir,
                )
            }
        }

        registerIosExportTask(project, extension)
    }

    /**
     * Resolves the Android `res/` source directory. Checks the extension override first, then
     * auto-detects by probing the two canonical KMP + traditional Android conventions:
     * - `src/androidMain/res` — Compose Multiplatform (JetBrains template)
     * - `src/main/res` — traditional Android module or KMM template
     */
    private fun resolveAndroidResDir(project: Project, extension: AppIconBannerExtension) =
        project.layout.projectDirectory.dir(
            extension.androidResDir
                ?: listOf("src/androidMain/res", "src/main/res")
                    .firstOrNull { project.layout.projectDirectory.dir(it).asFile.exists() }
                ?: "src/main/res",
        )

    private fun registerIosExportTask(project: Project, extension: AppIconBannerExtension) {
        project.tasks.register<ExportIosBannerConfigTask>(EXPORT_IOS_TASK) {
            group = "app icon banner"
            description =
                "Export the iOS banner config (app-icon-banner.config) and install the stamping CLI."
            configLines.set(project.provider { extension.iosConfigLines() })
            outputConfig.set(project.layout.projectDirectory.file("app-icon-banner.config"))
            outputCli.set(project.layout.projectDirectory.file("scripts/app-icon-banner"))
        }
    }

    companion object {
        const val EXTENSION_NAME = "appIconBanner"
        const val EXPORT_IOS_TASK = "exportIosBannerConfig"
    }
}
