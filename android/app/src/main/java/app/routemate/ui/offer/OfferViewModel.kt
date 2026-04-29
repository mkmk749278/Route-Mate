package app.routemate.ui.offer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.GeocodeHit
import app.routemate.data.LatLng
import app.routemate.data.LocationProvider
import app.routemate.data.PlaceRepository
import app.routemate.data.RideCreate
import app.routemate.data.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 500L

enum class OfferField { Origin, Destination }

data class OfferState(
    val originText: String = "",
    val destinationText: String = "",
    val originHits: List<GeocodeHit> = emptyList(),
    val destinationHits: List<GeocodeHit> = emptyList(),
    val originPick: GeocodeHit? = null,
    val destinationPick: GeocodeHit? = null,
    /** Minutes from now. */
    val departInMinutes: Int = 30,
    val seats: Int = 3,
    val pricePerSeat: String = "50",
    val busy: Boolean = false,
    val error: String? = null,
    val postedRideId: String? = null,
) {
    val canPost: Boolean
        get() = originPick != null
            && destinationPick != null
            && seats in 1..8
            && pricePerSeat.toIntOrNull() != null
}

@HiltViewModel
class OfferViewModel @Inject constructor(
    private val rides: RideRepository,
    private val places: PlaceRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(OfferState())
    val state: StateFlow<OfferState> = _state.asStateFlow()

    private var originJob: Job? = null
    private var destJob: Job? = null

    fun useCurrentLocation(field: OfferField) {
        viewModelScope.launch {
            val ll = location.current() ?: return@launch
            val hit = places.reverse(ll.lat, ll.lng) ?: GeocodeHit(
                label = "%.5f, %.5f".format(ll.lat, ll.lng), lat = ll.lat, lng = ll.lng,
            )
            onPickHit(field, hit)
        }
    }

    fun onTextChange(field: OfferField, value: String) {
        when (field) {
            OfferField.Origin -> {
                _state.value = _state.value.copy(
                    originText = value, originPick = null, error = null,
                )
                originJob?.cancel()
                originJob = viewModelScope.launch { suggest(field, value) }
            }
            OfferField.Destination -> {
                _state.value = _state.value.copy(
                    destinationText = value, destinationPick = null, error = null,
                )
                destJob?.cancel()
                destJob = viewModelScope.launch { suggest(field, value) }
            }
        }
    }

    fun onPickHit(field: OfferField, hit: GeocodeHit) {
        _state.value = when (field) {
            OfferField.Origin -> _state.value.copy(
                originText = hit.label, originPick = hit, originHits = emptyList(),
            )
            OfferField.Destination -> _state.value.copy(
                destinationText = hit.label, destinationPick = hit, destinationHits = emptyList(),
            )
        }
    }

    fun onDepartInMinutesChange(v: Int) {
        _state.value = _state.value.copy(departInMinutes = v.coerceIn(5, 1440))
    }

    fun onSeatsChange(delta: Int) {
        _state.value = _state.value.copy(seats = (_state.value.seats + delta).coerceIn(1, 8))
    }

    fun onPriceChange(v: String) {
        _state.value = _state.value.copy(pricePerSeat = v.filter(Char::isDigit).take(4))
    }

    private suspend fun suggest(field: OfferField, q: String) {
        delay(DEBOUNCE_MS)
        val hits = places.search(q)
        _state.value = when (field) {
            OfferField.Origin -> _state.value.copy(originHits = hits)
            OfferField.Destination -> _state.value.copy(destinationHits = hits)
        }
    }

    fun post() {
        val s = _state.value
        if (!s.canPost) return
        val origin = s.originPick!!
        val dest = s.destinationPick!!
        _state.value = s.copy(busy = true, error = null)

        val departAt = OffsetDateTime
            .ofInstant(Instant.now().plusSeconds(s.departInMinutes * 60L), ZoneOffset.UTC)
            .toString()

        viewModelScope.launch {
            runCatching {
                rides.create(
                    RideCreate(
                        origin = LatLng(origin.lat, origin.lng),
                        destination = LatLng(dest.lat, dest.lng),
                        origin_label = origin.label,
                        destination_label = dest.label,
                        depart_at = departAt,
                        seats_total = s.seats,
                        price_per_seat = s.pricePerSeat,
                    )
                )
            }.onSuccess { ride ->
                _state.value = OfferState(postedRideId = ride.id)
            }.onFailure {
                _state.value = _state.value.copy(busy = false, error = it.message)
            }
        }
    }

    fun resetPosted() {
        _state.value = OfferState()
    }
}
