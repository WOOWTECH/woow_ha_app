package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.ApiException
import io.homeassistant.companion.android.common.data.woowpaas.HTTP_CODE_UNKNOWN
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionStatus
import io.homeassistant.companion.android.common.data.woowpaas.SessionExpiredException
import io.homeassistant.companion.android.common.data.woowpaas.TokenPollResult
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasApiConfig
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSession
import io.homeassistant.companion.android.testing.unit.FakeClock
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private const val CLIENT_ID = "woow-ha-app"
private const val SCOPES = "ha:provision workspace:read smarthome:read"
private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
private const val REFRESH_GRANT_TYPE = "refresh_token"
private const val ACCESS_TOKEN = "token-123"
private const val REFRESH_TOKEN = "refresh-123"
private const val SESSION_EXPIRED_MESSAGE = "登入已過期，請返回重新登入"

/** The instant every test starts at, so token lifetimes are computed against a known reference. */
private val NOW = Instant.fromEpochMilliseconds(0)

private val ACCESS_TOKEN_LIFETIME = 1.hours

private const val TOKEN_PATH = "oauth2/token"

/** How long a request may wait for its sibling before the concurrency test gives up instead of hanging. */
private const val BARRIER_TIMEOUT_SECONDS = 10L

/**
 * Refuses the first call of every API endpoint, and only once both of them have been refused, so two calls
 * really are holding a token the backend just rejected at the same time.
 *
 * Counting the token endpoint calls in [refreshCount] is what tells whether the two callers raced to spend
 * the same refresh token or whether one of them reused the session the other obtained.
 */
private class RefusingOnceDispatcher(private val refreshCount: AtomicInteger) : Dispatcher() {

    private val bothRefused = CyclicBarrier(2)
    private val alreadyRefused = ConcurrentHashMap.newKeySet<String>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path.endsWith(TOKEN_PATH)) {
            refreshCount.incrementAndGet()
            return MockResponse(code = 200, body = ROTATED_TOKEN_BODY)
        }
        if (alreadyRefused.add(path)) {
            bothRefused.await(BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return MockResponse(code = 401, body = """{"error":"invalid_token"}""")
        }
        return MockResponse(code = 202, body = """{"status":"provisioning"}""")
    }
}

/** Long enough that a call cannot finish before the test cancels it. */
private const val RESPONSE_DELAY_SECONDS = 30L

/** Upper bound on how long a request may take to reach the server before the test gives up. */
private const val REQUEST_TIMEOUT_SECONDS = 10L

/** Long enough for a rotation to still be in flight when the test cancels its caller. */
private const val ROTATION_DELAY_MILLIS = 300L

private const val DEVICE_CODE_BODY = """
    {
      "device_code": "device-code-1",
      "user_code": "ABCD-1234",
      "verification_uri": "https://paas.example.com/device",
      "verification_uri_complete": "https://paas.example.com/device?user_code=ABCD-1234",
      "expires_in": 900,
      "interval": 5
    }
"""

private const val TOKEN_BODY = """
    {
      "access_token": "access-1",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "ha:provision"
    }
"""

private const val TOKEN_BODY_WITH_REFRESH = """
    {
      "access_token": "access-1",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "ha:provision",
      "refresh_token": "refresh-1"
    }
"""

/** What the backend answers to a refresh: a brand new pair, the previous one being invalidated. */
private const val ROTATED_TOKEN_BODY = """
    {
      "access_token": "access-2",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "ha:provision",
      "refresh_token": "refresh-2"
    }
"""

/**
 * Exercises [WoowPaasRepositoryImpl] against a real HTTP server so URL building, request shape,
 * deserialization and every status code branch are covered end to end.
 */
@OptIn(ExperimentalTime::class)
class WoowPaasRepositoryImplTest {

    private lateinit var server: MockWebServer

    private val clock = FakeClock().apply { currentInstant = NOW }

    /**
     * The starting point of most tests: a usable access token without a refresh token, so a call that is
     * refused reports the session is gone instead of reaching the refresh endpoint. The tests about
     * refreshing seed a session of their own.
     */
    private val sessionRepository = FakeWoowPaasSessionRepository(
        WoowPaasSession(
            accessToken = ACCESS_TOKEN,
            refreshToken = null,
            accessTokenExpiresAt = NOW + ACCESS_TOKEN_LIFETIME,
        ),
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    // region URL building

    @ParameterizedTest
    @CsvSource(
        "'', /oauth2/device_authorization",
        "/woow, /woow/oauth2/device_authorization",
        "/woow/, /woow/oauth2/device_authorization",
    )
    fun `Given a base URL when requesting a device code then the endpoint is appended to its path`(
        basePathSuffix: String,
        expectedPath: String,
    ) = runTest {
        server.enqueue(MockResponse(code = 200, body = DEVICE_CODE_BODY))

        repository(basePathSuffix).requestDeviceCode()

        assertEquals(expectedPath, server.takeRequest().url.encodedPath)
    }

    @Test
    fun `Given a base URL with a path prefix when polling the token then the prefix is kept`() = runTest {
        server.enqueue(MockResponse(code = 200, body = TOKEN_BODY))

        repository("/woow").pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals("/woow/oauth2/token", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `Given a base URL with a path prefix when provisioning then the prefix is kept`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"https://ha.example.com"}"""))

        repository("/woow").provision()

        assertEquals("/woow/api/ha-paas/provision", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `Given a base URL with a path prefix when querying the status then the prefix is kept`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"provisioning"}"""))

        repository("/woow").getStatus()

        assertEquals("/woow/api/ha-paas/status", server.takeRequest().url.encodedPath)
    }

    // endregion

    // region requestDeviceCode

    @Test
    fun `Given a valid payload when requesting a device code then every field is mapped`() = runTest {
        server.enqueue(MockResponse(code = 200, body = DEVICE_CODE_BODY))

        val response = repository().requestDeviceCode().getOrThrow()

        assertEquals("device-code-1", response.deviceCode)
        assertEquals("ABCD-1234", response.userCode)
        assertEquals("https://paas.example.com/device", response.verificationUri)
        assertEquals("https://paas.example.com/device?user_code=ABCD-1234", response.verificationUriComplete)
        assertEquals(900, response.expiresIn)
        assertEquals(5, response.interval)
    }

    @Test
    fun `Given a device code request when it is sent then the client id and scopes are form encoded`() = runTest {
        server.enqueue(MockResponse(code = 200, body = DEVICE_CODE_BODY))

        repository().requestDeviceCode()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(mapOf("client_id" to CLIENT_ID, "scope" to SCOPES), request.formBody())
    }

    @Test
    fun `Given an error payload when requesting a device code then the OAuth error becomes the message`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"invalid_client"}"""))

        val error = assertInstanceOf(ApiException::class.java, repository().requestDeviceCode().exceptionOrNull())

        assertEquals(400, error.code)
        assertEquals("invalid_client", error.message)
    }

    @Test
    fun `Given an error without an error field when requesting a device code then the message says unknown`() = runTest {
        server.enqueue(MockResponse(code = 400, body = "{}"))

        val error = repository().requestDeviceCode().exceptionOrNull()

        assertEquals("unknown_error", error?.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "not json at all"])
    fun `Given an unreadable body when requesting a device code then the failure mentions the status code`(
        body: String,
    ) = runTest {
        server.enqueue(MockResponse(code = 400, body = body))

        val error = repository().requestDeviceCode().exceptionOrNull()

        assertEquals("伺服器回應格式錯誤 (HTTP 400)", error?.message)
    }

    @Test
    fun `Given a payload missing a required field when requesting a device code then it fails`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"device_code":"only-this"}"""))

        val error = repository().requestDeviceCode().exceptionOrNull()

        assertInstanceOf(ApiException::class.java, error)
    }

    // endregion

    // region malformed successful bodies

    /**
     * Pins down a known limitation: Retrofit decodes a successful body while building its response object,
     * so the real status code is gone by the time the failure surfaces and [HTTP_CODE_UNKNOWN] stands in.
     */
    @Test
    fun `Given an unreadable body on a success when requesting a device code then the status code is unknown`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "not json at all"))

        val error = assertInstanceOf(ApiException::class.java, repository().requestDeviceCode().exceptionOrNull())

        assertEquals(HTTP_CODE_UNKNOWN, error.code)
        assertEquals("伺服器回應格式錯誤", error.message)
    }

    @Test
    fun `Given an unreadable body on a success when provisioning then the status code is unknown`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "not json at all"))

        val error = assertInstanceOf(ApiException::class.java, repository().provision().exceptionOrNull())

        assertEquals(HTTP_CODE_UNKNOWN, error.code)
        assertEquals("伺服器回應格式錯誤", error.message)
    }

    @Test
    fun `Given an unreadable body on a success when querying the status then the status code is unknown`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "not json at all"))

        val error = assertInstanceOf(ApiException::class.java, repository().getStatus().exceptionOrNull())

        assertEquals(HTTP_CODE_UNKNOWN, error.code)
        assertEquals("伺服器回應格式錯誤", error.message)
    }

    // endregion

    // region cancellation

    /**
     * The guard with teeth: removing the `CancellationException` re-throw from `runCatchingApi` makes this
     * test fail, while the end to end tests below would still pass because `withContext` re-throws on its
     * own once the calling job is cancelled.
     */
    @Test
    fun `Given a cancellation when running an API call then it is re-thrown instead of captured`() {
        assertThrows(CancellationException::class.java) {
            runCatchingApi { throw CancellationException("cancelled mid call") }
        }
    }

    @Test
    fun `Given the caller is cancelled when requesting a device code then no result is produced`() = runTest {
        assertCancellationIsNotSwallowed { requestDeviceCode() }
    }

    @Test
    fun `Given the caller is cancelled when provisioning then no result is produced`() = runTest {
        assertCancellationIsNotSwallowed { provision() }
    }

    @Test
    fun `Given the caller is cancelled when querying the status then no result is produced`() = runTest {
        assertCancellationIsNotSwallowed { getStatus() }
    }

    // endregion

    // region pollToken

    @Test
    fun `Given an issued token when polling then the session is persisted`() = runTest {
        server.enqueue(MockResponse(code = 200, body = TOKEN_BODY))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(TokenPollResult.Success, result)
        assertEquals(
            WoowPaasSession(
                accessToken = "access-1",
                refreshToken = null,
                accessTokenExpiresAt = NOW + 3600.seconds,
            ),
            sessionRepository.session,
        )
    }

    @Test
    fun `Given an issued token carrying a refresh token when polling then it is persisted too`() = runTest {
        server.enqueue(MockResponse(code = 200, body = TOKEN_BODY_WITH_REFRESH))

        repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals("refresh-1", sessionRepository.session?.refreshToken)
    }

    @Test
    fun `Given a device flow that has not completed when polling then the stored session is left alone`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"authorization_pending"}"""))
        val previous = sessionRepository.session

        repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(previous, sessionRepository.session)
    }

    @Test
    fun `Given a token poll when it is sent then the device flow grant is form encoded`() = runTest {
        server.enqueue(MockResponse(code = 200, body = TOKEN_BODY))

        repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(
            mapOf("grant_type" to GRANT_TYPE, "device_code" to "device-code-1", "client_id" to CLIENT_ID),
            server.takeRequest().formBody(),
        )
    }

    @Test
    fun `Given authorization_pending when polling then the flow keeps waiting`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"authorization_pending"}"""))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(TokenPollResult.Pending, result)
    }

    @Test
    fun `Given slow_down when polling then the interval grows by five seconds`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"slow_down"}"""))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(TokenPollResult.SlowDown(newInterval = 10), result)
    }

    @Test
    fun `Given access_denied when polling then the description becomes the terminal error`() = runTest {
        server.enqueue(
            MockResponse(code = 400, body = """{"error":"access_denied","error_description":"使用者拒絕授權"}"""),
        )

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(TokenPollResult.Failed("使用者拒絕授權"), result)
    }

    @Test
    fun `Given a server error when polling then the flow keeps retrying`() = runTest {
        server.enqueue(MockResponse(code = 500, body = """{"error":"server_error"}"""))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given a token payload missing required fields when polling then it fails terminally`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"token_type":"Bearer"}"""))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertEquals(TokenPollResult.Failed("伺服器回應缺少必要欄位"), result)
    }

    @Test
    fun `Given an unreadable token payload when polling then the flow keeps retrying`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "not json at all"))

        val result = repository().pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    @Test
    fun `Given an unreachable server when polling then the flow keeps retrying`() = runTest {
        val repository = repository()
        server.close()

        val result = repository.pollToken(deviceCode = "device-code-1", currentInterval = 5)

        assertInstanceOf(TokenPollResult.TransientError::class.java, result)
    }

    // endregion

    // region provision

    @Test
    fun `Given a running instance when provisioning then the URL is returned`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"https://ha.example.com"}"""))

        val response = repository().provision().getOrThrow()

        assertEquals(ProvisionStatus.Ready, response.status)
        assertEquals("https://ha.example.com", response.haUrl)
    }

    @Test
    fun `Given an instance being created when provisioning then the status is Provisioning`() = runTest {
        server.enqueue(MockResponse(code = 202, body = """{"status":"provisioning"}"""))

        val response = repository().provision().getOrThrow()

        assertEquals(ProvisionStatus.Provisioning, response.status)
        assertNull(response.haUrl)
    }

    @ParameterizedTest
    @CsvSource("suspended", "deleting")
    fun `Given a conflicting instance state when provisioning then the 409 body is reported as data`(
        rawStatus: String,
    ) = runTest {
        server.enqueue(MockResponse(code = 409, body = """{"status":"$rawStatus","error":"instance is $rawStatus"}"""))

        val response = repository().provision().getOrThrow()

        assertEquals(rawStatus, response.status.rawValue)
        assertEquals("instance is $rawStatus", response.error)
    }

    @ParameterizedTest
    @CsvSource(
        "401, 登入已過期，請返回重新登入",
        "403, 權限不足（缺少 ha:provision scope）",
        "503, 服務尚未開放，請稍後再試",
    )
    fun `Given a refused provisioning when provisioning then the status code drives the message`(
        code: Int,
        expectedMessage: String,
    ) = runTest {
        server.enqueue(MockResponse(code = code, body = """{"error":"ignored"}"""))

        val error = assertInstanceOf(ApiException::class.java, repository().provision().exceptionOrNull())

        assertEquals(code, error.code)
        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun `Given an unexpected status code when provisioning then the reported error is used`() = runTest {
        server.enqueue(MockResponse(code = 500, body = """{"error":"boom"}"""))

        val error = repository().provision().exceptionOrNull()

        assertEquals("boom", error?.message)
    }

    @Test
    fun `Given an unexpected status code without an error when provisioning then the message mentions it`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "{}"))

        val error = repository().provision().exceptionOrNull()

        assertEquals("未知錯誤 (HTTP 500)", error?.message)
    }

    @Test
    fun `Given a provisioning request when it is sent then it carries the bearer token`() = runTest {
        server.enqueue(MockResponse(code = 202, body = """{"status":"provisioning"}"""))

        repository().provision()

        assertEquals("Bearer $ACCESS_TOKEN", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `Given an unreachable server when provisioning then the failure stays an IOException`() = runTest {
        val repository = repository()
        server.close()

        val error = repository.provision().exceptionOrNull()

        assertInstanceOf(IOException::class.java, error)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "null"])
    fun `Given a blank URL placeholder when provisioning then it is read as no URL`(haUrl: String) = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"$haUrl"}"""))

        val response = repository().provision().getOrThrow()

        assertNull(response.haUrl)
    }

    // endregion

    // region getStatus

    @Test
    fun `Given a ready instance when querying the status then the URL is returned`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"https://ha.example.com"}"""))

        val response = repository().getStatus().getOrThrow()

        assertEquals(ProvisionStatus.Ready, response.status)
        assertEquals("https://ha.example.com", response.haUrl)
    }

    @Test
    fun `Given a failed provisioning when querying the status then the reported error is carried`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"error","error":"quota exceeded"}"""))

        val response = repository().getStatus().getOrThrow()

        assertEquals(ProvisionStatus.Error, response.status)
        assertEquals("quota exceeded", response.error)
    }

    @ParameterizedTest
    @CsvSource("none", "provisioning", "suspended", "deleting")
    fun `Given a documented status when querying the status then it is mapped to its own case`(
        rawStatus: String,
    ) = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"$rawStatus"}"""))

        val response = repository().getStatus().getOrThrow()

        assertEquals(ProvisionStatus.from(rawStatus), response.status)
    }

    @Test
    fun `Given a status this version does not know when querying the status then it stays Unknown`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"hibernating"}"""))

        val response = repository().getStatus().getOrThrow()

        assertEquals(ProvisionStatus.Unknown("hibernating"), response.status)
    }

    @Test
    fun `Given a status query when it is sent then it is a GET carrying the bearer token`() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"status":"provisioning"}"""))

        repository().getStatus()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer $ACCESS_TOKEN", request.headers["Authorization"])
    }

    @Test
    fun `Given a refused status query when querying the status then a new sign in is required`() = runTest {
        server.enqueue(MockResponse(code = 401, body = """{"error":"token expired"}"""))

        val error = repository().getStatus().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
        assertEquals(SESSION_EXPIRED_MESSAGE, error?.message)
    }

    @Test
    fun `Given a refused status query without an error when querying the status then the message mentions it`() = runTest {
        server.enqueue(MockResponse(code = 500, body = "{}"))

        val error = repository().getStatus().exceptionOrNull()

        assertEquals("查詢狀態失敗 (HTTP 500)", error?.message)
    }

    @Test
    fun `Given an unreachable server when querying the status then the failure stays an IOException`() = runTest {
        val repository = repository()
        server.close()

        val error = repository.getStatus().exceptionOrNull()

        assertInstanceOf(IOException::class.java, error)
    }

    // endregion

    // region session and refresh

    @Test
    fun `Given no stored session when provisioning then a new sign in is required without any request`() = runTest {
        sessionRepository.session = null

        val error = repository().provision().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `Given an expired access token when provisioning then it is refreshed before the call`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 202, body = """{"status":"provisioning"}"""))

        val response = repository().provision().getOrThrow()

        assertEquals(ProvisionStatus.Provisioning, response.status)
        val refreshRequest = server.takeRequest()
        assertEquals("/oauth2/token", refreshRequest.url.encodedPath)
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `Given a refresh request when it is sent then it carries no client secret`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 202, body = """{"status":"provisioning"}"""))

        repository().provision()

        // A public client that sends a secret is refused with a 401, so the body carries exactly three
        // fields (RFC 6749 §6).
        assertEquals(
            mapOf(
                "grant_type" to REFRESH_GRANT_TYPE,
                "refresh_token" to REFRESH_TOKEN,
                "client_id" to CLIENT_ID,
            ),
            server.takeRequest().formBody(),
        )
    }

    @Test
    fun `Given a successful refresh when it completed then the rotated session replaced the previous one`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 202, body = """{"status":"provisioning"}"""))

        repository().provision()

        assertEquals(
            WoowPaasSession(
                accessToken = "access-2",
                refreshToken = "refresh-2",
                accessTokenExpiresAt = NOW + 3600.seconds,
            ),
            sessionRepository.session,
        )
    }

    @Test
    fun `Given a token refused mid flight when provisioning then it is refreshed and the call replayed`() = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"https://ha.example.com"}"""))

        val response = repository().provision().getOrThrow()

        assertEquals(ProvisionStatus.Ready, response.status)
        assertEquals(3, server.requestCount)
        assertEquals("Bearer $ACCESS_TOKEN", server.takeRequest().headers["Authorization"])
        server.takeRequest()
        assertEquals("Bearer access-2", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `Given a token refused mid flight when querying the status then it is refreshed and the call replayed`() = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 200, body = """{"status":"ready","ha_url":"https://ha.example.com"}"""))

        val response = repository().getStatus().getOrThrow()

        assertEquals(ProvisionStatus.Ready, response.status)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `Given a reused refresh token when refreshing then the session is dropped and a new sign in is required`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 400, body = """{"error":"invalid_grant"}"""))

        val error = repository().provision().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
        assertNull(sessionRepository.session, "a session that cannot be refreshed must not survive")
    }

    @Test
    fun `Given the refresh endpoint failing when refreshing then the session survives and the failure is retryable`() = runTest {
        val session = expiredSession(refreshToken = REFRESH_TOKEN)
        sessionRepository.session = session
        server.enqueue(MockResponse(code = 503, body = """{"error":"unavailable"}"""))

        val error = repository().provision().exceptionOrNull()

        // A backend hiccup says nothing about the token: dropping the session here would force a
        // pointless new device flow.
        assertInstanceOf(ApiException::class.java, error)
        assertFalse(error is SessionExpiredException)
        assertEquals(session, sessionRepository.session)
    }

    @Test
    fun `Given an expired session without a refresh token when provisioning then it is dropped`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = null)

        val error = repository().provision().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
        assertNull(sessionRepository.session)
        assertEquals(0, server.requestCount)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "not json at all"])
    fun `Given a replay refused with a body that cannot be read when provisioning then a new sign in is required`(
        body: String,
    ) = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 401, body = body))

        val error = repository().provision().exceptionOrNull()

        // What the backend puts in a 401 changes nothing: reporting a parsing problem instead would offer a
        // retry that cannot possibly work.
        assertInstanceOf(SessionExpiredException::class.java, error)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "not json at all"])
    fun `Given a replay refused with a body that cannot be read when querying the status then a new sign in is required`(
        body: String,
    ) = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 401, body = body))

        val error = repository().getStatus().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
    }

    @Test
    fun `Given a token still refused after a refresh when provisioning then a new sign in is required`() = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))
        server.enqueue(MockResponse(code = 200, body = ROTATED_TOKEN_BODY))
        server.enqueue(MockResponse(code = 401, body = """{"error":"invalid_token"}"""))

        val error = repository().provision().exceptionOrNull()

        assertInstanceOf(SessionExpiredException::class.java, error)
    }

    @Test
    fun `Given the caller is cancelled while the session rotates then the new session is still stored`() = runTest {
        sessionRepository.session = expiredSession(refreshToken = REFRESH_TOKEN)
        // Slow enough that the caller is provably cancelled while the backend is answering the refresh.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(ROTATED_TOKEN_BODY)
                .bodyDelay(ROTATION_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .build(),
        )
        val repository = repository()

        val job = launch(Dispatchers.IO) { repository.provision() }
        val request = withContext(Dispatchers.IO) { server.takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        assertNotNull(request, "the refresh never reached the server, so nothing was cancelled mid rotation")
        job.cancel()
        job.join()

        // The backend invalidated the old pair the moment it answered. Giving up on the answer would leave
        // the account holding credentials that are already dead, and the user signing in again for nothing.
        assertEquals("access-2", sessionRepository.session?.accessToken)
        assertEquals("refresh-2", sessionRepository.session?.refreshToken)
    }

    @Test
    fun `Given concurrent calls refused at once when they refresh then the refresh token is spent once`() = runTest {
        sessionRepository.session = usableSession(refreshToken = REFRESH_TOKEN)
        val refreshCount = AtomicInteger()
        server.dispatcher = RefusingOnceDispatcher(refreshCount)
        val repository = repository()

        val results = withContext(Dispatchers.IO) {
            listOf(async { repository.provision() }, async { repository.getStatus() }).awaitAll()
        }

        // The backend invalidates a refresh token as soon as it is used, so a second concurrent refresh
        // would kill the session both calls just repaired.
        assertEquals(1, refreshCount.get())
        results.forEach { assertNull(it.exceptionOrNull()) }
        assertEquals("access-2", sessionRepository.session?.accessToken)
    }

    // endregion

    private fun usableSession(refreshToken: String?) = WoowPaasSession(
        accessToken = ACCESS_TOKEN,
        refreshToken = refreshToken,
        accessTokenExpiresAt = NOW + ACCESS_TOKEN_LIFETIME,
    )

    private fun expiredSession(refreshToken: String?) = WoowPaasSession(
        accessToken = ACCESS_TOKEN,
        refreshToken = refreshToken,
        accessTokenExpiresAt = NOW - 1.seconds,
    )

    /**
     * Starts [call], waits until its request provably reached the server, cancels the caller and checks that
     * the repository surfaced the cancellation rather than turning it into a [Result].
     */
    private suspend fun CoroutineScope.assertCancellationIsNotSwallowed(call: suspend WoowPaasRepository.() -> Any) {
        // A slow body keeps the call in flight long enough to be cancelled while it is still waiting.
        server.enqueue(
            MockResponse.Builder().code(200).body("{}").bodyDelay(RESPONSE_DELAY_SECONDS, TimeUnit.SECONDS).build(),
        )
        val repository = repository()
        val outcome = CompletableDeferred<Any>()

        val job = launch(Dispatchers.IO) {
            try {
                outcome.complete(repository.call())
            } catch (e: CancellationException) {
                outcome.complete(e)
            }
        }
        val request = withContext(Dispatchers.IO) { server.takeRequest(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        assertNotNull(request, "the call never reached the server, so nothing was cancelled mid flight")
        job.cancel()

        assertInstanceOf(CancellationException::class.java, outcome.await())
    }

    /**
     * Decodes an `application/x-www-form-urlencoded` request body so the assertions talk about the values
     * the backend receives instead of pinning down which of the two legal encodings of a space is used.
     */
    private fun RecordedRequest.formBody(): Map<String, String> = body?.utf8().orEmpty()
        .split("&")
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val name = pair.substringBefore("=")
            val value = pair.substringAfter("=", missingDelimiterValue = "")
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }

    private fun repository(basePathSuffix: String = ""): WoowPaasRepository = WoowPaasRepositoryImpl(
        config = WoowPaasApiConfig(
            baseUrl = server.url("/").toString().removeSuffix("/") + basePathSuffix,
            clientId = CLIENT_ID,
            scopes = SCOPES,
            deviceCodeGrantType = GRANT_TYPE,
            refreshTokenGrantType = REFRESH_GRANT_TYPE,
        ),
        sessionRepository = sessionRepository,
        clock = clock,
    )
}
