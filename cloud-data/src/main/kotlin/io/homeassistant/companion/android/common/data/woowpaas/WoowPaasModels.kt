package io.homeassistant.companion.android.common.data.woowpaas

/**
 * The device and user codes issued when the device flow starts (RFC 8628 §3.2).
 *
 * @param deviceCode the opaque code the application polls the token endpoint with
 * @param userCode the short code the user types on [verificationUri]
 * @param verificationUri the page where the user enters [userCode]
 * @param verificationUriComplete the same page with [userCode] already filled in, suitable for a QR code
 * @param expiresIn lifetime of [deviceCode] in seconds; polling must stop once it elapsed
 * @param interval the delay in seconds the client must wait between two polls
 */
data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int,
)

/**
 * The answer to a provisioning request.
 *
 * @param haUrl the URL of the instance, only present once [status] is [ProvisionStatus.Ready]
 * @param error a human readable reason, present when the backend refused the request
 */
data class ProvisionResponse(val status: ProvisionStatus, val haUrl: String? = null, val error: String? = null)

/**
 * The answer to a provisioning status query.
 *
 * @param haUrl the URL of the instance, only present once [status] is [ProvisionStatus.Ready]
 * @param error a human readable reason, present when [status] is [ProvisionStatus.Error]
 */
data class StatusResponse(val status: ProvisionStatus, val haUrl: String? = null, val error: String? = null)
