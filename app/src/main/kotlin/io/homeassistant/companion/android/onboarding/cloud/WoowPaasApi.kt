package io.homeassistant.companion.android.onboarding.cloud

import java.util.concurrent.TimeUnit
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

internal class WoowPaasApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

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

                client.newCall(request).execute().use { response ->
                    val json = response.parseJsonBody()

                    if (response.isSuccessful) {
                        TokenPollResult.Success(
                            TokenResponse(
                                accessToken = json.getString("access_token"),
                                tokenType = json.getString("token_type"),
                                expiresIn = json.getInt("expires_in"),
                                scope = json.getString("scope"),
                                refreshToken = json.nullableString("refresh_token"),
                            ),
                        )
                    } else {
                        when (json.nullableString("error")) {
                            "authorization_pending" -> TokenPollResult.Pending
                            "slow_down" -> TokenPollResult.SlowDown(currentInterval + 5)
                            else -> TokenPollResult.Failed(
                                json.nullableString("error_description")
                                    ?: json.nullableString("error")
                                    ?: "授權失敗",
                            )
                        }
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
                        else -> throw ApiException(response.code, json.nullableString("error") ?: "未知錯誤 (HTTP ${response.code})")
                    }
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

                client.newCall(request).execute().use { response ->
                    val json = response.parseJsonBody()

                    if (!response.isSuccessful) {
                        throw ApiException(response.code, json.nullableString("error") ?: "查詢狀態失敗 (HTTP ${response.code})")
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
