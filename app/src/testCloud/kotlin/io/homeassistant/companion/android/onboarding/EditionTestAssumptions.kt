package io.homeassistant.companion.android.onboarding

import org.junit.Assume.assumeTrue

/**
 * Cloud edition: onboarding starts at the CloudChooser, not Welcome, so shared specs that pin the
 * Welcome entry point (start destination and back-stack shape) do not apply and are reported as
 * skipped rather than silently rewritten. The cloud entry behaviour has its own specs in
 * [io.homeassistant.companion.android.onboarding.cloud.CloudOnboardingNavigationTest].
 */
internal fun assumeOnboardingStartsAtWelcome() {
    assumeTrue(
        "Cloud edition starts onboarding at CloudChooser; this spec describes the on-premise entry",
        false,
    )
}
