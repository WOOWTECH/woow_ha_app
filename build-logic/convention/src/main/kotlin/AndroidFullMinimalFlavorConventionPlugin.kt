import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

/**
 * A convention plugin that configures product flavors for Android application modules,
 * creating a `version` flavor dimension with `full` and `minimal` flavors.
 *
 * This plugin automates the setup of common flavor configurations, including:
 * - Defining the `version` flavor dimension.
 * - Creating the `minimal` flavor with the application ID suffix `.minimal` and the version name suffix `-minimal`.
 * - Creating the `full` flavor with no application ID or version name suffix.
 * - Generating a list of all application IDs (including suffixes) into the `BuildConfig` as `APPLICATION_IDS`.
 *
 * This plugin expects the Android Application Gradle plugin to be applied to the project.
 */
class AndroidFullMinimalFlavorConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())

            extensions.getByType<ApplicationExtension>().apply {
                flavorDimensions.add("version")
                productFlavors {
                    create("minimal") {
                        // Explicit dimension: implicit assignment only works while the module has a
                        // single dimension, and :app adds an "edition" dimension on top of this one.
                        dimension = "version"
                        applicationIdSuffix = ".minimal"
                        versionNameSuffix = "-minimal"
                        buildConfigField("Boolean", "IS_FULL", "false")
                    }
                    create("full") {
                        dimension = "version"
                        applicationIdSuffix = ""
                        versionNameSuffix = "-full"
                        // Code must branch on IS_FULL, never on BuildConfig.FLAVOR string compares:
                        // with more than one dimension FLAVOR becomes the combined name
                        // ("fullOnprem"), silently failing every == "full" comparison.
                        buildConfigField("Boolean", "IS_FULL", "true")
                    }

                    // Generate a list of application ids into BuildConfig
                    val values = productFlavors.joinToString {
                        "\"${it.applicationId ?: defaultConfig.applicationId}${it.applicationIdSuffix}\""
                    }

                    defaultConfig.buildConfigField("String[]", "APPLICATION_IDS", "{$values}")
                }
            }
        }
    }
}
