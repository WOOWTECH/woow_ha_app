package io.homeassistant.companion.android.settings.sensor

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.AppSupportLinks

internal class SensorSettingsMenuProvider(
    private val context: Context,
    private val viewModel: SensorSettingsViewModel,
) : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_fragment_sensor, menu)

        val searchViewItem = menu.findItem(R.id.action_search)
        val searchView = searchViewItem.actionView as SearchView
        searchView.apply {
            queryHint = context.getString(commonR.string.search_sensors)
            maxWidth = Integer.MAX_VALUE
        }
        // MenuItem expansion is not SearchView iconification (the item uses showAsAction=ifRoom).
        // Restore before listening so recreating the menu never clears the active filter.
        viewModel.searchQuery?.takeIf { it.isNotEmpty() }?.let { query ->
            searchView.isIconified = false
            searchView.setQuery(query, false)
        }
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchView.clearFocus()
                    return false
                }

                override fun onQueryTextChange(query: String?): Boolean {
                    viewModel.setSensorsSearchQuery(query)
                    return false
                }
            },
        )
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(viewModel.sensorFilter.menuItemId)?.isChecked = true

        menu.findItem(R.id.get_help)?.let {
            it.isVisible = true
            it.intent =
                Intent(
                    ACTION_VIEW,
                    "${AppSupportLinks.SENSORS}#android-sensors".toUri(),
                )
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
        R.id.action_show_sensors_all,
        R.id.action_show_sensors_enabled,
        R.id.action_show_sensors_disabled,
        -> {
            menuItem.isChecked = !menuItem.isChecked
            viewModel.setSensorFilterChoice(menuItem.itemId)
            true
        }

        else -> false
    }
}
