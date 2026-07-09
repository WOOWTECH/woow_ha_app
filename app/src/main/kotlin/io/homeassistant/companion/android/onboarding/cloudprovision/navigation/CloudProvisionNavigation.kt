package io.homeassistant.companion.android.onboarding.cloudprovision.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.cloudprovision.CloudProvisionScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object CloudProvisionRoute

internal fun NavController.navigateToCloudProvision(navOptions: NavOptions? = null) {
    navigate(route = CloudProvisionRoute, navOptions)
}

internal fun NavGraphBuilder.cloudProvisionScreen(
    onBackClick: () -> Unit,
) {
    composable<CloudProvisionRoute> {
        CloudProvisionScreen(
            viewModel = hiltViewModel(),
            onBackClick = onBackClick,
        )
    }
}
