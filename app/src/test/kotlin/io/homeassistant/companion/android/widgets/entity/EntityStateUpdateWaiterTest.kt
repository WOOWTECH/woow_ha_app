package io.homeassistant.companion.android.widgets.entity

import io.homeassistant.companion.android.common.data.integration.Entity
import java.time.LocalDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EntityStateUpdateWaiterTest {

    @Test
    fun `Given websocket state update arrives then returns updated entity`() = runTest {
        val expected = Entity(
            entityId = "light.test",
            state = "on",
            attributes = emptyMap(),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        )
        val stateUpdate = async {
            delay(500)
            expected
        }

        assertEquals(expected, awaitEntityStateUpdateOrTimeout(stateUpdate, timeoutMillis = 1_000))
        assertEquals(500L, testScheduler.currentTime)
    }

    @Test
    fun `Given websocket state update is missing then times out for fallback refresh`() = runTest {
        val stateUpdate = async<Entity?> { awaitCancellation() }

        assertNull(awaitEntityStateUpdateOrTimeout(stateUpdate, timeoutMillis = 1_000))
        assertEquals(1_000L, testScheduler.currentTime)
        stateUpdate.cancel()
    }
}
