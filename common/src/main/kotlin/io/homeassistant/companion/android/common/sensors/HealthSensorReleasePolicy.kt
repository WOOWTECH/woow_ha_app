package io.homeassistant.companion.android.common.sensors

/**
 * First-release policy, not a user preference: stored opt-ins cannot enable these integrations.
 * Keep the implementations for a future reviewed release. Re-enabling also requires restoring
 * the applicable manifest declarations and validating permissions and collection in signed builds.
 */
object HealthSensorReleasePolicy {
    val healthConnectEnabled: Boolean = false
    val healthServicesEnabled: Boolean = false
    val sleepSensorsEnabled: Boolean = false
}
