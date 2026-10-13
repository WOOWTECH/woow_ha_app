package io.homeassistant.companion.android.sensors

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.tasks.Tasks
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.util.PhoneIntegrationReleasePolicy
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ActivitySensorManagerReleasePolicyTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given phone release when discovering activity sensors then none are available without permission access`() =
        runTest {
            val context = mockk<Context>()
            val manager = ActivitySensorManager()
            assertFalse(manager.enabledByRelease)
            assertFalse(manager.hasSensor(context))
            assertTrue(manager.getAvailableSensors(context).isEmpty())
            assertFalse(manager.isSensorEnabledByRelease("detected_activity"))
            assertTrue(manager.requiredPermissions(context, "detected_activity").isEmpty())
            verify { context wasNot Called }
        }

    @Test
    fun `Given stale activity opt in when checked or updated then no storage or permission access occurs`() {
        assertSensorBlocked("detected_activity")
    }

    @Test
    fun `Given stale sleep confidence opt in when checked or updated then no storage or permission access occurs`() {
        assertSensorBlocked("sleep_confidence")
    }

    @Test
    fun `Given stale sleep segment opt in when checked or updated then no storage or permission access occurs`() {
        assertSensorBlocked("sleep_segment")
    }

    private fun assertSensorBlocked(sensorId: String) = runTest {
        val context = mockk<Context>()
        val sensor = SensorManager.BasicSensor(sensorId, "sensor", enabledByDefault = true)
        val manager = ActivitySensorManager()
        assertFalse(manager.isSensorEnabledByRelease(sensorId))
        assertTrue(manager.requiredPermissions(context, sensorId).isEmpty())
        assertFalse(manager.checkPermission(context, sensorId))
        assertFalse(manager.isEnabled(context, sensor))
        assertFalse(manager.isEnabled(context, sensor, serverId = 1))
        assertTrue(manager.getEnabledServers(context, sensor).isEmpty())
        manager.onSensorUpdated(context, sensor, 99, "mdi:sleep", emptyMap())
        verify { context wasNot Called }
    }

    @Test
    fun `Given no prior sleep subscription when updating then no sleep SDK client is accessed`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = spyk(ActivitySensorManager())
        coEvery { manager.isEnabled(context, any()) } returns false
        mockkStatic(ActivityRecognition::class)

        manager.requestSensorUpdate(context)

        verify(exactly = 0) { ActivityRecognition.getClient(any<Context>()) }
        coVerify(exactly = 0) { manager.isEnabled(context, match { it.id.startsWith("sleep_") }) }
    }

    @Test
    fun `Given Automotive with sleep subscription when updating then retain only activity`() = runTest {
        mockkObject(PhoneIntegrationReleasePolicy)
        every { PhoneIntegrationReleasePolicy.fitnessSensorsEnabled } returns true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sleepIntent = sleepPendingIntent(context, create = true)
        val manager = spyk(ActivitySensorManager())
        val client = mockk<ActivityRecognitionClient>(relaxed = true)
        mockkStatic(ActivityRecognition::class)
        every { ActivityRecognition.getClient(context) } returns client
        every { client.removeSleepSegmentUpdates(requireNotNull(sleepIntent)) } returns Tasks.forResult<Void>(null)
        coEvery { manager.isEnabled(context, any()) } answers {
            secondArg<SensorManager.BasicSensor>().id == "detected_activity"
        }
        mockkObject(SensorReceiverBase.Companion)
        coEvery { SensorReceiverBase.shouldDoFastUpdates(context) } returns false

        manager.requestSensorUpdate(context)
        shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) { client.removeSleepSegmentUpdates(requireNotNull(sleepIntent)) }
        verify(exactly = 1) { client.removeActivityUpdates(any()) }
        verify(exactly = 1) { client.requestActivityUpdates(120000L, any()) }
        verify(exactly = 0) { client.requestSleepSegmentUpdates(any(), any()) }
        assertNull(sleepPendingIntent(context, create = false))
    }

    @Test
    fun `Given Automotive sleep unsubscribe fails when updating then retain token without subscribing`() = runTest {
        mockkObject(PhoneIntegrationReleasePolicy)
        every { PhoneIntegrationReleasePolicy.fitnessSensorsEnabled } returns true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sleepIntent = sleepPendingIntent(context, create = true)
        val manager = spyk(ActivitySensorManager())
        val client = mockk<ActivityRecognitionClient>(relaxed = true)
        mockkStatic(ActivityRecognition::class)
        every { ActivityRecognition.getClient(context) } returns client
        every { client.removeSleepSegmentUpdates(requireNotNull(sleepIntent)) } returns
            Tasks.forException<Void>(SecurityException("Permission revoked"))
        coEvery { manager.isEnabled(context, any()) } returns false

        manager.requestSensorUpdate(context)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(sleepPendingIntent(context, create = false))
        verify(exactly = 0) { client.requestSleepSegmentUpdates(any(), any()) }
        verify(exactly = 0) { client.removeActivityUpdates(any()) }
        sleepIntent?.cancel()
    }

    @Test
    fun `Given stale sleep event when received then no event extraction or subscription occurs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, ActivitySensorManager::class.java).apply {
            action = ActivitySensorManager.ACTION_SLEEP_ACTIVITY
        }
        mockkStatic(ActivityRecognition::class)
        mockkStatic(SleepClassifyEvent::class)
        mockkStatic(SleepSegmentEvent::class)

        ActivitySensorManager().onReceive(context, intent)

        verify(exactly = 0) { SleepClassifyEvent.hasEvents(any()) }
        verify(exactly = 0) { SleepSegmentEvent.hasEvents(any()) }
        verify(exactly = 0) { ActivityRecognition.getClient(any<Context>()) }
    }

    @Test
    fun `Given phone with stale subscriptions and opt ins when updating then no SDK or stored settings are accessed`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val sleepIntent = sleepPendingIntent(context, create = true)
            val activityIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ActivitySensorManager::class.java).apply {
                    action = ActivitySensorManager.ACTION_UPDATE_ACTIVITY
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val manager = spyk(ActivitySensorManager())
            coEvery { manager.isEnabled(context, any()) } returns true
            mockkStatic(ActivityRecognition::class)
            try {
                manager.requestSensorUpdate(context)

                verify(exactly = 0) { ActivityRecognition.getClient(any<Context>()) }
                coVerify(exactly = 0) { manager.isEnabled(any(), any()) }
                coVerify(exactly = 0) { manager.onSensorUpdated(any(), any(), any(), any(), any(), any()) }
                // Ignoring legacy tokens is not proof of historical GMS subscription removal.
                assertNotNull(sleepPendingIntent(context, create = false))
            } finally {
                sleepIntent?.cancel()
                activityIntent.cancel()
            }
        }

    @Test
    fun `Given stale phone activity callback when received then no extraction client or sensor write occurs`() {
        val context = mockk<Context>()
        val intent = Intent().apply { action = ActivitySensorManager.ACTION_UPDATE_ACTIVITY }
        val manager = spyk(ActivitySensorManager())
        mockkStatic(ActivityRecognition::class)
        mockkStatic(ActivityRecognitionResult::class)

        manager.onReceive(context, intent)

        verify(exactly = 0) { ActivityRecognitionResult.hasResult(any()) }
        verify(exactly = 0) { ActivityRecognitionResult.extractResult(any()) }
        verify(exactly = 0) { ActivityRecognition.getClient(any<Context>()) }
        coVerify(exactly = 0) { manager.onSensorUpdated(any(), any(), any(), any(), any(), any()) }
        verify { context wasNot Called }
    }

    private fun sleepPendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, ActivitySensorManager::class.java).apply {
            action = ActivitySensorManager.ACTION_SLEEP_ACTIVITY
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
                PendingIntent.FLAG_MUTABLE,
        )
    }
}
