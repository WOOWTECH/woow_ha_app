package io.homeassistant.companion.android.onboarding.cloud

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
    data class Failed(val error: String) : TokenPollResult
}

internal data class ProvisionResponse(
    val status: String,
    val haUrl: String? = null,
    val error: String? = null,
)

internal data class StatusResponse(
    val status: String,
    val haUrl: String? = null,
    val error: String? = null,
)

internal class WoowPaasApi @Inject constructor() {

    private val client = OkHttpClient()

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

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body!!.string())

            if (!response.isSuccessful) {
                throw ApiException(response.code, json.optString("error", "unknown_error"))
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

    suspend fun pollToken(deviceCode: String, currentInterval: Int): TokenPollResult =
        withContext(Dispatchers.IO) {
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

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())

                if (response.isSuccessful) {
                    TokenPollResult.Success(
                        TokenResponse(
                            accessToken = json.getString("access_token"),
                            tokenType = json.getString("token_type"),
                            expiresIn = json.getInt("expires_in"),
                            scope = json.getString("scope"),
                            refreshToken = json.optString("refresh_token", null),
                        ),
                    )
                } else {
                    when (json.optString("error")) {
                        "authorization_pending" -> TokenPollResult.Pending
                        "slow_down" -> TokenPollResult.SlowDown(currentInterval + 5)
                        else -> TokenPollResult.Failed(
                            json.optString("error_description", json.optString("error", "授權失敗")),
                        )
                    }
                }
            } catch (e: Exception) {
                TokenPollResult.Failed(e.message ?: "網路錯誤")
            }
        }

    suspend fun provision(accessToken: String): Result<ProvisionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${WoowPaasConfig.BASE_URL}/api/ha-paas/provision")
                    .post(FormBody.Builder().build())
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())

                when (response.code) {
                    200, 202 -> ProvisionResponse(
                        status = json.getString("status"),
                        haUrl = json.optString("ha_url", null),
                    )
                    409 -> ProvisionResponse(
                        status = json.getString("status"),
                        error = json.optString("error", null),
                    )
                    401 -> throw ApiException(401, "登入已過期，請返回重新登入")
                    403 -> throw ApiException(403, "權限不足（缺少 ha:provision scope）")
                    503 -> throw ApiException(503, "服務尚未開放，請稍後再試")
                    else -> throw ApiException(response.code, json.optString("error", "未知錯誤"))
                }
            }
        }

    suspend fun getStatus(accessToken: String): Result<StatusResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${WoowPaasConfig.BASE_URL}/api/ha-paas/status")
                    .get()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())

                if (!response.isSuccessful) {
                    throw ApiException(response.code, json.optString("error", "查詢狀態失敗"))
                }

                StatusResponse(
                    status = json.getString("status"),
                    haUrl = json.optString("ha_url", null),
                    error = json.optString("error", null),
                )
            }
        }
}

internal class ApiException(val code: Int, message: String) : Exception(message)
