package app.routemate.data

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AuthRepository @Inject constructor(
    private val api: RouteMatesApi,
    private val store: AuthStore,
) {
    suspend fun exchangeFirebaseToken(): MeOut {
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Not signed in to Firebase")
        val idToken = user.getIdToken(true).await().token
            ?: error("No Firebase ID token")
        val res = api.exchange(AuthExchange(id_token = idToken))
        store.setToken(res.token)
        return res.user
    }

    suspend fun signOut() {
        FirebaseAuth.getInstance().signOut()
        store.setToken(null)
    }

    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
}
