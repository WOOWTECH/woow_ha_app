package io.homeassistant.companion.android.onboarding.cloud

// TODO: Switch BASE_URL to prod when going live
internal object WoowPaasConfig {
    const val BASE_URL = "https://stg.woowtech.io"
    const val CLIENT_ID = "woow-ha-app"
    const val SCOPES = "ha:provision workspace:read smarthome:read"
    const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
}
