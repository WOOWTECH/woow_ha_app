package io.homeassistant.companion.android.onboarding.cloud

import io.homeassistant.companion.android.BuildConfig

/**
 * WOOW PaaS environment configuration. [BASE_URL], [CLIENT_ID] and [SCOPES] all come from the
 * [BuildConfig] fields injected per variant (see `AndroidEditionFlavorConventionPlugin` in
 * build-logic): debug talks to staging, release to production, so switching environments never
 * requires a code change. These fields only exist on cloud variants.
 */
internal object WoowPaasConfig {
    const val BASE_URL = BuildConfig.WOOW_PAAS_BASE_URL
    const val CLIENT_ID = BuildConfig.WOOW_PAAS_CLIENT_ID
    const val SCOPES = BuildConfig.WOOW_PAAS_SCOPES
    const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
    const val REFRESH_TOKEN_GRANT_TYPE = "refresh_token"
}
