package io.homeassistant.companion.android.settings.wear

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.onboarding.WearOnboardingListener
import io.homeassistant.companion.android.settings.wear.views.SettingsWearMainView
import io.mockk.Called
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
@HiltAndroidTest
class SettingsWearReleasePolicyTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given direct Wear sign in route when opened then finish unavailable before SDK setup`() {
        assertUnavailableActivity(SettingsWearActivity::class.java)
    }

    @Test
    fun `Given direct Wear configuration when opened then finish unavailable before SDK setup`() {
        assertUnavailableActivity(SettingsWearMainView::class.java)
    }

    @Test
    fun `Given connected nodes when checking setup then detection is disabled without querying nodes`() = runTest {
        val context = mockk<Context>()
        assertFalse(SettingsWearDetection.hasAnyNodes(context))
        verify { context wasNot Called }
    }

    @Test
    fun `Given Wear instance request when received then no onboarding data is read or sent`() {
        val event = mockk<MessageEvent>()
        WearOnboardingListener().onMessageReceived(event)
        verify { event wasNot Called }
    }

    private fun <T : Activity> assertUnavailableActivity(activityClass: Class<T>) {
        hiltRule.inject()
        mockkStatic(Wearable::class)
        val intent = Intent(Intent.ACTION_VIEW, "woowtech://wear-phone-signin".toUri())
        val controller = Robolectric.buildActivity(activityClass, intent).create()
        try {
            val activity = controller.get()
            assertTrue(activity.isFinishing)
            assertEquals(
                activity.getString(commonR.string.phone_integration_unavailable),
                ShadowToast.getTextOfLatestToast(),
            )
            verify(exactly = 0) { Wearable.getCapabilityClient(any<Activity>()) }
            verify(exactly = 0) { Wearable.getNodeClient(any<Activity>()) }
            verify(exactly = 0) { Wearable.getDataClient(any<Activity>()) }
            verify(exactly = 0) { Wearable.getDataClient(any<Context>()) }
        } finally {
            controller.destroy()
        }
    }
}
