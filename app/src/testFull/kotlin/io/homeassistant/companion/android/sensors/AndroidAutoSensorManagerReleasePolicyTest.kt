package io.homeassistant.companion.android.sensors

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.util.PhoneIntegrationReleasePolicy
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AndroidAutoSensorManagerReleasePolicyTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given stale phone opt ins when Auto managers called then no context or storage access`() = runTest {
        val context = mockk<Context>()
        for (manager in listOf(AndroidAutoSensorManager(), CarSensorManager())) {
            val sensor = SensorManager.BasicSensor("android_auto", "binary_sensor", enabledByDefault = true)
            assertFalse(manager.enabledByRelease)
            assertFalse(manager.hasSensor(context))
            assertTrue(manager.getAvailableSensors(context).isEmpty())
            assertFalse(manager.checkPermission(context, sensor.id))
            assertFalse(manager.isEnabled(context, sensor))
            assertFalse(manager.isEnabled(context, sensor, serverId = 1))
            assertTrue(manager.getEnabledServers(context, sensor).isEmpty())
            manager.requestSensorUpdate(context)
            manager.onSensorUpdated(context, sensor, true, "mdi:car", emptyMap())
        }
        AndroidAutoSensorManager().onChanged(1)
        verify { context wasNot Called }
    }

    @Test
    fun `Given Automotive policy and device when discovering then vehicle sensors remain available`() = runTest {
        mockkObject(PhoneIntegrationReleasePolicy)
        every { PhoneIntegrationReleasePolicy.vehicleIntegrationEnabled } returns true
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true)

        for (manager in listOf(AndroidAutoSensorManager(), CarSensorManager())) {
            assertTrue(manager.enabledByRelease)
            assertTrue(manager.hasSensor(context))
            assertTrue(manager.getAvailableSensors(context).isNotEmpty())
        }
    }
}
