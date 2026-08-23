package io.homeassistant.companion.android.common.data.woowpaas

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.LocalStorageImpl
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.woowpaas.impl.WoowPaasRepositoryImpl
import io.homeassistant.companion.android.common.data.woowpaas.impl.WoowPaasSessionRepositoryImpl
import io.homeassistant.companion.android.common.util.getSharedPreferencesSuspend
import javax.inject.Singleton

/** Name of the shared preferences file the WOOW PaaS session is stored in. */
private const val WOOW_PAAS_PREFERENCES_NAME = "woow_paas_0"

/**
 * Binds the WOOW PaaS repositories to their implementations.
 *
 * The environment they talk to is not bound here: a [WoowPaasApiConfig] is expected from the hosting
 * application, which owns the staging and production values. Applications that never inject a
 * [WoowPaasRepository] therefore do not need to provide one.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WoowPaasModule {

    companion object {
        @Provides
        @NamedWoowPaasStorage
        @Singleton
        fun provideWoowPaasLocalStorage(@ApplicationContext appContext: Context): LocalStorage = LocalStorageImpl {
            appContext.getSharedPreferencesSuspend(WOOW_PAAS_PREFERENCES_NAME)
        }
    }

    // Scoped so the lazily built HTTP stack inside the implementation is created at most once.
    @Binds
    @Singleton
    abstract fun bindWoowPaasRepository(impl: WoowPaasRepositoryImpl): WoowPaasRepository

    // Scoped so every caller goes through the same mutex when the session is rotated.
    @Binds
    @Singleton
    abstract fun bindWoowPaasSessionRepository(impl: WoowPaasSessionRepositoryImpl): WoowPaasSessionRepository
}
