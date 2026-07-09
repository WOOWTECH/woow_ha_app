package io.homeassistant.companion.android.onboarding.cloudsignin.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.cloudsignin.CloudSignInScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object CloudSignInRoute

internal fun NavController.navigateToCloudSignIn(navOptions: NavOptions? = null) {
    navigate(route = CloudSignInRoute, navOptions)
}

internal fun NavGraphBuilder.cloudSignInScreen(
    onBackClick: () -> Unit,
    onSignInSuccess: () -> Unit,
) {
    composable<CloudSignInRoute> {
        CloudSignInScreen(
            viewModel = hiltViewModel(),
            onBackClick = onBackClick,
            onSignInSuccess = onSignInSuccess,
        )
    }
}
