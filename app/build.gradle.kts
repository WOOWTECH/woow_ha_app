plugins {
    alias(libs.plugins.homeassistant.android.application)
    alias(libs.plugins.homeassistant.android.flavor)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.google.services)
    alias(libs.plugins.homeassistant.android.dependencies)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    useLibrary("android.car")

    defaultConfig {
        buildConfigField("boolean", "IS_AUTOMOTIVE_APP", "false")

        bundle {
            language {
                // We want to keep the translations in the final AAB for all the language
                enableSplit = false
            }
        }
    }

    lint {
        // Until we fully migrate to Material3 this lint issue is too verbose https://github.com/home-assistant/android/issues/5420
        disable += listOf("UsingMaterialAndMaterial3Libraries")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        // Phone release marketing version, independent of Reckon and flavor suffixes.
        variant.outputs.forEach { output ->
            output.versionName.set("1.0")
        }
    }
}

firebaseAppDistributionDefault {
    serviceCredentialsFile = "firebaseAppDistributionServiceCredentialsFile.json"
    releaseNotesFile = "./app/build/outputs/changelogBeta"
    groups = "continuous-deployment"
}

dependencies {
    // Most of the dependencies are coming from the convention plugin to avoid duplication with `:automotive` module.
    "fullImplementation"(libs.car.projected)
}

// Disable to fix memory leak and be compatible with the configuration cache.
googleServices {
    disableVersionCheck = true
}

// Only full variants use Firebase; minimal has no registered Google client.
// Keep the plugin's full-variant processing without requiring a fake minimal mapping.
tasks.withType<com.google.gms.googleservices.GoogleServicesTask>().configureEach {
    if (name.startsWith("processMinimal")) {
        enabled = false
    }
}
