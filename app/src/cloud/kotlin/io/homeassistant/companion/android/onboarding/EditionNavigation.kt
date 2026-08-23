package io.homeassistant.companion.android.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.homeassistant.companion.android.onboarding.welcome.navigation.WelcomeRoute

/**
 * Cloud edition: onboarding will start at the CloudChooser screen once the cloud onboarding
 * flow is ported (PRD docs/plans/2026-08-23-prd-cloud-edition-single-repo.md, Phase 0 step 3).
 *
 * Skeleton placeholder: behaves exactly like the on-premise edition so every variant compiles
 * before the port lands. Replaced by the real chooser wiring in the port commit.
 */
internal fun editionStartDestination(): Any = WelcomeRoute

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
internal fun NavGraphBuilder.editionScreens(navController: NavController) {
    // Replaced by cloudChooserScreen/cloudSignInScreen/cloudProvisionScreen in the port commit.
}
