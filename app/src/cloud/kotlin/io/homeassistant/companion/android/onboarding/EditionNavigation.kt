package io.homeassistant.companion.android.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.homeassistant.companion.android.onboarding.cloudchooser.navigation.CloudChooserRoute
import io.homeassistant.companion.android.onboarding.cloudchooser.navigation.cloudChooserScreen
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.cloudProvisionScreen
import io.homeassistant.companion.android.onboarding.cloudprovision.navigation.navigateToCloudProvisionAfterSignIn
import io.homeassistant.companion.android.onboarding.cloudsignin.navigation.cloudSignInScreen
import io.homeassistant.companion.android.onboarding.cloudsignin.navigation.navigateToCloudSignIn
import io.homeassistant.companion.android.onboarding.welcome.navigation.navigateToWelcome

/**
 * Cloud edition: onboarding starts at the CloudChooser screen, which offers the local flow
 * (identical to the on-premise edition) or the WOOW cloud sign-in and provisioning flow.
 */
internal fun editionStartDestination(): Any = CloudChooserRoute

/**
 * Registers the three cloud onboarding screens. The wiring mirrors the cloud repository at
 * `893eae55` verbatim - in particular `navigateToCloudProvisionAfterSignIn`, which removes the
 * authorized sign-in screen from the back stack so that back from provisioning cannot land on a
 * dead sign-in session.
 */
internal fun NavGraphBuilder.editionScreens(navController: NavController) {
    cloudChooserScreen(
        onLocalClick = { navController.navigateToWelcome() },
        onCloudClick = { navController.navigateToCloudSignIn() },
    )
    cloudSignInScreen(
        onBackClick = navController::popBackStack,
        onAuthorized = navController::navigateToCloudProvisionAfterSignIn,
    )
    cloudProvisionScreen(onBackClick = navController::popBackStack)
}
