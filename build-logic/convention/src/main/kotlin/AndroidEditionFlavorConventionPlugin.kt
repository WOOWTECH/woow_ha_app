import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

// The cloud edition is a distinct app identity that existing installs already update from.
// Never fold it back into a suffix of the on-premise id.
private const val CLOUD_APPLICATION_ID = "com.woowtech.homecloud"

// WOOW PaaS endpoints for the cloud onboarding flow. Debug talks to staging, release to
// production. These exist only on cloud variants: the on-premise APK must not even contain
// the endpoint strings.
private const val WOOW_PAAS_BASE_URL_DEBUG = "https://stg.woowtech.io"
private const val WOOW_PAAS_BASE_URL_RELEASE = "https://paas.woowtech.io"
private const val WOOW_PAAS_CLIENT_ID = "woow-ha-app"
private const val WOOW_PAAS_SCOPES = "ha:provision workspace:read smarthome:read"

/**
 * Adds the `edition` flavor dimension to the application module: `onprem` (the on-premise app,
 * `com.woowtech.home`) and `cloud` (`com.woowtech.homecloud`, with the WOOW PaaS cloud
 * onboarding flow compiled in from the `cloud` source set).
 *
 * This plugin must only be applied to `:app`. `:automotive` shares the
 * [AndroidFullMinimalFlavorConventionPlugin] and must stay single-dimension, which is why the
 * edition dimension lives in its own plugin instead of the shared one.
 *
 * Everything edition-specific that varies per variant is injected here through the variant API
 * rather than the flavor DSL, because the values depend on the build type (PaaS endpoints) or
 * must override a default set by another plugin (`APPLICATION_IDS`).
 */
class AndroidEditionFlavorConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.getByType<ApplicationExtension>().apply {
                flavorDimensions.add("edition")
                productFlavors {
                    create("onprem") {
                        dimension = "edition"
                        // applicationId stays com.woowtech.home from defaultConfig.
                    }
                    create("cloud") {
                        dimension = "edition"
                        applicationId = CLOUD_APPLICATION_ID
                    }
                }
            }

            extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
                val isCloud = variant.productFlavors.contains("edition" to "cloud")
                if (!isCloud) return@onVariants

                val baseUrl = if (variant.buildType == "debug") {
                    WOOW_PAAS_BASE_URL_DEBUG
                } else {
                    WOOW_PAAS_BASE_URL_RELEASE
                }
                variant.buildConfigFields?.apply {
                    put("WOOW_PAAS_BASE_URL", BuildConfigField("String", "\"$baseUrl\"", null))
                    put("WOOW_PAAS_CLIENT_ID", BuildConfigField("String", "\"$WOOW_PAAS_CLIENT_ID\"", null))
                    put("WOOW_PAAS_SCOPES", BuildConfigField("String", "\"$WOOW_PAAS_SCOPES\"", null))

                    // The shared flavor plugin generates APPLICATION_IDS from the on-premise base
                    // id at configuration time, before this dimension exists. That default is
                    // correct for onprem and automotive; cloud variants override it here so NFC
                    // records point at the cloud app and the on-premise ids never leak into it.
                    put(
                        "APPLICATION_IDS",
                        BuildConfigField(
                            "String[]",
                            "{\"$CLOUD_APPLICATION_ID.minimal\", \"$CLOUD_APPLICATION_ID\"}",
                            null,
                        ),
                    )
                }
            }
        }
    }
}
