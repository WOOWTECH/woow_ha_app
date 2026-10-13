package io.homeassistant.companion.android.util

import io.homeassistant.companion.android.BuildConfig

/**
 * First-release phone restrictions, independent of stored opt-ins or connected accessories.
 * Automotive shares phone sources but retains its vehicle and fitness integrations. The standalone Wear
 * product does not use this policy.
 */
internal object PhoneIntegrationReleasePolicy {
    val fitnessSensorsEnabled: Boolean = BuildConfig.IS_AUTOMOTIVE_APP
    val vehicleIntegrationEnabled: Boolean = BuildConfig.IS_AUTOMOTIVE_APP
    val wearSetupEnabled: Boolean = false
}
