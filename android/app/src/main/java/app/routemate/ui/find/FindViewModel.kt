package app.routemate.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.GeocodeHit
import app.routemate.data.LatLng
import app.routemate.data.LocationProvider
import app.routemate.data.PlaceRepository
import app.routemate.data.RideOut
import app.routemate.data.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 500L

enum class FindField { From, To }

data class FindState(
    val fromText: String = "",
    val toText: String = "",
    val fromHits: List<GeocodeHit> = emptyList(),
    val toHits: List<GeocodeHit> = emptyList(),
    val fromPick: GeocodeHit? = null,
    val toPick: GeocodeHit? = null,
    val results: List<RideOut> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSearch: Boolean get() = fromPick != null && toPick != null
}

@HiltViewModel
class FindViewModel @Inject constructor(
    private val rides: RideRepository,
    private val places: PlaceRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FindState())
    val state: StateFlow<FindState> = _state.asStateFlow()

    private var fromJob: Job? = null
    private var toJob: Job? = null

    fun useCurrentLocation(field: FindField) {
        viewModelScope.launch {
            val ll = location.current() ?: return@launch
            val hit = places.reverse(ll.lat, ll.lng) ?: GeocodeHit(
                label = "%.5f, %.5f".format(ll.lat, ll.lng), lat = ll.lat, lng = ll.lng,
            )
            onPickHit(field, hit)
        }
    }

    fun onTextChange(field: FindField, value: String) {
        when (field) {
            FindField.From -> {
                _state.value = _state.value.copy(fromText = value, fromPick = null, error = null)
                fromJob?.cancel()
                fromJob = viewModelScope.launch { suggest(field, value) }
            }
            FindField.To -> {
                _state.value = _state.value.copy(toText = value, toPick = null, error = null)
                toJob?.cancel()
                toJob = viewModelScope.launch { suggest(field, value) }
            }
        }
    }

    fun onPickHit(field: FindField, hit: GeocodeHit) {
        _state.value = when (field) {
            FindField.From -> _state.value.copy(
                fromText = hit.label, fromPick = hit, fromHits = emptyList(),
            )
            FindField.To -> _state.value.copy(
                toText = hit.label, toPick = hit, toHits = emptyList(),
            )
        }
    }

    private suspend fun suggest(field: FindField, q: String) {
        delay(DEBOUNCE_MS)
        val hits = places.search(q)
        _state.value = when (field) {
            FindField.From -> _state.value.copy(fromHits = hits)
            FindField.To -> _state.value.copy(toHits = hits)
        }
    }

    fun search() {
        val s = _state.value
        val from = s.fromPick ?: return
        val to = s.toPick ?: return
        _state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching {
                rides.search(
                    from = LatLng(lat = from.lat, lng = from.lng),
                    to = LatLng(lat = to.lat, lng = to.lng),
                )
            }
                .onSuccess { _state.value = _state.value.copy(results = it, busy = false) }
                .onFailure { _state.value = _state.value.copy(error = it.message, busy = false) }
        }
    }
}
