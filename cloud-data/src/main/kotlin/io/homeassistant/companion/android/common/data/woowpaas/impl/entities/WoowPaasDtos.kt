package io.homeassistant.companion.android.common.data.woowpaas.impl.entities

import kotlinx.serialization.Serializable

/**
 * Wire representation of the device authorization response (RFC 8628 §3.2).
 *
 * Every field is optional even though the specification makes them mandatory: a backend that answers
 * an incomplete payload must produce a readable error instead of a deserialization crash, so the
 * validation happens in the mapping layer where the HTTP status code is still known.
 */
@Serializable
internal data class DeviceCodeResponseDto(
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val verificationUriComplete: String? = null,
    val expiresIn: Int? = null,
    val interval: Int? = null,
    val error: String? = null,
)

/** Wire representation of the token endpoint response and of its OAuth error payload (RFC 8628 §3.5). */
@Serializable
internal data class TokenResponseDto(
    val accessToken: String? = null,
    val tokenType: String? = null,
    val expiresIn: Int? = null,
    val scope: String? = null,
    val refreshToken: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
)

/** Wire representation of the provisioning request response. */
@Serializable
internal data class ProvisionResponseDto(
    val status: String? = null,
    val haUrl: String? = null,
    val error: String? = null,
)

/** Wire representation of the provisioning status response. */
@Serializable
internal data class StatusResponseDto(val status: String? = null, val haUrl: String? = null, val error: String? = null)
