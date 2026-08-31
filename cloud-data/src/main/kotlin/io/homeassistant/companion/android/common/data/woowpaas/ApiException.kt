package io.homeassistant.companion.android.common.data.woowpaas

import java.net.HttpURLConnection.HTTP_UNAUTHORIZED

/**
 * A failure reported by the WOOW PaaS backend, carrying the HTTP status code that produced it.
 *
 * Callers use [code] to tell apart the cases that need a dedicated reaction (403 means a missing scope,
 * 503 means the service is not open yet) from the generic ones. The one case that is not told apart by a
 * code has its own type, [SessionExpiredException].
 */
open class ApiException(val code: Int, message: String) : Exception(message)

/**
 * The stored session can no longer authenticate: the backend refused the access token and either there
 * was no refresh token left or refreshing it was refused too.
 *
 * Nothing but a new device flow recovers from this, so callers offer a new sign in rather than a retry.
 * The session has already been dropped by the time this is reported.
 */
class SessionExpiredException(message: String) : ApiException(HTTP_UNAUTHORIZED, message)

/**
 * Placeholder [ApiException.code] used when the failure happened before any HTTP status was known,
 * for instance while decoding a malformed response body.
 */
const val HTTP_CODE_UNKNOWN: Int = -1
