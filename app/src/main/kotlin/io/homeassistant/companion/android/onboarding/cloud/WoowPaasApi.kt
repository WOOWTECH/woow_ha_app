package io.homeassistant.companion.android.onboarding.cloud

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

internal data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int,
)

internal data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val scope: String,
    val refreshToken: String?,
)

internal sealed interface TokenPollResult {
    data class Success(val token: TokenResponse) : TokenPollResult
    data object Pending : TokenPollResult
    data class SlowDown(val newInterval: Int) : TokenPollResult

    /**
     * A retryable failure such as a network hiccup or an HTTP 5xx response.
     * Per RFC 8628 §3.4 the client should keep polling instead of aborting the flow.
     */
    data class TransientError(val reason: String) : TokenPollResult

    /**
     * A terminal failure such as `access_denied`, `expired_token` or any other OAuth error.
     * Polling must stop.
     */
    data class Failed(val error: String) : TokenPollResult
}

private const val SLOW_DOWN_INTERVAL_INCREMENT_SECONDS = 5
private val HTTP_SERVER_ERROR_RANGE = 500..599

private object OAuthDeviceFlowError {
    const val AUTHORIZATION_PENDING = "authorization_pending"
    const val SLOW_DOWN = "slow_down"
}

/**
 * Classifies a non-successful token endpoint response into a [TokenPollResult].
 *
 * Follows RFC 8628 §3.5: `authorization_pending` and `slow_down` keep the flow alive, an HTTP 5xx is
 * a transient server-side failure worth retrying, and every other OAuth error (`access_denied`,
 * `expired_token`, …) is terminal.
 *
 * @param httpCode the HTTP status code of the response
 * @param error the parsed OAuth `error` field, or null when it is absent or unparseable
 * @param errorDescription the parsed OAuth `error_description` field, if any
 * @param currentInterval the current polling interval in seconds, used to compute the slow-down backoff
 */
internal fun classifyTokenError(
    httpCode: Int,
    error: String?,
    errorDescription: String?,
    currentInterval: Int,
): TokenPollResult {
    if (httpCode in HTTP_SERVER_ERROR_RANGE) {
        return TokenPollResult.TransientError("伺服器暫時無法回應 (HTTP $httpCode)")
    }
    return when (error) {
        OAuthDeviceFlowError.AUTHORIZATION_PENDING -> TokenPollResult.Pending
        OAuthDeviceFlowError.SLOW_DOWN -> TokenPollResult.SlowDown(
            currentInterval + SLOW_DOWN_INTERVAL_INCREMENT_SECONDS,
        )
        else -> TokenPollResult.Failed(errorDescription ?: error ?: "授權失敗")
    }
}

/**
 * Classifies an exception raised while polling the token endpoint.
 *
 * Network-layer failures ([IOException] and its subtypes such as [java.net.SocketTimeoutException] and
 * [java.net.UnknownHostException]) are transient and worth retrying; anything else is terminal. A
 * [CancellationException] is re-thrown so structured coroutine cancellation is never swallowed.
 */
internal fun classifyTokenException(e: Exception): TokenPollResult {
    if (e is CancellationException) throw e
    return when (e) {
        is IOException -> TokenPollResult.TransientError(e.message ?: "網路連線中斷")
        else -> TokenPollResult.Failed(e.message ?: "登入發生錯誤")
    }
}

internal data class ProvisionResponse(val status: String, val haUrl: String? = null, val error: String? = null)

internal data class StatusResponse(val status: String, val haUrl: String? = null, val error: String? = null)

internal class WoowPaasApi {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun Response.parseJsonBody(): JSONObject {
        val bodyString = body?.string() ?: throw ApiException(code, "伺服器回應為空")
        return try {
            JSONObject(bodyString)
        } catch (e: JSONException) {
            throw ApiException(code, "伺服器回應格式錯誤 (HTTP $code)")
        }
    }

    private fun JSONObject.nullableString(key: String): String? {
        val value = optString(key, "")
        return if (value.isEmpty() || value == "null") null else value
    }

    suspend fun requestDeviceCode(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", WoowPaasConfig.CLIENT_ID)
                .add("scope", WoowPaasConfig.SCOPES)
                .build()

            val request = Request.Builder()
                .url("${WoowPaasConfig.BASE_URL}/oauth2/device_authorization")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.parseJsonBody()

                if (!response.isSuccessful) {
                    throw ApiException(response.code, json.nullableString("error") ?: "unknown_error")
                }

                DeviceCodeResponse(
                    deviceCode = json.getString("device_code"),
                    userCode = json.getString("user_code"),
                    verificationUri = json.getString("verification_uri"),
                    verificationUriComplete = json.getString("verification_uri_complete"),
                    expiresIn = json.getInt("expires_in"),
                    interval = json.getInt("interval"),
                )
            }
        }
    }

    suspend fun pollToken(deviceCode: String, currentInterval: Int): TokenPollResult = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", WoowPaasConfig.DEVICE_CODE_GRANT_TYPE)
                .add("device_code", deviceCode)
                .add("client_id", WoowPaasConfig.CLIENT_ID)
                .build()

            val request = Request.Builder()
                .url("${WoowPaasConfig.BASE_URL}/oauth2/token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                mapTokenResponse(response, currentInterval)
            }
        } catch (e: CancellationException) {
            // Never swallow cancellation: it must propagate so the polling coroutine is cancelled
            throw e
        } catch (e: Exception) {
            classifyTokenException(e)
        }
    }

    private fun mapTokenResponse(response: Response, currentInterval: Int): TokenPollResult {
        val json = response.parseJsonBodyOrNull()
        return if (response.isSuccessful) {
            json?.toTokenPollResult() ?: TokenPollResult.TransientError("伺服器回應格式錯誤")
        } else {
            classifyTokenError(
                httpCode = response.code,
                error = json?.nullableString("error"),
                errorDescription = json?.nullableString("error_description"),
                currentInterval = currentInterval,
            )
        }
    }

    private fun Response.parseJsonBodyOrNull(): JSONObject? {
        val bodyString = body?.string()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            JSONObject(bodyString)
        } catch (e: JSONException) {
            null
        }
    }

    private fun JSONObject.toTokenPollResult(): TokenPollResult = try {
        TokenPollResult.Success(
            TokenResponse(
                accessToken = getString("access_token"),
                tokenType = getString("token_type"),
                expiresIn = getInt("expires_in"),
                scope = getString("scope"),
                refreshToken = nullableString("refresh_token"),
            ),
        )
    } catch (e: JSONException) {
        TokenPollResult.Failed("伺服器回應缺少必要欄位")
    }

    suspend fun provision(accessToken: String): Result<ProvisionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${WoowPaasConfig.BASE_URL}/api/ha-paas/provision")
                .post(FormBody.Builder().build())
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.parseJsonBody()

                when (response.code) {
                    200, 202 -> ProvisionResponse(
                        status = json.getString("status"),
                        haUrl = json.nullableString("ha_url"),
                    )
                    409 -> ProvisionResponse(
                        status = json.getString("status"),
                        error = json.nullableString("error"),
                    )
                    401 -> throw ApiException(401, "登入已過期，請返回重新登入")
                    403 -> throw ApiException(403, "權限不足（缺少 ha:provision scope）")
                    503 -> throw ApiException(503, "服務尚未開放，請稍後再試")
                    else -> throw ApiException(
                        response.code,
                        json.nullableString("error") ?: "未知錯誤 (HTTP ${response.code})",
                    )
                }
            }
        }
    }

    suspend fun getStatus(accessToken: String): Result<StatusResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${WoowPaasConfig.BASE_URL}/api/ha-paas/status")
                .get()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                val json = response.parseJsonBody()

                if (!response.isSuccessful) {
                    throw ApiException(
                        response.code,
                        json.nullableString("error") ?: "查詢狀態失敗 (HTTP ${response.code})",
                    )
                }

                StatusResponse(
                    status = json.getString("status"),
                    haUrl = json.nullableString("ha_url"),
                    error = json.nullableString("error"),
                )
            }
        }
    }
}

internal class ApiException(val code: Int, message: String) : Exception(message)
