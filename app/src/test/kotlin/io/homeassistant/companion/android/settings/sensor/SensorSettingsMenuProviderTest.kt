package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.View
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.test.core.app.ApplicationProvider
import io.homeassistant.companion.android.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SensorSettingsMenuProviderTest {
    private lateinit var context: Context
    private lateinit var viewModel: SensorSettingsViewModel
    private lateinit var provider: SensorSettingsMenuProvider
    private var query: String? = null
    private var filter = SensorSettingsViewModel.SensorFilter.ALL

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            androidx.appcompat.R.style.Theme_AppCompat,
        )
        viewModel = mockk()
        every { viewModel.searchQuery } answers { query }
        every { viewModel.sensorFilter } answers { filter }
        every { viewModel.setSensorsSearchQuery(any()) } answers { query = firstArg() }
        every { viewModel.setSensorFilterChoice(any()) } answers {
            filter = SensorSettingsViewModel.SensorFilter.menuItemIdToFilter.getValue(firstArg())
        }
        provider = SensorSettingsMenuProvider(context, viewModel)
    }

    @Test
    fun `Given English active query when menu prepared and filter changed then query persists`() {
        assertQuerySurvivesRefresh("Health")
    }

    @Test
    fun `Given Chinese active query when menu prepared and filter changed then query persists`() {
        assertQuerySurvivesRefresh("電池")
    }

    @Test
    fun `Given active query when menu recreated then visible query and filtering agree`() {
        val menu = createMenu()
        val search = menu.findItem(R.id.action_search).actionView as SearchView
        search.isIconified = false
        search.setQuery("Health", false)

        val recreatedMenu = createMenu()
        val recreatedSearch = recreatedMenu.findItem(R.id.action_search).actionView as SearchView

        assertEquals("Health", query)
        assertEquals("Health", recreatedSearch.query.toString())
        assertFalse(recreatedSearch.isIconified)
    }

    @Test
    fun `Given active query when cleared or collapsed then query filter is removed`() {
        val menu = createMenu()
        val search = menu.findItem(R.id.action_search).actionView as SearchView
        search.isIconified = false
        search.setQuery("電池", false)
        search.setQuery("", false)
        assertEquals("", query)
        search.setQuery("Health", false)
        search.onActionViewCollapsed()
        assertEquals("", query)
        provider.onPrepareMenu(menu)
        assertEquals("", query)
    }

    private fun assertQuerySurvivesRefresh(value: String) {
        val menu = createMenu()
        val item = menu.findItem(R.id.action_search)
        val search = item.actionView as SearchView
        search.isIconified = false
        search.setQuery(value, false)
        assertEquals(value, query)
        // SearchView iconification is independent of MenuItem expansion for showAsAction=ifRoom.
        assertFalse(item.isActionViewExpanded)
        provider.onPrepareMenu(menu)
        assertEquals(value, query)
        provider.onMenuItemSelected(menu.findItem(R.id.action_show_sensors_enabled))
        provider.onPrepareMenu(menu)
        assertEquals(value, query)
        assertEquals(value, search.query.toString())
        assertEquals(SensorSettingsViewModel.SensorFilter.ENABLED, filter)
        verify(exactly = 0) { viewModel.setSensorsSearchQuery(null) }
    }

    private fun createMenu(): Menu {
        val menu = PopupMenu(context, View(context)).menu
        provider.onCreateMenu(menu, SupportMenuInflater(context))
        provider.onPrepareMenu(menu)
        return menu
    }
}
