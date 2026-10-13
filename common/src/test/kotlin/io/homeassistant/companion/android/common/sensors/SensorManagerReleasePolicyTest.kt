package io.homeassistant.companion.android.common.sensors

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.server.Server
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SensorManagerReleasePolicyTest {
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `Given stored enabled row when release policy is applied then only retained sensors read and update it`(
        managerAllowed: Boolean,
        sensorAllowed: Boolean,
    ) = runTest {
        val allowed = managerAllowed && sensorAllowed
        val context = mockk<Context>()
        val dao = mockk<SensorDao>(relaxed = true)
        val servers = mockk<ServerManager>()
        val server = mockk<Server> { every { id } returns 1 }
        val entryPoint = mockk<SensorManager.SensorManagerEntryPoint>()
        val stored = Sensor(id = "test", serverId = 1, enabled = true, state = "old")
        val sensor = SensorManager.BasicSensor("test", "sensor", enabledByDefault = true)
        val manager = ReleasePolicyTestSensorManager(managerAllowed = managerAllowed, sensorAllowed = sensorAllowed)

        mockkStatic(EntryPointAccessors::class)
        every { context.applicationContext } returns context
        every {
            EntryPointAccessors.fromApplication(context, SensorManager.SensorManagerEntryPoint::class.java)
        } returns entryPoint
        every { entryPoint.sensorDao() } returns dao
        every { entryPoint.serverManager() } returns servers
        coEvery { servers.servers() } returns listOf(server)
        coEvery { dao.get("test") } returns listOf(stored)
        coEvery { dao.getAnyIsEnabled("test", listOf(1), true, true) } returns true
        coEvery { dao.getOrDefault("test", 1, true, true) } returns stored

        assertEquals(allowed, manager.checkPermission(context, sensor.id))
        assertEquals(allowed, manager.isEnabled(context, sensor))
        assertEquals(allowed, manager.isEnabled(context, sensor, serverId = 1))
        assertEquals(if (allowed) setOf(1) else emptySet<Int>(), manager.getEnabledServers(context, sensor))
        manager.onSensorUpdated(context, sensor, "new", "mdi:test", emptyMap())

        assertTrue(stored.enabled)
        if (allowed) {
            coVerify(exactly = 1) { dao.update(match { it.state == "new" }) }
        } else {
            verify { context wasNot Called }
            verify { dao wasNot Called }
            verify { servers wasNot Called }
        }
        coVerify(exactly = 0) { dao.removeSensor(any(), any()) }
    }
}

private class ReleasePolicyTestSensorManager(managerAllowed: Boolean, private val sensorAllowed: Boolean) : SensorManager {
    override val enabledByRelease: Boolean = managerAllowed
    override val name: Int = -1

    override fun isSensorEnabledByRelease(sensorId: String): Boolean =
        super.isSensorEnabledByRelease(sensorId) && sensorAllowed

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> = emptyArray()

    override suspend fun requestSensorUpdate(context: Context) = Unit

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> = emptyList()
}
