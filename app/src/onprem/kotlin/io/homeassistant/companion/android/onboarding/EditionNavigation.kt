package io.homeassistant.companion.android.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute

/**
 * On-premise edition: onboarding starts at the Welcome screen, exactly as before the edition
 * dimension existed. The cloud source set provides the other implementation of this pair.
 */
internal fun editionStartDestination(): Any = WelcomeRoute

/**
 * On-premise edition contributes no extra screens to the onboarding graph.
 */
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun NavGraphBuilder.editionScreens(navController: NavController) {
    // The on-premise onboarding graph is fully defined in the main source set.
}
