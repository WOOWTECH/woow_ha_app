package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import io.homeassistant.companion.android.common.sensors.HealthSensorReleasePolicy
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ConsoleLogExtension::class)
class HealthConnectSensorManagerTest {

    private val sensorManager = HealthConnectSensorManager()

    private val context = mockk<Context> {
        every { applicationContext } returns this
    }
    private val healthConnectClient = mockk<HealthConnectClient>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(HealthConnectClient.Companion)
        every { HealthConnectClient.getOrCreate(any()) } returns healthConnectClient
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given release disabled when discovering or requesting permissions then no SDK is accessed`() = runTest {
        assertFalse(sensorManager.enabledByRelease)
        assertFalse(sensorManager.hasSensor(context))
        assertTrue(sensorManager.getAvailableSensors(context).isEmpty())
        assertTrue(sensorManager.requiredPermissions(context, HealthConnectSensorManager.steps.id).isEmpty())
        assertFalse(sensorManager.checkPermission(context, HealthConnectSensorManager.steps.id))
        assertNull(HealthConnectSensorManager.getPermissionIntent())
        assertNull(HealthConnectSensorManager.getPermissionResultContract())

        verify(exactly = 0) { HealthConnectClient.getOrCreate(any()) }
        verify(exactly = 0) { HealthConnectClient.getSdkStatus(any()) }
        verify { context wasNot Called }
    }

    @Test
    fun `Given release disabled when updating a previously enabled sensor then no storage or SDK is accessed`() = runTest {
        val sensor = HealthConnectSensorManager.steps
        assertFalse(sensorManager.isEnabled(context, sensor))
        assertFalse(sensorManager.isEnabled(context, sensor, serverId = 1))
        assertTrue(sensorManager.getEnabledServers(context, sensor).isEmpty())
        sensorManager.requestSensorUpdate(context)
        sensorManager.requestSensorUpdate(context, intent = null)
        sensorManager.onSensorUpdated(context, sensor, 123, sensor.statelessIcon, emptyMap())

        verify(exactly = 0) { HealthConnectClient.getOrCreate(any()) }
        verify { context wasNot Called }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `Given background read support when getting permission then includes background permission if appropriate`(
        available: Boolean,
    ) {
        // Preserve upstream permission behavior for a future reviewed re-enable, not a runtime opt-in.
        mockkObject(HealthSensorReleasePolicy)
        every { HealthSensorReleasePolicy.healthConnectEnabled } returns true
        mockkObject(healthConnectClient.features)
        every {
            healthConnectClient.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        } returns
            if (available) {
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } else {
                HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
            }

        // Get permission(s) for any sensor - the actual sensor doesn't matter here
        val permissions = sensorManager.requiredPermissions(context, HealthConnectSensorManager.steps.id)
        assertEquals(
            available,
            permissions.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND),
        )
    }
}
