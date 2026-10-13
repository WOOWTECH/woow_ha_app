package io.homeassistant.companion.android.sensors

import android.app.Application
import io.homeassistant.companion.android.common.sensors.BatterySensorManager
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorReceiverReleasePolicyTest {
    @Test
    fun `Given first release when registering managers then HealthServices is absent and other sensors remain`() {
        val managers = SensorReceiver.MANAGERS
        assertFalse(managers.any { it is HealthServicesSensorManager })
        assertTrue(managers.any { it is StepsSensorManager })
        assertTrue(managers.any { it is HeartRateSensorManager })
        assertTrue(managers.any { it is OnBodySensorManager })
        assertTrue(managers.any { it is BatterySensorManager })
    }
}
