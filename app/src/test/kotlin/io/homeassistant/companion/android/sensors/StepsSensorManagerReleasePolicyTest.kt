package io.homeassistant.companion.android.sensors

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.sensors.PressureSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import io.homeassistant.companion.android.database.sensor.Sensor as SensorEntity
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.util.PhoneIntegrationReleasePolicy
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class StepsSensorManagerReleasePolicyTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given stale enabled phone steps when called directly then no hardware permission or stored data is accessed`() = runTest {
        val context = mockk<Context>()
        val dao = mockk<SensorDao>(relaxed = true)
        val entryPoint = mockk<SensorManager.SensorManagerEntryPoint>()
        val stored = SensorEntity("steps_sensor", serverId = 1, enabled = true, registered = true, state = "123")
        mockkStatic(EntryPointAccessors::class)
        every { context.applicationContext } returns context
        every {
            EntryPointAccessors.fromApplication(context, SensorManager.SensorManagerEntryPoint::class.java)
        } returns entryPoint
        every { entryPoint.sensorDao() } returns dao
        coEvery { dao.get("steps_sensor") } returns listOf(stored)
        val manager = StepsSensorManager(enabledByRelease = PhoneIntegrationReleasePolicy.fitnessSensorsEnabled)
        val sensor = SensorManager.BasicSensor("steps_sensor", "sensor", enabledByDefault = true)

        assertFalse(manager.enabledByRelease)
        assertFalse(manager.hasSensor(context))
        assertTrue(manager.getAvailableSensors(context).isEmpty())
        assertTrue(manager.requiredPermissions(context, sensor.id).isEmpty())
        assertFalse(manager.checkPermission(context, sensor.id))
        assertFalse(manager.isEnabled(context, sensor))
        assertFalse(manager.isEnabled(context, sensor, serverId = 1))
        assertTrue(manager.getEnabledServers(context, sensor).isEmpty())
        manager.requestSensorUpdate(context)
        manager.requestSensorUpdate(context, intent = null)
        manager.onSensorUpdated(context, sensor, "456", "mdi:walk", emptyMap())

        verify { context wasNot Called }
        verify { dao wasNot Called }
        assertTrue(stored.enabled && stored.registered == true)
        assertEquals("123", stored.state)
    }

    @Test
    fun `Given direct phone step callback before initialization when delivered then no event access or write occurs`() {
        val manager = spyk(StepsSensorManager(enabledByRelease = PhoneIntegrationReleasePolicy.fitnessSensorsEnabled))
        val event = mockk<SensorEvent>()

        manager.onSensorChanged(event)
        manager.onSensorChanged(null)
        manager.onAccuracyChanged(null, 0)

        verify { event wasNot Called }
        coVerify(exactly = 0) { manager.onSensorUpdated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Given independent product steps policy when discovering then hardware availability is retained`() = runTest {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) } returns true
        val manager = StepsSensorManager(enabledByRelease = true)

        assertTrue(manager.enabledByRelease)
        assertTrue(manager.hasSensor(context))
        assertEquals(listOf("steps_sensor"), manager.getAvailableSensors(context).map { it.id })
        assertTrue(manager.requiredPermissions(context, "steps_sensor").isNotEmpty())
    }

    @Test
    fun `Given ordinary barometer enabled when updating then hardware subscription and stored measurement still work`() = runTest {
        val context = mockk<Context>()
        val hardware = mockk<android.hardware.SensorManager>(relaxed = true)
        val sensor = mockk<Sensor>()
        val dao = mockk<SensorDao>(relaxed = true)
        val entryPoint = mockk<SensorManager.SensorManagerEntryPoint>()
        val stored = SensorEntity("pressure_sensor", serverId = 1, enabled = true, state = "1000")
        every { context.getSystemService(android.hardware.SensorManager::class.java) } returns hardware
        every { hardware.getDefaultSensor(Sensor.TYPE_PRESSURE) } returns sensor
        every { context.applicationContext } returns context
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(context, SensorManager.SensorManagerEntryPoint::class.java)
        } returns entryPoint
        every { entryPoint.sensorDao() } returns dao
        coEvery { dao.get("pressure_sensor") } returns listOf(stored)
        val manager = spyk(PressureSensorManager())
        coEvery { manager.isEnabled(context, any()) } returns true

        manager.requestSensorUpdate(context)
        verify(exactly = 1) {
            hardware.registerListener(manager, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }
        manager.onSensorChanged(null)
        verify(exactly = 1) { hardware.unregisterListener(manager) }
        manager.onSensorUpdated(context, manager.getAvailableSensors(context).single(), "1001.5", "mdi:gauge", emptyMap())

        coVerify(exactly = 1) { dao.update(match { it.id == "pressure_sensor" && it.state == "1001.5" }) }
        assertTrue(manager.enabledByRelease)
    }
}
