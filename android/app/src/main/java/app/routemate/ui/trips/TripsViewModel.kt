package app.routemate.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.RideBooking
import app.routemate.data.RideOut
import app.routemate.data.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripsState(
    val driving: List<RideOut> = emptyList(),
    val riding: List<RideBooking> = emptyList(),
    val awaitingRating: List<RideBooking> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() =
        driving.isEmpty() && riding.isEmpty() && awaitingRating.isEmpty()
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val rides: RideRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    fun load() {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.myTrips() }
                .onSuccess {
                    _state.value = TripsState(
                        driving = it.driving,
                        riding = it.riding,
                        awaitingRating = it.awaiting_rating,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = it.message)
                }
        }
    }
}
