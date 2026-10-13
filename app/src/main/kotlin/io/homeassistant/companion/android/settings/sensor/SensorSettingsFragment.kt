package io.homeassistant.companion.android.settings.sensor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuHost
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.sensor.views.SensorListView
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

@AndroidEntryPoint
class SensorSettingsFragment : Fragment() {

    val viewModel: SensorSettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HomeAssistantAppTheme {
                    SensorListView(
                        viewModel = viewModel,
                        onSensorClicked = { sensor ->
                            parentFragmentManager
                                .beginTransaction()
                                .replace(
                                    R.id.content,
                                    SensorDetailFragment.newInstance(
                                        sensor,
                                    ),
                                )
                                .addToBackStack("Sensor Detail")
                                .commit()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            SensorSettingsMenuProvider(requireContext(), viewModel),
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(commonR.string.sensors)
    }
}
