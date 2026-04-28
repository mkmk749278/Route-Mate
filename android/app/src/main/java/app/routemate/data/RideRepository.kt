package app.routemate.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    private val api: RouteMatesApi,
) {
    suspend fun search(
        from: LatLng,
        to: LatLng,
        departAfter: String? = null,
        departBefore: String? = null,
    ): List<RideOut> = api.searchRides(
        from.lat, from.lng, to.lat, to.lng, departAfter, departBefore
    )

    suspend fun create(req: RideCreate): RideOut = api.createRide(req)

    suspend fun book(rideId: String, seats: Int = 1): BookingOut =
        api.book(rideId, BookingCreate(seats = seats))

    suspend fun myBookings(): List<BookingOut> = api.myBookings()

    suspend fun start(rideId: String): RideOut = api.startRide(rideId)
    suspend fun complete(rideId: String): RideOut = api.completeRide(rideId)
}
