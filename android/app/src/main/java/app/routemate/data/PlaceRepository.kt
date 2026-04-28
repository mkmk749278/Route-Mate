package app.routemate.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val api: RouteMatesApi,
) {
    suspend fun search(query: String): List<GeocodeHit> {
        if (query.trim().length < 3) return emptyList()
        return runCatching { api.geocode(query.trim()) }.getOrDefault(emptyList())
    }
}
