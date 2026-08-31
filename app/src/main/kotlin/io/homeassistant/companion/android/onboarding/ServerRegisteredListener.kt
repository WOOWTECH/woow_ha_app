package io.homeassistant.companion.android.onboarding

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Hook invoked after a server has been fully registered and activated during onboarding, before
 * the flow moves on.
 *
 * This is the seam through which an edition reacts to a completed registration without the shared
 * onboarding code knowing about it: the cloud edition contributes an implementation that discards
 * its PaaS session (credentials that only existed to obtain the server's address), while the
 * on-premise edition contributes nothing and the injected set stays empty.
 *
 * Contract for implementations: the call is awaited inside the registration transaction, after the
 * server is activated and before onboarding proceeds. Throwing anything other than a recoverable
 * storage failure will propagate and roll the registration back, so implementations must swallow
 * failures that should not undo an otherwise successful registration.
 */
fun interface ServerRegisteredListener {
    suspend fun onServerRegistered()
}

/**
 * Declares the multibound set so that the graph resolves to an empty set when no edition
 * contributes a listener (on-premise, automotive).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ServerRegisteredListenerModule {
    @Multibinds
    abstract fun serverRegisteredListeners(): Set<ServerRegisteredListener>
}
