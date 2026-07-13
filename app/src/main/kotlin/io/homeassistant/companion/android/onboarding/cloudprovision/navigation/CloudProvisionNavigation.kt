package io.homeassistant.companion.android.onboarding.cloudprovision.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.cloud.CloudOnboardingState
import io.homeassistant.companion.android.onboarding.cloudprovision.CloudProvisionScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object CloudProvisionRoute

internal fun NavController.navigateToCloudProvision(navOptions: NavOptions? = null) {
    navigate(route = CloudProvisionRoute, navOptions)
}

internal fun NavGraphBuilder.cloudProvisionScreen(
    onBackClick: () -> Unit,
    sharedState: CloudOnboardingState,
) {
    composable<CloudProvisionRoute> {
        CloudProvisionScreen(
            viewModel = hiltViewModel(),
            sharedState = sharedState,
            onBackClick = onBackClick,
        )
    }
}
