package io.homeassistant.companion.android.common.data.woowpaas

/**
 * Everything the WOOW PaaS client needs to talk to a given environment.
 *
 * The values are injected rather than hardcoded so the hosting application stays the single owner of
 * the environment configuration (staging versus production) and so tests can point the client at a
 * local server.
 *
 * @param baseUrl the environment root, with or without a trailing slash. It may carry a path prefix
 * (production is served under `/woow`); every endpoint is appended to that prefix.
 * @param clientId the OAuth 2.0 client identifier registered for this application
 * @param scopes the space separated OAuth 2.0 scopes requested during the device flow
 * @param deviceCodeGrantType the OAuth 2.0 grant type identifier of the device flow, see RFC 8628 §3.4
 * @param refreshTokenGrantType the OAuth 2.0 grant type identifier used to rotate a session, see
 * RFC 6749 §6
 */
data class WoowPaasApiConfig(
    val baseUrl: String,
    val clientId: String,
    val scopes: String,
    val deviceCodeGrantType: String,
    val refreshTokenGrantType: String,
)
