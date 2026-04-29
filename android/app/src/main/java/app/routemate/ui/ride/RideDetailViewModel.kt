package app.routemate.ui.ride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthStore
import app.routemate.data.LatLng
import app.routemate.data.MessageOut
import app.routemate.data.RatingCreate
import app.routemate.data.RatingOut
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

data class ChatLine(
    val id: String,
    val from: String,
    val body: String,
    val at: String,
    val mine: Boolean,
)

data class RideDetailState(
    val ride: RideOut? = null,
    val driverPin: LatLng? = null,
    val driverPinTs: Long? = null,
    val isMyRide: Boolean = false,
    val meId: String? = null,
    val chat: List<ChatLine> = emptyList(),
    val draft: String = "",
    val myRating: RatingOut? = null,
    val ratingDraftStars: Int = 0,
    val ratingDraftText: String = "",
    val ratingSubmitting: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canPromptForRating: Boolean
        get() = ride != null && ride.status == "completed" && !isMyRide && myRating == null
}

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
            _state.value = _state.value.copy(meId = meId)
            runCatching { rides.getRide(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(
                        ride = ride,
                        isMyRide = meId != null && ride.driver.id == meId,
                    )
                    seedLastLocation(id)
                    seedChat(id)
                    seedMyRating(id)
                    connectSocket(id)
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

    private suspend fun seedChat(id: String) {
        val msgs = runCatching { api.rideMessages(id) }.getOrDefault(emptyList())
        val meId = _state.value.meId
        _state.value = _state.value.copy(
            chat = msgs.map { it.toLine(meId) },
        )
    }

    private suspend fun seedMyRating(id: String) {
        val r = runCatching { api.myRating(id) }.getOrNull()
        _state.value = _state.value.copy(myRating = r)
    }

    fun onRatingStarsChange(stars: Int) {
        _state.value = _state.value.copy(ratingDraftStars = stars.coerceIn(1, 5))
    }

    fun onRatingTextChange(text: String) {
        _state.value = _state.value.copy(ratingDraftText = text)
    }

    fun submitRating() {
        val s = _state.value
        val ride = s.ride ?: return
        if (s.ratingDraftStars !in 1..5) return
        if (s.ratingSubmitting) return
        _state.value = s.copy(ratingSubmitting = true, error = null)

        viewModelScope.launch {
            runCatching {
                api.rate(
                    ride.id,
                    RatingCreate(
                        to_user_id = ride.driver.id,
                        stars = s.ratingDraftStars,
                        text = s.ratingDraftText.trim().ifBlank { null },
                    ),
                )
                api.myRating(ride.id)
            }
                .onSuccess {
                    _state.value = _state.value.copy(
                        myRating = it,
                        ratingSubmitting = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        ratingSubmitting = false,
                        error = it.message,
                    )
                }
        }
    }

    private fun connectSocket(id: String) {
        if (conn != null) return
        viewModelScope.launch {
            runCatching { socket.connect(id) }
                .onSuccess { c ->
                    conn = c
                    c.events.collect { env ->
                        when (env.type) {
                            "location" -> if (env.lat != null && env.lng != null) {
                                _state.value = _state.value.copy(
                                    driverPin = LatLng(env.lat, env.lng),
                                    driverPinTs = env.ts,
                                )
                            }
                            "chat" -> {
                                val meId = _state.value.meId
                                val line = ChatLine(
                                    id = env.id ?: "",
                                    from = env.from ?: "",
                                    body = env.body.orEmpty(),
                                    at = env.at.orEmpty(),
                                    mine = env.from != null && env.from == meId,
                                )
                                if (_state.value.chat.none { it.id == line.id && line.id.isNotBlank() }) {
                                    _state.value = _state.value.copy(chat = _state.value.chat + line)
                                }
                            }
                        }
                    }
                }
                .onFailure { /* keep last-known state */ }
        }
    }

    fun onDraftChange(v: String) {
        _state.value = _state.value.copy(draft = v)
    }

    fun sendDraft() {
        val body = _state.value.draft.trim()
        if (body.isEmpty()) return
        conn?.sendChat(body)
        _state.value = _state.value.copy(draft = "")
    }

    fun start() {
        val id = rideId ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.start(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(ride = ride, busy = false)
                    RideLocationService.start(ctx, id, authStore.token() ?: "")
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
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = it.message) }
        }
    }

    override fun onCleared() {
        conn?.close()
        super.onCleared()
    }
}

private fun MessageOut.toLine(meId: String?): ChatLine = ChatLine(
    id = id,
    from = sender_id,
    body = body,
    at = created_at,
    mine = meId != null && sender_id == meId,
)
