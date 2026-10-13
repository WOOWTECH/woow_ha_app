package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl.Companion.PREF_ASK_NOTIFICATION_PERMISSION
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IntegrationRepositoryImplTest {

    private val integrationService = mockk<IntegrationService>()
    private val serverManager = mockk<ServerManager>()
    private val serverID = 42
    private val server = mockk<Server>()
    private val serverConnection = mockk<ServerConnectionInfo>()
    private val connectionStateProvider = mockk<ServerConnectionStateProvider>()
    private val localStorage = mockk<LocalStorage>()

    private lateinit var repository: IntegrationRepository

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.getServer(serverID) } returns server
        every { server.connection } returns serverConnection
        coEvery { serverManager.connectionStateProvider(serverID) } returns connectionStateProvider
        coEvery { connectionStateProvider.getApiUrls() } returns listOf("http://homeassistant:8123".toHttpUrl())

        repository = IntegrationRepositoryImpl(integrationService, serverManager, serverID, localStorage, "", "", "", "")
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026.1.7-full (1)", "2026.1.7-minimal (1)", "2026.1.7 (2)", "2026.1.7-full (4)"])
    fun `Given a device registration when sending then payload uses brand identity and preserves version and push configuration`(version: String) = runTest {
        val request = slot<RegisterDeviceRequest>()
        val transportFailure = IOException("Test transport unavailable")
        coEvery { localStorage.getString(any()) } returns null
        every { server.deviceName } returns "Test device"
        every { connectionStateProvider.urlFlow() } returns flowOf(UrlState.HasUrl("https://example.com".toHttpUrl().toUrl()))
        coEvery { serverManager.authenticationRepository(serverID).buildBearerToken() } returns "test-bearer"
        coEvery { integrationService.registerDevice(any(), any(), capture(request)) } throws transportFailure

        val result = runCatching {
            repository.registerDevice(
                DeviceRegistration(
                    appVersion = AppVersion.from(rawVersion = version),
                    pushToken = MessagingToken("test-push-token"),
                ),
            )
        }

        assertEquals(transportFailure, result.exceptionOrNull())
        val payload = kotlinJsonMapper.encodeToJsonElement(request.captured).jsonObject
        assertEquals(JsonPrimitive("com.woowtech.aiot"), payload["app_id"])
        assertEquals(JsonPrimitive("woowtech aiot"), payload["app_name"])
        assertEquals(JsonPrimitive(version), payload["app_version"])
        assertEquals(JsonPrimitive("Android"), payload["os_name"])
        assertEquals(BuildConfig.PUSH_URL, request.captured.appData?.get("push_url"))
        assertEquals(MessagingToken("test-push-token"), request.captured.appData?.get("push_token"))
        assertEquals(true, request.captured.appData?.get("push_websocket_channel"))
    }

    @Test
    fun `Given an empty response when invoking renderTemplate then it returns an empty string`() = runTest {
        val expectedResult = ""
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a number when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = 42
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a string when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = "hello world"
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a boolean when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = true
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a list when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = listOf(true, false)
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonArray(expectedResult.map { JsonPrimitive(it) })))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals("[true,false]", result)
    }

    @Test
    fun `Given no preference set when checking shouldAskNotificationPermission then returns null`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns null

        val result = repository.shouldAskNotificationPermission()

        assertNull(result)
    }

    @Test
    fun `Given preference set to true when checking shouldAskNotificationPermission then returns true`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns true

        val result = repository.shouldAskNotificationPermission()

        assertTrue(result == true)
    }

    @Test
    fun `Given preference set to false when checking shouldAskNotificationPermission then returns false`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns false

        val result = repository.shouldAskNotificationPermission()

        assertEquals(false, result)
    }

    @Test
    fun `Given setAskNotificationPermission called with true then stores true for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
    }

    @Test
    fun `Given setAskNotificationPermission called with false then stores false for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Test
    fun `Given different server IDs then notification permission is stored separately per server`() = runTest {
        val otherServerId = 99
        coEvery { serverManager.getServer(otherServerId) } returns server
        val otherRepository = IntegrationRepositoryImpl(
            integrationService,
            serverManager,
            otherServerId,
            localStorage,
            "",
            "",
            "",
            "",
        )

        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)
        otherRepository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
        coVerify { localStorage.putBoolean("${otherServerId}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }
}
