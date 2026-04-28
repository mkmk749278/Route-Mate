package app.routemate.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: RouteMatesApi,
    private val store: AuthStore,
) {
    /**
     * v0.1 dev login. Backend upserts a user keyed by phone and returns
     * an app JWT. Replaced by Firebase ID-token exchange in v0.2.
     */
    suspend fun devLogin(phone: String, name: String? = null): MeOut {
        val res = api.devLogin(DevLoginRequest(phone = phone, name = name))
        store.setToken(res.token)
        return res.user
    }

    suspend fun signOut() {
        store.setToken(null)
    }

    suspend fun isSignedIn(): Boolean = !store.token().isNullOrBlank()
}
