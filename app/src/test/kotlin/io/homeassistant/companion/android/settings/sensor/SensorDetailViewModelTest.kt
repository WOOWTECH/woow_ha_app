package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.sensors.ActivitySensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit4Rule
import io.homeassistant.companion.android.util.PhoneIntegrationReleasePolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given removed sensor manager when stale settings dialog returns then no setting or opt in is written`() {
        assertStaleSettingsBlocked(managerPresent = false)
    }

    @Test
    fun `Given release disabled sensor when stale settings dialog returns then no setting or opt in is written`() {
        assertStaleSettingsBlocked(managerPresent = true)
    }

    @Test
    fun `Given available sensor without database row when explicitly enabled then activation is requested`() = runTest {
        val sensorId = "missing"
        val application = mockk<Application>(relaxed = true)
        val dao = mockk<SensorDao>(relaxed = true)
        every { dao.getSettingsFlow(sensorId) } returns flowOf(emptyList())
        every { dao.getFullFlow(sensorId) } returns flowOf(emptyMap())
        val basicSensor = SensorManager.BasicSensor(sensorId, "sensor")
        val manager = mockk<SensorManager>(relaxed = true) {
            every { isSensorEnabledByRelease(sensorId) } returns true
            every { hasSensor(application) } returns true
            coEvery { getAvailableSensors(application) } returns listOf(basicSensor)
            every { requiredPermissions(application, sensorId) } returns emptyArray()
            coEvery { checkPermission(application, sensorId) } returns true
        }
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.MANAGERS } returns listOf(manager)
        every { SensorReceiver.updateAllSensors(application) } returns Unit
        val viewModel = SensorDetailViewModel(
            SavedStateHandle(mapOf("id" to sensorId)),
            mockk(relaxed = true),
            dao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            application,
        )
        try {
            advanceUntilIdle()
            assertEquals(basicSensor, viewModel.basicSensor)
            assertNull(viewModel.sensor)
            coVerify(exactly = 0) { dao.setSensorEnabled(any(), any(), any()) }
            coVerify(exactly = 0) { manager.checkPermission(any(), any()) }

            viewModel.setEnabled(isEnabled = true, serverId = 1)
            advanceUntilIdle()

            // The DAO's explicit activation path inserts missing rows; discovery must not do so.
            coVerify(exactly = 1) { dao.setSensorEnabled(sensorId, listOf(1), true) }
            coVerify(exactly = 1) { manager.requestSensorUpdate(application) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `Given stale steps settings route when enabling then release policy prevents permissions and writes`() {
        assertFitnessRouteBlocked("steps_sensor")
    }

    @Test
    fun `Given stale activity settings route when enabling then release policy prevents permissions and writes`() {
        assertFitnessRouteBlocked("detected_activity")
    }

    @Test
    fun `Given notification allowlist when saving visible apps then unqueryable selections survive`() {
        assertAppSelectionsPreserved(sensorId = "last_notification")
    }

    @Test
    fun `Given removed notification allowlist when saving visible apps then unqueryable selections survive`() {
        assertAppSelectionsPreserved(sensorId = "last_removed_notification")
    }

    @Test
    fun `Given next alarm allowlist when saving visible apps then unqueryable selections survive`() {
        assertAppSelectionsPreserved(sensorId = "next_alarm")
    }

    @Test
    @Config(sdk = [32])
    fun `Given legacy package query when saving visible apps then unqueryable selections survive`() {
        assertAppSelectionsPreserved(sensorId = "last_notification")
    }

    @Test
    fun `Given unknown selected package when loading summary then package name is used`() {
        assertAppSummaryFallback()
    }

    @Test
    @Config(sdk = [32])
    fun `Given unknown selected package on legacy API when loading summary then package name is used`() {
        assertAppSummaryFallback()
    }

    private fun assertAppSelectionsPreserved(sensorId: String) = runTest {
        val application = mockk<Application>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        val visible = ApplicationInfo().apply { packageName = "com.visible" }
        val blank = ApplicationInfo().apply { packageName = "com.blank" }
        val setting = SensorSetting(sensorId, "allow_list", "com.hidden, com.visible", SensorSettingType.LIST_APPS)
        val dao = mockk<SensorDao>(relaxed = true)
        mockInstalledApplications(packageManager, listOf(visible, blank))
        every { packageManager.getApplicationLabel(visible) } returns "Visible app"
        every { packageManager.getApplicationLabel(blank) } returns " "
        val viewModel = createAppSettingsViewModel(application, dao, sensorId)
        try {
            viewModel.onSettingWithDialogPressed(setting)
            advanceUntilIdle()
            val dialog = requireNotNull(viewModel.sensorSettingsDialog)
            assertEquals(
                listOf("com.blank" to "com.blank", "com.hidden" to "com.hidden", "com.visible" to "Visible app\n(com.visible)"),
                dialog.entries,
            )
            assertEquals(listOf("com.hidden", "com.visible"), dialog.entriesSelected)
            coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                verify(exactly = 1) { packageManager.getInstalledApplications(any<PackageManager.ApplicationInfoFlags>()) }
            } else {
                @Suppress("DEPRECATION")
                verify(exactly = 1) { packageManager.getInstalledApplications(any<Int>()) }
            }

            // Mirror the existing dialog's Save action without toggling any rows.
            viewModel.submitSettingWithDialog(dialog.copy(setting = setting.copy(value = dialog.entriesSelected.joinToString(", "))))
            advanceUntilIdle()
            coVerify(exactly = 1) { dao.add(setting) }
            coVerify(exactly = 0) { dao.setSensorEnabled(any(), any(), any()) }
            verify(exactly = 0) { packageManager.setApplicationEnabledSetting(any(), any(), any()) }
            verify(exactly = 0) { packageManager.setComponentEnabledSetting(any(), any(), any()) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun assertAppSummaryFallback() = runTest {
        val sensorId = "last_notification"
        val application = mockk<Application>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        val visible = ApplicationInfo().apply { packageName = "com.visible" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.getApplicationInfo("com.hidden", any<PackageManager.ApplicationInfoFlags>()) } throws PackageManager.NameNotFoundException()
            every { packageManager.getApplicationInfo("com.visible", any<PackageManager.ApplicationInfoFlags>()) } returns visible
        } else {
            @Suppress("DEPRECATION")
            every { packageManager.getApplicationInfo("com.hidden", any<Int>()) } throws PackageManager.NameNotFoundException()
            @Suppress("DEPRECATION")
            every { packageManager.getApplicationInfo("com.visible", any<Int>()) } returns visible
        }
        every { packageManager.getApplicationLabel(visible) } returns "Visible app"
        val dao = mockk<SensorDao>(relaxed = true)
        val viewModel = createAppSettingsViewModel(application, dao, sensorId)
        val setting = SensorSetting(sensorId, "allow_list", "com.hidden, com.visible", SensorSettingType.LIST_APPS)
        try {
            assertEquals(
                listOf("com.hidden", "Visible app"),
                viewModel.getSettingEntries(setting, listOf("com.visible", "com.hidden")),
            )
            assertEquals(emptyList<String>(), viewModel.getSettingEntries(setting, emptyList()))
            coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun mockInstalledApplications(packageManager: PackageManager, apps: List<ApplicationInfo>) {
        // A second enumeration would have a different order; the dialog must use one snapshot.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.getInstalledApplications(any<PackageManager.ApplicationInfoFlags>()) } returnsMany listOf(apps, apps.reversed())
        } else {
            @Suppress("DEPRECATION")
            every { packageManager.getInstalledApplications(any<Int>()) } returnsMany listOf(apps, apps.reversed())
        }
    }

    private fun createAppSettingsViewModel(application: Application, dao: SensorDao, sensorId: String): SensorDetailViewModel {
        every { dao.getSettingsFlow(sensorId) } returns flowOf(emptyList())
        every { dao.getFullFlow(sensorId) } returns flowOf(emptyMap())
        val manager = mockk<SensorManager>(relaxed = true) {
            every { isSensorEnabledByRelease(sensorId) } returns true
            every { hasSensor(application) } returns true
            coEvery { getAvailableSensors(application) } returns listOf(SensorManager.BasicSensor(sensorId, "sensor"))
        }
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.MANAGERS } returns listOf(manager)
        every { SensorReceiver.updateAllSensors(application) } returns Unit
        return SensorDetailViewModel(
            SavedStateHandle(mapOf("id" to sensorId)),
            mockk(relaxed = true),
            dao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            application,
        )
    }

    private fun assertFitnessRouteBlocked(sensorId: String) = runTest {
        val dao = mockk<SensorDao>(relaxed = true)
        every { dao.getSettingsFlow(sensorId) } returns flowOf(emptyList())
        every { dao.getFullFlow(sensorId) } returns flowOf(emptyMap())
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.MANAGERS } returns listOf(
            StepsSensorManager(enabledByRelease = PhoneIntegrationReleasePolicy.fitnessSensorsEnabled),
            ActivitySensorManager(),
        )
        val viewModel = SensorDetailViewModel(
            SavedStateHandle(mapOf("id" to sensorId)),
            mockk(relaxed = true),
            dao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(),
        )
        try {
            viewModel.setEnabled(isEnabled = true, serverId = 1)
            viewModel.setSetting(SensorSetting(sensorId, "stale", "true", SensorSettingType.TOGGLE))
            advanceUntilIdle()

            assertNull(viewModel.sensorManager)
            assertNull(viewModel.basicSensor)
            assertNull(viewModel.permissionRequests.value)
            assertNull(viewModel.locationPermissionRequests.value)
            assertTrue(SensorReceiver.MANAGERS.none { it.enabledByRelease })
            coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
            coVerify(exactly = 0) { dao.setSensorEnabled(any(), any(), any()) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun assertStaleSettingsBlocked(managerPresent: Boolean) = runTest {
        val sensorId = "android_auto"
        val dao = mockk<SensorDao>(relaxed = true)
        every { dao.getSettingsFlow(sensorId) } returns flowOf(emptyList())
        every { dao.getFullFlow(sensorId) } returns flowOf(emptyMap())
        val manager = mockk<SensorManager> {
            every { isSensorEnabledByRelease(sensorId) } returns false
        }
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.MANAGERS } returns if (managerPresent) listOf(manager) else emptyList()
        val viewModel = SensorDetailViewModel(
            SavedStateHandle(mapOf("id" to sensorId)),
            mockk(relaxed = true),
            dao,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(),
        )
        try {
            val setting = SensorSetting(sensorId, "stale", "true", SensorSettingType.TOGGLE)
            viewModel.setSetting(setting)
            viewModel.onSettingWithDialogPressed(setting)
            viewModel.submitSettingWithDialog(
                SensorDetailViewModel.Companion.SettingDialogState(setting, false, emptyList(), emptyList()),
            )
            viewModel.setEnabled(isEnabled = true, serverId = 1)
            advanceUntilIdle()

            assertNull(viewModel.sensorManager)
            assertNull(viewModel.sensorSettingsDialog)
            coVerify(exactly = 0) { dao.add(any<SensorSetting>()) }
            coVerify(exactly = 0) { dao.setSensorEnabled(any(), any(), any()) }
            coVerify(exactly = 0) { manager.getAvailableSensors(any()) }
            coVerify(exactly = 0) { manager.requestSensorUpdate(any()) }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
