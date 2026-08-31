package io.homeassistant.companion.android.common.data.woowpaas.impl

import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.DeviceCodeResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.ProvisionResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.StatusResponseDto
import io.homeassistant.companion.android.common.data.woowpaas.impl.entities.TokenResponseDto
import okhttp3.HttpUrl
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Path segments appended to the configured environment root.
 *
 * They are relative on purpose: the production environment is served under a `/woow` prefix, so the
 * caller builds the final URL from the configured base instead of relying on Retrofit's base URL
 * resolution, which would drop that prefix.
 */
internal object WoowPaasEndpoints {
    const val DEVICE_AUTHORIZATION = "oauth2/device_authorization"
    const val TOKEN = "oauth2/token"
    const val PROVISION = "api/ha-paas/provision"
    const val STATUS = "api/ha-paas/status"
}

/** Name of the HTTP header carrying the bearer token. */
internal const val HEADER_AUTHORIZATION = "Authorization"

/**
 * Retrofit description of the WOOW PaaS endpoints used by the cloud onboarding flow.
 *
 * Every call takes its full [Url] and returns a [Response] rather than the payload directly: the
 * onboarding flow reacts to individual status codes (a 409 is a normal answer, a 401 has to be told
 * apart from a 403) and reads the body of unsuccessful responses, which Retrofit does not decode.
 */
internal interface WoowPaasService {

    @FormUrlEncoded
    @POST
    suspend fun requestDeviceCode(
        @Url url: HttpUrl,
        @Field("client_id") clientId: String,
        @Field("scope") scope: String,
    ): Response<DeviceCodeResponseDto>

    @FormUrlEncoded
    @POST
    suspend fun pollToken(
        @Url url: HttpUrl,
        @Field("grant_type") grantType: String,
        @Field("device_code") deviceCode: String,
        @Field("client_id") clientId: String,
    ): Response<TokenResponseDto>

    /**
     * Exchanges a refresh token for a brand new session (RFC 6749 §6).
     *
     * The client is a public one: sending a client secret here is answered with a 401, so the body carries
     * nothing but the grant type, the refresh token and the client identifier.
     */
    @FormUrlEncoded
    @POST
    suspend fun refreshToken(
        @Url url: HttpUrl,
        @Field("grant_type") grantType: String,
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
    ): Response<TokenResponseDto>

    @POST
    suspend fun provision(
        @Url url: HttpUrl,
        @Header(HEADER_AUTHORIZATION) authorization: String,
        @Body body: RequestBody,
    ): Response<ProvisionResponseDto>

    @GET
    suspend fun getStatus(
        @Url url: HttpUrl,
        @Header(HEADER_AUTHORIZATION) authorization: String,
    ): Response<StatusResponseDto>
}
