package app.routemate.data

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class AuthRepository @Inject constructor(
    private val api: RouteMatesApi,
    private val store: AuthStore,
) {

    private suspend fun registerCurrentFcmToken() {
        if (!firebaseAvailable()) return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await() ?: return
            api.registerFcm(FcmRegister(token = token))
        }.onFailure { Log.w("AuthRepository", "FCM register failed", it) }
    }
    /**
     * v0.1 dev login. Backend upserts a user keyed by phone and returns
     * an app JWT. Used when Firebase isn't configured for this build.
     */
    suspend fun devLogin(phone: String, name: String? = null): MeOut {
        val res = api.devLogin(DevLoginRequest(phone = phone, name = name))
        store.setToken(res.token)
        registerCurrentFcmToken()
        return res.user
    }

    /**
     * Exchange Firebase phone-auth ID token for an app JWT. Backend verifies
     * the token via the Admin SDK and upserts a user keyed by Firebase UID.
     */
    suspend fun exchangeFirebaseToken(): MeOut {
        check(firebaseAvailable()) { "Firebase not initialised" }
        val user = FirebaseAuth.getInstance().currentUser
            ?: error("Not signed in to Firebase")
        val idToken = user.getIdToken(true).await().token
            ?: error("No Firebase ID token")
        val res = api.exchange(AuthExchange(id_token = idToken))
        store.setToken(res.token)
        registerCurrentFcmToken()
        return res.user
    }

    suspend fun signOut() {
        runCatching {
            if (firebaseAvailable()) FirebaseAuth.getInstance().signOut()
        }
        store.setToken(null)
    }

    suspend fun isSignedIn(): Boolean = !store.token().isNullOrBlank()

    private fun firebaseAvailable(): Boolean =
        runCatching { FirebaseApp.getInstance() }.isSuccess
}
