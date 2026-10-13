package io.homeassistant.companion.android

import android.content.Context

/** Crash telemetry is intentionally disabled for the woowtech aiot production app. */
@Suppress("UNUSED_PARAMETER")
fun initCrashReporting(context: Context, enabled: Boolean) {
    // No-op by owner decision. Local crash saving remains independent.
}
