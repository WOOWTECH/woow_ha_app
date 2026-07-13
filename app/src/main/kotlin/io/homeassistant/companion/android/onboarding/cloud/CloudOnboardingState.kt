package io.homeassistant.companion.android.onboarding.cloud

/**
 * Shared state holder for the cloud onboarding flow.
 * Used to pass the access token from CloudSignIn to CloudProvision
 * without serializing it into a navigation route URL.
 * Created once per onboarding() call and shared between screens.
 */
internal class CloudOnboardingState {
    var accessToken: String? = null
}
