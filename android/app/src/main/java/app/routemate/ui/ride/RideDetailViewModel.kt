package app.routemate.ui.ride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthStore
import app.routemate.data.LatLng
import app.routemate.data.RideConnection
import app.routemate.data.RideOut
import app.routemate.data.RideRepository
import app.routemate.data.RideSocket
import app.routemate.data.RouteMatesApi
import app.routemate.service.RideLocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RideDetailState(
    val ride: RideOut? = null,
    val driverPin: LatLng? = null,
    val driverPinTs: Long? = null,
    val isMyRide: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val rides: RideRepository,
    private val socket: RideSocket,
    private val authStore: AuthStore,
    private val api: RouteMatesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(RideDetailState())
    val state: StateFlow<RideDetailState> = _state.asStateFlow()

    private var conn: RideConnection? = null
    private var rideId: String? = null

    fun load(id: String) {
        rideId = id
        viewModelScope.launch {
            val meId = runCatching { api.me().id }.getOrNull()
            runCatching { rides.getRide(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(
                        ride = ride,
                        isMyRide = meId != null && ride.driver.id == meId,
                    )
                    seedLastLocation(id)
                    if (ride.status == "started") connectSocket(id)
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    private suspend fun seedLastLocation(id: String) {
        val loc = rides.lastDriverLocation(id) ?: return
        _state.value = _state.value.copy(
            driverPin = LatLng(loc.lat, loc.lng),
            driverPinTs = loc.ts,
        )
    }

    private fun connectSocket(id: String) {
        viewModelScope.launch {
            runCatching { socket.connect(id) }
                .onSuccess { c ->
                    conn = c
                    c.events.collect { env ->
                        if (env.type == "location" && env.lat != null && env.lng != null) {
                            _state.value = _state.value.copy(
                                driverPin = LatLng(env.lat, env.lng),
                                driverPinTs = env.ts,
                            )
                        }
                    }
                }
                .onFailure { /* keep last-known pin from REST seed */ }
        }
    }

    fun start() {
        val id = rideId ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.start(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(ride = ride, busy = false)
                    RideLocationService.start(ctx, id, authStore.token() ?: "")
                    connectSocket(id)
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
        }
    }

    fun complete() {
        val id = rideId ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.complete(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(ride = ride, busy = false)
                    RideLocationService.stop(ctx)
                    conn?.close()
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
        }
    }

    override fun onCleared() {
        conn?.close()
        super.onCleared()
    }
}
