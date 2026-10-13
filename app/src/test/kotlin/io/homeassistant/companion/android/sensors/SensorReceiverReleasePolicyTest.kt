package io.homeassistant.companion.android.sensors

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.PressureSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.PhoneIntegrationReleasePolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorReceiverReleasePolicyTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given phone release when registering managers then fitness is absent and ordinary device sensors remain`() {
        val managers = SensorReceiver.MANAGERS
        assertFalse(managers.any { it is HealthConnectSensorManager })
        assertFalse(managers.any { it is AndroidAutoSensorManager })
        assertFalse(managers.any { it is CarSensorManager })
        assertFalse(managers.any { it is StepsSensorManager })
        assertFalse(managers.any { it is ActivitySensorManager })
        assertTrue(managers.any { it is PressureSensorManager })
        assertTrue(managers.any { it is LocationSensorManager })
        assertTrue(managers.any { it is BatterySensorManager })
    }

    @Test
    fun `Given stale fitness health sleep and Auto rows when syncing then disabled sensors never register or report`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = mockk<SensorDao>(relaxed = true)
        val servers = mockk<ServerManager>()
        val repository = mockk<IntegrationRepository>(relaxed = true)
        val server = mockk<Server>(relaxed = true) { every { id } returns 1 }
        val sleepManager = mockk<SensorManager> {
            every { enabledByRelease } returns true
            every { hasSensor(context) } returns true
            every { isSensorEnabledByRelease("sleep_confidence") } returns false
            coEvery { requestSensorUpdate(context, null) } returns Unit
            // The receiver must enforce policy even if an integration mistakenly exposes an old sensor.
            coEvery { getAvailableSensors(context) } returns listOf(
                SensorManager.BasicSensor("sleep_confidence", "sensor"),
            )
        }
        // The concrete phone receiver reads its static registry field directly, not the companion getter.
        // Supply the test registry through the base receiver's actual update/sync seam instead.
        val receiver = ReleasePolicySensorReceiver(
            listOf(
                HealthConnectSensorManager(),
                AndroidAutoSensorManager(),
                CarSensorManager(),
                StepsSensorManager(enabledByRelease = PhoneIntegrationReleasePolicy.fitnessSensorsEnabled),
                ActivitySensorManager(),
                sleepManager,
            ),
        )
        val staleRows = listOf(
            "health_connect_steps",
            "sleep_confidence",
            "android_auto",
            "steps_sensor",
            "detected_activity",
        ).map {
            Sensor(it, serverId = 1, enabled = true, registered = true, state = "stale")
        }
        coEvery { dao.getFull(any(), any()) } returns staleRows.associateWith { emptyList() }
        coEvery { servers.isRegistered() } returns true
        coEvery { servers.servers() } returns listOf(server)
        coEvery { servers.integrationRepository(1) } returns repository
        coEvery { repository.getConfig() } returns mockk(relaxed = true)
        coEvery { repository.getHomeAssistantVersion() } returns "2026.1.0"
        coEvery { repository.isHomeAssistantVersionAtLeast(any(), any(), any()) } returns true

        receiver.updateSensors(context, servers, dao, intent = null)

        coVerify(exactly = 1) { repository.getConfig() }
        coVerify(exactly = 1) { sleepManager.requestSensorUpdate(context, null) }
        coVerify(exactly = 1) { sleepManager.getAvailableSensors(context) }
        coVerify(exactly = 0) { dao.getFull(any(), any()) }
        coVerify(exactly = 0) { repository.registerSensor(any()) }
        coVerify(exactly = 0) { repository.updateSensors(any()) }
        assertTrue(staleRows.all { it.enabled && it.state == "stale" })
    }
}

private class ReleasePolicySensorReceiver(override val managers: List<SensorManager>) : SensorReceiverBase() {
    override val currentAppVersion: String = "test"
    override val skippableActions: Map<String, List<String>> = emptyMap()

    override fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int,
    ): PendingIntent? = null
}
