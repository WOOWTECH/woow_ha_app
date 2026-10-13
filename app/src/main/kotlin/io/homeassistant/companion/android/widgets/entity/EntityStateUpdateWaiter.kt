package io.homeassistant.companion.android.widgets.entity

import io.homeassistant.companion.android.common.data.integration.Entity
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

internal const val ENTITY_STATE_UPDATE_TIMEOUT_MS = 5_000L

internal suspend fun awaitEntityStateUpdateOrTimeout(
    stateUpdate: Deferred<Entity?>?,
    timeoutMillis: Long = ENTITY_STATE_UPDATE_TIMEOUT_MS,
): Entity? = stateUpdate?.let { withTimeoutOrNull(timeoutMillis) { it.await() } }
