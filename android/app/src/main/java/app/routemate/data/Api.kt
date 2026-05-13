package app.routemate.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable data class LatLng(val lat: Double, val lng: Double)

@Serializable data class UserOut(
    val id: String,
    val name: String? = null,
    val photo_url: String? = null,
    val upi_id: String? = null,
    val rating_avg: Double = 0.0,
    val rating_count: Int = 0,
)

@Serializable data class TrustedContact(val name: String, val phone: String)

@Serializable data class MeOut(
    val id: String,
    val name: String? = null,
    val photo_url: String? = null,
    val upi_id: String? = null,
    val phone: String? = null,
    val rating_avg: Double = 0.0,
    val rating_count: Int = 0,
    val trusted_contacts: List<TrustedContact> = emptyList(),
)

@Serializable data class MePatch(
    val name: String? = null,
    val photo_url: String? = null,
    val upi_id: String? = null,
    val trusted_contacts: List<TrustedContact>? = null,
)

@Serializable data class IncidentCreate(
    val kind: String,
    val description: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable data class IncidentOut(
    val id: String,
    val ride_id: String,
    val kind: String,
    val description: String? = null,
    val created_at: String,
)

@Serializable data class ShareTokenOut(val token: String, val expires_at: String)

@Serializable data class AuthExchange(val id_token: String)
@Serializable data class DevLoginRequest(val phone: String, val name: String? = null)
@Serializable data class AuthResult(val token: String, val user: MeOut)

@Serializable data class RideCreate(
    val origin: LatLng,
    val destination: LatLng,
    val origin_label: String,
    val destination_label: String,
    val polyline: String? = null,
    val depart_at: String,
    val seats_total: Int,
    val price_per_seat: String,
    val recurrence_days: Int = 0,
)

@Serializable data class RideOut(
    val id: String,
    val driver: UserOut,
    val origin: LatLng,
    val destination: LatLng,
    val origin_label: String,
    val destination_label: String,
    val depart_at: String,
    val seats_total: Int,
    val seats_available: Int,
    val price_per_seat: String,
    val status: String,
    val polyline: String? = null,
    val recurrence_days: Int = 0,
)

@Serializable data class BookingCreate(val seats: Int = 1)
@Serializable data class BookingOut(
    val id: String,
    val ride_id: String,
    val rider: UserOut,
    val seats: Int,
    val status: String,
    val created_at: String,
)

@Serializable data class RatingCreate(
    val to_user_id: String,
    val stars: Int,
    val text: String? = null,
)

@Serializable data class FcmRegister(val token: String, val platform: String = "android")

@Serializable data class GeocodeHit(val label: String, val lat: Double, val lng: Double)

@Serializable data class MessageOut(
    val id: String,
    val ride_id: String,
    val sender_id: String,
    val body: String,
    val created_at: String,
)

@Serializable data class RideBooking(val booking: BookingOut, val ride: RideOut)
@Serializable data class RatingPrompt(val ride: RideOut, val target: UserOut)
@Serializable data class TripsOut(
    val driving: List<RideOut>,
    val riding: List<RideBooking>,
    val awaiting_rating: List<RideBooking> = emptyList(),
    val awaiting_driver_rating: List<RatingPrompt> = emptyList(),
)
@Serializable data class RatingOut(
    val id: String,
    val ride_id: String,
    val from_id: String,
    val to_id: String,
    val stars: Int,
    val text: String? = null,
)
@Serializable data class DriverLocation(val lat: Double, val lng: Double, val ts: Long)

interface RouteMatesApi {
    @POST("v1/auth/exchange")
    suspend fun exchange(@Body body: AuthExchange): AuthResult

    @POST("v1/auth/dev-login")
    suspend fun devLogin(@Body body: DevLoginRequest): AuthResult

    @GET("v1/me")
    suspend fun me(): MeOut

    @PATCH("v1/me")
    suspend fun patchMe(@Body body: MePatch): MeOut

    @POST("v1/rides")
    suspend fun createRide(@Body body: RideCreate): RideOut

    @GET("v1/rides/search")
    suspend fun searchRides(
        @Query("from_lat") fromLat: Double,
        @Query("from_lng") fromLng: Double,
        @Query("to_lat") toLat: Double,
        @Query("to_lng") toLng: Double,
        @Query("depart_after") departAfter: String? = null,
        @Query("depart_before") departBefore: String? = null,
    ): List<RideOut>

    @GET("v1/rides/{id}")
    suspend fun getRide(@Path("id") id: String): RideOut

    @POST("v1/rides/{id}/bookings")
    suspend fun book(@Path("id") rideId: String, @Body body: BookingCreate): BookingOut

    @POST("v1/rides/{id}/start")
    suspend fun startRide(@Path("id") id: String): RideOut

    @POST("v1/rides/{id}/complete")
    suspend fun completeRide(@Path("id") id: String): RideOut

    @POST("v1/rides/{id}/cancel")
    suspend fun cancelRide(@Path("id") id: String): RideOut

    @GET("v1/rides/{id}/booking/me")
    suspend fun myBooking(@Path("id") id: String): BookingOut?

    @POST("v1/rides/{id}/ratings")
    suspend fun rate(@Path("id") id: String, @Body body: RatingCreate): Map<String, Boolean>

    @GET("v1/rides/{id}/ratings/me")
    suspend fun myRating(
        @Path("id") id: String,
        @Query("target") target: String? = null,
    ): RatingOut?

    @GET("v1/bookings/me")
    suspend fun myBookings(): List<BookingOut>

    @POST("v1/bookings/{id}/accept")
    suspend fun accept(@Path("id") id: String): BookingOut

    @POST("v1/bookings/{id}/reject")
    suspend fun reject(@Path("id") id: String): BookingOut

    @POST("v1/bookings/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): BookingOut

    @POST("v1/devices/fcm")
    suspend fun registerFcm(@Body body: FcmRegister): Map<String, Boolean>

    @GET("v1/geocode")
    suspend fun geocode(
        @Query("q") q: String,
        @Query("limit") limit: Int = 5,
    ): List<GeocodeHit>

    @GET("v1/geocode/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
    ): GeocodeHit

    @GET("v1/me/trips")
    suspend fun myTrips(): TripsOut

    @GET("v1/me/rides/{id}/location")
    suspend fun rideLocation(@Path("id") id: String): DriverLocation

    @GET("v1/rides/{id}/messages")
    suspend fun rideMessages(
        @Path("id") id: String,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 200,
    ): List<MessageOut>

    @POST("v1/rides/{id}/share")
    suspend fun createShareToken(@Path("id") id: String): ShareTokenOut

    @POST("v1/rides/{id}/incidents")
    suspend fun reportIncident(
        @Path("id") id: String,
        @Body body: IncidentCreate,
    ): IncidentOut

    @POST("v1/users/{id}/block")
    suspend fun blockUser(@Path("id") id: String): retrofit2.Response<Unit>

    @retrofit2.http.DELETE("v1/users/{id}/block")
    suspend fun unblockUser(@Path("id") id: String): retrofit2.Response<Unit>
}
