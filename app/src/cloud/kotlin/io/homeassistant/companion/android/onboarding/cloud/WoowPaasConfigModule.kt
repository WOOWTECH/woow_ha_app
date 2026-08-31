package io.homeassistant.companion.android.onboarding.cloud

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasApiConfig
import javax.inject.Singleton

/**
 * Supplies the environment the WOOW PaaS client in `:common` talks to.
 *
 * The client itself is bound in `:common`; only the configuration lives here, because the application
 * module is the one that knows which environment this build targets.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WoowPaasConfigModule {

    @Provides
    @Singleton
    fun providesWoowPaasApiConfig(): WoowPaasApiConfig = WoowPaasApiConfig(
        baseUrl = WoowPaasConfig.BASE_URL,
        clientId = WoowPaasConfig.CLIENT_ID,
        scopes = WoowPaasConfig.SCOPES,
        deviceCodeGrantType = WoowPaasConfig.DEVICE_CODE_GRANT_TYPE,
        refreshTokenGrantType = WoowPaasConfig.REFRESH_TOKEN_GRANT_TYPE,
    )
}
