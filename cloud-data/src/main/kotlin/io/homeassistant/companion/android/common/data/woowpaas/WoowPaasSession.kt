package io.homeassistant.companion.android.common.data.woowpaas

import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How long before its nominal expiry an access token is already considered unusable.
 *
 * A request started at the very end of the lifetime of a token would be refused by the time it reaches
 * the backend, so the token is retired early and refreshed instead.
 */
private val ACCESS_TOKEN_EXPIRY_LEEWAY = 1.minutes

/** Stands in for a credential in [WoowPaasSession.toString] so it never reaches a log. */
private const val REDACTED = "***"

/**
 * The credentials issued by WOOW PaaS for the account that authorized this device.
 *
 * The backend rotates the credentials on every refresh: a refresh returns a brand new access **and**
 * refresh token, and invalidates the ones that were used. A session is therefore replaced as a whole and
 * never updated field by field.
 *
 * @param accessToken the bearer token sent to the API endpoints
 * @param refreshToken the token that buys a new session, absent when the backend did not hand one out.
 * Its own expiry is deliberately not modelled: the backend does not report it, so whether it is still
 * accepted is only known once a refresh is attempted.
 * @param accessTokenExpiresAt the instant [accessToken] stops being accepted, computed from the lifetime
 * the backend reported when it issued it
 */
@OptIn(ExperimentalTime::class)
data class WoowPaasSession(val accessToken: String, val refreshToken: String?, val accessTokenExpiresAt: Instant) {

    /**
     * Whether [accessToken] can still be used for a request started at [now].
     *
     * Tokens about to expire are reported as unusable so the caller refreshes ahead of time rather than
     * paying for a request that is going to be refused.
     */
    fun isAccessTokenUsableAt(now: Instant): Boolean = now < accessTokenExpiresAt - ACCESS_TOKEN_EXPIRY_LEEWAY

    /**
     * Whether this session is worth trying to authenticate a request started at [now] with.
     *
     * This is a local, optimistic check, and the only one that can be made without calling the backend: a
     * present refresh token is assumed to still be accepted, because nothing here knows when it expires. A
     * false answer is therefore certain (nothing left to authenticate or refresh with, only a new device
     * flow recovers) while a true answer only means it is worth trying: a refresh token that expired or was
     * revoked is discovered when the backend refuses to exchange it.
     */
    fun canAuthenticateAt(now: Instant): Boolean = isAccessTokenUsableAt(now) || refreshToken != null

    /**
     * Describes the session without ever spelling out a credential.
     *
     * The generated representation of a data class is what ends up in a log or a crash report the moment
     * anything interpolates a session, so the tokens are replaced by a placeholder here rather than trusted
     * to never be printed.
     */
    override fun toString(): String = "WoowPaasSession(" +
        "accessToken=$REDACTED, " +
        "refreshToken=${refreshToken?.let { REDACTED }}, " +
        "accessTokenExpiresAt=$accessTokenExpiresAt" +
        ")"
}
