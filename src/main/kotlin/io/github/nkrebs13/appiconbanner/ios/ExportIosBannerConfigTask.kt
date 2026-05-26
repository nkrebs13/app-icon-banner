package io.github.nkrebs13.appiconbanner.ios

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes `app-icon-banner.config` (one `name|color|label` line per iOS configuration) and installs
 * the bundled `app-icon-banner` stamping CLI into the consumer's `scripts/` directory. Both outputs
 * are meant to be committed; the Xcode Run Script phase invokes the installed CLI at build time.
 *
 * Config-cache safe: inputs are plain Strings/files and the action holds no [org.gradle.api.Project]
 * reference. The CLI is loaded from this plugin's jar resources.
 */
abstract class ExportIosBannerConfigTask : DefaultTask() {

    @get:Input
    abstract val configLines: ListProperty<String>

    @get:OutputFile
    abstract val outputConfig: RegularFileProperty

    @get:OutputFile
    abstract val outputCli: RegularFileProperty

    @TaskAction
    fun export() {
        val configFile = outputConfig.get().asFile
        configFile.parentFile?.mkdirs()
        configFile.writeText(configLines.get().joinToString(separator = "\n", postfix = "\n"))

        val cliFile = outputCli.get().asFile
        cliFile.parentFile?.mkdirs()
        val resource = javaClass.getResourceAsStream(CLI_RESOURCE)
            ?: error("Bundled CLI resource $CLI_RESOURCE missing from the plugin jar")
        resource.use { input -> cliFile.outputStream().use { input.copyTo(it) } }
        cliFile.setExecutable(true, false)

        logger.lifecycle(
            "app-icon-banner: wrote ${configLines.get().size} config line(s) to " +
                "${configFile.name} and installed CLI at ${cliFile.path}",
        )
    }

    private companion object {
        const val CLI_RESOURCE = "/app-icon-banner"
    }
}
