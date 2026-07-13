package io.homeassistant.companion.android.onboarding.cloudprovision.navigation

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.cloudprovision.CloudProvisionScreen
import kotlinx.serialization.Serializable

@Serializable
internal data class CloudProvisionRoute(val accessToken: String)

internal fun NavController.navigateToCloudProvision(accessToken: String, navOptions: NavOptions? = null) {
    navigate(route = CloudProvisionRoute(accessToken), navOptions)
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
