package io.homeassistant.companion.android.onboarding.cloudchooser.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import io.homeassistant.companion.android.onboarding.cloudchooser.CloudChooserScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object CloudChooserRoute

internal fun NavController.navigateToCloudChooser(navOptions: NavOptions? = null) {
    navigate(route = CloudChooserRoute, navOptions)
}

internal fun NavGraphBuilder.cloudChooserScreen(onLocalClick: () -> Unit, onCloudClick: () -> Unit) {
    composable<CloudChooserRoute> {
        CloudChooserScreen(onLocalClick = onLocalClick, onCloudClick = onCloudClick)
    }
}
