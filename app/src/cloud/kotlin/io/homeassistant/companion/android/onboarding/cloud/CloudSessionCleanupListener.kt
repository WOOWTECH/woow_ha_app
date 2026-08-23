package io.homeassistant.companion.android.onboarding.cloud

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import io.homeassistant.companion.android.onboarding.ServerRegisteredListener
import java.io.IOException
import javax.inject.Inject
import timber.log.Timber

/**
 * Drops the WOOW PaaS credentials once the server they produced belongs to the application.
 *
 * They only exist to obtain the address of a cloud instance during onboarding: keeping them around
 * afterwards would store credentials nothing reads. A server registered through a local address
 * never had such a session and this is then a no-op.
 *
 * A storage failure is logged rather than propagated. The storage in use today cannot report one,
 * but the guard is deliberate rather than defensive clutter: this runs after the server is
 * registered, so letting a failure out would revert a registration that actually succeeded, only
 * to leave behind credentials nobody reads.
 */
internal class CloudSessionCleanupListener @Inject constructor(
    private val woowPaasSessionRepository: WoowPaasSessionRepository,
) : ServerRegisteredListener {
    override suspend fun onServerRegistered() {
        try {
            woowPaasSessionRepository.clearSession()
        } catch (e: IOException) {
            Timber.e(e, "Failed to discard the WOOW PaaS session after the server was registered")
        }
    }
}

/**
 * Contributes the cleanup listener to the [ServerRegisteredListener] set that
 * `NameYourDeviceViewModel` awaits after a successful registration.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CloudSessionCleanupModule {
    @Binds
    @IntoSet
    abstract fun bindCloudSessionCleanupListener(impl: CloudSessionCleanupListener): ServerRegisteredListener
}
