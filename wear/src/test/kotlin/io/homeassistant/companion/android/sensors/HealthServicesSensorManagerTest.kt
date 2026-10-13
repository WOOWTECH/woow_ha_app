package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthServicesSensorManagerTest {
    private val context = mockk<Context>()
    private val manager = HealthServicesSensorManager()

    @Test
    fun `Given release disabled when discovering or requesting permissions then no SDK context is accessed`() = runTest {
        assertFalse(manager.enabledByRelease)
        assertFalse(manager.hasSensor(context))
        assertTrue(manager.getAvailableSensors(context).isEmpty())
        assertTrue(manager.requiredPermissions(context, "daily_steps").isEmpty())
        assertFalse(manager.checkPermission(context, "daily_steps"))
        verify { context wasNot Called }
    }

    @Test
    fun `Given release disabled when updating stale daily steps then no SDK or storage is accessed`() = runTest {
        val sensor = SensorManager.BasicSensor("daily_steps", "sensor", enabledByDefault = true)
        assertFalse(manager.isEnabled(context, sensor))
        assertFalse(manager.isEnabled(context, sensor, serverId = 1))
        assertTrue(manager.getEnabledServers(context, sensor).isEmpty())
        manager.requestSensorUpdate(context)
        manager.requestSensorUpdate(context, intent = null)
        manager.onSensorUpdated(context, sensor, 123, "mdi:shoe-print", emptyMap())
        verify { context wasNot Called }
    }
}
