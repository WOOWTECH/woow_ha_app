package io.homeassistant.companion.android.onboarding.cloudprovision.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import io.homeassistant.companion.android.onboarding.cloudprovision.CloudProvisionScreen
import io.homeassistant.companion.android.onboarding.cloudsignin.navigation.CloudSignInRoute
import kotlinx.serialization.Serializable

@Serializable
internal data object CloudProvisionRoute

internal fun NavController.navigateToCloudProvision(navOptions: NavOptions? = null) {
    navigate(route = CloudProvisionRoute, navOptions)
}

/**
 * Moves to provisioning once the device flow authorized the device, dropping the sign in screen.
 *
 * The sign in screen is removed rather than kept because it has nothing left to do: its credentials are
 * persisted, and it reacts to being authorized by navigating here. Leaving it on the back stack turns the
 * back button into a trap, sending the user straight back to provisioning instead of out of the cloud
 * flow, which matters exactly when they need the way out: a session the backend refuses cannot be retried,
 * so the only thing left to do is leave and sign in again.
 *
 * Backing out of provisioning therefore lands on the screen offering the choice between a local and a
 * cloud server, from which a new device flow starts clean.
 */
internal fun NavController.navigateToCloudProvisionAfterSignIn() {
    navigateToCloudProvision(
        navOptions {
            popUpTo<CloudSignInRoute> { inclusive = true }
        },
    )
}

internal fun NavGraphBuilder.cloudProvisionScreen(onBackClick: () -> Unit) {
    composable<CloudProvisionRoute> {
        CloudProvisionScreen(
            viewModel = hiltViewModel(),
            onBackClick = onBackClick,
        )
    }
}
