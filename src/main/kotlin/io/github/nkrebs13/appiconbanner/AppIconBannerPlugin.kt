package io.github.nkrebs13.appiconbanner

import com.android.build.api.variant.AndroidComponentsExtension
import com.project.starter.easylauncher.filter.ColorRibbonFilter
import com.project.starter.easylauncher.plugin.EasyLauncherExtension
import io.github.nkrebs13.appiconbanner.ios.ExportIosBannerConfigTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Wires the `appIconBanner { }` DSL to:
 *  - Android: the `com.starter.easylauncher` plugin (bundled), one resolved ribbon per variant.
 *  - iOS: the [ExportIosBannerConfigTask], which installs the stamping CLI + its config file.
 *
 * Apply AFTER the Android application plugin in a KMP app's Android module. The iOS export task is
 * always available; the Android wiring activates only when an Android plugin is present.
 */
class AppIconBannerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(EXTENSION_NAME, AppIconBannerExtension::class.java)

        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
        if (androidComponents != null) {
            // Register our onVariants callback BEFORE applying easylauncher so that, for each
            // variant, ours runs first and populates easylauncher's `variants` override slot —
            // easylauncher then renders exactly that one ribbon (no flavor+buildType stacking).
            androidComponents.onVariants { variant ->
                val config = extension.resolveAndroid(
                    variantName = variant.name,
                    flavorNames = variant.productFlavors.map { it.second },
                    buildTypeName = variant.buildType,
                ) ?: return@onVariants

                val easyLauncher = project.extensions.getByType(EasyLauncherExtension::class.java)
                easyLauncher.variants.maybeCreate(variant.name).setFilters(
                    ColorRibbonFilter(
                        label = config.label,
                        ribbonColor = config.color,
                        gravity = ColorRibbonFilter.Gravity.BOTTOM,
                    ),
                )
            }
            project.plugins.apply(EASYLAUNCHER_PLUGIN_ID)
        }

        registerIosExportTask(project, extension)
    }

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
        private const val EASYLAUNCHER_PLUGIN_ID = "com.starter.easylauncher"
    }
}
