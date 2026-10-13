package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val RETAINED_SENSOR_SETTINGS_TAG = "retained_sensor_settings"
private const val UNAVAILABLE_SENSOR_TAG = "unavailable_sensor"

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorDetailFragmentTest {
    @Test
    fun `Given unavailable root sensor deep link when dismissed then activity finishes`() {
        assertUnavailableSensorDismissal(stacked = false, restored = false)
    }

    @Test
    fun `Given unavailable stacked sensor when dismissed then retained settings return`() {
        assertUnavailableSensorDismissal(stacked = true, restored = false)
    }

    @Test
    fun `Given restored unavailable root sensor when dismissed then activity finishes`() {
        assertUnavailableSensorDismissal(stacked = false, restored = true)
    }

    @Test
    fun `Given restored unavailable stacked sensor when dismissed then retained settings return`() {
        assertUnavailableSensorDismissal(stacked = true, restored = true)
    }

    private fun assertUnavailableSensorDismissal(stacked: Boolean, restored: Boolean) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        try {
            val initialManager = controller.get().supportFragmentManager
            if (stacked) {
                initialManager.beginTransaction()
                    .replace(android.R.id.content, Fragment(), RETAINED_SENSOR_SETTINGS_TAG)
                    .commitNow()
            }
            // SettingsActivity installs Deeplink.Sensor as root without adding a back stack entry.
            val transaction = initialManager.beginTransaction()
                .replace(android.R.id.content, Fragment(), UNAVAILABLE_SENSOR_TAG)
            if (stacked) transaction.addToBackStack(null)
            transaction.commit()
            initialManager.executePendingTransactions()
            if (restored) controller.recreate()

            val activity = controller.get()
            val manager = activity.supportFragmentManager
            assertEquals(if (stacked) 1 else 0, manager.backStackEntryCount)
            assertFalse(activity.isFinishing)
            // Exercise the production navigation helper without constructing the Hilt sensor SDK/UI graph.
            requireNotNull(manager.findFragmentByTag(UNAVAILABLE_SENSOR_TAG)).dismissUnavailableSensor()
            manager.executePendingTransactions()

            if (stacked) {
                assertFalse(activity.isFinishing)
                assertEquals(0, manager.backStackEntryCount)
                assertNotNull(manager.findFragmentByTag(RETAINED_SENSOR_SETTINGS_TAG))
                assertNull(manager.findFragmentByTag(UNAVAILABLE_SENSOR_TAG))
            } else {
                assertTrue(activity.isFinishing)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
