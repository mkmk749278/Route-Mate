package app.routemate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "auth")
private val TOKEN = stringPreferencesKey("app_jwt")

@Singleton
class AuthStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cached = AtomicReference<String?>(null)

    val tokenFlow: Flow<String?> = ctx.dataStore.data
        .map { it[TOKEN] }
        .onEach { cached.set(it) }

    init {
        // Warm + maintain the synchronous cache so the OkHttp interceptor
        // doesn't have to runBlocking on every HTTP call.
        scope.launch { tokenFlow.collect { /* updates cached */ } }
    }

    /** Synchronous, non-blocking read. May return null briefly on cold start
     *  before the first DataStore read lands; the interceptor treats that
     *  the same as "no token" and skips the Authorization header. */
    fun cachedToken(): String? = cached.get()

    suspend fun token(): String? = tokenFlow.first()

    suspend fun setToken(value: String?) {
        ctx.dataStore.edit { p ->
            if (value == null) p.remove(TOKEN) else p[TOKEN] = value
        }
        cached.set(value)
    }
}
