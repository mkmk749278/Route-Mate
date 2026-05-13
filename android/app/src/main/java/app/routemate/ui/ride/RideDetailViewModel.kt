package app.routemate.ui.ride

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routemate.data.AuthStore
import app.routemate.data.BookingOut
import app.routemate.data.IncidentCreate
import app.routemate.data.LatLng
import app.routemate.data.MessageOut
import app.routemate.data.RatingCreate
import app.routemate.data.RatingOut
import app.routemate.data.RideConnection
import app.routemate.data.RideOut
import app.routemate.data.RideRepository
import app.routemate.data.RideSocket
import app.routemate.data.RouteMatesApi
import app.routemate.data.local.MessageDao
import app.routemate.data.local.MessageEntity
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
    val myBooking: BookingOut? = null,
    val driverPin: LatLng? = null,
    val driverPinTs: Long? = null,
    val isMyRide: Boolean = false,
    val meId: String? = null,
    val chat: List<ChatLine> = emptyList(),
    val draft: String = "",
    val myRating: RatingOut? = null,
    /** Who I'm rating on this ride. Null until ride is loaded; set to driver
     *  for riders, or to a specific rider when navigated as driver. */
    val rateTargetId: String? = null,
    val rateTargetName: String? = null,
    val ratingDraftStars: Int = 0,
    val ratingDraftText: String = "",
    val ratingSubmitting: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canCancelAsDriver: Boolean
        get() = isMyRide && ride?.status == "scheduled"

    val canCancelAsRider: Boolean
        get() = !isMyRide
            && ride?.status == "scheduled"
            && myBooking != null
            && myBooking.status != "cancelled"
    val canPromptForRating: Boolean
        get() = ride != null
            && ride.status == "completed"
            && rateTargetId != null
            && rateTargetId != meId
            && myRating == null
}

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val rides: RideRepository,
    private val socket: RideSocket,
    private val authStore: AuthStore,
    private val api: RouteMatesApi,
    private val messages: MessageDao,
) : ViewModel() {

    private val _state = MutableStateFlow(RideDetailState())
    val state: StateFlow<RideDetailState> = _state.asStateFlow()

    private var conn: RideConnection? = null
    private var rideId: String? = null

    fun load(id: String, rateTargetId: String? = null) {
        rideId = id
        viewModelScope.launch {
            val meId = runCatching { api.me().id }.getOrNull()
            _state.value = _state.value.copy(meId = meId)
            runCatching { rides.getRide(id) }
                .onSuccess { ride ->
                    val isMine = meId != null && ride.driver.id == meId
                    val resolvedTargetId = rateTargetId ?: if (!isMine) ride.driver.id else null
                    val resolvedTargetName = when (resolvedTargetId) {
                        ride.driver.id -> ride.driver.name
                        else -> null
                    }
                    _state.value = _state.value.copy(
                        ride = ride,
                        isMyRide = isMine,
                        rateTargetId = resolvedTargetId,
                        rateTargetName = resolvedTargetName,
                    )
                    seedLastLocation(id)
                    seedChat(id)
                    seedMyRating(id, resolvedTargetId)
                    seedMyBooking(id)
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
        // 1) Paint cached messages immediately so chat is usable offline.
        val cached = messages.list(id)
        val meId = _state.value.meId
        if (cached.isNotEmpty()) {
            _state.value = _state.value.copy(
                chat = cached.map { it.toLine(meId) },
            )
        }
        // 2) Refresh from network; on success write through to Room.
        val fresh = runCatching { api.rideMessages(id) }.getOrNull() ?: return
        if (fresh.isNotEmpty()) {
            messages.upsertAll(fresh.map { it.toEntity() })
            _state.value = _state.value.copy(
                chat = fresh.map { it.toLine(meId) },
            )
        }
    }

    private suspend fun seedMyRating(id: String, target: String?) {
        val r = runCatching { api.myRating(id, target) }.getOrNull()
        _state.value = _state.value.copy(myRating = r)
    }

    private suspend fun seedMyBooking(id: String) {
        val b = rides.myBookingOnRide(id)
        _state.value = _state.value.copy(myBooking = b)
    }

    fun cancelAsDriver() {
        val id = rideId ?: return
        if (!_state.value.canCancelAsDriver) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.cancelRide(id) }
                .onSuccess { ride ->
                    _state.value = _state.value.copy(ride = ride, busy = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = it.message)
                }
        }
    }

    fun cancelAsRider() {
        val booking = _state.value.myBooking ?: return
        if (!_state.value.canCancelAsRider) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { rides.cancelBooking(booking.id) }
                .onSuccess { b ->
                    _state.value = _state.value.copy(myBooking = b, busy = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(busy = false, error = it.message)
                }
        }
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
        val target = s.rateTargetId ?: return
        if (s.ratingDraftStars !in 1..5) return
        if (s.ratingSubmitting) return
        _state.value = s.copy(ratingSubmitting = true, error = null)

        viewModelScope.launch {
            runCatching {
                api.rate(
                    ride.id,
                    RatingCreate(
                        to_user_id = target,
                        stars = s.ratingDraftStars,
                        text = s.ratingDraftText.trim().ifBlank { null },
                    ),
                )
                api.myRating(ride.id, target)
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
                                    if (line.id.isNotBlank() && rideId != null) {
                                        messages.upsert(
                                            MessageEntity(
                                                id = line.id,
                                                rideId = rideId!!,
                                                senderId = line.from,
                                                body = line.body,
                                                createdAt = line.at,
                                            )
                                        )
                                    }
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

    /** Build a share URL the user can paste into SMS / WhatsApp. */
    suspend fun createShareUrl(): String? {
        val id = rideId ?: return null
        val tok = runCatching { api.createShareToken(id) }.getOrNull() ?: return null
        val base = app.routemate.BuildConfig.API_BASE_URL.trimEnd('/')
        return "$base/v1/share/${tok.token}"
    }

    fun reportSos() {
        val id = rideId ?: return
        viewModelScope.launch {
            runCatching {
                api.reportIncident(id, IncidentCreate(kind = "sos", description = "panic button"))
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
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

private fun MessageOut.toEntity(): MessageEntity = MessageEntity(
    id = id,
    rideId = ride_id,
    senderId = sender_id,
    body = body,
    createdAt = created_at,
)

private fun MessageEntity.toLine(meId: String?): ChatLine = ChatLine(
    id = id,
    from = senderId,
    body = body,
    at = createdAt,
    mine = meId != null && senderId == meId,
)
