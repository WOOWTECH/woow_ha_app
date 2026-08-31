package io.homeassistant.companion.android.onboarding

/**
 * On-premise edition: onboarding starts at Welcome, so specs written against that entry point
 * simply run. The cloud test source set provides the skipping counterpart.
 */
internal fun assumeOnboardingStartsAtWelcome() {
    // Nothing to assume: this is the edition those specs describe.
}
