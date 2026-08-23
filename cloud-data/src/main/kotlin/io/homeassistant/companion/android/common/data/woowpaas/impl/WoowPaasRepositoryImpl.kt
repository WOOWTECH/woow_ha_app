package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.ApiException
import io.homeassistant.companion.android.common.data.woowpaas.DeviceCodeResponse
import io.homeassistant.companion.android.common.data.woowpaas.HTTP_CODE_UNKNOWN
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionResponse
import io.homeassistant.companion.android.common.data.woowpaas.ProvisionStatus
import io.homeassistant.companion.android.common.data.woowpaas.SessionExpiredException
import io.homeassistant.companion.android.common.data.woowpaas.StatusResponse
import io.homeassistant.companion.android.common.data.woowpaas.TokenPollResult
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasApiConfig
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasRepository
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSession
import io.homeassistant.companion.android.common.data.woowpaas.WoowPaasSessionRepository
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.DeviceCodeResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.ProvisionResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.StatusResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.TokenResponseDto
import java.net.HttpURLConnection.HTTP_ACCEPTED
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 15L
private const val WRITE_TIMEOUT_SECONDS = 15L
private const val CALL_TIMEOUT_SECONDS = 30L

/**
 * Retrofit refuses to be built without a base URL even when every call carries its own [okhttp3.HttpUrl]
 * through `@Url`. This placeholder is never contacted.
 */
private const val UNUSED_RETROFIT_BASE_URL = "http://localhost/"

private const val JSON_MEDIA_TYPE = "application/json; charset=UTF-8"
private const val BEARER_PREFIX = "Bearer "

internal const val MALFORMED_BODY_MESSAGE = "伺服器回應格式錯誤"
private const val MISSING_FIELDS_MESSAGE = "伺服器回應缺少必要欄位"
private const val UNKNOWN_OAUTH_ERROR = "unknown_error"
private const val SESSION_EXPIRED_MESSAGE = "登入已過期，請返回重新登入"
private const val REFRESH_UNAVAILABLE_MESSAGE = "無法更新登入狀態，請稍後再試"

/**
 * The literal the backend sometimes sends instead of a JSON null for an absent string.
 */
private const val NULL_LITERAL = "null"

/**
 * Retrofit backed [WoowPaasRepository].
 *
 * The HTTP stack is built lazily on first use: creating an [OkHttpClient] performs disk and TLS setup,
 * and this repository is injected into view models that are constructed on the main thread. Every
 * function switches to [Dispatchers.IO] before touching the network, so callers do not have to.
 *
 * The authenticated calls read their credentials from [sessionRepository] and refresh them when needed.
 * Refreshing is serialized: the backend invalidates a refresh token the moment it is used, so two calls
 * spending the same one would leave the account without a session at all.
 *
 * @param config the environment this instance talks to; its `baseUrl` may carry a path prefix, in which
 * case every endpoint is appended to it.
 * @param sessionRepository where the credentials issued by the device flow are kept
 * @param clock used to tell whether an access token is still worth sending
 */
@OptIn(ExperimentalTime::class)
internal class WoowPaasRepositoryImpl @Inject constructor(
    private val config: WoowPaasApiConfig,
    private val sessionRepository: WoowPaasSessionRepository,
    private val clock: Clock,
) : WoowPaasRepository {

    private val baseUrl: HttpUrl by lazy { config.baseUrl.toHttpUrl() }

    private val refreshMutex = Mutex()

    private val service: WoowPaasService by lazy {
        Retrofit.Builder()
            .baseUrl(UNUSED_RETROFIT_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
            .addConverterFactory(woowPaasJsonMapper.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(WoowPaasService::class.java)
    }

    override suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatchingApi {
            val response = service.requestDeviceCode(
                url = endpoint(WoowPaasEndpoints.DEVICE_AUTHORIZATION),
                clientId = config.clientId,
                scope = config.scopes,
            )
            val dto = response.bodyOrErrorBody(::decodeDeviceCodeError)

            if (!response.isSuccessful) {
                throw ApiException(response.code(), dto.error.orApiNull() ?: UNKNOWN_OAUTH_ERROR)
            }
            dto.toDeviceCodeResponse(response.code())
        }
    }

    override suspend fun pollToken(deviceCode: String, currentInterval: Int): TokenPollResult =
        withContext(Dispatchers.IO) {
            try {
                val response = service.pollToken(
                    url = endpoint(WoowPaasEndpoints.TOKEN),
                    grantType = config.deviceCodeGrantType,
                    deviceCode = deviceCode,
                    clientId = config.clientId,
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body == null) {
                        TokenPollResult.TransientError(MALFORMED_BODY_MESSAGE)
                    } else {
                        persistIssuedSession(body)
                    }
                } else {
                    val dto = decodeTokenError(response.errorBodyText())
                    classifyTokenError(
                        httpCode = response.code(),
                        error = dto?.error.orApiNull(),
                        errorDescription = dto?.errorDescription.orApiNull(),
                        currentInterval = currentInterval,
                    )
                }
            } catch (e: CancellationException) {
                // Never swallow cancellation: it must propagate so the polling coroutine is cancelled
                throw e
            } catch (e: SerializationException) {
                TokenPollResult.TransientError(MALFORMED_BODY_MESSAGE)
            } catch (e: Exception) {
                classifyTokenException(e)
            }
        }

    override suspend fun provision(): Result<ProvisionResponse> = withContext(Dispatchers.IO) {
        runCatchingApi {
            val response = authenticated { accessToken ->
                service.provision(
                    url = endpoint(WoowPaasEndpoints.PROVISION),
                    authorization = BEARER_PREFIX + accessToken,
                    body = FormBody.Builder().build(),
                )
            }
            val code = response.code()
            // Decided before the body is looked at: whatever a refused answer carries, the credentials were
            // already refreshed and replayed by then, so nothing but a new sign in helps.
            if (code == HTTP_UNAUTHORIZED) throw dropExpiredSession()
            val dto = response.bodyOrErrorBody(::decodeProvisionError)

            when (code) {
                HTTP_OK, HTTP_ACCEPTED -> ProvisionResponse(
                    status = dto.status.toProvisionStatus(code),
                    haUrl = dto.haUrl.orApiNull(),
                )
                // A 409 is data, not a failure: it reports an instance that is suspended or being deleted.
                HTTP_CONFLICT -> ProvisionResponse(
                    status = dto.status.toProvisionStatus(code),
                    error = dto.error.orApiNull(),
                )
                HTTP_FORBIDDEN -> throw ApiException(code, "權限不足（缺少 ha:provision scope）")
                HTTP_UNAVAILABLE -> throw ApiException(code, "服務尚未開放，請稍後再試")
                else -> throw ApiException(code, dto.error.orApiNull() ?: "未知錯誤 (HTTP $code)")
            }
        }
    }

    override suspend fun getStatus(): Result<StatusResponse> = withContext(Dispatchers.IO) {
        runCatchingApi {
            val response = authenticated { accessToken ->
                service.getStatus(
                    url = endpoint(WoowPaasEndpoints.STATUS),
                    authorization = BEARER_PREFIX + accessToken,
                )
            }
            val code = response.code()
            if (code == HTTP_UNAUTHORIZED) throw dropExpiredSession()
            val dto = response.bodyOrErrorBody(::decodeStatusError)

            if (!response.isSuccessful) {
                throw ApiException(code, dto.error.orApiNull() ?: "查詢狀態失敗 (HTTP $code)")
            }
            StatusResponse(
                status = dto.status.toProvisionStatus(code),
                haUrl = dto.haUrl.orApiNull(),
                error = dto.error.orApiNull(),
            )
        }
    }

    /**
     * Persists the session carried by a successful token response.
     *
     * @return [TokenPollResult.Success] once the session is stored, or a terminal failure when the payload
     * carried no usable session
     */
    private suspend fun persistIssuedSession(dto: TokenResponseDto): TokenPollResult {
        val session = dto.toSession(clock.now()) ?: return TokenPollResult.Failed(MISSING_FIELDS_MESSAGE)
        sessionRepository.saveSession(session)
        return TokenPollResult.Success
    }

    /**
     * Runs [call] with the access token of the stored session, refreshing that session when needed.
     *
     * The session is rotated before the call when its access token is about to expire, and once more when
     * the backend refuses a token this client believed valid. The call is replayed a single time: a second
     * refusal means the account lost its session for good.
     *
     * @throws SessionExpiredException when no stored session can authenticate any more
     */
    private suspend fun <T : Any> authenticated(call: suspend (accessToken: String) -> Response<T>): Response<T> {
        val stored = sessionRepository.currentSession() ?: throw SessionExpiredException(SESSION_EXPIRED_MESSAGE)
        val session = if (stored.isAccessTokenUsableAt(clock.now())) stored else rotateSession(stored)

        val response = call(session.accessToken)
        if (response.code() != HTTP_UNAUTHORIZED) return response

        return call(rotateSession(session).accessToken)
    }

    /**
     * Exchanges the refresh token of the stored session for a brand new one and persists it.
     *
     * Runs under a lock because the backend invalidates a refresh token as soon as it is used: a second
     * caller arriving here while a rotation is in flight waits, then reuses its result instead of spending
     * a token that is already gone. Such a caller recognises the situation by finding a stored session that
     * is no longer the [spent] one it came in with.
     *
     * @param spent the session that turned out to be unusable, compared as a whole rather than by its
     * access token alone so a value reappearing could never be mistaken for the session never having moved
     * @throws SessionExpiredException when the session cannot be rotated, in which case it is dropped
     * @throws ApiException when the backend could not answer, in which case the session is kept so the
     * caller can try again later
     */
    private suspend fun rotateSession(spent: WoowPaasSession): WoowPaasSession = refreshMutex.withLock {
        val current = sessionRepository.currentSession() ?: throw SessionExpiredException(SESSION_EXPIRED_MESSAGE)
        if (current != spent) return@withLock current

        val refreshToken = current.refreshToken ?: throw dropExpiredSession()
        // Cancellation is held off from the moment the refresh token is handed over until the session that
        // replaces it is stored. The backend invalidates the old pair as soon as it answers, so giving up in
        // between would leave the account holding credentials that are already dead, and the user would have
        // to sign in again for no reason. The window is bounded by the call timeout of the HTTP client.
        withContext(NonCancellable) {
            val response = service.refreshToken(
                url = endpoint(WoowPaasEndpoints.TOKEN),
                grantType = config.refreshTokenGrantType,
                refreshToken = refreshToken,
                clientId = config.clientId,
            )
            if (!response.isSuccessful) throw refreshRefusal(response.code())

            val session = response.body()?.toSession(clock.now())
                ?: throw ApiException(response.code(), "$MISSING_FIELDS_MESSAGE (HTTP ${response.code()})")
            sessionRepository.saveSession(session)
            session
        }
    }

    /**
     * Tells apart a refresh the backend could not serve from one it refused.
     *
     * A server side failure says nothing about the credentials, so the session is kept and the caller is
     * free to try again; anything else means the refresh token is spent or revoked (`invalid_grant`) and
     * only a new device flow recovers.
     */
    private suspend fun refreshRefusal(httpCode: Int): Exception = if (httpCode in HTTP_SERVER_ERROR_RANGE) {
        ApiException(httpCode, REFRESH_UNAVAILABLE_MESSAGE)
    } else {
        dropExpiredSession()
    }

    /** Drops the session that cannot authenticate any more and reports that a new device flow is needed. */
    private suspend fun dropExpiredSession(): SessionExpiredException {
        sessionRepository.clearSession()
        return SessionExpiredException(SESSION_EXPIRED_MESSAGE)
    }

    /** Appends [segments] to the configured base URL, keeping any path prefix it already carries. */
    private fun endpoint(segments: String): HttpUrl = baseUrl.newBuilder().addPathSegments(segments).build()
}

/**
 * Runs an API call and turns every failure into a failed [Result], the way the callers expect.
 *
 * A [CancellationException] is re-thrown instead: these calls happen inside polling loops, and reporting
 * a cancelled call as a failure would surface a spurious error to the user.
 *
 * Known limitation: when a successful response carries a body that is not readable JSON, Retrofit runs the
 * converter while building the response object, so the failure surfaces before there is anything to read
 * the status code from. Such a failure is reported with [HTTP_CODE_UNKNOWN] rather than the real code. The
 * callers only branch on 401, 403 and 503, none of which can reach this path, so the placeholder never
 * changes what the user is shown.
 */
internal inline fun <T> runCatchingApi(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    Result.failure(e)
} catch (e: SerializationException) {
    Result.failure(ApiException(HTTP_CODE_UNKNOWN, MALFORMED_BODY_MESSAGE))
} catch (e: Exception) {
    Timber.e(e, "WOOW PaaS call failed")
    Result.failure(e)
}

/**
 * Returns the payload of [this] response, falling back to [decodeErrorBody] when the call was not successful.
 *
 * Retrofit only runs the converter on successful responses, but this API answers with a meaningful JSON
 * body on failures too (the OAuth `error` field, the state behind a 409), so those are decoded by the caller
 * with the concrete payload type.
 *
 * @throws ApiException when the body is empty or is not JSON
 */
private fun <T : Any> Response<T>.bodyOrErrorBody(decodeErrorBody: (String) -> T?): T {
    val payload = if (isSuccessful) body() else decodeErrorBody(errorBodyText())
    return payload ?: throw ApiException(code(), "$MALFORMED_BODY_MESSAGE (HTTP ${code()})")
}

/** Reads the error body of [this] response, treating an absent body as an empty one. */
private fun Response<*>.errorBodyText(): String = errorBody()?.string().orEmpty()

/** Turns a payload that is not readable JSON into a null instead of an exception. */
private inline fun <T : Any> decodeOrNull(decode: () -> T): T? = try {
    decode()
} catch (e: SerializationException) {
    null
}

private fun decodeDeviceCodeError(json: String): DeviceCodeResponseDto? =
    decodeOrNull { woowPaasJsonMapper.decodeFromString<DeviceCodeResponseDto>(json) }

private fun decodeTokenError(json: String): TokenResponseDto? =
    decodeOrNull { woowPaasJsonMapper.decodeFromString<TokenResponseDto>(json) }

private fun decodeProvisionError(json: String): ProvisionResponseDto? =
    decodeOrNull { woowPaasJsonMapper.decodeFromString<ProvisionResponseDto>(json) }

private fun decodeStatusError(json: String): StatusResponseDto? =
    decodeOrNull { woowPaasJsonMapper.decodeFromString<StatusResponseDto>(json) }

/**
 * Mirrors the leniency the previous hand written client had: an empty string and the literal `"null"`
 * both mean "the backend did not provide this value".
 */
private fun String?.orApiNull(): String? = this?.takeIf { it.isNotEmpty() && it != NULL_LITERAL }

/**
 * @throws ApiException when the backend omitted the status, which every documented answer carries
 */
private fun String?.toProvisionStatus(httpCode: Int): ProvisionStatus =
    ProvisionStatus.from(this ?: throw ApiException(httpCode, "$MISSING_FIELDS_MESSAGE (HTTP $httpCode)"))

/**
 * @throws ApiException when a field required by RFC 8628 §3.2 is missing
 */
private fun DeviceCodeResponseDto.toDeviceCodeResponse(httpCode: Int): DeviceCodeResponse {
    fun <T : Any> T?.required(): T = this ?: throw ApiException(httpCode, "$MISSING_FIELDS_MESSAGE (HTTP $httpCode)")

    return DeviceCodeResponse(
        deviceCode = deviceCode.required(),
        userCode = userCode.required(),
        verificationUri = verificationUri.required(),
        verificationUriComplete = verificationUriComplete.required(),
        expiresIn = expiresIn.required(),
        interval = interval.required(),
    )
}

/**
 * Turns a token payload into the session to store, or null when the backend answered a 2xx that does not
 * actually carry usable credentials.
 *
 * Only the two fields the application depends on are required: the token to send and how long it lasts.
 * `token_type` and `scope` are echoed back by the backend but nothing branches on them, so a payload
 * missing them still produces a working session.
 *
 * @param issuedAt when the answer was received, from which the expiry of the access token is computed
 */
@OptIn(ExperimentalTime::class)
private fun TokenResponseDto.toSession(issuedAt: Instant): WoowPaasSession? = WoowPaasSession(
    accessToken = accessToken.orApiNull() ?: return null,
    refreshToken = refreshToken.orApiNull(),
    accessTokenExpiresAt = issuedAt + (expiresIn ?: return null).seconds,
)
