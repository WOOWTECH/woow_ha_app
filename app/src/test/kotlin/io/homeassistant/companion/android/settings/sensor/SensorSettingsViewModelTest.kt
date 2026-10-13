package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.PressureSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.StepsSensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherJUnit4Rule()

    private val dao = mockk<SensorDao>(relaxed = true)
    private val manager = mockk<SensorManager>()
    private val application = mockk<Application>()
    private val rows = MutableStateFlow(
        listOf(
            Sensor("battery", serverId = 1, enabled = false, state = "old"),
            Sensor("battery", serverId = 2, enabled = true, state = "current"),
            Sensor("activity", serverId = 1, enabled = false, state = "still"),
        ),
    )
    private lateinit var viewModel: SensorSettingsViewModel

    @Before
    fun setUp() {
        every { application.getString(1) } returns "Sensors 感測器"
        every { application.getString(2) } returns "Battery 電池"
        every { application.getString(3) } returns "Activity 活動"
        every { application.getString(4) } returns "Missing"
        every { manager.name } returns 1
        every { manager.enabledByRelease } returns true
        every { manager.hasSensor(application) } returns true
        every { manager.isSensorEnabledByRelease(any()) } returns true
        coEvery { manager.getAvailableSensors(application) } returns listOf(
            SensorManager.BasicSensor("battery", "sensor", name = 2),
            SensorManager.BasicSensor("activity", "sensor", name = 3),
            SensorManager.BasicSensor("missing", "sensor", name = 4),
        )
        every { dao.getAllFlow() } returns rows
        mockkObject(SensorReceiver.Companion)
        every { SensorReceiver.MANAGERS } returns listOf(manager)
        viewModel = SensorSettingsViewModel(dao, application, mainDispatcherRule.testDispatcher)
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        unmockkAll()
    }

    @Test
    fun `Given English query when filtering and clearing then observed sensors are restored without writes`() {
        assertReadOnlyQuery("bAtTeRy")
    }

    @Test
    fun `Given Chinese query when filtering and clearing then observed sensors are restored without writes`() {
        assertReadOnlyQuery("電池")
    }

    private fun assertReadOnlyQuery(query: String) = runTest {
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity"), viewModel.sensors.keys)
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        viewModel.setSensorsSearchQuery(query)
        assertEquals(query, viewModel.searchQuery)
        advanceUntilIdle()
        assertEquals(setOf("battery"), viewModel.sensors.keys)
        assertEquals("current", viewModel.sensors.getValue("battery").state)
        assertEquals(setOf("battery"), visibleSensorIds())

        viewModel.setSensorFilterChoice(R.id.action_show_sensors_disabled)
        advanceUntilIdle()
        assertTrue(viewModel.sensors.isEmpty())
        assertTrue(visibleSensorIds().isEmpty())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_enabled)
        advanceUntilIdle()
        assertEquals(setOf("battery"), viewModel.sensors.keys)
        assertEquals(setOf("battery"), visibleSensorIds())
        viewModel.setSensorsSearchQuery("")
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_all)
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity"), viewModel.sensors.keys)
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        assertNoFilteringSideEffects()
    }

    @Test
    fun `Given rapid queries and changed database rows when filtering finishes then only latest state is displayed`() = runTest {
        advanceUntilIdle()
        viewModel.setSensorsSearchQuery("battery")
        viewModel.setSensorsSearchQuery("activity")
        rows.value = listOf(Sensor("activity", serverId = 1, enabled = true, state = "walking"))
        advanceUntilIdle()
        assertEquals("activity", viewModel.searchQuery)
        assertEquals(setOf("activity"), viewModel.sensors.keys)
        assertEquals("walking", viewModel.sensors.getValue("activity").state)
        assertEquals(setOf("activity"), visibleSensorIds())
        assertNoFilteringSideEffects()
    }

    @Test
    fun `Given first empty database emission when listing sensors then available metadata is visible without creating rows`() = runTest {
        // The collector has not run yet, so emptyList is its first emission. No setter triggers filtering.
        rows.value = emptyList()
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        assertTrue(viewModel.sensors.isEmpty())

        viewModel.setSensorFilterChoice(R.id.action_show_sensors_disabled)
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_enabled)
        advanceUntilIdle()
        assertTrue(visibleSensorIds().isEmpty())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_all)
        viewModel.setSensorsSearchQuery("電池")
        advanceUntilIdle()
        assertEquals(setOf("battery"), visibleSensorIds())
        viewModel.setSensorsSearchQuery("")
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        assertTrue(viewModel.sensors.isEmpty())
        assertNoFilteringSideEffects()
    }

    @Test
    fun `Given partially populated database when filtering missing sensor then metadata stays selectable without a row`() = runTest {
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_disabled)
        advanceUntilIdle()
        assertEquals(setOf("activity", "missing"), visibleSensorIds())
        assertEquals(setOf("activity"), viewModel.sensors.keys)
        viewModel.setSensorsSearchQuery("missing")
        advanceUntilIdle()
        assertEquals(setOf("missing"), visibleSensorIds())
        assertTrue(viewModel.sensors.isEmpty())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_enabled)
        advanceUntilIdle()
        assertTrue(visibleSensorIds().isEmpty())
        viewModel.setSensorsSearchQuery("")
        advanceUntilIdle()
        assertEquals(setOf("battery"), visibleSensorIds())
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_all)
        viewModel.setSensorsSearchQuery("Missing")
        advanceUntilIdle()
        assertEquals(setOf("missing"), visibleSensorIds())
        assertTrue(viewModel.sensors.isEmpty())
        viewModel.setSensorsSearchQuery("")
        advanceUntilIdle()
        assertEquals(setOf("battery", "activity", "missing"), visibleSensorIds())
        assertNoFilteringSideEffects()
    }

    @Test
    fun `Given missing sensor explicitly activated elsewhere when its row is observed then enabled catalog includes it`() = runTest {
        viewModel.setSensorsSearchQuery("missing")
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_enabled)
        advanceUntilIdle()
        assertTrue(visibleSensorIds().isEmpty())

        rows.value = rows.value + Sensor("missing", serverId = 1, enabled = true, state = "new")
        advanceUntilIdle()
        assertEquals(setOf("missing"), visibleSensorIds())
        assertEquals("new", viewModel.sensors.getValue("missing").state)
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_disabled)
        advanceUntilIdle()
        assertTrue(visibleSensorIds().isEmpty())
        assertNoFilteringSideEffects()
    }

    @Test
    fun `Given real phone fitness managers and saved opt ins when searching then only ordinary barometer is discoverable`() = runTest {
        viewModel.viewModelScope.cancel()
        val packageManager = mockk<PackageManager>()
        every { application.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER) } returns true
        every { application.getString(any()) } returns "Barometer"
        every { SensorReceiver.MANAGERS } returns listOf(
            StepsSensorManager(enabledByRelease = PhoneIntegrationReleasePolicy.fitnessSensorsEnabled),
            ActivitySensorManager(),
            PressureSensorManager(),
        )
        rows.value = listOf("steps_sensor", "detected_activity", "pressure_sensor").map {
            Sensor(it, serverId = 1, enabled = true, state = "saved")
        }
        viewModel = SensorSettingsViewModel(dao, application, mainDispatcherRule.testDispatcher)
        advanceUntilIdle()
        assertEquals(setOf("pressure_sensor"), visibleSensorIds())
        assertEquals(setOf("pressure_sensor"), viewModel.sensors.keys)
        viewModel.setSensorFilterChoice(R.id.action_show_sensors_enabled)
        viewModel.setSensorsSearchQuery("Barometer")
        advanceUntilIdle()
        assertEquals(setOf("pressure_sensor"), visibleSensorIds())
        viewModel.setSensorsSearchQuery("steps")
        advanceUntilIdle()
        assertTrue(visibleSensorIds().isEmpty())
        viewModel.setSensorsSearchQuery("")
        advanceUntilIdle()
        assertEquals(setOf("pressure_sensor"), visibleSensorIds())
        assertTrue(rows.value.all { it.enabled && it.state == "saved" })
        assertNoFilteringSideEffects()
    }

    private fun visibleSensorIds(): Set<String> = viewModel.allSensors.values.flatten().map { it.id }.toSet()

    private fun assertNoFilteringSideEffects() {
        coVerify(exactly = 0) { manager.isEnabled(any(), any()) }
        coVerify(exactly = 0) { manager.checkPermission(any(), any()) }
        coVerify(exactly = 0) { dao.getAnyIsEnabled(any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.add(any<Sensor>()) }
        coVerify(exactly = 0) { dao.update(any()) }
        coVerify(exactly = 0) { dao.setSensorEnabled(any(), any(), any()) }
    }
}
