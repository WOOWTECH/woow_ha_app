package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.annotation.IdRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.sensors.SensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SensorSettingsViewModel internal constructor(
    sensorDao: SensorDao,
    application: Application,
    private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    @Inject
    constructor(sensorDao: SensorDao, application: Application) : this(sensorDao, application, Dispatchers.IO)

    enum class SensorFilter(@IdRes val menuItemId: Int) {
        ALL(R.id.action_show_sensors_all),
        ENABLED(R.id.action_show_sensors_enabled),
        DISABLED(R.id.action_show_sensors_disabled),
        ;

        companion object {
            val menuItemIdToFilter = values().associateBy { it.menuItemId }
        }
    }

    // Null distinguishes the first emission, including an empty database, from an unchanged list.
    private var sensorsList: List<Sensor>? = null
    private var filterJob: Job? = null
    var sensors by mutableStateOf<Map<String, Sensor>>(emptyMap())
        private set

    var allSensors by mutableStateOf<Map<SensorManager, List<SensorManager.BasicSensor>>>(emptyMap())
        private set

    var searchQuery: String? = null
        private set
    var sensorFilter by mutableStateOf(SensorFilter.ALL)
        private set

    init {
        viewModelScope.launch {
            sensorDao.getAllFlow().collect {
                // Sensor updates often emit unchanged contents. Keep filtering read-only.
                if (sensorsList != it) {
                    sensorsList = it
                    filterSensorsList()
                }
            }
        }
    }

    /** Updates the search immediately so menu recreation sees the latest query. */
    fun setSensorsSearchQuery(query: String? = "") {
        searchQuery = query
        filterSensorsList()
    }

    /** Filters available sensors using observed state without changing opt-ins or checking permissions. */
    fun setSensorFilterChoice(@IdRes filterMenuItemId: Int) {
        sensorFilter = SensorFilter.menuItemIdToFilter.getValue(filterMenuItemId)
        filterSensorsList()
    }

    private fun filterSensorsList() {
        filterJob?.cancel()
        val query = searchQuery.orEmpty()
        val filter = sensorFilter
        val rows = sensorsList.orEmpty()
        filterJob = viewModelScope.launch {
            val (filteredSensors, filteredManagers) = withContext(ioDispatcher) {
                filterSensors(rows, query, filter)
            }
            // A cancelled older query must not overwrite a newer query or database emission.
            sensors = filteredSensors
            allSensors = filteredManagers
        }
    }

    private suspend fun filterSensors(
        rows: List<Sensor>,
        query: String,
        filter: SensorFilter,
    ): Pair<Map<String, Sensor>, Map<SensorManager, List<SensorManager.BasicSensor>>> {
        val app = getApplication<Application>()
        // Enabled on any server wins. Sensors without rows remain discoverable as disabled.
        val displayedRows = rows.groupBy { it.id }.mapValues { (_, sensors) -> sensors.maxBy { it.enabled } }
        val filteredSensors = mutableMapOf<String, Sensor>()
        val filteredManagers = SensorReceiver.MANAGERS
            .filter { it.enabledByRelease && it.hasSensor(app) }
            .sortedBy { app.getString(it.name) }
            .associateWith { manager ->
                manager.getAvailableSensors(app).filter { basicSensor ->
                    val row = displayedRows[basicSensor.id]
                    val matchesQuery = query.isEmpty() ||
                        app.getString(basicSensor.name).contains(query, ignoreCase = true) ||
                        app.getString(manager.name).contains(query, ignoreCase = true)
                    val matchesFilter = when (filter) {
                        SensorFilter.ALL -> true
                        SensorFilter.ENABLED -> row?.enabled == true
                        SensorFilter.DISABLED -> row?.enabled != true
                    }
                    val include = manager.isSensorEnabledByRelease(basicSensor.id) && matchesQuery && matchesFilter
                    if (include && row != null) filteredSensors[basicSensor.id] = row
                    include
                }.sortedBy { app.getString(it.name) }.distinct()
            }
        return filteredSensors to filteredManagers
    }
}
